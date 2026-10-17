/**
 * Health Connect ingest (ring data via the phone app). Gabit writes sleep stages, heart rate,
 * resting HR, steps, VO2max and calories into Health Connect; the HomeOS Bridge app reads them
 * (including months of history) and posts them here. Each night becomes a sleep Episode linked to
 * context bands, so the Scientist's graph algorithms run over the person's real history.
 */
import { run, agentUser, now } from "../graph/db.js";
import { bodyGraph } from "../config.js";
import * as w from "../writeapi/index.js";
import { nightBands, minutesAfterSixPm } from "./bands.js";
import { bus } from "../bus.js";
import { checkRestingHr } from "../agents/guardian.js";
import { pushHr } from "./buffer.js";

export interface HealthBatch {
  source?: string;
  /** Wall-clock offset of the person's data (minutes east of UTC); default IST. */
  utc_offset_min?: number;
  /** Human label for episode summaries, e.g. "PMData p01 (public dataset)". */
  label?: string;
  /** quality: the person's own rating of the night when available (e.g. PMData wellness 1-5). */
  sleep?: { start: number; end: number; quality?: "good" | "bad"; stages?: { start: number; end: number; stage: string }[] }[];
  heart_rate?: { t: number; bpm: number }[];
  resting_hr?: { t: number; bpm: number }[];
  steps?: { start: number; end: number; count: number }[];
  exercise?: { start: number; end: number; type?: string }[];
  hrv?: { t: number; rmssd: number }[];
  skin_temp?: { t: number; delta: number }[];
  vo2max?: { t: number; value: number }[];
}

const DAY = 86_400_000;
const median = (a: number[]) => {
  if (!a.length) return NaN;
  const s = [...a].sort((x, y) => x - y);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};
const sd = (a: number[]) => {
  if (a.length < 2) return 0;
  const m = a.reduce((x, y) => x + y, 0) / a.length;
  return Math.sqrt(a.reduce((x, y) => x + (y - m) ** 2, 0) / (a.length - 1));
};

async function existingSleepIds(alias: string): Promise<Set<string>> {
  const rows = await run(bodyGraph(alias), "MATCH (e:Episode {kind:'sleep'}) RETURN e.id AS id", {}, { as: agentUser(alias), readOnly: true });
  return new Set(rows.map((r) => r.id as string));
}

export async function ingestHealth(alias: string, b: HealthBatch) {
  const out = { nights: 0, newNights: 0, baselines: [] as string[] };

  // Recent heart-rate samples also feed the live buffer (ring HR when the watch is off).
  for (const s of (b.heart_rate ?? []).filter((x) => now() - x.t < 15 * 60_000)) pushHr(alias, { t: s.t, hr: s.bpm });

  // Baselines (semantic memory, bi-temporal) from the 30 days before the batch's newest record
  // (= "now" for live syncs; the end of the recording for an imported history).
  const times = [
    ...(b.resting_hr ?? []).map((x) => x.t),
    ...(b.heart_rate ?? []).map((x) => x.t),
    ...(b.sleep ?? []).map((x) => x.end),
  ];
  const ref = times.length ? Math.max(...times) : now();
  const recentRhr = (b.resting_hr ?? []).filter((x) => ref - x.t < 30 * DAY).map((x) => x.bpm);
  if (recentRhr.length >= 3) {
    await w.setBaseline(alias, "hr_rest", median(recentRhr), Math.max(sd(recentRhr), 1.5), recentRhr.length, "health_connect");
    out.baselines.push("hr_rest");
  }
  const dayHr = (b.heart_rate ?? []).filter((x) => ref - x.t < 14 * DAY).map((x) => x.bpm);
  if (dayHr.length >= 50) {
    await w.setBaseline(alias, "hr", median(dayHr), Math.max(sd(dayHr), 4), dayHr.length, "health_connect");
    out.baselines.push("hr");
  }
  const hrv = (b.hrv ?? []).filter((x) => ref - x.t < 30 * DAY).map((x) => x.rmssd);
  if (hrv.length >= 3) {
    await w.setBaseline(alias, "rmssd", median(hrv), Math.max(sd(hrv), 3), hrv.length, "health_connect");
    out.baselines.push("rmssd");
  }

  // Nights -> sleep episodes.
  const nights = (b.sleep ?? []).filter((s) => s.end - s.start > 90 * 60_000).sort((x, y) => x.start - y.start);
  out.nights = nights.length;
  if (nights.length) {
    const have = await existingSleepIds(alias);
    const stats = nights.map((n) => {
      const mins = (stage: string) =>
        (n.stages ?? []).filter((s) => s.stage === stage).reduce((a, s) => a + (s.end - s.start) / 60_000, 0);
      const durationMin = (n.end - n.start) / 60_000 - mins("awake");
      return { n, durationMin, deep: mins("deep"), rem: mins("rem"), awake: mins("awake"), hasStages: (n.stages ?? []).length > 0 };
    });
    const deepMed = median(stats.filter((s) => s.hasStages).map((s) => s.deep));
    const deepSd = sd(stats.filter((s) => s.hasStages).map((s) => s.deep)) || 10;
    const durMed = median(stats.map((s) => s.durationMin));
    const durSd = sd(stats.map((s) => s.durationMin)) || 30;
    const offsetMin = b.utc_offset_min ?? 330;
    const usualBed = median(nights.map((n) => minutesAfterSixPm(n.start, offsetMin)));
    const rhrMed = median(recentRhr);
    const rhrSd = Math.max(sd(recentRhr), 1.5);

    for (const s of stats) {
      const id = `sleep-${alias}-${s.n.start}`;
      if (have.has(id)) continue; // already in memory, or a duplicate within this batch
      have.add(id);
      const dayStart = s.n.start - 16 * 3600_000;
      const stepsBefore = (b.steps ?? []).filter((x) => x.start >= dayStart && x.end <= s.n.start).reduce((a, x) => a + x.count, 0);
      const workoutEvening = (b.exercise ?? [])
        .filter((x) => x.start >= s.n.start - 5 * 3600_000 && x.start <= s.n.start)
        .reduce((a, x) => a + (x.end - x.start) / 60_000, 0);
      const rhrNight = (b.resting_hr ?? []).find((x) => Math.abs(x.t - s.n.end) < 14 * 3600_000)?.bpm ?? null;
      const bands = nightBands({
        start: s.n.start,
        durationMin: s.durationMin,
        deepMin: s.deep,
        restingHr: rhrNight,
        rhrBaseline: Number.isFinite(rhrMed) ? { mean: rhrMed, sd: rhrSd } : null,
        stepsBefore: (b.steps ?? []).length ? stepsBefore : null,
        workoutEveningMin: (b.exercise ?? []).length ? workoutEvening : null,
        usualBedtimeMin: usualBed,
        offsetMin,
      });
      // Quality: the person's own rating when they gave one; otherwise relative to THEIR own nights.
      const z = (s.hasStages && Number.isFinite(deepMed) ? (s.deep - deepMed) / deepSd : 0) + (s.durationMin - durMed) / durSd;
      const quality = s.n.quality ?? (z < -0.6 ? "bad" : "good");
      const summary = `${b.label ? `${b.label}: ` : ""}${Math.round(s.durationMin / 6) / 10} h sleep${s.hasStages ? `, ${Math.round(s.deep)} min deep, ${Math.round(s.rem)} min REM` : ""}${rhrNight ? `, resting HR ${rhrNight}` : ""}`;
      await w.openEpisode(alias, { id, kind: "sleep", bands, summary, trigger: "health_connect", start: s.n.start });
      await w.closeEpisode(alias, id, Number(z.toFixed(2)), quality);
      out.newNights++;
    }
  }

  if (out.newNights) bus.feed({ agent: `guardian:${alias}`, kind: "sync", person: alias, text: `Synced ${out.newNights} new night(s) from the ring (Health Connect)` });
  const latestRhr = (b.resting_hr ?? []).sort((x, y) => y.t - x.t)[0];
  if (latestRhr && now() - latestRhr.t < 36 * 3600_000) void checkRestingHr(alias, latestRhr.bpm);
  return out;
}
