/**
 * Track 2 proofs with numbers:
 *  - high-concurrency reads + writes on one graph: no lost updates, latency measured
 *  - memory survives a full FalkorDB restart (episodic, procedural, conversation)
 * Results are written to docs/results/*.json and quoted in the README.
 */
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { setupAll } from "../src/graph/setup.js";
import { closeAll, run, now } from "../src/graph/db.js";
import { bodyRead, homeRead } from "../src/graph/graphs.js";
import * as w from "../src/writeapi/index.js";
import { contextBands } from "../src/ingest/bands.js";
import { REPO_ROOT } from "../src/config.js";

const RESULTS = path.join(REPO_ROOT, "docs", "results");
const pct = (a: number[], p: number) => [...a].sort((x, y) => x - y)[Math.min(a.length - 1, Math.floor((p / 100) * a.length))];

beforeAll(async () => {
  for (const g of ["home:test", "body:alice", "body:bob"]) {
    try {
      await run(g, "MATCH (n) DETACH DELETE n");
    } catch {
      /* new */
    }
  }
  await setupAll();
  await w.setPresence("alice", true);
  await w.setPresence("bob", true);
  fs.mkdirSync(RESULTS, { recursive: true });
});
afterAll(closeAll);

async function timed<T>(lat: number[], f: () => Promise<T>): Promise<T> {
  const t = performance.now();
  try {
    return await f();
  } finally {
    lat.push(performance.now() - t);
  }
}

describe("high-concurrency reads and writes", () => {
  it("40 concurrent learners + 40 readers + lease/claim contention lose no updates", async () => {
    // One decision with 40 actions -> 40 concurrent Welford updates hit the SAME evidence nodes.
    const episode = await w.openEpisode("bob", {
      kind: "recovery",
      bands: contextBands({ kind: "recovery", hrZ: 2.2, activity: "post_exercise" }),
      summary: "load test",
      trigger: "test",
    });
    const decision = await w.logDecision("bob", {
      episode,
      by: "test",
      rationale: "load",
      considered: [{ name: "fan_high", score: 1, chosen: true, rejected_because: null }],
    });
    const N = 40;
    const actions = await Promise.all(
      Array.from({ length: N }, () => w.logAction("bob", { decision, intervention: "fan_high", device: "fan", applied: {}, evalDue: now() })),
    );
    const [before] = await run("body:bob", "MATCH (e:Evidence {kind:'recovery', intervention:'fan_high', band:'kind:recovery'}) RETURN e.n AS n");
    for (let i = 0; i < 20; i++) await w.createTask({ agent: "evaluator", kind: "evaluate", payload: { i } });

    const wLat: number[] = [];
    const rLat: number[] = [];
    const t0 = performance.now();
    const results = await Promise.all([
      ...actions.map((a, i) => timed(wLat, () => w.learn("bob", { action: a, effect: (i % 5) / 10, signal: "hr", method: "load" }))),
      ...Array.from({ length: N }, (_, i) =>
        timed(rLat, () => (i % 2 ? homeRead("feasible_settings", { room: "bedroom", now: now() }) : bodyRead("bob", "choose_intervention", { episode, alias: "bob", now: now() }))),
      ),
      ...Array.from({ length: 30 }, (_, i) => timed(wLat, () => w.acquireDevice("fan", i % 2 ? "arbiter" : "planner:alice", 60_000))),
      ...Array.from({ length: 30 }, (_, i) => timed(wLat, () => w.claimTask("evaluator", `w${i}`))),
    ]);
    const wall = performance.now() - t0;
    const [after] = await run("body:bob", "MATCH (e:Evidence {kind:'recovery', intervention:'fan_high', band:'kind:recovery'}) RETURN e.n AS n");
    expect(after.n - before.n).toBe(N); // every concurrent learn landed exactly once

    const leaseWinners = new Set(results.slice(2 * N, 2 * N + 30).map((r, i) => (r ? (i % 2 ? "arbiter" : "planner:alice") : null)).filter(Boolean));
    expect(leaseWinners.size).toBe(1);
    const claimed = results.slice(2 * N + 30).filter(Boolean).length;
    expect(claimed).toBe(20); // 20 tasks, 30 workers: each task exactly once

    const report = {
      ops: wLat.length + rLat.length,
      wall_ms: Math.round(wall),
      ops_per_s: Math.round(((wLat.length + rLat.length) / wall) * 1000),
      writes: { n: wLat.length, p50_ms: +pct(wLat, 50).toFixed(1), p95_ms: +pct(wLat, 95).toFixed(1) },
      reads: { n: rLat.length, p50_ms: +pct(rLat, 50).toFixed(1), p95_ms: +pct(rLat, 95).toFixed(1) },
      evidence_n_increment: after.n - before.n,
      lease_winners: leaseWinners.size,
      tasks_claimed: claimed,
      at: new Date().toISOString(),
    };
    fs.writeFileSync(path.join(RESULTS, "concurrency.json"), JSON.stringify(report, null, 2));
    console.log("CONCURRENCY", JSON.stringify(report));
  });
});

describe("memory survives a FalkorDB restart", () => {
  it("episodes, playbooks, preferences and conversation turns are still there", async () => {
    await w.addTurn("alice", { session: "ses-restart", role: "user", text: "24 degrees felt too cold for me last night" });
    await w.setPreference("alice", "room_temp", 25, 27, "voice");
    await w.publishPlaybook("alice", "sick_day", ["ac_warmer", "light_warm_dim"], "restart test");
    const marker = await w.openEpisode("alice", { kind: "too_cold", bands: contextBands({ kind: "too_cold" }), summary: "restart marker", trigger: "test" });
    await closeAll();
    execSync("docker restart homeos-falkordb", { stdio: "ignore" });
    for (let i = 0; i < 30; i++) {
      try {
        execSync("docker exec homeos-falkordb redis-cli PING", { stdio: "ignore" });
        break;
      } catch {
        await new Promise((r) => setTimeout(r, 500));
      }
    }
    await new Promise((r) => setTimeout(r, 1500));
    const ep = await bodyRead("alice", "explain", { decision: "none" }).catch(() => []);
    expect(Array.isArray(ep)).toBe(true);
    const [e] = await run("body:alice", "MATCH (e:Episode {id:$id}) RETURN e.summary AS s", { id: marker });
    expect(e.s).toBe("restart marker");
    const pb = await bodyRead("alice", "playbooks_for", { condition: "sick" });
    expect(pb[0].version).toBeGreaterThanOrEqual(2);
    const turns = await bodyRead("alice", "recall_fulltext", { text: "cold", k: 5 });
    expect(turns.some((t) => /too cold/.test(t.text))).toBe(true);
    const beliefs = await bodyRead("alice", "beliefs_at", { alias: "alice", at: now() });
    expect(beliefs.find((b) => b.kind === "PREFERS" && b.about === "room_temp")?.min).toBe(25);
    fs.writeFileSync(path.join(RESULTS, "restart_recall.json"), JSON.stringify({ ok: true, playbook_version: pb[0].version, recalled_turn: turns[0]?.text, at: new Date().toISOString() }, null, 2));
  });
});
