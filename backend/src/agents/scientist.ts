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
import { present, KEYSTONE_LABELS, KEYSTONE_RELS } from "../graph/schemaInfo.js";
import { bus } from "../bus.js";

const CONDITION_KIND: Record<string, string> = { sick_day: "unwell", cool_down: "recovery", wind_down: "sleep" };

export async function runScientist(alias: string) {
  const t0 = Date.now();
  await bodyAlgo(alias, "sci_band_likelihood", { kind: "sleep" }, true);
  await bodyAlgo(alias, "sci_cooccurrence", { kind: "sleep" }, true);
  const ks = await present(alias, KEYSTONE_LABELS, KEYSTONE_RELS);
  const [drivers, keystone, types] = await Promise.all([
    bodyAlgo(alias, "drivers", { limit: 5 }).catch(() => []),
    ks.rels.length ? bodyAlgo(alias, "keystone", { limit: 5, labels: ks.labels, rels: ks.rels }).catch(() => []) : Promise.resolve([]),
    bodyAlgo(alias, "episode_types", {}, true).catch(() => []),
  ]);
  const insights: string[] = [];
  const pct = (x: number) => `${Math.round(x * 100)}%`;
  if (drivers[0]) {
    const d = drivers[0];
    const text = `Top driver of bad nights: ${d.band} — ${d.bad_nights} of ${d.nights} such nights were bad (${pct(d.p_bad)}), ${Number(d.lift).toFixed(1)}× ${alias}'s usual rate (PageRank ${Number(d.score).toFixed(2)})`;
    insights.push(text);
    await w.writeInsight(alias, "pageRank", text, d.band, drivers);
  }
  const k = keystone.find((x) => (x.lift ?? 0) > 1);
  if (k) {
    const text = `Keystone deviation: ${k.band} sits on the most cause chains (betweenness ${Number(k.score).toFixed(0)}, ${Number(k.lift ?? 1).toFixed(1)}× risk)`;
    insights.push(text);
    await w.writeInsight(alias, "betweenness", text, k.band, keystone);
  }
  if (types.length) {
    const kinds = types.map((t: any) => `${t.signature.join(" + ") || "no clear deviation"} (${t.size})`).join("; ");
    const text = `${alias}'s bad nights come in ${types.length} kind${types.length > 1 ? "s" : ""}: ${kinds}`;
    insights.push(text);
    await w.writeInsight(alias, "labelPropagation", text, types[0].signature[0] ?? null, types);
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
