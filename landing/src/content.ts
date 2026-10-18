/**
 * Every claim on the page lives here, and every number comes from the repo:
 * README.md and docs/results/*.json (latency, concurrency, ablation, scientist_p01).
 */

export const GITHUB_URL = "https://github.com/shashwath1278/Graph-hackathon";

/** The Bridge APK. Publish it as a GitHub release asset named homeos-bridge.apk, or set VITE_APK_URL. */
export const APK_URL: string =
  import.meta.env.VITE_APK_URL || `${GITHUB_URL}/releases/latest/download/homeos-bridge.apk`;

/** The walkable house. `frontned 3d/index.html`, served at /world/. */
export const WORLD_URL = "/world/";

export const NAV = [
  { href: "#how", label: "How it works" },
  { href: "#hardware", label: "Hardware" },
  { href: "#agents", label: "Agents" },
  { href: "#privacy", label: "Privacy" },
  { href: "#app", label: "App" },
];

export type Hue = "teal" | "violet" | "amber" | "red" | "green";

export const STEPS: { n: string; title: string; text: string; hue: Hue }[] = [
  { n: "01", title: "Sense", hue: "teal", text: "Heart rate from your watch, sleep from your ring, temperature and light from the room." },
  { n: "02", title: "Notice", hue: "red", text: "An episode opens when your signals leave your own baseline, not an average person's." },
  { n: "03", title: "Decide", hue: "violet", text: "Agents rank what has helped you before, within what everyone in the room accepts." },
  { n: "04", title: "Act", hue: "amber", text: "AC, fan, light, or a breathing pacer on your wrist." },
  { n: "05", title: "Learn & explain", hue: "green", text: "Your body's response becomes evidence. Ask “why?” and the graph answers." },
];

export type Agent = "watch" | "guardian" | "planner" | "arbiter" | "coach" | "evaluator";

export const EVENING: { at: string; agent: Agent; text: string; note: string }[] = [
  { at: "21:40", agent: "watch", text: "“I'm feeling sick.”", note: "Stored as a private condition, only in her own graph" },
  { at: "21:40", agent: "arbiter", text: "Room floor set to 26 °C, priority 3", note: "The shared home graph gets the range, never the reason" },
  { at: "21:52", agent: "guardian", text: "Harsh finished a workout; heart rate well above his baseline", note: "A recovery episode opens for him" },
  { at: "21:52", agent: "planner", text: "Fan to level 1 and a 5.5 breaths/min pacer on his watch", note: "The AC stays at 26 °C: the higher-priority floor wins" },
  { at: "21:53", agent: "coach", text: "“Why isn't it colder?”", note: "“Someone present needs it at 26 °C or warmer.”" },
  { at: "21:55", agent: "evaluator", text: "His heart-rate recovery is measured", note: "Evidence updated in one atomic query" },
];

export const DEVICES = [
  {
    id: "watch",
    name: "Galaxy Watch",
    kind: "Wear OS",
    hue: "teal" as Hue,
    line: "The live signal, and the voice you talk to.",
    senses: ["Heart rate, every second", "Beat-to-beat intervals for HRV", "Check-in chips on the wrist"],
    does: ["Push-to-talk questions in Hindi or English", "Spoken answers in about 6–9 s", "Haptic breathing pacer at your rate"],
  },
  {
    id: "ring",
    name: "Gabit smart ring",
    kind: "Health Connect",
    hue: "violet" as Hue,
    line: "The quiet one. It knows how you slept.",
    senses: ["Sleep and sleep windows", "HRV and resting heart rate", "Steps, exercise and VO2max"],
    does: ["Backfills 120 days on first sync", "Syncs every 15 minutes in the background", "Feeds the nightly Scientist"],
  },
  {
    id: "board",
    name: "ESP32 room node",
    kind: "ESPHome",
    hue: "amber" as Hue,
    line: "A small board on the shelf that reads the room and works the light.",
    senses: ["Temperature and humidity (DHT11)", "Bright or dark (light sensor)"],
    does: ["Dimmable LED as the room light", "Answers over a local REST API in ~100 ms"],
  },
];

export const ROOM_DEVICES = [
  { name: "WiZ bulb", text: "Warm and dim, or bright, over local UDP." },
  { name: "Air conditioner", text: "Setpoint chosen within everyone's comfort range." },
  { name: "Fan", text: "Level 0–3. Simulated on the dashboard until an IR blaster is wired in." },
];

export const HANDOFF = ["Guardian", "Planner", "Arbiter", "Planner", "Evaluator"];

export const AGENTS: { name: string; kind: string; hue: Hue; text: string; reads: string; writes: string }[] = [
  { name: "Guardian", kind: "deterministic · per person", hue: "red", text: "Compares your live signals with your own baseline and opens an episode when something drifts.", reads: "live HR / IBI, chips, ring nights", writes: "conditions, episodes, plan tasks" },
  { name: "Planner", kind: "LLM + graph tools · per person", hue: "violet", text: "Ranks what has helped you before with Thompson sampling, clamped to what the room allows.", reads: "evidence, playbooks, similar episodes", writes: "intents, decisions, actions" },
  { name: "Arbiter", kind: "deterministic · home", hue: "amber", text: "Settles conflicting needs by priority and applies them under a device lease. It can't read private graphs.", reads: "the shared home graph only", writes: "leases, arbitrations, device state" },
  { name: "Evaluator", kind: "deterministic", hue: "green", text: "Measures your body after each action and updates the evidence inside the database.", reads: "the raw buffer after an action", writes: "outcomes, evidence" },
  { name: "Coach", kind: "LLM voice · per person", hue: "teal", text: "The voice on your watch. Answers “why?” from the graph and remembers past conversations.", reads: "your graph + the home, read-only", writes: "sessions, turns, preferences" },
  { name: "Scientist", kind: "batch · nightly", hue: "violet", text: "Runs graph algorithms over your history each night and updates your playbooks.", reads: "your full history", writes: "insights, playbook versions" },
];

export const PRIVACY = [
  { title: "Enforced by the database", text: "Each person's agents log in as their own FalkorDB user. Ask for someone else's graph and the answer is NOPERM." },
  { title: "The model never writes", text: "Language models read through tools. Every write goes through typed functions, read queries run read-only with timeouts." },
  { title: "Raw signals stay in memory", text: "Second-by-second heart rate lives in a short buffer. The graph keeps only what agents reason over." },
];

export const STATS = [
  { value: "3.6–6.8", unit: "ms", label: "median time per graph query an agent runs" },
  { value: "1,049", unit: "ops/s", label: "mixed reads and writes under concurrent agents" },
  { value: "40 / 40", unit: "", label: "simultaneous evidence updates landed, none lost" },
  { value: "19", unit: "tools", label: "graph tools the agents choose from over MCP" },
];

export const FINDINGS = [
  { algo: "algo.pageRank", text: "Top driver of bad nights: a short sleep window. 30 of 63 such nights were rated bad (48%), 1.6× the usual rate." },
  { algo: "algo.betweenness", text: "Keystone: the short sleep window sits on the most cause chains." },
  { algo: "algo.labelPropagation", text: "Bad nights come in 3 kinds: short window + late bedtime (23), elevated resting HR + evening workout (9), very active day + evening workout (6)." },
  { algo: "algo.SPpaths", text: "Last bad night: short sleep window (likelihood 0.48), then late bedtime (0.29)." },
];

export const ABLATION = [
  { step: "She says “I'm sick”", on: "AC 27 °C, fan off", off: "AC 27 °C" },
  { step: "He finishes a workout", on: "AC stays 27 °C; he gets fan level 1 + a 5.5/min pacer", off: "AC drops to 22 °C on a sick person" },
  { step: "“Why not colder?”", on: "“Someone present bounds it.”", off: "“I don't have any memory…”" },
];

export const APP_FEATURES = [
  { title: "Check in", text: "One tap tells the home how you feel." },
  { title: "Ask", text: "Questions answered from your own evidence." },
  { title: "Why?", text: "Every decision opens into its reasons." },
  { title: "Memory", text: "What the home has learned about you, and when." },
  { title: "Ring sync", text: "Your ring's data, through Health Connect." },
  { title: "Your room in 3D", text: "The home and its devices, live." },
];
