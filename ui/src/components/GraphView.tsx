import { useEffect, useMemo, useRef, useState } from "react";
import ForceGraph2D from "react-force-graph-2d";
import type { Graph, GNode } from "../api";

/** Node families -> colour (6 groups, fixed order; label + legend carry identity too). */
const GROUPS: { name: string; labels: string[]; color: string }[] = [
  { name: "People & agents", labels: ["Person", "Agent", "Home"], color: "#3987e5" },
  { name: "Episodes & events", labels: ["Episode", "Event", "Session", "Turn"], color: "#d95926" },
  { name: "Context bands", labels: ["Band", "Habit"], color: "#199e70" },
  { name: "Decisions & actions", labels: ["Decision", "Action", "Arbitration", "Intent", "Task", "Insight", "Feedback"], color: "#c98500" },
  { name: "Conditions & constraints", labels: ["Condition", "Constraint", "ConstraintExport", "ConstraintRef", "Playbook"], color: "#d55181" },
  { name: "Home & body knowledge", labels: ["Room", "Device", "DeviceRef", "Var", "Signal", "Intervention", "Evidence"], color: "#9085e9" },
];
const groupOf = (label: string) => GROUPS.find((g) => g.labels.includes(label)) ?? GROUPS[5];

function caption(n: GNode): string {
  const p = n.props;
  return String(p.alias ?? p.key ?? p.kind ?? p.name ?? p.label ?? p.id ?? n.label).slice(0, 26);
}

export function GraphView({ graph, highlight }: { graph: Graph | null; highlight?: Graph | null }) {
  const box = useRef<HTMLDivElement>(null);
  const fg = useRef<any>(null);
  const [size, setSize] = useState({ w: 600, h: 400 });
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setSize({ w: el.clientWidth, h: el.clientHeight }));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const hiNodes = useMemo(() => new Set((highlight?.nodes ?? []).map((n) => n.id)), [highlight]);
  const hiEdges = useMemo(() => new Set((highlight?.edges ?? []).map((e) => e.id)), [highlight]);

  const data = useMemo(() => {
    const nodes = new Map<number, any>();
    for (const n of [...(graph?.nodes ?? []), ...(highlight?.nodes ?? [])]) nodes.set(n.id, { ...n });
    const links = new Map<number, any>();
    for (const e of [...(graph?.edges ?? []), ...(highlight?.edges ?? [])]) {
      if (nodes.has(e.source) && nodes.has(e.target)) links.set(e.id, { ...e });
    }
    return { nodes: [...nodes.values()], links: [...links.values()] };
  }, [graph, highlight]);

  // Fit the whole graph into view whenever the data changes (and once the layout has settled).
  useEffect(() => {
    const t = setTimeout(() => fg.current?.zoomToFit(400, 40), 1200);
    return () => clearTimeout(t);
  }, [data, size.w, size.h]);

  const hasHi = hiNodes.size > 0;
  return (
    <div className="graphbox" ref={box}>
      <ForceGraph2D
        ref={fg}
        onEngineStop={() => fg.current?.zoomToFit(400, 30)}
        width={size.w}
        height={size.h}
        graphData={data}
        backgroundColor="#0b1322"
        cooldownTicks={120}
        nodeRelSize={4}
        linkColor={(l: any) => (hiEdges.has(l.id) ? "#4fd1c5" : hasHi ? "rgba(140,150,170,0.12)" : "rgba(140,150,170,0.35)")}
        linkWidth={(l: any) => (hiEdges.has(l.id) ? 2.5 : 1)}
        linkDirectionalArrowLength={3}
        linkDirectionalArrowRelPos={1}
        linkDirectionalParticles={(l: any) => (hiEdges.has(l.id) ? 2 : 0)}
        linkDirectionalParticleWidth={2.5}
        linkLabel={(l: any) => l.type}
        nodeLabel={(n: any) => `${n.label}: ${caption(n)}`}
        nodeCanvasObject={(n: any, ctx, scale) => {
          const g = groupOf(n.label);
          const on = !hasHi || hiNodes.has(n.id);
          const r = n.label === "Person" ? 7 : n.label === "Decision" || n.label === "Episode" ? 5.5 : 4;
          ctx.globalAlpha = on ? 1 : 0.25;
          ctx.beginPath();
          ctx.arc(n.x, n.y, r, 0, 2 * Math.PI);
          ctx.fillStyle = g.color;
          ctx.fill();
          ctx.lineWidth = 2 / scale;
          ctx.strokeStyle = hiNodes.has(n.id) ? "#4fd1c5" : "#0b1322";
          ctx.stroke();
          if (scale > 1.4 || hiNodes.has(n.id) || n.label === "Person") {
            ctx.font = `${10 / scale + 1}px Inter`;
            ctx.fillStyle = on ? "#c3c2b7" : "#55607a";
            ctx.fillText(caption(n), n.x + r + 2, n.y + 3);
          }
          ctx.globalAlpha = 1;
        }}
      />
      <div className="legend">
        {GROUPS.map((g) => (
          <span key={g.name}>
            <span className="dot" style={{ background: g.color, marginRight: 4 }} />
            {g.name}
          </span>
        ))}
      </div>
    </div>
  );
}
