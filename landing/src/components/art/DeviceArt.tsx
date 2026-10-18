/**
 * Original line drawings of the three devices. Strokes and fills come from the theme, the accent from
 * the card's hue, so the drawings re-ink themselves for daylight and night.
 *
 * Each one moves the way the device works: the watch buzzes out the breathing pacer and shows a live
 * pulse, the ring floats with a glint running round the band while it tracks sleep, and the board
 * passes data from its sensors in and commands out to the light. CSS keyframes are switched off by the
 * reduced-motion rule in base.css; the SMIL data packets are only rendered when motion is allowed.
 */
import { useEffect, useState } from "react";
import { usePrefersReducedMotion } from "../../hooks";

const BPM = [72, 73, 75, 76, 74, 73, 71, 72];

export function WatchArt() {
  const reduced = usePrefersReducedMotion();
  const [beat, setBeat] = useState(0);
  useEffect(() => {
    if (reduced) return;
    const id = window.setInterval(() => setBeat((b) => (b + 1) % BPM.length), 1100);
    return () => window.clearInterval(id);
  }, [reduced]);

  return (
    <svg viewBox="0 0 240 300" role="img" aria-label="A round smartwatch running the HomeOS app, buzzing out a breathing pacer">
      <defs>
        <clipPath id="watch-face">
          <circle cx="120" cy="150" r="66" />
        </clipPath>
      </defs>

      {/* haptic pacer: ripples leave the case on every buzz */}
      {[0, 1.2, 2.4].map((d) => (
        <circle
          key={d}
          className="art-hue-stroke ripple"
          style={{ animationDelay: `${d}s` }}
          cx="120"
          cy="150"
          r="88"
          fill="none"
          strokeWidth="2"
          vectorEffect="non-scaling-stroke"
        />
      ))}

      <g className="watch-buzz">
        <rect className="art-fill-2 art-stroke" x="86" y="2" width="68" height="74" rx="16" strokeWidth="2.5" />
        <rect className="art-fill-2 art-stroke" x="86" y="224" width="68" height="74" rx="16" strokeWidth="2.5" />
        <rect className="art-fill art-stroke" x="200" y="120" width="14" height="24" rx="5" strokeWidth="2.5" />
        <rect className="art-fill art-stroke" x="200" y="158" width="14" height="24" rx="5" strokeWidth="2.5" />
        <circle className="art-fill art-stroke" cx="120" cy="150" r="86" strokeWidth="3" />
        <circle className="art-fill-2 art-stroke" cx="120" cy="150" r="75" strokeWidth="2" />
        <circle className="art-screen" cx="120" cy="150" r="66" />
        <image
          href="/img/watch.webp"
          x="54"
          y="84"
          width="132"
          height="132"
          clipPath="url(#watch-face)"
          preserveAspectRatio="xMidYMid slice"
        />
      </g>

      {/* live heart-rate readout */}
      <g className="hr-chip">
        <rect x="158" y="28" width="74" height="32" rx="16" className="art-fill art-stroke" strokeWidth="2" />
        <path
          className="beat"
          d="M176 52.5C176 52.5 168 47.5 168 42.5C168 39.8 170 38 172.4 38C174 38 175.3 38.9 176 40.2C176.7 38.9 178 38 179.6 38C182 38 184 39.8 184 42.5C184 47.5 176 52.5 176 52.5Z"
          fill="var(--red-fill)"
          stroke="var(--art-stroke)"
          strokeWidth="1.2"
        />
        <text x="190" y="50" className="hr-text">
          {BPM[beat]}
          <tspan className="hr-unit" dx="2">bpm</tspan>
        </text>
      </g>
    </svg>
  );
}

export function RingArt() {
  return (
    <svg viewBox="0 0 240 200" role="img" aria-label="A smart ring floating, its sensor glowing, tracking sleep">
      <ellipse className="ring-shadow" cx="120" cy="188" rx="72" ry="7" />
      <g className="ring-float">
        <ellipse className="art-fill-2 art-stroke" cx="120" cy="122" rx="86" ry="58" strokeWidth="2.5" />
        <ellipse className="art-fill art-stroke" cx="120" cy="108" rx="86" ry="58" strokeWidth="3" />
        <ellipse cx="120" cy="104" rx="63" ry="40" fill="var(--surface-2)" className="art-stroke" strokeWidth="2.5" />
        {/* the inner wall seen through the opening */}
        <path
          d="M57 104c0 22 28 40 63 40s63-18 63-40c0 14-28 28-63 28s-63-14-63-28Z"
          className="art-fill-2 art-stroke"
          strokeWidth="2"
        />
        {/* optical sensor on the inner wall */}
        <rect className="art-fill art-stroke" x="104" y="126" width="32" height="12" rx="6" strokeWidth="2" />
        <circle className="art-hue glow-pulse" cx="113" cy="132" r="3.4" />
        <circle className="art-hue glow-pulse" cx="127" cy="132" r="3.4" style={{ animationDelay: "0.6s" }} />
        {/* a glint that runs round the band */}
        <ellipse className="ring-glint" cx="120" cy="108" rx="86" ry="58" pathLength={100} />
      </g>
      {/* it's the one that knows how you slept */}
      {[
        { d: 0, s: 15 },
        { d: 1.2, s: 19 },
        { d: 2.4, s: 23 },
      ].map((z) => (
        <text key={z.d} x="178" y="64" className="zz" fontSize={z.s} style={{ animationDelay: `${z.d}s` }}>
          z
        </text>
      ))}
    </svg>
  );
}

const PINS = Array.from({ length: 15 }, (_, i) => 42 + i * 9.4);

/** Data moving along the wires: readings in from the sensors, commands out to the light. */
const PACKETS = [
  { path: "M268 66 H228", dur: "1.4s", begin: "0s" },
  { path: "M268 66 H228", dur: "1.4s", begin: "0.7s" },
  { path: "M268 132 H228", dur: "1.4s", begin: "0.35s" },
  { path: "M102 112 H46", dur: "1.6s", begin: "0s" },
];

const RAYS = Array.from({ length: 8 }, (_, i) => (i / 8) * Math.PI * 2);

export function BoardArt() {
  const reduced = usePrefersReducedMotion();
  return (
    <svg viewBox="0 -16 320 226" role="img" aria-label="An ESP32 board on Wi-Fi, reading a temperature sensor and a light sensor and switching an LED">
      {/* Wi-Fi, on your own network */}
      {[10, 19, 28].map((r, i) => (
        <path
          key={r}
          className="wifi-arc"
          style={{ animationDelay: `${i * 0.25}s` }}
          d={`M${165 - r * 0.7071} ${18 - r * 0.7071} A ${r} ${r} 0 0 1 ${165 + r * 0.7071} ${18 - r * 0.7071}`}
          fill="none"
          strokeWidth="3"
          strokeLinecap="round"
        />
      ))}

      {/* wires */}
      <g fill="none" strokeWidth="2" strokeLinecap="round" className="art-hue-stroke" opacity="0.7">
        <path d="M227 60H268" />
        <path d="M227 72H268" />
        <path d="M227 132H268" />
        <path d="M103 112H44" />
      </g>

      {/* the LED, standing in for the room's light */}
      <circle cx="30" cy="112" r="24" className="led-glow" />
      <g className="led-rays art-stroke" strokeWidth="2" strokeLinecap="round">
        {RAYS.map((a) => (
          <line key={a} x1={30 + Math.cos(a) * 17} y1={112 + Math.sin(a) * 17} x2={30 + Math.cos(a) * 23} y2={112 + Math.sin(a) * 23} />
        ))}
      </g>
      <circle cx="30" cy="112" r="11" className="art-fill art-stroke" strokeWidth="2.5" />
      <circle cx="30" cy="112" r="5" className="art-hue led-core" />

      {/* board */}
      <rect className="art-fill-2 art-stroke" x="96" y="22" width="138" height="172" rx="10" strokeWidth="2.5" />
      <rect className="art-fill art-stroke" x="114" y="32" width="102" height="92" rx="5" strokeWidth="2" />
      <path d="M124 42h10v8h10v-8h10v8h10v-8h10v8h10v-8h12" fill="none" className="art-stroke" strokeWidth="2" strokeLinejoin="round" />
      <rect className="art-fill-2 art-stroke" x="124" y="60" width="82" height="54" rx="4" strokeWidth="2" />
      <g className="art-stroke" strokeWidth="1.5" opacity="0.5">
        <path d="M134 72h62M134 82h62M134 92h62M134 102h40" />
      </g>
      {PINS.map((y) => (
        <g key={y}>
          <circle cx="105" cy={y} r="2.6" className="art-fill art-stroke" strokeWidth="1.5" />
          <circle cx="225" cy={y} r="2.6" className="art-fill art-stroke" strokeWidth="1.5" />
        </g>
      ))}
      <rect className="art-fill art-stroke" x="147" y="184" width="36" height="16" rx="4" strokeWidth="2" />
      <rect className="art-fill art-stroke" x="120" y="160" width="14" height="14" rx="3" strokeWidth="2" />
      <rect className="art-fill art-stroke" x="196" y="160" width="14" height="14" rx="3" strokeWidth="2" />
      <circle cx="165" cy="148" r="4" className="art-hue blink" />

      {/* DHT11 temperature + humidity */}
      <rect className="art-fill art-stroke" x="268" y="44" width="40" height="44" rx="5" strokeWidth="2.5" />
      <g className="art-stroke" strokeWidth="1.5" opacity="0.6">
        <path d="M275 54h26M275 62h26M275 70h26M275 78h26" />
      </g>
      {/* light sensor */}
      <rect className="art-fill art-stroke" x="268" y="116" width="40" height="32" rx="5" strokeWidth="2.5" />
      <circle cx="288" cy="132" r="8" className="art-fill-2 art-stroke" strokeWidth="2" />
      <path d="M284 132h8" className="art-stroke" strokeWidth="1.5" />

      {!reduced &&
        PACKETS.map((p, i) => (
          <circle key={i} r="3.6" className="packet" opacity="0">
            <animateMotion path={p.path} dur={p.dur} begin={p.begin} repeatCount="indefinite" />
            <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;0.15;0.8;1" dur={p.dur} begin={p.begin} repeatCount="indefinite" />
          </circle>
        ))}
    </svg>
  );
}
