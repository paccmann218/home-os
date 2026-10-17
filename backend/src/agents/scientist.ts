/**
 * Scientist (batch). Runs graph algorithms over a person's whole history:
 *   - re-learns -ln P(bad | band) for every context band (feeds SPpaths cause chains)
 *   - CO_OCCURS edges between bands on bad nights -> PageRank = top drivers
 *   - betweenness on episode->band->lever graph   -> keystone context
 *   - label propagation over episodes+bands        -> episode types
 *   - rewrites playbooks (procedural memory) when learned evidence disagrees with them
 * Results are stored as Insight nodes and new Playbook versions (SUPERSEDES chain).
 */
import { bodyAlgo, bodyRead } from "../graph/graphs.js";
import * as w from "../writeapi/index.js";
import { bus } from "../bus.js";

const CONDITION_KIND: Record<string, string> = { sick_day: "unwell", cool_down: "recovery", wind_down: "sleep" };

export async function runScientist(alias: string) {
  const t0 = Date.now();
  await bodyAlgo(alias, "sci_band_likelihood", { kind: "sleep" }, true);
  await bodyAlgo(alias, "sci_cooccurrence", { kind: "sleep" }, true);
  const [drivers, keystone, types] = await Promise.all([
    bodyAlgo(alias, "drivers", { limit: 5 }),
    bodyAlgo(alias, "keystone", { limit: 5 }),
    bodyAlgo(alias, "episode_types", {}, true),
  ]);
  const insights: string[] = [];
  if (drivers[0]) {
    const d = drivers[0];
    const text = `Top driver of bad nights: ${d.band} (PageRank ${Number(d.score).toFixed(2)}, P(bad night | ${d.band}) = ${Number(d.p_bad).toFixed(2)} over ${d.nights} nights)`;
    insights.push(text);
    await w.writeInsight(alias, "pageRank", text, d.band, drivers);
  }
  if (keystone[0]) {
    const k = keystone[0];
    const text = `Keystone context: ${k.band} sits on the most cause chains (betweenness ${Number(k.score).toFixed(1)})`;
    insights.push(text);
    await w.writeInsight(alias, "betweenness", text, k.band, keystone);
  }
  if (types.length) {
    const text = `Found ${types.length} episode types; largest: ${types[0].signature.join(" + ")} (${types[0].size} episodes)`;
    insights.push(text);
    await w.writeInsight(alias, "labelPropagation", text, null, types);
  }

  // Procedural memory: if the person's own evidence now disagrees with a playbook, version it.
  const playbookChanges: string[] = [];
  for (const [name, kind] of Object.entries(CONDITION_KIND)) {
    const works = await bodyRead(alias, "what_works", { kind, limit: 4 });
    const learned = works.filter((x) => x.learned_n >= 2 && x.intervention !== "none" && x.mean > 0);
    if (!learned.length) continue;
    const current = await bodyRead(alias, "playbooks_for", { condition: condFor(name) });
    const steps = learned.slice(0, 3).map((x) => x.intervention as string);
    const now = current.find((c) => c.playbook === name);
    const currentSteps = (now?.steps ?? []) as string[];
    const labels = learned.slice(0, 3).map((x) => x.label as string);
    if (labels.join("|") !== currentSteps.join("|")) {
      const r = await w.publishPlaybook(alias, name, steps, `re-ordered from ${alias}'s own outcomes: ${learned.map((x) => `${x.intervention} ${Number(x.mean).toFixed(2)} (n=${x.n})`).join(", ")}`);
      if (r) playbookChanges.push(`${name} v${r.version}`);
    }
  }
  const report = { alias, ms: Date.now() - t0, drivers, keystone, types, insights, playbookChanges };
  bus.feed({ agent: "scientist", kind: "report", person: alias, text: `Nightly analysis done in ${report.ms} ms: ${insights.length} insights, ${playbookChanges.length} playbook update(s)`, data: report });
  return report;
}

function condFor(playbook: string): string {
  return { sick_day: "sick", cool_down: "post_workout", wind_down: "sleeping" }[playbook] ?? playbook;
}
