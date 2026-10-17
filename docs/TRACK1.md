# Track 1: Agents That Act on Connected Data

HomeOS agents change a real room (AC, fan, light) and a person's watch (breathing pacer). Every action is decided by traversing connected data in FalkorDB, and every action can be explained from that data afterwards.

## 1. Multi-hop reasoning

**What a person needs right now** ([`effective_comfort.cypher`](../graph/queries/effective_comfort.cypher)), run in that person's private graph:

```cypher
MATCH (p:Person {alias: $alias}), (v:Var)
OPTIONAL MATCH (p)-[pr:PREFERS]->(v) WHERE pr.valid_from <= $now AND pr.valid_to > $now
...
OPTIONAL MATCH (p)-[hc:HAS_CONDITION]->(c0:Condition)-[:IMPLIES*0..3]->(c:Condition)-[s:SHIFTS]->(v)
WHERE hc.valid_from <= $now AND hc.valid_to > $now
WITH v, prefs, collect({min: s.min, max: s.max, priority: c.priority + 1, id: c.kind, via: c0.kind}) AS shifts
WITH v, HomeOS.resolveRanges(prefs + shifts) AS eff ...
```

`fever -IMPLIES-> sick -SHIFTS-> room_temp ≥ 26`: the rule is graph data, so it composes. A person can be sick *and* post-workout *and* sleeping, and the traversal finds every shift. Users extend it by voice ("I hate the fan when I'm sick" creates a `DISLIKES {when_condition:'sick'}` edge).

**What everyone in the room can accept** ([`feasible_settings.cypher`](../graph/queries/feasible_settings.cypher)), run in the shared home graph:

```cypher
MATCH (r:Room {id: $room})-[:HAS]->(d:Device)-[k:CONTROLS]->(v:Var)
OPTIONAL MATCH (r)<-[:IN]-(p:Person)-[:HAS_CONSTRAINT]->(c:Constraint {var: v.name})-[:FOR_ROOM]->(r)
OPTIONAL MATCH (v)-[a:AFFECTS]->(s:Signal)
WITH ..., HomeOS.resolveRanges(cs + [{min: d.min, max: d.max, priority: 99}]) AS eff
RETURN d.id, v.name, eff.min, eff.max, bound_by, overridden_for, touches
```

The query goes Room → Device → Var → Signal, and Room → Person → Constraint. When ranges conflict, the `resolveRanges` UDF applies higher-priority ranges first. A sick person's floor (priority 3) beats a post-workout ceiling (priority 1), and the overridden person is told so. That is tested in `test/core.test.ts › conflicting needs are resolved by priority`.

## 2. Graph queries and algorithms as agent tools

[`backend/src/tools/index.ts`](../backend/src/tools/index.ts) defines 19 tools. The graph-algorithm ones:

| Tool | Algorithm | Question it answers |
|---|---|---|
| `why_bad_night` | `algo.SPpaths` with `weightProp:'neglogp'`, `pathCount:1`, `maxLen:4`, target = every `:Lever` | Most likely *actionable* cause chain for a bad night: episode → band → variable → device or habit |
| `drivers` | `algo.pageRank('Band','CO_OCCURS')` | Which context bands dominate bad nights |
| `keystone` | `algo.betweenness` over Episode/Band/Var/DeviceRef/Habit | The band that most cause chains pass through |
| `episode_types` | `algo.labelPropagation` over Episode + Band | Kinds of nights/situations a person has |
| `impact` | `algo.bfs(device, 2, 'CONTROLS')` + AFFECTS | What a device change reaches, and who would feel it |
| `similar_episodes` | `db.idx.vector.queryNodes` + shared `Band` nodes | Past situations like this one, and what was done |

The cause-chain weights are learned. The Scientist sets each band's `neglogp = −ln P(bad night | band)` with Laplace smoothing ([`sci_band_likelihood.cypher`](../graph/queries/sci_band_likelihood.cypher)), so the lightest path is the most probable cause, and `HomeOS.likelihood(pathWeight)` turns it back into a probability.

## 3. Tool selection through MCP

- [`backend/src/mcp/server.ts`](../backend/src/mcp/server.ts) (**homeos-mcp**) exposes all 19 tools over stdio. One instance runs per person and connects to FalkorDB with that person's ACL user.
- [`backend/src/llm/agent.ts`](../backend/src/llm/agent.ts) also attaches the official **FalkorDB MCP server** (`@falkordb/mcpserver`) as the read-only `home_reader` user. Only its read-only, schema and list tools are exposed.
- The Planner and Coach run on Azure OpenAI (`gpt-5.4-mini`), which picks tools from both servers. The dashboard feed shows each `MCP tool → …` call live.

## 4. Actions based on graph results

[`choose_intervention.cypher`](../graph/queries/choose_intervention.cypher) ranks interventions using Evidence that matches the episode's context bands. Each candidate gets a Thompson-sampling draw from the `HomeOS.thompson` UDF, minus the person's dislikes, plus a boost for steps in an active playbook. The Planner clamps each candidate to `feasible_settings` and lets the LLM choose. The Arbiter then:

1. re-reads the feasible band,
2. takes an atomic lease ([`acquire_device.cypher`](../graph/queries/acquire_device.cypher)),
3. drives the device (ESP32 REST, WiZ UDP, or a virtual AC/fan),
4. records an `Arbitration` node linked to the Intents it resolved and the Constraints that bounded it.

## 5. Explainable results

Every decision leaves a provenance subgraph in the owner's private graph:

```
(:Decision)-[:FOR]->(:Episode)-[:IN_STATE]->(:Band)
(:Decision)-[:CHOSE|CONSIDERED {rejected_because}]->(:Intervention)
(:Decision)-[:USED]->(:Evidence {n, mean})-[:WHEN]->(:Band)
(:Decision)-[:RESPECTED]->(:ConstraintRef)-[:SAME_AS]->(:ConstraintExport)-[:DERIVED_FROM]->(:Condition)   // own constraints only
```

`explain` ([`explain.cypher`](../graph/queries/explain.cypher)) and `explain_arbitration` ([`explain_arbitration.cypher`](../graph/queries/explain_arbitration.cypher)) walk this subgraph:

- The owner hears their own reasons ("your own comfort (sick)").
- Other people's constraints appear only as "someone present", because their reasons do not exist in the home graph.
- The dashboard highlights the same paths ([`explain_paths.cypher`](../graph/queries/explain_paths.cypher)).
