# HomeOS: a home with a nervous system

**Graph Hacks 2026 (WeMakeDevs × FalkorDB). Entered in Track 1 (Agents That Act on Connected Data) and Track 2 (Agent Memory and Coordination).**

Your wearables know when you're sick, stressed or overheated. Your home doesn't.

HomeOS links a **Galaxy Watch**, a **Gabit smart ring** and **room sensors** to a team of agents. Each person gets a **private FalkorDB memory** that learns what actually helps *their* body. The home's agents agree on the room through a **shared graph** that never contains anyone's reasons. Ask your watch *"why?"* and the answer is traced from the graph.

> Demo video: _link added on submission_ · Dashboard: `http://localhost:3001` · FalkorDB Browser: `http://localhost:3000`

<p align="center">
  <img src="docs/img/dashboard-scientist.png" alt="HomeOS dashboard: p01's private graph and the Scientist's PageRank, betweenness, label-propagation and SPpaths findings" width="78%" />
  <img src="docs/img/watch.png" alt="HomeOS on a Galaxy Watch4 answering 'What helps me when I'm sick?' with Sarvam voice" width="20%" />
</p>

```
"I'm feeling sick" (watch)  ─▶  private Condition{sick} in body:shashwath
                              ─▶ fever ⇒ sick ⇒ SHIFTS room_temp ≥ 26 °C, airflow ≤ 1   (multi-hop, graph data)
                              ─▶ only "room_temp 26–27, priority 3" is shared into home:flat1   (no reason, ever)
Harsh finishes a workout      ─▶ his planner wants the AC colder; the arbiter's multi-hop query says 26 °C is the floor
                              ─▶ fan to level 1 + a 5.5 breaths/min haptic pacer on his watch instead
"Why isn't it colder?"        ─▶ explain(): "someone present needs it ≥ 26 °C" — who and why stay private
3 minutes later               ─▶ the evaluator measures his HR recovery and updates Evidence with an atomic Welford UDF
Restart everything            ─▶ "what helps me when I'm sick?" — answered from the playbook, episodes and past turns
```

---

## Tracks

Every row points to the code and the Cypher that does the work. Full write-ups: [`docs/TRACK1.md`](docs/TRACK1.md), [`docs/TRACK2.md`](docs/TRACK2.md).

### Track 1: Agents That Act on Connected Data

| Official focus | What HomeOS does | Where (code · Cypher) |
|---|---|---|
| **Multi-hop reasoning** | Arbitration walks Room → Device -CONTROLS→ Var -AFFECTS→ Signal, and Room ←IN- Person -HAS_CONSTRAINT→ Constraint, then resolves conflicting ranges by priority (`HomeOS.resolveRanges` UDF). A person's needs come from Person → Condition -IMPLIES*0..3→ Condition -SHIFTS→ Var. | [`feasible_settings.cypher`](graph/queries/feasible_settings.cypher) · [`effective_comfort.cypher`](graph/queries/effective_comfort.cypher) · [`agents/arbiter.ts`](backend/src/agents/arbiter.ts) |
| **Graph queries & algorithms as agent tools** | 19 typed tools over MCP. They include `why_bad_night` (**algo.SPpaths** over −ln p weights), `drivers` (**algo.pageRank**), `keystone` (**algo.betweenness**), `episode_types` (**algo.labelPropagation**), `impact` (**algo.bfs**) and `similar_episodes` (**vector index** + shared bands). On 150 real nights the full analysis takes 64 ms ([results](#real-data-results-the-scientist)). | [`tools/index.ts`](backend/src/tools/index.ts) · [`why_bad_night.cypher`](graph/queries/why_bad_night.cypher) · [`drivers.cypher`](graph/queries/drivers.cypher) |
| **Tool selection through MCP** | Each person's agents attach to **homeos-mcp** (our server, scoped to that person's ACL user) and the **official FalkorDB MCP server** (read-only, `home_reader` user). Azure OpenAI chooses among both sets of tools. | [`mcp/server.ts`](backend/src/mcp/server.ts) · [`llm/agent.ts`](backend/src/llm/agent.ts) |
| **Actions based on graph results** | AC, fan, light and the watch's breathing pacer are driven only by `choose_intervention` (Evidence + Thompson-sampling UDF) clamped by `feasible_settings`, then applied under an atomic device lease. | [`choose_intervention.cypher`](graph/queries/choose_intervention.cypher) · [`agents/planner.ts`](backend/src/agents/planner.ts) · [`acquire_device.cypher`](graph/queries/acquire_device.cypher) |
| **Explainable results** | Every Decision stores its provenance: context bands, chosen and rejected options with reasons, evidence used (n, mean) and the constraints that bounded it. `explain` walks it. The owner sees their private reasons; everyone else sees "someone present". | [`explain.cypher`](graph/queries/explain.cypher) · [`explain_arbitration.cypher`](graph/queries/explain_arbitration.cypher) · [`explain_paths.cypher`](graph/queries/explain_paths.cypher) |

### Track 2: Agent Memory and Coordination

| Official focus | What HomeOS does | Where |
|---|---|---|
| **Episodic memory** | Episodes (live and every ring night) → Decisions → Actions → OUTCOME edges with measured effects | [`create_episode.cypher`](graph/queries/create_episode.cypher) · [`learn.cypher`](graph/queries/learn.cypher) |
| **Semantic memory** | Baselines, conditions, preferences and dislikes as **bi-temporal** edges (`valid_from`/`valid_to`, never overwritten); device→variable→body priors with source URLs | [`set_baseline.cypher`](graph/queries/set_baseline.cypher) · [`belief_diff.cypher`](graph/queries/belief_diff.cypher) · [`seed/catalog.json`](graph/seed/catalog.json) |
| **Procedural memory** | Versioned Playbooks (`SUPERSEDES` chain), rewritten by the Scientist when a person's own outcomes disagree | [`publish_playbook.cypher`](graph/queries/publish_playbook.cypher) · [`agents/scientist.ts`](backend/src/agents/scientist.ts) |
| **Persistent conversation history** | Session -HAS_TURN→ Turn -ABOUT→ episode / decision / condition, with **vector + full-text** recall that survives restarts | [`add_turn.cypher`](graph/queries/add_turn.cypher) · [`recall_turns.cypher`](graph/queries/recall_turns.cypher) |
| **Shared state & handoffs** | Agents talk **only through the graph**: Task nodes with atomic claims and `HANDOFF_FROM` edges (Guardian → Planner → Arbiter → Planner → Evaluator); Intents; leases; a stalled agent's task is reclaimed | [`claim_task.cypher`](graph/queries/claim_task.cypher) · [`agents/worker.ts`](backend/src/agents/worker.ts) |
| **High-concurrency reads & writes** | Measured: 140 mixed operations at **~1,090 ops/s**. 40 concurrent Welford updates to the same Evidence nodes all landed (n +40, no lost updates), 30 workers each claimed one of 20 tasks exactly once, and 1 of 30 lease contenders won | [`test/memory.test.ts`](backend/test/memory.test.ts) · [`docs/results/concurrency.json`](docs/results/concurrency.json) |
| **Separate memory per user/tenant** | One graph per person (`body:<alias>`) and a shared `home:flat1`. Isolation is enforced **by FalkorDB ACL**: `agent_bob` gets `NOPERM` on `body:alice`, and agents can only `GRAPH.RO_QUERY` the home graph | [`graph/setup.ts`](backend/src/graph/setup.ts) · [`test/core.test.ts`](backend/test/core.test.ts) |

### Proof the graph is load-bearing: ablation

The same two-person scenario was run with `HOMEOS_GRAPH=on` and with FalkorDB removed (`HOMEOS_GRAPH=off`, fixed rules). Source: [`docs/results/ablation.md`](docs/results/ablation.md), generated by [`scripts/ablation.ts`](backend/scripts/ablation.ts).

| Step | FalkorDB on | FalkorDB off |
|---|---|---|
| Alice: "I'm sick" | AC 27 °C, fan off; within her own sick-day range | AC 27 °C |
| Bob finishes a workout | AC **stays 27 °C** (Alice's floor wins by priority); Bob gets the fan at level 1 plus a 5.5/min pacer | AC dropped to **22 °C** on a sick person |
| Bob: "why not colder?" | "someone present bounds it… your post-workout range had to stay within the shared limit" | "I don't have any memory…" |
| Alice: "what helps me when I'm sick?" | warmer AC, fan off, warm dim light, from her evidence and playbook | "I don't have any memory…" |

---

## Real-data results: the Scientist

The team's ring had almost no history before the event: the Gabit → Health Connect → HomeOS Bridge pipeline is verified end to end, but there was little to sync. So HomeOS also loads one participant of the public **PMData** dataset (Fitbit Versa 2, Nov 2019 – Mar 2020, CC BY 4.0, [attribution](replay/datasets/README.md)) as a separate, clearly labelled household member `p01` who is never in the home. Nothing is synthesised: 150 real nights, 137 rated by the participant (38 rated bad).

The Scientist works on *deviations from the person's own normal*: bands such as `sleep_window:short` or `rhr:elevated` carry the `:Notable` label, and everyday context does not. Output on p01 ([`docs/results/scientist_p01.json`](docs/results/scientist_p01.json), 64 ms):

| Algorithm | Finding |
|---|---|
| `algo.pageRank` over deviations co-occurring on bad nights | **Top driver: short sleep window.** 30 of 63 such nights were rated bad (48%), 1.6× p01's usual rate |
| `algo.betweenness` on episode → deviation → lever | Keystone deviation: short sleep window (most cause chains run through it) |
| `algo.labelPropagation` over bad nights + deviations | Bad nights come in **3 kinds**: short window + late bedtime (23) · elevated resting HR + evening workout (9) · very active day + evening workout (6) |
| `algo.SPpaths` over −ln P(bad \| deviation) | Last bad night: short sleep window (likelihood 0.48), then late bedtime (0.29) |

Asked "What usually ruins my nights?", the Coach chose `drivers`, `keystone` and `what_works` through MCP and answered: *"Mostly short sleep windows, evening workouts, and elevated resting heart rate."*

### Latency (graph work per decision)

Each query ran 30 times through the agents' own client and ACL users, against FalkorDB in Docker on a laptop ([`scripts/bench.ts`](backend/scripts/bench.ts), [`docs/results/latency.json`](docs/results/latency.json)). The p01 graph has 254 nodes and 1326 edges.

| Query | p50 | p95 |
|---|---|---|
| `effective_comfort (multi-hop, harsh)` | 6.2 ms | 13.8 ms |
| `feasible_settings (multi-hop, home)` | 6.0 ms | 7.2 ms |
| `impact (algo.bfs, home)` | 4.0 ms | 4.8 ms |
| `choose_intervention (Thompson UDF, p01)` | 6.5 ms | 7.7 ms |
| `why_bad_night (algo.SPpaths, p01)` | 4.1 ms | 5.9 ms |
| `drivers (algo.pageRank, p01)` | 3.6 ms | 5.0 ms |
| `keystone (algo.betweenness, p01)` | 5.6 ms | 24.2 ms |
| `similar_episodes (vector + bands, p01)` | 6.8 ms | 8.3 ms |

The graph is never the bottleneck. A full voice round trip (Sarvam STT → Coach choosing graph tools over MCP → Sarvam TTS on the watch) takes about 6–9 s, almost all of it LLM and speech.

---

## How it works

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/img/architecture-dark.png" />
  <img src="docs/img/architecture-light.png" alt="HomeOS architecture: the watch, ring and ESP32 feed the HomeOS server's agent team (1 Sense, 2 Notice, 3 Decide, 4 Act, 5 Learn & explain); agents read through MCP and write through the Write API into FalkorDB, where each person's private graph shares only a range and priority with the shared home graph; agents act on the room light, fan and AC, the breathing pacer and spoken answers, using Azure OpenAI and Sarvam." width="100%" />
</picture>

**Tech stack**

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/img/tech-stack-dark.png" />
  <img src="docs/img/tech-stack-light.png" alt="HomeOS tech stack. Wearables and mobile: Wear OS, Android, Kotlin, Jetpack Compose. Server and agents: Node.js, TypeScript, Express, MCP. Graph memory: FalkorDB, Docker. Room hardware: ESP32, ESPHome. AI and voice: Azure OpenAI, Sarvam. Web and 3D: React, Vite, Framer Motion, GSAP, three.js." width="100%" />
</picture>

Both images are generated from [`docs/architecture/`](docs/architecture). The detailed flow:

```mermaid
flowchart LR
  subgraph Wearables
    W[Galaxy Watch4 · Wear OS app<br/>HR 1 Hz · chips · voice · pacer] -- WebSocket --> GW
    R[Gabit ring] --> HC[Health Connect] --> P[HomeOS Bridge phone app<br/>backfill + 15-min sync] -- HTTP --> GW
  end
  ESP[ESP32 room node<br/>temp · humidity · light · LED] <--> DEV
  GW[Gateway + raw buffer<br/>samples never enter the graph] --> G[Guardian]
  G -- Task --> PL[Planner · LLM]
  PL -- Intent + Task --> AR[Arbiter]
  AR -- Task --> PL
  PL -- Task --> EV[Evaluator]
  CO[Coach · LLM voice] 
  SC[Scientist · algorithms]
  PL & CO -- MCP --> M1[homeos-mcp] & M2[FalkorDB MCP]
  M1 & M2 --> F[(FalkorDB<br/>home:flat1 + body:alias)]
  G & AR & EV & SC --> F
  AR --> DEV[Device drivers<br/>ESP32 · WiZ · virtual AC/fan]
  F --> UI[Dashboard + FalkorDB Browser]
```

| Agent | Kind | Reads | Writes (through the typed write API only) |
|---|---|---|---|
| **Guardian** (per person) | deterministic | live HR/IBI vs the person's own baseline, watch/phone chips, ring data | Conditions, Episodes + context Bands, `plan` tasks; asks before assuming |
| **Planner** (per person) | Azure OpenAI + graph tools via MCP | Evidence, feasible ranges, playbooks, similar episodes | Intents, `arbitrate` task, then Decision + Actions + `evaluate` task |
| **Arbiter** (home) | deterministic | home graph only (it cannot read anyone's private graph) | device leases, Arbitration nodes, device state, `record` task |
| **Evaluator** | deterministic | raw buffer after the action window, feedback | OUTCOME edges, Evidence via the `HomeOS.welford` UDF (one atomic query) |
| **Coach** (per person) | Azure OpenAI voice agent via MCP | everything in that person's graph + read-only home graph | Session/Turn memory; conditions, preferences and dislikes taught by voice |
| **Scientist** | batch | full history | band likelihoods, CO_OCCURS edges, Insights, new Playbook versions |

### The graph model

```
home:flat1 (shared)                                     body:<alias> (private, one per person)
(:Room)-[:HAS]->(:Device)-[:CONTROLS]->(:Var)            (:Person)-[:HAS_CONDITION {valid_from,valid_to}]->(:Condition)-[:IMPLIES]->(:Condition)
(:Var)-[:AFFECTS {sign,prior,source_url}]->(:Signal)     (:Condition {priority})-[:SHIFTS {min,max}]->(:Var)
(:Person)-[:IN]->(:Room)                                 (:Person)-[:PREFERS|DISLIKES|HAS_BASELINE {valid_from,valid_to}]->(...)
(:Person)-[:HAS_CONSTRAINT]->(:Constraint {var,min,max,  (:Episode)-[:IN_STATE {neglogp}]->(:Band)-[:OF_VAR|OF_HABIT]->(:Var|:Habit:Lever)
          priority})   <- no reasons, ever               (:Decision)-[:FOR]->(:Episode); -[:CHOSE|CONSIDERED|USED|RESPECTED]->(...)
(:Agent)-[:POSTED]->(:Intent)-[:ON]->(:Room)             (:Action)-[:PART_OF]->(:Decision); (:Action)-[:OUTCOME {effect}]->(:Signal)
(:Task)-[:ASSIGNED_TO]->(:Agent); (:Task)-[:HANDOFF_FROM]->(:Task)   (:Evidence {n,mean,m2})-[:FOR]->(:Intervention); -[:WHEN]->(:Band)
(:Device)-[:LEASED_BY {until}]->(:Agent)                 (:Playbook {v})-[:STEP]->(:Intervention); -[:SUPERSEDES]->(:Playbook)
(:Arbitration)-[:RESOLVED|BOUNDED_BY|APPLIED]->(...)     (:ConstraintExport)-[:DERIVED_FROM]->(:Condition)   <- why, private
                                                         (:Session)-[:HAS_TURN]->(:Turn {embedding})-[:ABOUT]->(...)
```

### FalkorDB features in use

| Feature | Used for |
|---|---|
| Multiple graphs + **Redis ACL** (`~body:alice`, `+GRAPH.RO_QUERY`) | one private graph per person, enforced by the database; read-only shared home |
| **JavaScript UDFs** (`GRAPH.UDF LOAD`): `welford`, `thompson`, `resolveRanges`, `intersectRanges`, `zscore`, `likelihood` | learning, exploration and conflict resolution inside queries ([`graph/udf/homeos.js`](graph/udf/homeos.js)) |
| **algo.SPpaths, algo.pageRank, algo.betweenness, algo.labelPropagation, algo.bfs** | cause chains, drivers, keystones, episode types, impact |
| **Vector indexes** (`Episode.embedding`, `Turn.embedding`, 1536-d cosine) + `db.idx.vector.queryNodes` | similar situations, semantic recall |
| **Full-text indexes** (`Turn.text`, `Episode.summary`) | keyword recall over conversations |
| **Unique constraints** (`GRAPH.CONSTRAINT`) + range indexes | integrity of people, devices, episodes, bands |
| Variable-length paths (`IMPLIES*0..3`, provenance `*1..4`) | condition inheritance, explanations |
| **Atomic single-query writes**, serialized per graph | race-free leases, task claims, evidence updates |
| `GRAPH.RO_QUERY` + per-query `TIMEOUT` | safe agent reads |
| **Official FalkorDB MCP server** | LLM ad-hoc read-only Cypher over the home graph |
| FalkorDB Browser | live inspection in the demo |

All 59 queries live in [`graph/queries/`](graph/queries) as `.cypher` files, each with a header comment saying what it does.

---

## Run it

**Requirements:** Docker, Node 20+, and an Azure OpenAI key (`gpt-5.4-mini` + `text-embedding-3-small` deployments). Sarvam is optional.

```bash
cp .env.example .env              # fill in Azure (and optionally Sarvam) keys
docker compose up -d --build      # FalkorDB + Browser (:3000) + HomeOS backend & dashboard (:3001)
```

Load the public-dataset person (months of real nights for the Scientist):

```bash
cd backend && npx tsx scripts/import-pmdata.ts --batch ../replay/datasets/pmdata-p01.json --alias p01
```

Without hardware, replay a recorded real session ([`replay/README.md`](replay/README.md)):

```bash
cd backend && npm ci && npx tsx scripts/replay.ts ../replay/recordings/<file>.jsonl --speed 2
```

Development mode:

```bash
docker compose up -d falkordb
cd backend && npm ci && npm run dev        # agents + API on :3001
cd ui && npm ci && npm run dev             # dashboard on :5173 (proxied to :3001)
cd backend && npm test                     # 15 integration tests against live FalkorDB
npx tsx scripts/ablation.ts                # graph on vs off comparison
```

Connect any MCP client (Claude Desktop, Cursor, …) to a person's memory:

```json
{ "mcpServers": { "homeos-harsh": { "command": "npx", "args": ["tsx", "backend/src/mcp/server.ts"], "env": { "HOMEOS_ALIAS": "harsh" } } } }
```

### Hardware

| Device | App | Notes |
|---|---|---|
| Samsung Galaxy Watch4 (SM-R865F, Wear OS 5) | [`apps/watch`](apps/watch) (Kotlin, Compose for Wear OS) | HR via Health Services; IBI/HRV via the Samsung Health Sensor SDK when its AAR is present; chips; push-to-talk with **Sarvam** speech-to-text (`saaras:v3`, code-mixed Hindi/English) and spoken replies (`bulbul:v3`), about 6–9 s from question to voice; haptic breathing pacer. Set `homeos.ws` and `homeos.alias` in `apps/watch/local.properties`, then `./gradlew installDebug` |
| Gabit smart ring | [`apps/phone`](apps/phone) "HomeOS Bridge" (Kotlin, Health Connect) | Gabit holds Health Connect write permission for HR, HRV, resting HR, sleep, steps, exercise and VO2max (verified on the phone). The app backfills 120 days, syncs every 15 minutes in the background, reports per-type read errors, and can be triggered from a laptop: `adb shell am broadcast -n com.homeos.bridge/.SyncReceiver --el days 120` |
| ESP32 room node | [`esphome/room-node.yaml`](esphome/room-node.yaml) | DHT11 temperature/humidity, light sensor, dimmable LED as the room light (`ESP32_HOST`) |
| Philips WiZ bulb | — | local UDP (`WIZ_HOST`) |
| AC and fan | — | virtual devices on the dashboard behind the same driver interface (an IR blaster driver plugs in here) |

---

## Privacy and safety

- Raw samples (1 Hz HR, IBI) stay in memory; the graph stores only what agents reason over.
- Health conditions live only in the person's own graph. The home graph gets an anonymous range and priority, and a database ACL stops other people's agents from reading private graphs.
- The LLM never writes Cypher. Every write goes through typed functions ([`writeapi/index.ts`](backend/src/writeapi/index.ts)); agent reads are read-only with timeouts.
- Device setpoints are clamped to device limits. The "do nothing" control arm is never used in live care. Outcomes are only learned from measured signals or explicit feedback, never invented.
- HomeOS is a wellness tool, not a medical device.

## What was built during the event, and what was reused

All FalkorDB work was written during Graph Hacks (Oct 15–18, 2026): the schema, the 59 queries, the UDFs, the agents, the MCP server, the tests and the dashboard. Device plumbing reused from earlier team work, and disclosed here:

- **Wear OS client patterns** from the team's earlier project Rakshak (`NaomiiAP/codexhack`): WebSocket reconnect, `AudioBridge` (microphone → WAV, TTS playback), Health Services heart rate, Samsung SDK reflection.
- **Sarvam client** (`saaras:v3` / `bulbul:v3`) ported from the same project.
- The **ESP32 room node** and our notes on the Gabit ring's BLE protocol (from before the event). No decompiled vendor code is in this repo.

## Repository map

```
graph/       schema seeds, 59 queries (.cypher), UDF library, catalog of conditions/devices/priors
backend/     TypeScript: agents, write API, MCP server, ingest, device drivers, Sarvam voice, tests, scripts
ui/          React dashboard (live agents, decisions, provenance graph, evidence)
apps/watch/  Wear OS app          apps/phone/  Health Connect bridge
replay/      recorded real sessions, public dataset (PMData p01) + README       docs/  track write-ups, results
```
