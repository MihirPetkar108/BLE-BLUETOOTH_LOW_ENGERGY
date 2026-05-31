"""
Offline Emergency Coordination — North East India Core
Reads hospitals/police CSVs from emergency-routing/data (with local fallbacks), routes via local GraphHopper.
"""

import json
import requests
import math
import os
import csv
import argparse
from dataclasses import dataclass, field
from typing import Optional

# ─────────────────────────────────────────────
# 1. Core Data Types
# ─────────────────────────────────────────────
@dataclass
class Location:
    lat: float
    lon: float

    def distance_km(self, other: "Location") -> float:
        R = 6371
        dlat = math.radians(other.lat - self.lat)
        dlon = math.radians(other.lon - self.lon)
        a = (math.sin(dlat / 2) ** 2
             + math.cos(math.radians(self.lat))
             * math.cos(math.radians(other.lat))
             * math.sin(dlon / 2) ** 2)
        return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

@dataclass
class EmergencyPacket:
    packet_id: str
    severity: str
    location: Location

@dataclass
class AgentResult:
    agent: str
    success: bool
    data: dict
    reason: str = ""

# ─────────────────────────────────────────────
# 2. Database Loader
# ─────────────────────────────────────────────
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "emergency-routing", "data")


def resolve_existing_path(candidates: list[str]) -> Optional[str]:
    for path in candidates:
        if os.path.exists(path):
            return path
    return None


def load_database() -> dict:
    db = {"hospitals": [], "police": [], "ambulances": [], "tow_trucks": []}
    
    def parse_csv(filename: str, category: str, fallback_name: str):
        if os.path.exists(filename):
            with open(filename, "r", encoding="utf-8-sig") as f:
                reader = csv.DictReader(f)
                for i, row in enumerate(reader):
                    # THE FIX: Scrub the '@' symbol and spaces out of the column headers
                    keys = {k.lower().strip().replace('@', ''): k for k in row.keys() if k}
                    
                    lat_col = keys.get("lat") or keys.get("latitude") or keys.get("y")
                    lon_col = keys.get("lon") or keys.get("longitude") or keys.get("x")
                    name_col = keys.get("name") or keys.get("amenity") or keys.get("hospital_name")
                    
                    if lat_col and lon_col and row[lat_col] and row[lon_col]:
                        try:
                            item = {
                                "id": f"{category[:4].upper()}-{i}",
                                "location": Location(float(row[lat_col]), float(row[lon_col])),
                                "available": True # Default to true for logic
                            }
                            if category == "hospitals":
                                item["name"] = row[name_col] if name_col and row[name_col] else f"{fallback_name}_{i}"
                                item["capacity"] = 10 # Mock capacity
                                item["trauma"] = True # Mock trauma capability
                            else:
                                item["name"] = row[name_col] if name_col and row[name_col] else f"{fallback_name}_{i}"
                                
                            db[category].append(item)
                        except ValueError:
                            continue # Skip rows with corrupted GPS numbers
        else:
            print(f"⚠️ Missing file: {filename}")

    # Prefer emergency-routing/data CSVs, then fall back to legacy local names.
    hospitals_csv = resolve_existing_path([
        os.path.join(DATA_DIR, "hospitals_raw - hospitals_raw.csv"),
        os.path.join(BASE_DIR, "hospital.csv")
    ])
    police_csv = resolve_existing_path([
        os.path.join(DATA_DIR, "police_ne - interpreter.csv.csv"),
        os.path.join(BASE_DIR, "police.csv")
    ])

    if hospitals_csv:
        parse_csv(hospitals_csv, "hospitals", "Hospital")
    else:
        print("⚠️ Missing hospital CSV in emergency-routing/data and project root")

    if police_csv:
        parse_csv(police_csv, "police", "Police_Station")
    else:
        print("⚠️ Missing police CSV in emergency-routing/data and project root")

    # Local vehicle state targets for Guwahati mapping (Fakes so it doesn't crash)
    db["ambulances"] = [{"id": "AMB-01", "location": Location(26.140, 91.730), "available": True}]
    db["tow_trucks"] = [{"id": "TOW-01", "location": Location(26.142, 91.720), "available": True}]
    
    return db

LOCAL_DB = load_database()

# ─────────────────────────────────────────────
# 3. Agents
# ─────────────────────────────────────────────
class BaseAgent:
    name: str = "base"
    def run(self, packet: EmergencyPacket, context: dict) -> AgentResult:
        raise NotImplementedError

    def _nearest(self, units: list[dict], origin: Location) -> Optional[dict]:
        valid = [u for u in units if u.get("available", True)]
        if not valid: return None
        return min(valid, key=lambda u: origin.distance_km(u["location"]))

class AmbulanceAgent(BaseAgent):
    name = "ambulance"
    def run(self, packet, context):
        # Prefer dispatching an ambulance that belongs to the selected hospital.
        hosp_id = context.get("hospital_id")
        if hosp_id:
            hospital = next((h for h in LOCAL_DB["hospitals"] if h["id"] == hosp_id), None)
            if hospital:
                amb_id = f"AMB-{hosp_id}"
                return AgentResult(
                    self.name,
                    True,
                    {
                        "unit_id": amb_id,
                        "hospital_id": hosp_id,
                        "hospital_name": hospital.get("name")
                    }
                )

        # Fallback if hospital context is unavailable.
        unit = self._nearest(LOCAL_DB["ambulances"], packet.location)
        if not unit:
            return AgentResult(self.name, False, {}, "No ambulances available")
        return AgentResult(self.name, True, {"unit_id": unit["id"]})

class PoliceAgent(BaseAgent):
    name = "police"
    def run(self, packet, context):
        unit = self._nearest(LOCAL_DB["police"], packet.location)
        if not unit: return AgentResult(self.name, False, {}, "No police available in CSV")
        return AgentResult(self.name, True, {"unit_id": unit["id"], "name": unit.get("name")})

class TowAgent(BaseAgent):
    name = "tow"
    def run(self, packet, context):
        unit = self._nearest(LOCAL_DB["tow_trucks"], packet.location)
        if not unit: return AgentResult(self.name, False, {}, "No tow trucks available")
        return AgentResult(self.name, True, {"unit_id": unit["id"]})

class HospitalAgent(BaseAgent):
    name = "hospital"
    def run(self, packet, context):
        if not LOCAL_DB["hospitals"]: 
            return AgentResult(self.name, False, {}, "Hospital CSV is empty or missing Lat/Lon columns")
        
        best = min(LOCAL_DB["hospitals"], key=lambda h: packet.location.distance_km(h["location"]))
        return AgentResult(self.name, True, {"hospital_id": best["id"], "hospital_name": best["name"]})

class RoutingAgent(BaseAgent):
    name = "routing"
    def run(self, packet, context):
        hosp_id = context.get("hospital_id")
        if not hosp_id: return AgentResult(self.name, False, {}, "No destination target selected")

        hospital = next((h for h in LOCAL_DB["hospitals"] if h["id"] == hosp_id), None)
        
        try:
            response = requests.post("http://localhost:8989/route", json={
                "points": [[packet.location.lon, packet.location.lat], [hospital["location"].lon, hospital["location"].lat]],
                "profile": "car",
                "calc_points": False
            }).json()
            
            if "paths" not in response:
                return AgentResult(self.name, False, {}, f"GraphHopper Routing Error: {response.get('message', 'Unknown')}")
            
            path = response["paths"][0]
            dist_km = path["distance"] / 1000
            eta_mins = path["time"] / 60000
            return AgentResult(self.name, True, {"route_summary": f"{dist_km:.1f} km, ETA {eta_mins:.0f} mins"})
        except Exception as e:
            return AgentResult(self.name, False, {}, f"GraphHopper offline server disconnected: {e}")


def parse_coord(text: str) -> tuple[float, float]:
    parts = [p.strip() for p in text.split(",")]
    if len(parts) != 2:
        raise ValueError("Coordinate must be in 'lat,lon' format")
    return float(parts[0]), float(parts[1])


def run_dispatch(packet_id: str, lat: float, lon: float, severity: str = "critical") -> dict:
    crash = EmergencyPacket(packet_id, severity, Location(lat, lon))
    agents = [HospitalAgent(), RoutingAgent(), AmbulanceAgent(), PoliceAgent(), TowAgent()]
    context, results, notes = {}, [], []

    for agent in agents:
        res = agent.run(crash, context)
        results.append(res)
        if agent.name == "hospital" and res.success:
            context["hospital_id"] = res.data.get("hospital_id")
        if not res.success:
            notes.append(f"[{agent.name.upper()}] {res.reason}")

    res_dict = {r.agent: r for r in results}

    police_output = None
    if res_dict.get("police") and res_dict["police"].success:
        police_unit = res_dict["police"].data.get("unit_id")
        police_name = res_dict["police"].data.get("name")
        police_output = f"{police_unit} ({police_name})" if police_name else police_unit

    return {
        "packet_id": crash.packet_id,
        "input": {"lat": lat, "lon": lon},
        "ambulance": res_dict["ambulance"].data.get("unit_id") if res_dict.get("ambulance") and res_dict["ambulance"].success else None,
        "hospital": res_dict["hospital"].data.get("hospital_name") if res_dict.get("hospital") and res_dict["hospital"].success else None,
        "route_eta": res_dict["routing"].data.get("route_summary") if res_dict.get("routing") and res_dict["routing"].success else None,
        "police": police_output,
        "tow": res_dict["tow"].data.get("unit_id") if res_dict.get("tow") and res_dict["tow"].success else None,
        "notes": notes
    }

# ─────────────────────────────────────────────
# 4. Orchestrator Engine Execution
# ─────────────────────────────────────────────
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Offline emergency coordinator")
    parser.add_argument(
        "--coord",
        action="append",
        help="Coordinate in 'lat,lon' format. Repeat for multiple incidents."
    )
    parser.add_argument(
        "--severity",
        default="critical",
        help="Severity label for generated packets"
    )
    args = parser.parse_args()

    coord_inputs = args.coord if args.coord else ["26.1445,91.7362"]

    plans = []
    for idx, coord_text in enumerate(coord_inputs, start=1):
        try:
            crash_lat, crash_lon = parse_coord(coord_text)
        except ValueError as e:
            print(json.dumps({"error": str(e), "coord": coord_text}, indent=2))
            raise SystemExit(2)

        packet_id = f"pkt_{idx:03d}"
        plans.append(run_dispatch(packet_id, crash_lat, crash_lon, severity=args.severity))

    output = plans[0] if len(plans) == 1 else plans
    print("\n" + json.dumps(output, indent=2))