# My home knows I'm sick, and it won't tell my roommate why

*How we gave a house a nervous system with a Galaxy Watch, a smart ring, an ESP32 and a FalkorDB knowledge graph, in four days at Graph Hacks 2026.*

---

Your smartwatch knows when your heart rate is 40 beats above normal. Your ring knows you slept five hours. Your phone knows you said "I think I have a fever". And your AC is still blasting 22 °C, because the person who turned it on finished a workout ten minutes ago.

We built **HomeOS** for Graph Hacks (WeMakeDevs × FalkorDB): a home whose agents learn what helps *each* person's body and coordinate the room between people, without anyone's health data leaking to anyone else. It's entered in Track 1 (*Agents That Act on Connected Data*) and Track 2 (*Agent Memory and Coordination*). This post explains why a graph, specifically FalkorDB, turned out to be the only sane way to build it.

## The problem is relationships, not rows

"If someone is sick, don't make the AC cold" sounds like an `if` statement until you try to write it:

- *Fever* implies *sick*. *Cold symptoms* imply *sick* and also want humidity ≥ 40%.
- A sick person wants ≥ 26 °C. Someone who just worked out wants ≤ 25 °C. Both are in the same room.
- The AC changes temperature **and** humidity **and** airflow **and** noise. The fan changes airflow and noise.
- One person hates the fan *only while sick*.
- None of this should be visible to the other person.

That's a graph. In HomeOS it literally is one:

```
(:Condition {kind:'fever'})-[:IMPLIES]->(:Condition {kind:'sick', priority:3})-[:SHIFTS {min:26}]->(:Var {name:'room_temp'})
(:Device {id:'ac'})-[:CONTROLS]->(:Var)-[:AFFECTS {sign, prior, source_url}]->(:Signal {name:'deep_sleep'})
(:Person)-[:HAS_CONSTRAINT]->(:Constraint {var:'room_temp', min:26, priority:3})   // shared: no reason, ever
```

A person's needs come out of one multi-hop query: Person → active conditions → `IMPLIES*0..3` → `SHIFTS` → variable, combined with their own preferences. The room's feasible band comes from another: Room → Device → `CONTROLS` → Var, joined with everyone present → their constraints. Conflicts are settled inside the query by a 30-line JavaScript UDF, `HomeOS.resolveRanges`: higher-priority ranges win, and the person whose range gave way is told so.

## Privacy by graph topology

Every person gets a **private graph** (`body:harsh`, `body:shashwath`). The house gets a **shared graph** (`home:flat1`). When Shashwath says "I'm feeling sick", here's what happens:

1. His private graph records `(:Person)-[:HAS_CONDITION]->(:Condition {kind:'sick'})`. It's bi-temporal and expires tomorrow.
2. His agent computes his comfort ranges and remembers *why* privately: `(:ConstraintExport)-[:DERIVED_FROM]->(:Condition)`.
3. Only `{var:'room_temp', min:26, priority:3}` is written to the shared graph.

When Harsh asks his watch "why isn't it colder?", the answer is *"someone present needs it at 26 °C or warmer"*. The reason can't leak, because it isn't in any graph Harsh's agent can read. That isn't just app logic: each person's agents connect to FalkorDB as their own **Redis ACL user** (`~body:harsh`), and the home graph is read-only to them (`+GRAPH.RO_QUERY`). In our tests, Bob's agent reading Alice's graph gets `NOPERM` from the database itself.

## Agents that only talk through the graph

HomeOS has six agents: Guardian, Planner, Arbiter, Evaluator, Coach and Scientist. None of them call each other. They hand off work as `(:Task)` nodes in the shared graph, claimed atomically:

```cypher
MATCH (t:Task {status:'open'})-[:ASSIGNED_TO]->(:Agent {id:$agent}) WHERE t.due_at <= $now
WITH t ORDER BY t.created LIMIT 1
SET t.status = 'claimed', t.worker = $worker, t.lease_until = $now + $lease_ms
```

FalkorDB runs writes to one graph one at a time and every query is atomic, so 30 workers racing for 20 tasks claim each exactly once. That's measured in our test suite, along with 40 concurrent learning updates to the *same* evidence node with zero lost updates. Each task links `HANDOFF_FROM` the task that spawned it, so the whole Guardian → Planner → Arbiter → Planner → Evaluator chain is a path you can look at in the FalkorDB Browser.

## The graph is the policy

How does HomeOS decide between "fan on high" and "AC 2 °C cooler" for *you*? Every intervention has `(:Evidence {n, mean, m2})` nodes per context band ("post-exercise", "room warm", "evening"). After every action the Evaluator measures what your body did: heart-rate recovery against *your* baseline, or HRV change. It folds that into the evidence with a Welford update, computed by a UDF inside the same atomic query that writes it. The Planner ranks candidates with a Thompson-sampling UDF over that evidence. An Azure OpenAI model then picks the final plan, using our graph tools over **MCP**. It can call `similar_episodes` (a vector index plus shared bands), `what_works` or `playbooks_for` before deciding, and the official FalkorDB MCP server is attached too for read-only Cypher.

Every decision leaves a provenance subgraph:

```
(:Decision)-[:FOR]->(:Episode)-[:IN_STATE]->(:Band)
(:Decision)-[:CHOSE|CONSIDERED {rejected_because}]->(:Intervention)
(:Decision)-[:USED]->(:Evidence)   (:Decision)-[:RESPECTED]->(:ConstraintRef)
```

"Why?" is a walk over that subgraph, spoken on the watch through Sarvam's Bulbul TTS.

## Graph algorithms that found something real

Our own ring had barely been worn, so we loaded five months of one participant of the public **PMData** dataset (Fitbit sleep stages plus their own nightly sleep-quality ratings, CC BY 4.0) as a clearly labelled household member. The first version of our "Scientist" was boring. PageRank told us the top driver of bad nights was… weeknights. Of course: weeknights are just common.

The fix was to make the graph say what we meant. Context bands that deviate from a person's own normal (`sleep_window:short`, `rhr:elevated`, `evening_workout:yes`) get a `:Notable` label, and bad nights get `:BadEpisode`. FalkorDB's algorithms filter by label, so:

- **PageRank** over deviations that co-occur on bad nights: *short sleep window, 30 of 63 such nights rated bad, 1.6× their usual rate.*
- **Label propagation** over `:BadEpisode` + `:Notable`: bad nights come in **three kinds**. Short window + late bedtime; elevated resting HR + evening workout; very active day + evening workout.
- **SPpaths** with edge weights −ln P(bad | deviation): the most likely cause chain for the last bad night was *short sleep window → sleep schedule* (0.48).

All of it runs in 64 ms over 150 nights, and every individual query is single-digit milliseconds.

## Proof the graph is load-bearing

The hackathon rule we kept rereading: *"if the product still works once FalkorDB is taken out, the graph is decoration."* So we took it out. `HOMEOS_GRAPH=off` replaces everything with fixed rules and we ran the same scenario. The sick person says "I'm sick", their roommate finishes a workout, and the fixed rule sets the AC to **22 °C** on the sick person. Asked "why?", it says *"I don't have any memory of you."* With the graph, the AC holds at 27 °C, the roommate gets the fan within the shared limit plus a breathing pacer on their watch, and both of them get real answers.

## Things that broke (honestly)

- FalkorDB 4.22.1 segfaulted replaying its own append-only log after a restart, while we were testing restart recovery. We switched to RDB snapshots every second. The test that caught it is still in the suite.
- Wear Compose 1.4 crashes on Wear OS 5 if you target SDK 35 (it reads a settings key it isn't allowed to read). We target 34.
- OnePlus blocks `pm grant` from adb, so Health Connect permissions have to be granted in-app. We added an adb-triggered background sync so we could test without touching the phone.
- An empty sync looks exactly like a broken sync. Our phone app now reports per-type read errors, and that's how we learned the ring simply had no history.

## Stack

FalkorDB (multi-graph, ACL, JS UDFs, vector and full-text indexes, `algo.*`, the official MCP server) · TypeScript agents · Azure OpenAI (`gpt-5.4-mini`, `text-embedding-3-small`) · Sarvam (Saaras STT, Bulbul TTS) · Wear OS app on a Galaxy Watch4 · Health Connect bridge for the Gabit ring · ESP32 room node on ESPHome · React dashboard.

Code, tests, ablation and replay mode: **github.com/shashwath1278/Graph-hackathon**
