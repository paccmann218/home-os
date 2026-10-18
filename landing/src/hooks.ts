import { useEffect, useState } from "react";

export type ThemeChoice = "system" | "light" | "dark";
const KEY = "homeos-theme";

function readChoice(): ThemeChoice {
  try {
    const v = localStorage.getItem(KEY);
    return v === "light" || v === "dark" ? v : "system";
  } catch {
    return "system";
  }
}

/** Light, dark, or follow the system. The choice is a per-visitor convenience, so storage failures are harmless. */
export function useTheme() {
  const [choice, setChoice] = useState<ThemeChoice>(readChoice);
  useEffect(() => {
    const root = document.documentElement;
    if (choice === "system") delete root.dataset.theme;
    else root.dataset.theme = choice;
    try {
      if (choice === "system") localStorage.removeItem(KEY);
      else localStorage.setItem(KEY, choice);
    } catch {
      /* private mode: the choice just isn't remembered */
    }
  }, [choice]);
  const cycle = () => setChoice((c) => (c === "system" ? "light" : c === "light" ? "dark" : "system"));
  return { choice, cycle };
}

/** True once the page has scrolled past `offset` pixels. */
export function useScrolled(offset = 8) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > offset);
    on();
    window.addEventListener("scroll", on, { passive: true });
    return () => window.removeEventListener("scroll", on);
  }, [offset]);
  return scrolled;
}

export function usePrefersReducedMotion() {
  const [reduced, setReduced] = useState(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const on = () => setReduced(mq.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);
  return reduced;
}
