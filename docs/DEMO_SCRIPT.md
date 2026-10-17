# Demo video script (about 4 minutes)

**Cast:** person A wears the Galaxy Watch (`harsh`); person B wears the Gabit ring and uses the phone app (`shashwath`). This works with one person too: use the phone's chips for B.

**Screens to record:**
- the dashboard at `http://<laptop>:3001`, full screen;
- the watch mirrored with `scrcpy -s <watch-ip:port>` (currently 192.168.215.52:35839);
- the FalkorDB Browser at `http://localhost:3000`;
- a phone camera on the room (ESP32 LED, people).

**Before recording:**
- `HOMEOS_EVAL_WINDOW_SEC=120`, `HOMEOS_RECORD=on` (keeps the session for replay mode).
- p01 is imported (`scripts/import-pmdata.ts --batch ../replay/datasets/pmdata-p01.json --alias p01`). If you wear the ring the night before, HomeOS Bridge (or `adb shell am broadcast -n com.homeos.bridge/.SyncReceiver --el days 2`) syncs that real night too.
- Nobody fakes numbers: every value on screen comes from the devices.

| # | Time | Shot | Narration (one line, naming the track bullet) |
|---|---|---|---|
| 1 | 0:00 | Room + both wearables, dashboard | "Your wearables know when you're sick or overheated. Your home doesn't. HomeOS gives it a nervous system, and its memory is FalkorDB." |
| 2 | 0:15 | Dashboard header: 3 graphs. FalkorDB Browser: `GRAPH.LIST` | "One private graph per person, one shared home graph. **Isolated memory per user**, enforced by FalkorDB ACL." |
| 3 | 0:30 | B taps **Feeling sick** on the phone. Feed: condition (private) → shared ranges. Dashboard tab `home:flat1` shows a Constraint with no reason | "B's condition stays in B's graph. The home only learns a range, 26 °C or warmer, never why. **Multi-hop**: fever implies sick, which shifts room temperature." |
| 4 | 1:00 | A does 60 s of jumping jacks; HR line climbs. Guardian opens a recovery episode; planner feed shows `MCP tool → …` | "A's heart rate is two SD above *his own* baseline. His planner picks tools **through MCP** and ranks options from his evidence." |
| 5 | 1:25 | AC tile stays 26 °C, fan turns on, watch vibrates (pacer). Decision card: `room_temp 26–27 · someone present` | "He wanted it colder. The arbiter's graph query says someone present needs 26. **Actions from graph results**: fan plus a breathing pacer instead." |
| 6 | 1:50 | A raises his wrist: "Why isn't it colder?" The watch speaks; provenance path highlighted | "**Explainable**: the answer is a path in the graph, and B's reason can't leak because it isn't in any graph A can read." |
| 7 | 2:15 | About 2 minutes later, feed: "Measured hr_recovery…" Evidence bars move (grey ghost = before) | "The evaluator measured how *his body* responded and updated evidence in one atomic query. Next time the ranking changes. **Episodic + procedural memory**." |
| 8 | 2:40 | `docker restart homeos-falkordb`, then B asks on the phone: "What helps me when I'm sick?" | "Kill it, restart, ask again. The playbook, episodes and past turns are all still there. **Persistent memory**." |
| 9 | 3:00 | Click **Run Scientist · p01** (labelled public PMData dataset, 150 real nights). Report card: driver, keystone, 3 kinds of bad nights, cause chain. Ask box as p01: "What usually ruins my nights?" | "Our ring had little history, so here's five months of a real person's nights from a public dataset. PageRank finds the driver (short sleep, 1.6× risk), label propagation finds three kinds of bad night, SPpaths explains the last one. **Graph algorithms as agent tools**." |
| 10 | 3:30 | Coordination board (task handoffs). Terminal: `npm test` (ACL denial, lease race, 0 lost updates) and `docs/results/ablation.md` | "Agents coordinate only through the graph. Concurrent writes lose nothing. And with FalkorDB off the house drops to 22 °C on a sick person and can't explain anything." |
| 11 | 3:50 | Dashboard | "HomeOS: a home that remembers what helps *you*." |

Put chapters in the YouTube description: 0:00 Intro · 0:30 Track 2: private memory · 1:00 Track 1: multi-hop decision · 1:50 Explainable · 2:15 Learning · 2:40 Restart recall · 3:00 Graph algorithms · 3:30 Coordination, concurrency, ablation.
