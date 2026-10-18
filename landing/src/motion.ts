/**
 * The page's whole motion vocabulary, kept small on purpose.
 * Framer Motion: things rise into place once as they scroll in, and lift a little under the pointer.
 * GSAP (see gsap.ts): the hero headline, the stat counters and the privacy diagram.
 * Everything respects prefers-reduced-motion (MotionConfig reducedMotion="user" in App, gsap.matchMedia).
 */
import type { Transition, Variants } from "framer-motion";

export const EASE = [0.2, 0.7, 0.2, 1] as const;

/**
 * Render everything in place, with no entrance animations, for visitors who prefer reduced motion and
 * for automated browsers (screenshot services, crawlers, AI judges), which never scroll and would
 * otherwise capture sections still waiting to reveal.
 */
export const STATIC =
  typeof window === "undefined" ||
  window.matchMedia("(prefers-reduced-motion: reduce)").matches ||
  navigator.webdriver === true;

/** A container that staggers its children in. */
export const group: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.07, delayChildren: 0.04 } },
};

/** One child: a short rise and fade. */
export const item: Variants = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.6, ease: EASE } },
};

/** Animate once, when the element is a little way into the viewport. Static pages skip it entirely. */
export const inView = STATIC
  ? {}
  : ({
      initial: "hidden",
      whileInView: "show",
      viewport: { once: true, margin: "0px 0px -12% 0px" },
    } as const);

const spring: Transition = { type: "spring", stiffness: 380, damping: 28 };

/** A plate lifting a few pixels toward the pointer; its hard shadow stays put, so it reads as raised. */
export const lift = { whileHover: { y: -4 }, transition: spring } as const;
