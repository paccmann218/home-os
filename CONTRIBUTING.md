# Contributing

HomeOS is the Graph Hacks 2026 submission from Naomi, Harshendra, and Shashwath. The graph is the product. A change that still works with `HOMEOS_GRAPH=off` is probably not a HomeOS change.

## Where a change belongs

| You are changing | Put it in |
|---|---|
| A question the agents ask the graph | `graph/queries/<name>.cypher`, called by name from `backend/src/graph/db.ts` |
| A write | `backend/src/writeapi/index.ts` and a query file. Agents do not send write Cypher. |
| A new tool the model may choose | `backend/src/tools/index.ts`, with a Zod argument schema |
| Who may read which graph | `backend/src/graph/setup.ts` (`createAclUsers`) |
| Seed facts (rooms, devices, conditions, priors) | `graph/seed/catalog.json` and the matching seed Cypher |
| Watch or phone UI | `apps/watch`, `apps/phone` |

## Rules that keep the submission honest

- Parameters only. Never concatenate model output into a query string.
- A private fact stays in `body:<alias>`. The home graph receives a range and a priority, not a reason.
- An empty traversal is an answer. Say that the graph does not contain the fact. Do not fill the gap with a plausible sentence.
- Spoken text on the watch is assembled from query rows (times, counts, titles), not from a fixed script.
- New queries get a one-line header comment naming the graph they run on.

## Check

```bash
docker compose up -d falkordb
cd backend && npm test
cd backend && npx tsx scripts/ablation.ts
```

`npm test` needs FalkorDB on `127.0.0.1:6379`. The ablation script is the check that the graph is load-bearing: with FalkorDB off, the same scene drops the room onto a sick person and cannot explain itself.

Setup, debug, and run steps are in the [README](README.md) and in [docs/SETUP.md](docs/SETUP.md).
