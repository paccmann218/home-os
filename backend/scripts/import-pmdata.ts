/**
 * Import one participant of the public PMData dataset as a HomeOS "dataset person", through the
 * exact same Health Connect ingest path the phone app uses. This gives the Scientist months of
 * real, labelled nights (Fitbit sleep stages + the participant's own 1-5 sleep-quality ratings)
 * to run graph algorithms on, since the team's own ring was rarely worn before the event.
 *
 * PMData: Thambawita et al., "PMData: A Sports Logging Dataset", MMSys '20,
 * https://datasets.simula.no/pmdata/ - licensed CC BY 4.0. Nothing here is synthesised.
 *
 *   # from the converted batch committed in the repo (no download needed):
 *   npx tsx scripts/import-pmdata.ts --batch ../replay/datasets/pmdata-p01.json --alias p01
 *   # or from the original files of a participant (sleep.json, resting_heart_rate.json, ...):
 *   npx tsx scripts/import-pmdata.ts --dir <folder> --alias p01 --save ../replay/datasets/pmdata-p01.json
 */
import fs from "node:fs";
import path from "node:path";

const args = process.argv.slice(2);
const arg = (name: string, def = "") => (args.includes(`--${name}`) ? args[args.indexOf(`--${name}`) + 1] : def);
const url = arg("url", "http://localhost:3001");
const alias = arg("alias", "p01");

/** PMData times are Norwegian local time: CET (+60) until DST began on 2020-03-29 01:00 UTC (+120). */
function osloLocalToEpoch(s: string): number {
  const m = s.match(/(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})/) ?? s.match(/(\d{2})\/(\d{2})\/(\d{2}) (\d{2}):(\d{2}):(\d{2})/);
  if (!m) throw new Error(`bad time ${s}`);
  let [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  if (m[0].includes("/")) [y, mo, d] = [2000 + Number(m[3]), Number(m[1]), Number(m[2])]; // MM/DD/YY
  const utcGuess = Date.UTC(y, mo - 1, d, Number(m[4]), Number(m[5]), Number(m[6]));
  const offset = utcGuess - 60 * 60_000 >= Date.UTC(2020, 2, 29, 1) ? 120 : 60;
  return utcGuess - offset * 60_000;
}

function file(dir: string, ...names: string[]): string {
  for (const n of names) {
    const p = path.join(dir, n);
    if (fs.existsSync(p)) return p;
  }
  throw new Error(`none of ${names.join(", ")} in ${dir}`);
}

function convert(dir: string) {
  const sleep = JSON.parse(fs.readFileSync(file(dir, "sleep.json", "p01__fitbit__sleep.json"), "utf8")) as any[];
  const rhr = JSON.parse(fs.readFileSync(file(dir, "resting_heart_rate.json", "p01__fitbit__resting_heart_rate.json"), "utf8")) as any[];
  const exercise = JSON.parse(fs.readFileSync(file(dir, "exercise.json", "p01__fitbit__exercise.json"), "utf8")) as any[];
  const steps = JSON.parse(fs.readFileSync(file(dir, "steps.json", "p01__fitbit__steps.json"), "utf8")) as any[];
  const wellnessCsv = fs.readFileSync(file(dir, "wellness.csv", "p01__pmsys__wellness.csv"), "utf8").trim().split(/\r?\n/);
  const header = wellnessCsv[0].split(",");
  const wellness = wellnessCsv.slice(1).map((l) => Object.fromEntries(l.split(",").map((v, i) => [header[i], v])));

  // The participant's own rating of each night (reported the next morning): <=2 = bad.
  const ratingByDate = new Map<string, number>();
  for (const w of wellness) {
    const local = new Date(Date.parse(w.effective_time_frame) + 60 * 60_000).toISOString().slice(0, 10);
    const q = Number(w.sleep_quality);
    if (Number.isFinite(q) && q > 0) ratingByDate.set(local, q);
  }

  const stageName: Record<string, string> = { wake: "awake", light: "light", deep: "deep", rem: "rem", asleep: "light", restless: "awake", awake: "awake" };
  // PMData's monthly exports overlap by one night: keep each sleep log once.
  const seenLogs = new Set<string>();
  const nights = sleep
    .filter((s) => s.mainSleep && !seenLogs.has(s.startTime) && Boolean(seenLogs.add(s.startTime)))
    .map((s) => {
      const rating = ratingByDate.get(s.dateOfSleep);
      return {
        start: osloLocalToEpoch(s.startTime),
        end: osloLocalToEpoch(s.endTime),
        ...(rating != null ? { quality: rating <= 2 ? "bad" : "good", rating } : {}),
        stages: (s.levels?.data ?? []).map((x: any) => {
          const st = osloLocalToEpoch(x.dateTime);
          return { start: st, end: st + x.seconds * 1000, stage: stageName[x.level] ?? "light" };
        }),
      };
    });

  const resting = rhr
    .filter((r) => r.value?.value > 0)
    .map((r) => ({ t: osloLocalToEpoch(r.dateTime) + 8 * 3600_000, bpm: Math.round(r.value.value * 10) / 10 }));

  // Per-minute steps -> hourly totals.
  const hourly = new Map<number, number>();
  for (const s of steps) {
    const n = Number(s.value);
    if (!n) continue;
    const t = osloLocalToEpoch(s.dateTime);
    const h = t - (t % 3600_000);
    hourly.set(h, (hourly.get(h) ?? 0) + n);
  }
  const stepRecords = [...hourly].sort((a, b) => a[0] - b[0]).map(([h, count]) => ({ start: h, end: h + 3600_000, count }));

  const workouts = exercise.map((e) => {
    const start = osloLocalToEpoch(e.startTime);
    return { start, end: start + (e.activeDuration ?? e.duration ?? 0), type: e.activityName };
  });

  return {
    source: "pmdata",
    label: "PMData p01 (public dataset)",
    utc_offset_min: 60,
    attribution: "PMData (Thambawita et al., MMSys '20), https://datasets.simula.no/pmdata/, CC BY 4.0. Participant p01. Converted by HomeOS scripts/import-pmdata.ts.",
    sleep: nights,
    resting_hr: resting,
    steps: stepRecords,
    exercise: workouts,
  };
}

const batchPath = arg("batch");
const dir = arg("dir");
if (!batchPath && !dir) {
  console.error("usage: import-pmdata.ts (--batch <converted.json> | --dir <participant files>) [--alias p01] [--save out.json] [--url URL]");
  process.exit(1);
}
const batch = batchPath ? JSON.parse(fs.readFileSync(batchPath, "utf8")) : convert(dir);
const save = arg("save");
if (save) {
  fs.mkdirSync(path.dirname(save), { recursive: true });
  fs.writeFileSync(save, JSON.stringify(batch));
  console.log(`saved converted batch -> ${save} (${(fs.statSync(save).size / 1024).toFixed(0)} KB)`);
}
const rated = batch.sleep.filter((n: any) => n.quality).length;
console.log(`PMData ${alias}: ${batch.sleep.length} nights (${rated} self-rated, ${batch.sleep.filter((n: any) => n.quality === "bad").length} bad), ${batch.resting_hr.length} resting-HR days, ${batch.steps.length} step-hours, ${batch.exercise.length} workouts`);
const res = await fetch(`${url}/api/health/${alias}`, {
  method: "POST",
  headers: { "Content-Type": "application/json", "x-homeos-import": "pmdata" },
  body: JSON.stringify(batch),
});
console.log(res.status, await res.text());
