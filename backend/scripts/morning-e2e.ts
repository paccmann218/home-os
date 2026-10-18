/**
 * Developer loader. Writes nights and one calendar event through the same functions the
 * phone sync and POST /api/calendar use, then runs the morning query. The decision is
 * morning_case.cypher, not this file.
 *
 *   npx tsx scripts/morning-e2e.ts harsh
 */
import { setupAll } from "../src/graph/setup.js";
import { openEpisode, closeEpisode } from "../src/writeapi/index.js";
import { bodyRead, homeRead } from "../src/graph/graphs.js";
import { recordCalendarEvent, proposeMorning, tryAnswerMorning } from "../src/agents/morning.js";
import { closeAll, now } from "../src/graph/db.js";

const alias = process.argv[2] || "harsh";
const DAY = 86_400_000;
const OFFSET = 330;

function atLocal(dayOffset: number, hour: number, minute = 0) {
  const nowLocal = now() + OFFSET * 60_000;
  const day = new Date(nowLocal);
  const midnight = Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate());
  return midnight + dayOffset * DAY + (hour * 60 + minute) * 60_000 - OFFSET * 60_000;
}

async function main() {
  await setupAll();
  const existing = await bodyRead(alias, "morning_case", { alias, now: now(), horizon: 36 * 3600_000 });
  const nights = Number(existing[0]?.nights ?? 0);
  if (nights < 5) {
    for (let i = 1; i <= 5; i++) {
      const start = atLocal(-i, 1, 30);
      const id = await openEpisode(alias, {
        id: `sleep-${alias}-week-${i}`,
        kind: "sleep",
        start,
        summary: `Short night, ${5 + (i % 2)} hours`,
        trigger: "health_connect",
        bands: [{ key: "sleep_window:short", var: "sleep_window", level: "short" }],
      });
      await closeEpisode(alias, id, -1.2, "bad");
    }
    console.log("wrote 5 bad sleep episodes");
  } else {
    console.log(`sleep already in graph: ${nights} nights`);
  }
  const meet = atLocal(1, 8, 0);
  await recordCalendarEvent(alias, { id: `cal-${alias}-standup`, title: "Morning standup", at: meet, source: "google_calendar" });
  console.log("calendar event", new Date(meet).toISOString());
  const proposed = await proposeMorning(alias);
  console.log("proposal", proposed);
  if (proposed && "proposed" in proposed && proposed.proposed) {
    const done = await tryAnswerMorning(alias, proposed.proposed, "yes");
    console.log("committed", done);
  }
  const clock = await homeRead("devices", { now: now() });
  console.log("clock", clock.find((d) => d.id === "clock"));
  const again = await bodyRead(alias, "morning_case", { alias, now: now(), horizon: 36 * 3600_000 });
  console.log("case", again[0]);
  await closeAll();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
