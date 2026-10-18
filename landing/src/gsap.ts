import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";

gsap.registerPlugin(ScrollTrigger, useGSAP);

/**
 * Run GSAP animations only for visitors who haven't asked for reduced motion. Components also skip
 * GSAP entirely when the page is STATIC (see motion.ts), e.g. in automated browsers.
 */
export const MOTION_OK = "(prefers-reduced-motion: no-preference)";

export { gsap, ScrollTrigger, useGSAP };
