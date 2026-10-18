import { useRef } from "react";
import { gsap, MOTION_OK, useGSAP } from "../gsap";
import { STATIC } from "../motion";

/**
 * Counts every number inside `value` up from zero when it scrolls into view, keeping the
 * surrounding text and each number's format ("3.6–6.8", "1,049", "40 / 40").
 * The final value is what renders first, so no-JS and reduced-motion visitors just see it.
 */
export function CountUp({ value }: { value: string }) {
  const ref = useRef<HTMLSpanElement>(null);

  useGSAP(
    () => {
      const el = ref.current;
      if (!el || STATIC) return;
      const parts = value.split(/(\d[\d,]*(?:\.\d+)?)/);
      const nums = parts.map((p, i) =>
        i % 2 ? { target: parseFloat(p.replace(/,/g, "")), decimals: (p.split(".")[1] ?? "").length, grouped: p.includes(",") } : null,
      );
      const format = (t: number) =>
        parts
          .map((p, i) => {
            const n = nums[i];
            if (!n) return p;
            const v = n.target * t;
            return n.grouped
              ? v.toLocaleString("en-US", { minimumFractionDigits: n.decimals, maximumFractionDigits: n.decimals })
              : v.toFixed(n.decimals);
          })
          .join("");

      const mm = gsap.matchMedia();
      mm.add(MOTION_OK, () => {
        const state = { t: 0 };
        el.textContent = format(0);
        gsap.to(state, {
          t: 1,
          duration: 1.5,
          ease: "power2.out",
          scrollTrigger: { trigger: el, start: "top 90%", once: true },
          onUpdate: () => {
            el.textContent = format(state.t);
          },
        });
        return () => {
          el.textContent = value;
        };
      });
    },
    { dependencies: [value] },
  );

  return <span ref={ref}>{value}</span>;
}
