"""
Offline Emergency Coordination — Multi-Agent Dispatch System
Each agent is stateless and runs purely on local data.
"""

from __future__ import annotations
import json
import math
from dataclasses import dataclass, field
from typing import Optional

# ─────────────────────────────────────────────
# Shared data types
# ─────────────────────────────────────────────
@dataclass
class Location:
    lat: float
    lon: float

    def distance_km(self, other: "Location") -> float:
        """Haversine distance in kilometres."""
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
    event_type: str
    severity: str
    location: Location
    timestamp: int
    ttl: int = 3

@dataclass
class AgentResult:
    agent: str
    success: bool
    data: dict
    reason: str = ""

@dataclass
class DispatchPlan:
    packet_id: str
    ambulance_id: Optional[str]
    hospital_id: Optional[str]
    hospital_name: Optional[str]
    route_summary: Optional[str]
    police_unit: Optional[str]
    tow_unit: Optional[str]
    priority_order: list[str]
    notes: list[str] = field(default_factory=list)

# ------------------------------
# Place Holder Database
# ------------------------------

LOCAL_RESOURCES = {
    "ambulances": [
        {"id": "AMB-01", "location": Location(27.710, 85.310), "available": True},
        {"id": "AMB-02", "location": Location(27.730, 85.350), "available": False},
        {"id": "AMB-03", "location": Location(27.700, 85.340), "available": True},
    ],
    "police_units": [
        {"id": "POL-01", "location": Location(27.720, 85.315), "available": True},
        {"id": "POL-02", "location": Location(27.705, 85.360), "available": True},
    ],
    "tow_trucks": [
        {"id": "TOW-01", "location": Location(27.715, 85.320), "available": True},
        {"id": "TOW-02", "location": Location(27.740, 85.330), "available": False},
    ],
    "hospitals": [
        {"id": "HOSP-01", "name": "Tribhuvan University Teaching Hospital",
         "location": Location(27.732, 85.330), "capacity": 12, "trauma": True},
        {"id": "HOSP-02", "name": "Bir Hospital",
         "location": Location(27.705, 85.315), "capacity": 0, "trauma": True},
        {"id": "HOSP-03", "name": "Kathmandu Model Hospital",
         "location": Location(27.718, 85.355), "capacity": 5, "trauma": False},
    ],
}
# ─────────────────────────────────────────────
# Base agent
# ─────────────────────────────────────────────
class BaseAgent:
    name: str = "base"

    def run(self, packet: EmergencyPacket, context: dict) -> AgentResult:
        raise NotImplementedError

    def _nearest_available(self, units: list[dict], origin: Location) -> Optional[dict]:
        available = [u for u in units if u["available"]]
        if not available:
            return None
        return min(available, key=lambda u: origin.distance_km(u["location"]))

# ─────────────────────────────────────────────
# Specialized agents
# ─────────────────────────────────────────────
class AmbulanceAgent(BaseAgent):
    name = "ambulance"
    def run(self, packet: EmergencyPacket, context: dict) -> AgentResult:
        units = LOCAL_RESOURCES["ambulances"]
        primary = self._nearest_available(units, packet.location)

        if not primary:
            return AgentResult(self.name, False, {}, reason="No ambulances available")

        dist = packet.location.distance_km(primary["location"])
        result = {
            "primary_unit": primary["id"],
            "distance_km": round(dist, 2),
            "eta_minutes": round((dist / 60) * 60, 1),  # assume 60 km/h
        }

        # For critical events, also dispatch a backup unit
        if packet.severity == "critical":
            remaining = [u for u in units if u["available"] and u["id"] != primary["id"]]
            if remaining:
                backup = min(remaining, key=lambda u: packet.location.distance_km(u["location"]))
                result["backup_unit"] = backup["id"]

        return AgentResult(self.name, True, result)


class PoliceAgent(BaseAgent):
    name = "police"

    def run(self, packet: EmergencyPacket, context: dict) -> AgentResult:
        units = LOCAL_RESOURCES["police_units"]
        unit = self._nearest_available(units, packet.location)

        if not unit:
            return AgentResult(self.name, False, {}, reason="No police units available")

        needs_closure = packet.severity in ("critical", "high")
        return AgentResult(self.name, True, {
            "unit_id": unit["id"],
            "road_closure_required": needs_closure,
            "action": "traffic_diversion" if needs_closure else "scene_control",
        })


class TowAgent(BaseAgent):
    name = "tow"

    COLLISION_TYPES = {"multi_vehicle_collision", "single_vehicle_accident", "vehicle_rollover"}

    def run(self, packet: EmergencyPacket, context: dict) -> AgentResult:
        if packet.event_type not in self.COLLISION_TYPES:
            return AgentResult(self.name, True, {"dispatched": False},
                               reason="Event type does not require tow")

        units = LOCAL_RESOURCES["tow_trucks"]
        unit = self._nearest_available(units, packet.location)

        if not unit:
            return AgentResult(self.name, False, {}, reason="No tow trucks available")

        return AgentResult(self.name, True, {
            "dispatched": True,
            "unit_id": unit["id"],
            "distance_km": round(packet.location.distance_km(unit["location"]), 2),
        })


class RoutingAgent(BaseAgent):
    name = "routing"

    def run(self, packet: EmergencyPacket, context: dict) -> AgentResult:
        target_id = context.get("hospital_id")
        if not target_id:
            return AgentResult(self.name, False, {}, reason="No hospital selected yet")

        hospital = next(
            (h for h in LOCAL_RESOURCES["hospitals"] if h["id"] == target_id), None
        )
        if not hospital:
            return AgentResult(self.name, False, {}, reason=f"Hospital {target_id} not found")

        dist = packet.location.distance_km(hospital["location"])

        # In production: call GraphHopper /route API on localhost with OSM data
        # response = requests.get("http://localhost:8989/route", params={...})
        route_summary = (
            f"Accident ({packet.location.lat:.4f}, {packet.location.lon:.4f}) -> "
            f"{hospital['name']} | {dist:.1f} km | "
            f"ETA ~{round((dist/60)*60, 0):.0f} min"
        )

        return AgentResult(self.name, True, {
            "distance_km": round(dist, 2),
            "eta_minutes": round((dist / 60) * 60, 1),
            "route_summary": route_summary,
            "algorithm": "dijkstra",  # or A* depending on graph size
        })


class HospitalAgent(BaseAgent):
    name = "hospital"

    def run(self, packet: EmergencyPacket, context: dict) -> AgentResult:
        hospitals = LOCAL_RESOURCES["hospitals"]

        # First pass: trauma centre with capacity
        candidates = [h for h in hospitals if h["trauma"] and h["capacity"] > 0]

        # Fallback: any hospital with capacity
        if not candidates:
            candidates = [h for h in hospitals if h["capacity"] > 0]

        if not candidates:
            return AgentResult(self.name, False, {}, reason="All hospitals at capacity")

        best = min(candidates, key=lambda h: packet.location.distance_km(h["location"]))
        dist = packet.location.distance_km(best["location"])

        return AgentResult(self.name, True, {
            "hospital_id": best["id"],
            "hospital_name": best["name"],
            "distance_km": round(dist, 2),
            "remaining_capacity": best["capacity"],
            "is_trauma_centre": best["trauma"],
        })


# ─────────────────────────────────────────────
# Edge orchestrator
# ─────────────────────────────────────────────

class EdgeOrchestrator:

    def __init__(self):
        self.agents: list[BaseAgent] = [
            HospitalAgent(),
            RoutingAgent(),
            AmbulanceAgent(),
            PoliceAgent(),
            TowAgent(),
        ]

    def run(self, packet: EmergencyPacket) -> DispatchPlan:
        context: dict = {}
        results: dict[str, AgentResult] = {}
        notes: list[str] = []

        # Sequential fan-out (hospital → routing → rest can be parallelised in production)
        for agent in self.agents:
            result = agent.run(packet, context)
            results[agent.name] = result

            # Routing depends on hospital selection — pass it forward via context
            if agent.name == "hospital" and result.success:
                context["hospital_id"] = result.data.get("hospital_id")

            if not result.success:
                notes.append(f"[{agent.name.upper()}] {result.reason}")

        # Merge results into dispatch plan
        hosp = results.get("hospital")
        routing = results.get("routing")
        amb = results.get("ambulance")
        pol = results.get("police")
        tow = results.get("tow")

        priority_order = self._priority_order(packet.severity, results)

        return DispatchPlan(
            packet_id=packet.packet_id,
            ambulance_id=amb.data.get("primary_unit") if amb and amb.success else None,
            hospital_id=hosp.data.get("hospital_id") if hosp and hosp.success else None,
            hospital_name=hosp.data.get("hospital_name") if hosp and hosp.success else None,
            route_summary=routing.data.get("route_summary") if routing and routing.success else None,
            police_unit=pol.data.get("unit_id") if pol and pol.success else None,
            tow_unit=tow.data.get("unit_id") if tow and tow.success and tow.data.get("dispatched") else None,
            priority_order=priority_order,
            notes=notes,
        )

    def _priority_order(self, severity: str, results: dict[str, AgentResult]) -> list[str]:
        """Returns dispatch priority order based on severity and agent outcomes."""
        base_order = {
            "critical": ["ambulance", "hospital", "police", "routing", "tow"],
            "high":     ["ambulance", "police", "hospital", "routing", "tow"],
            "medium":   ["police", "ambulance", "hospital", "routing", "tow"],
            "low":      ["police", "tow", "ambulance", "hospital", "routing"],
        }
        return base_order.get(severity, base_order["medium"])


# ─────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────

if __name__ == "__main__":
    packet = EmergencyPacket(
        packet_id="pkt_102",
        event_type="multi_vehicle_collision",
        severity="critical",
        location=Location(lat=27.7172, lon=85.3240),
        timestamp=1748419200,
        ttl=3,
    )

    orchestrator = EdgeOrchestrator()
    plan = orchestrator.run(packet)

    print(json.dumps({
        "packet_id":      plan.packet_id,
        "ambulance":      plan.ambulance_id,
        "hospital":       plan.hospital_name,
        "route":          plan.route_summary,
        "police":         plan.police_unit,
        "tow":            plan.tow_unit,
        "priority_order": plan.priority_order,
        "notes":          plan.notes,
    }, indent=2))