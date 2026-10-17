/**
 * Latency of the graph queries the agents run on every decision, measured against the live graphs
 * (each query run 30 times through the same client and ACL users the agents use).
 * Writes docs/results/latency.json.
 *
 *   npx tsx scripts/bench.ts
 */
import fs from "node:fs";
import path from "node:path";
import { REPO_ROOT, config } from "../src/config.js";
import { closeAll, now } from "../src/graph/db.js";
import { bodyRead, bodyAlgo, homeRead } from "../src/graph/graphs.js";
import { present, KEYSTONE_LABELS, KEYSTONE_RELS } from "../src/graph/schemaInfo.js";

const N = 30;
const pct = (a: number[], p: number) => [...a].sort((x, y) => x - y)[Math.min(a.length - 1, Math.floor((p / 100) * a.length))];

async function time(name: string, f: () => Promise<unknown>) {
  await f(); // warm-up
  const t: number[] = [];
  for (let i = 0; i < N; i++) {
    const s = performance.now();
    await f();
    t.push(performance.now() - s);
  }
  const r = { query: name, p50_ms: +pct(t, 50).toFixed(2), p95_ms: +pct(t, 95).toFixed(2) };
  console.log(`${name.padEnd(46)} p50 ${r.p50_ms.toFixed(1).padStart(6)} ms   p95 ${r.p95_ms.toFixed(1).padStart(6)} ms`);
  return r;
}

const live = config.people.find((p) => !config.datasetPeople.includes(p)) ?? config.people[0];
const hist = config.datasetPeople[0] ?? live;
const { run, agentUser } = await import("../src/graph/db.js");
const { bodyGraph } = await import("../src/config.js");
const [ep] = await run(bodyGraph(hist), "MATCH (e:Episode) RETURN e.id AS id ORDER BY e.start DESC LIMIT 1", {}, { as: agentUser(hist), readOnly: true });
const [badEp] = await run(bodyGraph(hist), "MATCH (e:BadEpisode) RETURN e.id AS id ORDER BY e.start DESC LIMIT 1", {}, { as: agentUser(hist), readOnly: true });
const [dec] = await bodyRead(live, "recent_decisions", { limit: 1 });
const ks = await present(hist, KEYSTONE_LABELS, KEYSTONE_RELS);
const [nodes] = await run(bodyGraph(hist), "MATCH (n) RETURN count(n) AS n", {}, { as: agentUser(hist), readOnly: true });
const [edges] = await run(bodyGraph(hist), "MATCH ()-[r]->() RETURN count(r) AS n", {}, { as: agentUser(hist), readOnly: true });

const results = [];
results.push(await time(`effective_comfort (multi-hop, ${live})`, () => bodyRead(live, "effective_comfort", { alias: live, now: now() })));
results.push(await time("feasible_settings (multi-hop, home)", () => homeRead("feasible_settings", { room: "bedroom", now: now() })));
results.push(await time("impact (algo.bfs, home)", () => homeRead("impact", { device: "ac" })));
if (ep) results.push(await time(`choose_intervention (Thompson UDF, ${hist})`, () => bodyRead(hist, "choose_intervention", { episode: ep.id, alias: hist, now: now() })));
if (dec) results.push(await time(`explain (provenance walk, ${live})`, () => bodyRead(live, "explain", { decision: dec.id })));
if (badEp) results.push(await time(`why_bad_night (algo.SPpaths, ${hist})`, () => bodyAlgo(hist, "why_bad_night", { episode: badEp.id })));
results.push(await time(`drivers (algo.pageRank, ${hist})`, () => bodyAlgo(hist, "drivers", { limit: 5 })));
if (ks.rels.length) results.push(await time(`keystone (algo.betweenness, ${hist})`, () => bodyAlgo(hist, "keystone", { limit: 5, labels: ks.labels, rels: ks.rels })));
if (ep) results.push(await time(`similar_episodes (vector + bands, ${hist})`, () => bodyRead(hist, "similar_episodes", { episode: ep.id, k: 6 })));

const out = {
  at: new Date().toISOString(),
  runs_per_query: N,
  graph_sizes: { [bodyGraph(hist)]: { nodes: nodes.n, edges: edges.n } },
  note: "Measured from the backend's Node client to FalkorDB in Docker on a laptop, using the agents' own ACL users.",
  results,
};
const file = path.join(REPO_ROOT, "docs", "results", "latency.json");
fs.writeFileSync(file, JSON.stringify(out, null, 2));
console.log(`\nwrote ${path.relative(REPO_ROOT, file)}`);
await closeAll();
