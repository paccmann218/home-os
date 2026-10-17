import { useState } from "react";
import { fmtTime } from "../api";

export interface Series {
  name: string;
  color: string;
  points: { t: number; v: number }[];
}

/** Live heart rate, last 5 minutes, one line per person (colour follows the person). */
export function HrChart({ series, height = 120 }: { series: Series[]; height?: number }) {
  const [hover, setHover] = useState<number | null>(null);
  const W = 300;
  const H = height;
  const pad = { l: 24, r: 62, t: 8, b: 16 };
  const now = Date.now();
  const t0 = now - 5 * 60_000;
  const all = series.flatMap((s) => s.points.filter((p) => p.t >= t0));
  const lo = Math.min(55, ...all.map((p) => p.v)) - 5;
  const hi = Math.max(110, ...all.map((p) => p.v)) + 5;
  const x = (t: number) => pad.l + ((t - t0) / (now - t0)) * (W - pad.l - pad.r);
  const y = (v: number) => pad.t + (1 - (v - lo) / (hi - lo)) * (H - pad.t - pad.b);
  const ticks = [lo + 5, (lo + hi) / 2, hi - 5].map((v) => Math.round(v));
  const hoverT = hover != null ? t0 + ((hover - pad.l) / (W - pad.l - pad.r)) * (now - t0) : null;

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      width="100%"
      height={H}
      onMouseMove={(e) => {
        const r = (e.currentTarget as SVGSVGElement).getBoundingClientRect();
        setHover(((e.clientX - r.left) / r.width) * W);
      }}
      onMouseLeave={() => setHover(null)}
      role="img"
      aria-label="Live heart rate per person"
    >
      {ticks.map((v) => (
        <g key={v}>
          <line x1={pad.l} x2={W - pad.r} y1={y(v)} y2={y(v)} stroke="#243049" strokeWidth={1} />
          <text x={pad.l - 4} y={y(v) + 3} fontSize={9} fill="#8a8f9c" textAnchor="end">
            {v}
          </text>
        </g>
      ))}
      <text x={pad.l} y={H - 4} fontSize={9} fill="#8a8f9c">
        −5 min
      </text>
      <text x={W - pad.r} y={H - 4} fontSize={9} fill="#8a8f9c" textAnchor="end">
        now
      </text>
      {series.map((s) => {
        const pts = s.points.filter((p) => p.t >= t0);
        if (pts.length < 2) return null;
        const d = pts.map((p, i) => `${i ? "L" : "M"}${x(p.t).toFixed(1)},${y(p.v).toFixed(1)}`).join("");
        const last = pts[pts.length - 1];
        return (
          <g key={s.name}>
            <path d={d} fill="none" stroke={s.color} strokeWidth={2} strokeLinejoin="round" />
            <circle cx={x(last.t)} cy={y(last.v)} r={4} fill={s.color} stroke="#111827" strokeWidth={2} />
            <text x={x(last.t) + 8} y={y(last.v) + 3} fontSize={10} fill="#f0efec">
              {s.name} {Math.round(last.v)}
            </text>
          </g>
        );
      })}
      {hoverT != null && hover! >= pad.l && hover! <= W - pad.r && (
        <g>
          <line x1={hover!} x2={hover!} y1={pad.t} y2={H - pad.b} stroke="#8a8f9c" strokeDasharray="2 3" />
          {series.map((s, i) => {
            const p = s.points.reduce<{ t: number; v: number } | null>((best, q) => (!best || Math.abs(q.t - hoverT) < Math.abs(best.t - hoverT) ? q : best), null);
            if (!p || Math.abs(p.t - hoverT) > 15_000) return null;
            return (
              <text key={s.name} x={Math.min(hover! + 6, W - 120)} y={pad.t + 12 + i * 12} fontSize={10} fill="#f0efec">
                {s.name}: {Math.round(p.v)} bpm · {fmtTime(p.t)}
              </text>
            );
          })}
        </g>
      )}
      {!all.length && (
        <text x={W / 2} y={H / 2} fontSize={11} fill="#8a8f9c" textAnchor="middle">
          Waiting for a watch on a wrist…
        </text>
      )}
    </svg>
  );
}
