/**
 * Discretise a moment into context Band keys ("hr:high", "room_temp:warm", "tod:evening").
 * Episodes that share bands become neighbours in the graph, which is how HomeOS finds
 * "situations like this one" and learns per-context evidence.
 */
export interface BandSpec {
  key: string;
  var: string;
  level: string;
}

export interface Context {
  kind: string;
  hrZ?: number | null;
  rmssdZ?: number | null;
  roomTemp?: number | null;
  humidity?: number | null;
  bright?: boolean | null;
  at?: number;
  activity?: "rest" | "post_exercise" | "sleep" | null;
  occupants?: number | null;
  conditions?: string[];
}

const band = (v: string, level: string): BandSpec => ({ key: `${v}:${level}`, var: v, level });

export function timeOfDay(at: number): string {
  // IST wall clock (the demo home is in India).
  const h = new Date(at + 5.5 * 3600_000).getUTCHours();
  if (h >= 5 && h < 12) return "morning";
  if (h >= 12 && h < 17) return "afternoon";
  if (h >= 17 && h < 22) return "evening";
  return "night";
}

export function contextBands(c: Context): BandSpec[] {
  const out: BandSpec[] = [band("kind", c.kind)];
  if (c.hrZ != null) out.push(band("hr", c.hrZ > 2 ? "high" : c.hrZ > 1 ? "elevated" : "normal"));
  if (c.rmssdZ != null) out.push(band("rmssd", c.rmssdZ < -1 ? "low" : c.rmssdZ > 1 ? "high" : "normal"));
  if (c.roomTemp != null) out.push(band("room_temp", c.roomTemp < 24 ? "cool" : c.roomTemp > 27 ? "warm" : "ok"));
  if (c.humidity != null) out.push(band("humidity", c.humidity < 35 ? "dry" : c.humidity > 65 ? "humid" : "ok"));
  if (c.bright != null) out.push(band("lux", c.bright ? "bright" : "dark"));
  out.push(band("tod", timeOfDay(c.at ?? Date.now())));
  if (c.activity) out.push(band("activity", c.activity));
  if (c.occupants != null) out.push(band("occupants", c.occupants >= 2 ? "2plus" : "1"));
  for (const k of c.conditions ?? []) out.push(band("condition", k));
  return dedupe(out);
}

/** Bands for a night of sleep built from Health Connect (ring) data. */
export interface NightFacts {
  start: number;
  durationMin: number;
  deepMin?: number | null;
  remMin?: number | null;
  awakeMin?: number | null;
  restingHr?: number | null;
  rhrBaseline?: { mean: number; sd: number } | null;
  stepsBefore?: number | null;
  workoutEveningMin?: number | null;
  roomTempAvg?: number | null;
  usualBedtimeMin?: number | null; // minutes after 18:00 IST
}

export function minutesAfterSixPm(at: number): number {
  const d = new Date(at + 5.5 * 3600_000);
  let m = d.getUTCHours() * 60 + d.getUTCMinutes() - 18 * 60;
  if (m < 0) m += 24 * 60;
  return m;
}

export function nightBands(n: NightFacts): BandSpec[] {
  const out: BandSpec[] = [band("kind", "sleep")];
  const bed = minutesAfterSixPm(n.start);
  const usual = n.usualBedtimeMin ?? 5 * 60; // 23:00
  out.push(band("bedtime", bed > usual + 45 ? "late" : bed < usual - 60 ? "early" : "usual"));
  out.push(band("sleep_window", n.durationMin < 360 ? "short" : n.durationMin > 540 ? "long" : "ok"));
  if (n.restingHr != null && n.rhrBaseline && n.rhrBaseline.sd > 0) {
    const z = (n.restingHr - n.rhrBaseline.mean) / n.rhrBaseline.sd;
    out.push(band("rhr", z > 1 ? "elevated" : z < -1 ? "low" : "normal"));
  }
  if (n.stepsBefore != null) out.push(band("steps", n.stepsBefore < 4000 ? "low" : n.stepsBefore > 10000 ? "high" : "ok"));
  if (n.workoutEveningMin != null) out.push(band("evening_workout", n.workoutEveningMin > 10 ? "yes" : "no"));
  if (n.roomTempAvg != null) out.push(band("room_temp", n.roomTempAvg < 24 ? "cool" : n.roomTempAvg > 27 ? "warm" : "ok"));
  const dow = new Date(n.start + 5.5 * 3600_000).getUTCDay();
  out.push(band("day", dow === 5 || dow === 6 ? "weekend_night" : "weeknight"));
  return dedupe(out);
}

/** Variables that are behaviour levers (Habit) rather than device levers (Var). */
export const HABIT_VARS = ["bedtime", "sleep_window", "steps", "evening_workout", "day", "activity", "tod"];

function dedupe(b: BandSpec[]): BandSpec[] {
  const seen = new Set<string>();
  return b.filter((x) => (seen.has(x.key) ? false : (seen.add(x.key), true)));
}
