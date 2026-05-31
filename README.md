# StrandAid

This repository demonstrates an end-to-end, low-connectivity emergency dispatch prototype combining:

- An Android BLE Kotlin module (`app/`) that broadcasts and/or receives simple emergency telemetry from devices.
- A Python orchestrator (`agents.py`) that ingests regional CSV datasets (hospitals, police, towing), queries a locally-hosted GraphHopper router for ETAs, chooses nearest assets, and emits a structured dispatch plan.
- A locally-run GraphHopper instance (`emergency_routing/jar/graphhopper.jar`) for offline routing over the imported OSM extract.
- An optional offline summarizer (`emergency_routing/llm_summary.py`) that uses Ollama to produce concise, human-readable summaries of dispatch plans.

This README explains how the pieces fit together, installation, running, testing, and common troubleshooting tips.

**Architecture (high level)**

```mermaid
flowchart LR
  A[Android BLE app] -->|Telemetry| B(Orchestrator / agents.py)
  B -->|Route requests| C[GraphHopper (local server)]
  B -->|Summarize| D[Ollama local LLM]
  B -->|Dispatch plan| E[Operator Console / Logs]
```

## Prerequisites

- Java 17+ (Temurin 21 recommended) — required for GraphHopper.
- Python 3.10+ and `pip`.
- Android SDK + Gradle (for building the app).
- (Optional) Ollama + a local LLM model if you want on-device/offline summaries.

## Files of interest

- `agents.py` — Python orchestrator, main entrypoint to produce a dispatch plan.
- `emergency_routing/` — GraphHopper config, jar, and `data/` folder for OSM/CSV files.
- `emergency_routing/llm_summary.py` — Optional offline summarizer that calls `ollama`.
- `app/` — Android BLE Kotlin application.
- `requirements.txt` — Python dependencies.
- `.gitignore` — updated to ignore GraphHopper caches, jars, and OS/editor artifacts.

## Setup — Python environment

1. Create a virtual environment (recommended):

```bash
python3 -m venv .venv
source .venv/bin/activate
```

2. Install Python dependencies:

```bash
python3 -m pip install --upgrade pip
python3 -m pip install -r requirements.txt
```

Dependencies included:

- `requests` — HTTP calls to GraphHopper.
- `ollama` — optional client for Ollama local LLM.

If you won't use the LLM, you can omit `ollama` by removing it from `requirements.txt` before installing.

## Data

Place your dataset files into `emergency_routing/data/`. The loader is resilient to common header variations (it normalizes `@lat/@lon` and similar headers), but filenames are currently matched explicitly — either use the repo's sample filenames or update `agents.py`.

Expected CSVs (examples)

- `hospitals_raw - hospitals_raw.csv` — hospital name, lat, lon (extra columns are fine).
- `police_ne - interpreter.csv.csv` — police locations (loader normalizes @-prefixed headers).
- `towing_carrepair_ne - interpreter (1).csv.csv` — tow/car-repair locations used as tow assets.

Add any `.osm.pbf` extracts you need for GraphHopper into `emergency_routing/data/`.

## Start GraphHopper (required for routing)

1. From repo root:

```bash
cd emergency_routing
```

2. Run GraphHopper server (adjust memory as needed):

```bash
java -Xmx1500m -Xms256m -jar jar/graphhopper.jar server config.yml
```

3. Wait for the import and CH/LM preparation to finish. Successful startup ends with a log like `Started Server` and the web UI becomes available at `http://localhost:8989/maps`.

Notes

- If GraphHopper fails at startup, inspect `config.yml`. For custom weighting you must either include a valid custom model file (e.g., `car.json`) or ensure `profiles` do not use `weighting: custom` without a model.

## (Optional) Offline LLM with Ollama

If you want automatic summaries included in the plan output, run Ollama locally and pull a model:

```bash
# Install Ollama per https://ollama.com/docs
ollama pull llama3.2:3b
ollama serve
```

`emergency_routing/llm_summary.py` calls `ollama.chat` with `llama3.2:3b`. If Ollama or the model is not present, consider applying the suggested fallback patch so missing LLM service does not crash the orchestrator.

## Run the Python orchestrator

From the repository root (with virtualenv active):

```bash
python3 agents.py
```

Output

- The script prints a JSON dispatch plan to stdout with keys: `packet_id`, `ambulance`, `hospital`, `route_eta`, `police`, `tow`, `notes`.
- If Ollama is running and reachable, `notes` will include an `[LLM SUMMARY]` entry.

## Android app (BLE)

Build in Android Studio or via Gradle:

```bash
./gradlew :app:assembleDebug
```

Important runtime notes

- The manifest (`app/src/main/AndroidManifest.xml`) includes modern BLE permissions: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` and location permissions. On Android 12+ you must request these at runtime; the app must implement permission prompts.
- Modify `BleAdvertiser.kt` / `BleScanner.kt` to match the telemetry payload you want to broadcast or parse.

## Testing & Validation

1. Start GraphHopper and confirm `/route` works using `curl`:

```bash
curl -s -X POST "http://localhost:8989/route" -H "Content-Type: application/json" -d '{"points":[[91.7362,26.1445],[91.7859,26.1310]],"profile":"car","calc_points":false}' | jq
```

2. Run `python3 agents.py` and confirm the plan contains valid `hospital` and `route_eta` values.

3. If testing BLE flows, run the Android app on a device or emulator with BLE support and verify telemetry is emitted and received.

## Troubleshooting

- Python import errors: run `pip install -r requirements.txt` inside an active virtualenv.
- Ollama errors: ensure `ollama serve` is running and the model is installed. If you can't run Ollama, disable the summary or patch `llm_summary.py` to return a deterministic fallback.
- GraphHopper routing errors: verify the OSM extract matches your geographic area; the geocoder only resolves locations inside the imported extract.
- Filenames with spaces/parentheses: loader supports the current dataset names, but renaming files can break detection — either normalize filenames or update `agents.py`.

## Suggested next steps (optional)

- Add a safe fallback in `emergency_routing/llm_summary.py` so missing Ollama does not crash the run (I can patch this now).
- Add a `docker-compose.yml` to orchestrate GraphHopper + Ollama + the orchestrator for reproducible runs.
- Add unit tests for `load_database()` header normalization and `RoutingAgent` behavior.

If you want, I can implement the safe fallback for the LLM now or scaffold a Docker Compose to run everything together.

