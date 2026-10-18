# Setup, debug, and run

HomeOS runs on one laptop. FalkorDB, the agents, and the dashboard come up together. The watch and the phone join that laptop over the LAN. There is no hosted deployment: the watch, the ring, and the room node are hardware on a private network, so the submission is this repository plus the steps below.

## Requirements

| Need | Why |
|---|---|
| Docker | FalkorDB and, optionally, the backend container |
| Node.js 20 or newer | Agents, write API, MCP server, dashboard |
| An Azure OpenAI key | Planner and Coach. Deployments: `gpt-5.4-mini` and `text-embedding-3-small` on a v1 endpoint |
| Sarvam key | Optional. Empty means the watch speaks with its own TTS |
| JDK 17 or 21 and Android SDK | Only if you install the watch or phone app |

## First run

From the repository root:

```bash
cp .env.example .env
```

Fill `AZURE_OPENAI_BASE_URL` and `AZURE_OPENAI_API_KEY`. Leave `SARVAM_API_KEY` empty if you do not have one. `HOMEOS_GRAPH` stays `on`.

```bash
docker compose up -d --build
```

| URL | What it is |
|---|---|
| http://localhost:3001 | Dashboard, HTTP API, watch and phone WebSocket |
| http://localhost:3000 | FalkorDB Browser |
| `localhost:6379` | FalkorDB (Redis protocol) |

The backend listens on `0.0.0.0:3001`. On first start it loads the UDF library, creates one ACL user per person, seeds `home:flat1` and `body:<alias>`, and imports PMData participant `p01` if `HOMEOS_DATASET_PEOPLE=p01` and that graph is empty.

Confirm:

```bash
curl http://127.0.0.1:3001/api/ping
curl http://127.0.0.1:3001/api/stats
```

`ping` returns `{"ok":true,"graph":true,...}`. `stats` returns a node and edge count for `home:flat1` and each `body:<alias>`. Those counts are live. They are not a fixed header.

## Development, without rebuilding the image

```bash
docker compose up -d falkordb
cd backend && npm ci && npm run dev
```

In another terminal, the dashboard with hot reload:

```bash
cd ui && npm ci && npm run dev
```

Vite serves http://localhost:5173 and proxies `/api` and the WebSocket to port 3001.

## Debug

**FalkorDB Browser.** Open http://localhost:3000. `GRAPH.LIST` should include `home:flat1`, `body:harsh`, `body:shashwath`, and `body:p01` after the dataset load. Run a read as a sanity check:

```cypher
GRAPH.RO_QUERY body:harsh
"MATCH (p:Person)-[:HAS_EVENT]->(e:Event) RETURN e.title, e.at"
```

If that returns rows, the morning query has something to walk. If it returns nothing, no calendar event has been written yet. That is an empty traversal, not a failure.

**Backend log.** The process prints one line per feed event: connect, condition, decision, say. A stack trace prefixed `[guardian:harsh]` or `[falkordb:agent_harsh]` is the first place to look.

**Ablation.** `HOMEOS_GRAPH=off` in `.env`, then restart the backend. The same chips hit fixed rules. The dashboard banner says memory is off. Turn it back on after. This is the comparison in `docs/results/ablation.md`.

**ACL.** `agent_harsh` can read and write only `body:harsh`. Asking it to read `body:shashwath` returns `NOPERM` from FalkorDB. `home_reader` can run `GRAPH.RO_QUERY` on `home:*` and cannot write. Users are created in `backend/src/graph/setup.ts`.

**Watch cannot reach the laptop.** The watch app reads `homeos.ws` from `apps/watch/local.properties` (gitignored). Set it to `ws://<laptop-LAN-ip>:3001/ws/device` and `homeos.alias` to `harsh` or `shashwath`. The laptop and the watch must be on the same subnet. Windows Firewall must allow inbound TCP 3001 on private networks. `curl http://<laptop-LAN-ip>:3001/api/ping` from another machine is the check. Localhost succeeding does not prove the LAN path.

**Voice says on-device.** `SARVAM_API_KEY` is empty. Speech still happens. The text is the same string the backend built from the query.

**Graph queries.** Every query the agents run is a file in `graph/queries/`. The name in code (`bodyRead(alias, "morning_case", ...)`) is the filename. Paste the file into FalkorDB Browser, substitute parameters, and read the rows before changing TypeScript.

**Tests.** From `backend/`, with FalkorDB up: `npm test`. Integration tests create their own graphs and assert ACL denial, the lease race, and lost-update behaviour.

## Calendar and sleep, the morning path

Nights enter through the phone:

```bash
adb shell am broadcast -n com.homeos.bridge/.SyncReceiver --el days 120
```

That posts Health Connect history to `POST /api/health/:alias`. Each night becomes a `sleep` episode.

A calendar event enters through the write API, which runs `upsert_calendar_event.cypher`:

```bash
curl -X POST http://127.0.0.1:3001/api/calendar/harsh \
  -H "content-type: application/json" \
  -d "{\"id\":\"cal-harsh-standup\",\"title\":\"Morning standup\",\"at\":1792463400000,\"source\":\"google_calendar\"}"
```

`at` is epoch milliseconds. The watch chip **Morning alarm**, or `POST /api/morning/harsh`, runs `morning_case.cypher`. The reply is spoken from those rows. Yes on the watch runs `record_morning_decision.cypher` and writes `alarm_primary` and `alarm_backup` onto the bedroom clock.

A reviewer without the ring can load five bad nights and one event through the same write functions:

```bash
cd backend && npx tsx scripts/morning-e2e.ts harsh
```

That script is a loader. It is not the decision. The decision is the Cypher.

## Replay and the public dataset

```bash
cd backend && npx tsx scripts/import-pmdata.ts --batch ../replay/datasets/pmdata-p01.json --alias p01
cd backend && npx tsx scripts/replay.ts ../replay/recordings/<file>.jsonl --speed 2
```

`p01` is a labelled public dataset (PMData, CC BY 4.0). That person is never present in the home and never controls a device. Details are in `replay/datasets/README.md`.

## Reset

`POST /api/reset` drops and reseeds the graphs, then reloads `p01` if configured. You lose learned evidence. Use it when the model and the seed have drifted, not as a routine restart.

## Watch and phone builds

```bash
# apps/watch/local.properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
homeos.ws=ws://<laptop-LAN-ip>:3001/ws/device
homeos.alias=harsh
```

```bash
cd apps/watch && ./gradlew :app:installDebug
cd apps/phone && ./gradlew :app:installDebug
```

Wireless debugging: `adb pair <ip>:<pairing-port> <code>`, then `adb connect <ip>:<debug-port>`. The debug port is not the pairing port.
