/**
 * Replay a recorded real session (replay/recordings/*.jsonl) into a running HomeOS backend,
 * preserving the original timing (optionally sped up). No watch, ring or phone needed.
 *
 *   npx tsx scripts/replay.ts ../replay/recordings/<file>.jsonl [--speed 4] [--url http://localhost:3001]
 */
import fs from "node:fs";

const args = process.argv.slice(2);
const file = args.find((a) => a.endsWith(".jsonl"));
if (!file) {
  console.error("usage: replay.ts <recording.jsonl> [--speed N] [--url URL]");
  process.exit(1);
}
const speed = Number(args[args.indexOf("--speed") + 1] || 1) || 1;
const url = args.includes("--url") ? args[args.indexOf("--url") + 1] : "http://localhost:3001";
const events = fs.readFileSync(file, "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l));
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const post = (p: string, body: unknown) =>
  fetch(url + p, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }).catch((e) => console.warn(p, e.message));

console.log(`Replaying ${events.length} events from ${file} at ${speed}x into ${url}`);
const t0 = events[0]?.t ?? Date.now();
const start = Date.now();
for (const e of events) {
  const due = start + (e.t - t0) / speed;
  if (due > Date.now()) await sleep(due - Date.now());
  const d = e.data ?? {};
  switch (e.kind) {
    case "hr":
      await post(`/api/hr/${e.person}`, { hr: d.hr, ibi: d.ibi, t: Date.now() });
      break;
    case "onbody":
      await post(`/api/presence/${e.person}`, { present: d.on });
      break;
    case "chip":
      console.log(`  ${e.person}: chip ${d.chip}`);
      await post(`/api/chip/${e.person}`, { chip: d.chip, source: "replay" });
      break;
    case "ask":
      console.log(`  ${e.person}: "${d.text}"`);
      await post(`/api/ask/${e.person}`, { text: d.text, channel: "replay" });
      break;
    case "health":
      console.log(`  ${e.person}: Health Connect batch (${(d.sleep ?? []).length} nights)`);
      await post(`/api/health/${e.person}`, d);
      break;
    default:
      break; // prompt answers and raw audio are not replayed (ids/audio are session-specific)
  }
}
console.log("Replay finished.");
