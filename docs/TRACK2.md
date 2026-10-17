# Track 2: Agent Memory and Coordination

Six agents share no in-process state. They remember, coordinate and hand off work only through FalkorDB: one private graph per person plus one shared home graph.

## Memory

| Kind | In the graph | Updated by |
|---|---|---|
| **Episodic** | `Episode` (live situations, and every night synced from the ring) → `Decision` → `Action` -`OUTCOME {effect, d_hr, d_rmssd, recover_s}`→ `Signal` | Guardian, Planner, Evaluator |
| **Semantic** | `HAS_BASELINE`, `HAS_CONDITION`, `PREFERS`, `DISLIKES` as **bi-temporal** edges (`valid_from`/`valid_to`). Old beliefs are closed, never overwritten. `Condition -IMPLIES/SHIFTS→`; `Var -AFFECTS {source_url}→ Signal` priors | Guardian, Coach (by voice), Health Connect ingest |
| **Procedural** | `Playbook {v}` -`STEP {i}`→ `Intervention`, with a `SUPERSEDES` chain; `Evidence {n, mean, m2}` per (episode kind, intervention, band) = the learned policy | Evaluator (atomic Welford UDF), Scientist |
| **Conversation** | `Session -HAS_TURN→ Turn {embedding} -ABOUT→ Episode/Decision/Condition/Intervention` | Coach |

- **Recall** ([`recall_turns.cypher`](../graph/queries/recall_turns.cypher), [`recall_fulltext.cypher`](../graph/queries/recall_fulltext.cypher), [`recall_episodes.cypher`](../graph/queries/recall_episodes.cypher)) combines vector search, full-text search and graph expansion.
- **Memory over time** ([`beliefs_at.cypher`](../graph/queries/beliefs_at.cypher), [`belief_diff.cypher`](../graph/queries/belief_diff.cypher)) answers "what did you believe about me last week, and what changed?"
- **Survives restarts:** `test/memory.test.ts › memory survives a FalkorDB restart` restarts the container and recalls a playbook (v2), a preference and a conversation turn. Result: [`results/restart_recall.json`](results/restart_recall.json).

## Coordination: the graph is the message bus

```
Guardian ──(:Task plan)──▶ Planner ──(:Intent)+(:Task arbitrate)──▶ Arbiter ──(:Task record)──▶ Planner ──(:Task evaluate, due +3 min)──▶ Evaluator
                each task -[:HANDOFF_FROM]-> the task that spawned it
```

- [`claim_task.cypher`](../graph/queries/claim_task.cypher) claims the oldest due open task for an agent in **one atomic write**. FalkorDB runs writes to one graph one at a time, so two workers can never claim the same task.
- Claims carry a lease. [`reclaim_tasks.cypher`](../graph/queries/reclaim_tasks.cypher) puts a crashed agent's task back on the board.
- Devices are leased atomically ([`acquire_device.cypher`](../graph/queries/acquire_device.cypher)); an empty result means someone else holds the device.
- Planners never touch devices. They post `Intent`s; the Arbiter resolves all intents for a room against everyone's constraints.

## High-concurrency reads and writes (measured)

`test/memory.test.ts` fires 140 operations at once against one graph:
- 40 Welford evidence updates to the *same* Evidence nodes
- 40 multi-hop reads
- 30 lease attempts on one device
- 30 workers claiming 20 tasks

Results ([`results/concurrency.json`](results/concurrency.json)):

| Metric | Result |
|---|---|
| Throughput | ~1,090 ops/s (140 ops in 128 ms wall) |
| Lost updates | **0**: evidence `n` increased by exactly 40 |
| Lease winners | exactly 1 of 30 |
| Task claims | 20 of 20 tasks, each exactly once, by 30 workers |
| Latency p50 / p95 | writes 67 / 100 ms · reads 84 / 102 ms (under full contention) |

## Separate memory per user

- Each person has their own graph, `body:<alias>`. The home has `home:flat1`.
- [`setup.ts › createAclUsers`](../backend/src/graph/setup.ts) creates one Redis ACL user per person's agents with key pattern `~body:<alias>`. A separate `home_reader` user can only `GRAPH.RO_QUERY` the home graph.
- Tested (`test/core.test.ts › tenant isolation is enforced by FalkorDB ACL`): `agent_bob` reading `body:alice` gets `NOPERM`, and `home_reader` cannot write.
- **The privacy boundary is structural.** `refreshConstraints` computes a person's needs in their private graph and records *why* there (`ConstraintExport -DERIVED_FROM→ Condition`). It publishes only `{var, min, max, priority}` to the home graph. Explanations to other people therefore cannot leak a reason, because the reason is not in any graph they can read.
