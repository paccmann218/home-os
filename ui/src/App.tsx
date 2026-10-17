import { useCallback, useEffect, useRef, useState } from "react";
import { get, post, subscribe, PERSON_COLORS, type FeedEvent, type Graph } from "./api";
import { GraphView } from "./components/GraphView";
import { AskBox, Board, DecisionCard, EvidenceBars, Feed, Room, ScientistReport, type Device } from "./components/Panels";
import { HrChart, type Series } from "./components/HrChart";

interface PersonState {
  alias: string;
  devices: string[];
  present: boolean;
  dataset?: string | null;
}

export default function App() {
  const [people, setPeople] = useState<PersonState[]>([]);
  const [graphOn, setGraphOn] = useState(true);
  const [voice, setVoice] = useState(false);
  const [devices, setDevices] = useState<Device[]>([]);
  const [feed, setFeed] = useState<FeedEvent[]>([]);
  const [tab, setTab] = useState<string>("home");
  const [graph, setGraph] = useState<Graph | null>(null);
  const [highlight, setHighlight] = useState<Graph | null>(null);
  const [decision, setDecision] = useState<{ owner: string; d: any } | null>(null);
  const [evidence, setEvidence] = useState<{ owner: string; kind: string; rows: any[]; before?: any[] } | null>(null);
  const [board, setBoard] = useState<any[]>([]);
  const [stats, setStats] = useState<Record<string, { nodes: number; edges: number }>>({});
  const [flash, setFlash] = useState<Set<string>>(new Set());
  const [report, setReport] = useState<{ r: any; chains: any[] } | null>(null);

  const runScientist = useCallback(async (alias: string) => {
    const r = await post(`/api/scientist/${alias}`);
    const why = await get(`/api/tool/${alias}/why_bad_night`).catch(() => null);
    setReport({ r, chains: why?.chains ?? [] });
    setTab(alias);
  }, []);
  const hr = useRef<Record<string, { t: number; v: number }[]>>({});
  const [, tick] = useState(0);

  const refreshState = useCallback(async () => {
    const s = await get("/api/state");
    setPeople(s.people);
    setGraphOn(s.graphEnabled);
    setVoice(s.voice);
    setDevices(s.devices);
    setFeed(s.feed);
  }, []);

  const refreshGraph = useCallback(async () => {
    setGraph(await get(`/api/graph/${tab}?limit=350`));
    setStats(await get("/api/stats"));
    setBoard(await get("/api/board?minutes=60"));
  }, [tab]);

  const showDecision = useCallback(async (owner: string, id: string) => {
    const d = await get(`/api/tool/${owner}/explain?decision=${encodeURIComponent(id)}`);
    setDecision({ owner, d });
    setHighlight(await get(`/api/provenance/${owner}/${encodeURIComponent(id)}`));
    setTab(owner);
    const kind = d?.private?.episode_kind;
    if (kind) setEvidence({ owner, kind, rows: await get(`/api/evidence/${owner}/${kind}`) });
  }, []);

  useEffect(() => {
    refreshState()
      .then(async () => {
        // Show the most recent decision across people on load.
        const s = await get("/api/state");
        const latest = (
          await Promise.all(s.people.map(async (p: PersonState) => ((await get(`/api/decisions/${p.alias}?limit=1`))[0] ? { owner: p.alias, ...(await get(`/api/decisions/${p.alias}?limit=1`))[0] } : null)))
        )
          .filter(Boolean)
          .sort((a: any, b: any) => b.at - a.at)[0] as any;
        if (latest) await showDecision(latest.owner, latest.id);
      })
      .catch(() => undefined);
  }, [refreshState, showDecision]);

  useEffect(() => {
    refreshGraph().catch(() => undefined);
    const h = setInterval(() => refreshGraph().catch(() => undefined), 6000);
    return () => clearInterval(h);
  }, [refreshGraph]);

  useEffect(() => {
    const h = setInterval(() => tick((x) => x + 1), 1000);
    return () => clearInterval(h);
  }, []);

  useEffect(
    () =>
      subscribe((type, data) => {
        if (type === "feed") setFeed((f) => [...f.slice(-200), data]);
        if (type === "signal") {
          const arr = (hr.current[data.person] ??= []);
          arr.push({ t: data.t, v: data.hr });
          while (arr.length && arr[0].t < Date.now() - 6 * 60_000) arr.shift();
        }
        if (type === "device") {
          setDevices((ds) => ds.map((d) => (d.id === data.device ? { ...d, state: { ...d.state, ...data.state } } : d)));
          setFlash(new Set([data.device]));
          setTimeout(() => setFlash(new Set()), 1500);
        }
        if (type === "decision") showDecision(data.person, data.decision).catch(() => undefined);
        if (type === "learned") setEvidence({ owner: data.person, kind: data.kind, rows: data.after, before: data.before });
        if (type === "feed" && ["presence", "connect", "disconnect"].includes(data.kind)) refreshState().catch(() => undefined);
      }),
    [showDecision, refreshState],
  );

  const series: Series[] = people.map((p, i) => ({ name: p.alias, color: PERSON_COLORS[i % 3], points: hr.current[p.alias] ?? [] }));
  const totals = Object.values(stats).reduce((a, s) => ({ n: a.n + s.nodes, e: a.e + s.edges }), { n: 0, e: 0 });

  return (
    <div className="app">
      <div>
        <header>
          <h1>
            Home<span>OS</span>
          </h1>
          <span className="pill">
            <span className="dot" style={{ background: graphOn ? "var(--good)" : "var(--critical)" }} />
            FalkorDB {graphOn ? "memory on" : "OFF (ablation)"}
          </span>
          <span className="pill">voice: {voice ? "Sarvam" : "on-device"}</span>
          <span className="pill mono">
            {Object.keys(stats).length} graphs · {totals.n} nodes · {totals.e} edges
          </span>
          <span className="spacer" />
          {people.map((p) => (
            <button key={p.alias} onClick={() => runScientist(p.alias).then(refreshGraph)}>
              Run Scientist · {p.alias}
            </button>
          ))}
        </header>
        {!graphOn && <div className="ablation">Ablation: HOMEOS_GRAPH=off — fixed rules only. No personalisation, no conflict resolution, no memory, no explanations.</div>}
      </div>

      <div className="grid">
        <div className="col">
          <div className="card">
            <h2>People</h2>
            {people.map((p, i) => (
              <div className="person" key={p.alias} style={{ ["--c" as any]: PERSON_COLORS[i % 3] }}>
                <div className="name">{p.alias}</div>
                {p.dataset && <div className="dataset">public dataset · {p.dataset} · not in the home</div>}
                <div className="kv">
                  devices <b>{p.devices.length ? p.devices.join(", ") : "—"}</b>
                </div>
                <div className="kv">
                  in bedroom <b>{p.present ? "yes" : "no"}</b>
                </div>
                <div className="kv">
                  private graph{" "}
                  <b className="mono">
                    body:{p.alias} · {stats[`body:${p.alias}`]?.nodes ?? 0} nodes
                  </b>
                </div>
              </div>
            ))}
          </div>
          <div className="card">
            <h2>Heart rate · live</h2>
            <HrChart series={series.filter((s) => !people.find((p) => p.alias === s.name)?.dataset)} />
          </div>
          <div className="card">
            <h2>Ask HomeOS</h2>
            <AskBox
              people={people.map((p) => p.alias)}
              onAsk={(alias, text) => post(`/api/ask/${alias}`, { text, speak: false, channel: "dashboard" })}
            />
          </div>
          <div className="card grow">
            <h2>Coordination board · tasks & handoffs</h2>
            <Board tasks={board} />
          </div>
        </div>

        <div className="col">
          <div className="card">
            <h2>Bedroom · devices</h2>
            <Room devices={devices} flash={flash} />
          </div>
          <div className="card grow">
            <h2>
              Graph
              <span className="tabs">
                {["home", ...people.map((p) => p.alias)].map((t) => (
                  <button key={t} className={tab === t ? "active" : ""} onClick={() => setTab(t)}>
                    {t === "home" ? "home:flat1 (shared)" : `body:${t} (private)`}
                  </button>
                ))}
              </span>
              {highlight && (
                <button style={{ padding: "2px 8px", fontSize: 10 }} onClick={() => setHighlight(null)}>
                  clear path
                </button>
              )}
            </h2>
            <GraphView graph={graph} highlight={tab === decision?.owner ? highlight : null} />
          </div>
        </div>

        <div className="col">
          <div className="card">
            <h2>{report ? "Scientist · graph algorithms" : "Latest decision · why"}</h2>
            {report ? (
              <ScientistReport r={report.r} chains={report.chains} onClose={() => setReport(null)} />
            ) : (
              <DecisionCard d={decision?.d} owner={decision?.owner ?? ""} />
            )}
          </div>
          <div className="card">
            <h2>
              What works for {evidence?.owner ?? "…"} · {evidence?.kind ?? ""}
            </h2>
            {evidence ? <EvidenceBars rows={evidence.rows} before={evidence.before} /> : <div className="muted">Evidence appears after the first decision.</div>}
            {evidence?.before && <div className="privacy">Thin grey bar = before the last measured outcome.</div>}
          </div>
          <div className="card grow">
            <h2>Agents · live</h2>
            <Feed items={feed.slice(-120)} />
          </div>
        </div>
      </div>
    </div>
  );
}
