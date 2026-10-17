import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { setupAll } from "../src/graph/setup.js";
import { closeAll, run, agentUser, now } from "../src/graph/db.js";
import { bodyRead, homeRead, bodyAlgo } from "../src/graph/graphs.js";
import * as w from "../src/writeapi/index.js";
import { contextBands, nightBands } from "../src/ingest/bands.js";
import { toolByName } from "../src/tools/index.js";
import { homeGraph } from "../src/config.js";

beforeAll(async () => {
  // Reset only the test graphs (home:test, body:alice, body:bob).
  for (const g of ["home:test", "body:alice", "body:bob"]) {
    try {
      await run(g, "MATCH (n) DETACH DELETE n");
    } catch {
      /* graph did not exist */
    }
  }
  await setupAll();
  await w.setPresence("alice", true);
  await w.setPresence("bob", true);
});
afterAll(closeAll);

describe("personal conditions -> private reasons, anonymous shared constraints", () => {
  it("fever IMPLIES sick, which raises alice's room_temp floor to 26", async () => {
    await w.setCondition("alice", "fever", { hours: 12 });
    const comfort = await bodyRead("alice", "effective_comfort", { alias: "alice", now: now() });
    const temp = comfort.find((c) => c.var === "room_temp")!;
    expect(temp.min).toBe(26);
    expect(temp.conditions).toContain("fever");
    expect(temp.via).toContain("sick");
  });

  it("the home graph learns the range but not the reason", async () => {
    const rows = await run(
      homeGraph(),
      "MATCH (:Person {alias:'alice'})-[:HAS_CONSTRAINT]->(c:Constraint {var:'room_temp'}) WHERE c.valid_to > $now RETURN properties(c) AS c",
      { now: now() },
    );
    expect(rows[0].c.min).toBe(26);
    expect(JSON.stringify(rows)).not.toMatch(/fever|sick/);
  });

  it("feasible AC range for the room is bounded by alice's floor", async () => {
    const f = await homeRead("feasible_settings", { room: "bedroom", now: now() });
    const ac = f.find((r) => r.device === "ac" && r.var === "room_temp")!;
    expect(ac.min).toBe(26);
    expect(ac.bound_by).toContain("alice");
  });
});

describe("conflicting needs are resolved by priority, not by who asked last", () => {
  it("a sick person's 26 °C floor beats a post-workout 25 °C ceiling", async () => {
    await w.setCondition("bob", "post_workout", { hours: 1 });
    const f = await homeRead("feasible_settings", { room: "bedroom", now: now() });
    const ac = f.find((r) => r.device === "ac" && r.var === "room_temp")!;
    expect(ac.min).toBe(26);
    expect(ac.max).toBeGreaterThanOrEqual(26);
    expect(ac.overridden_for).toContain("bob");
    await w.clearCondition("bob", "post_workout");
  });
});

describe("decide, act, learn, explain", () => {
  let episode = "";
  let decision = "";
  it("ranks interventions for bob's post-workout episode from evidence", async () => {
    episode = await w.openEpisode("bob", {
      kind: "recovery",
      bands: contextBands({ kind: "recovery", hrZ: 2.5, roomTemp: 28, activity: "post_exercise", occupants: 2 }),
      summary: "HR 150 after a workout",
      trigger: "test",
    });
    const r = (await toolByName.get("choose_intervention")!.run("bob", { episode })) as any;
    expect(r.ranking.length).toBeGreaterThan(2);
    expect(r.ranking.map((x: any) => x.intervention)).toContain("fan_high");
  });

  it("logs a decision bounded by alice's constraint and learns from the outcome", async () => {
    const f = await homeRead("feasible_settings", { room: "bedroom", now: now() });
    const ac = f.find((r) => r.device === "ac" && r.var === "room_temp")!;
    decision = await w.logDecision("bob", {
      episode,
      by: "planner:bob",
      rationale: "fan instead of colder AC",
      considered: [
        { name: "fan_high", score: 0.4, chosen: true, rejected_because: null },
        { name: "ac_cooler", score: 0.5, chosen: false, rejected_because: "AC floor 26 °C set by someone present" },
      ],
      respected: [{ id: ac.constraint_ids[0], var: "room_temp", min: 26, max: null, mine: false }],
    });
    const action = await w.logAction("bob", { decision, intervention: "fan_high", device: "fan", applied: { level: 3 }, evalDue: now() });
    const updated = await w.learn("bob", { action, effect: 0.8, signal: "hr", d_hr: -20, method: "test" });
    expect(updated.length).toBeGreaterThan(0);
    expect(updated.find((u) => u.band === "kind:recovery")!.n).toBe(2);
  });

  it("explains with provenance; bob can't see why the floor exists", async () => {
    const ex = (await toolByName.get("explain")!.run("bob", { decision })) as any;
    expect(ex.private.chosen[0].name).toBe("fan_high");
    expect(ex.private.rejected[0].because).toMatch(/26/);
    expect(JSON.stringify(ex)).not.toMatch(/fever|sick/);
  });
});

describe("tenant isolation is enforced by FalkorDB ACL", () => {
  it("bob's agent user cannot read alice's graph", async () => {
    await expect(run("body:alice", "MATCH (n) RETURN count(n)", {}, { as: agentUser("bob"), readOnly: true })).rejects.toThrow(/NOPERM|permission/i);
  });
  it("agents cannot write the home graph", async () => {
    await expect(run(homeGraph(), "CREATE (:Hack)", {}, { as: "home_reader" })).rejects.toThrow(/NOPERM|permission/i);
  });
});

describe("coordination under concurrency", () => {
  it("exactly one of 20 concurrent agents wins a device lease", async () => {
    const agents = ["planner:alice", "planner:bob", "arbiter"];
    const results = await Promise.all(
      Array.from({ length: 20 }, (_, i) => w.acquireDevice("fan", agents[i % 3], 60_000).then((ok) => (ok ? agents[i % 3] : null))),
    );
    expect(new Set(results.filter(Boolean)).size).toBe(1);
  });

  it("a task is claimed by exactly one of 10 concurrent workers, and the handoff is linked", async () => {
    const first = await w.createTask({ agent: "arbiter", kind: "arbitrate", payload: { x: 1 } });
    const second = await w.createTask({ agent: "evaluator", kind: "evaluate", payload: { y: 1 }, from: first });
    const claims = await Promise.all(Array.from({ length: 10 }, (_, i) => w.claimTask("evaluator", `worker-${i}`)));
    expect(claims.filter(Boolean).length).toBe(1);
    const rows = await run(homeGraph(), "MATCH (t:Task {id:$id})-[:HANDOFF_FROM]->(p:Task) RETURN p.id AS p", { id: second });
    expect(rows[0].p).toBe(first);
  });
});

describe("memory over time and graph algorithms", () => {
  it("belief_diff shows what was learned", async () => {
    const d = (await toolByName.get("belief_diff")!.run("alice", { hours: 1 })) as any[];
    expect(d.some((x) => x.kind === "HAS_CONDITION" && x.about === "fever")).toBe(true);
  });

  it("Scientist algorithms run over a history of nights", async () => {
    const DAY = 86_400_000;
    const base = now() - 30 * DAY;
    for (let i = 0; i < 20; i++) {
      const late = i % 3 === 0;
      const warm = i % 2 === 0;
      const start = base + i * DAY + (late ? 2 : 0) * 3600_000 - 6 * 3600_000;
      const id = await w.openEpisode("alice", {
        kind: "sleep",
        bands: nightBands({ start, durationMin: late ? 330 : 450, roomTempAvg: warm ? 28 : 25, stepsBefore: 6000 }),
        summary: `night ${i}`,
        trigger: "test",
        start,
      });
      await w.closeEpisode("alice", id, null, late && warm ? "bad" : "good");
    }
    await bodyAlgo("alice", "sci_band_likelihood", { kind: "sleep" }, true);
    await bodyAlgo("alice", "sci_cooccurrence", { kind: "sleep" }, true);
    const why = (await toolByName.get("why_bad_night")!.run("alice", {})) as any;
    expect(why.chains.length).toBeGreaterThan(0);
    expect((await bodyAlgo("alice", "drivers", { limit: 5 })).length).toBeGreaterThan(0);
    expect(Array.isArray(await bodyAlgo("alice", "keystone", { limit: 5 }))).toBe(true);
    expect((await bodyAlgo("alice", "episode_types", {}, true)).length).toBeGreaterThan(0);
  });
});
