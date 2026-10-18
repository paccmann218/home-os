import { AnimatePresence, m } from "framer-motion";
import { APK_URL, NAV } from "../content";
import { useScrolled, useTheme } from "../hooks";
import { DownloadIcon, MoonIcon, SunIcon, SystemIcon } from "./Icons";
import { LogoMark } from "./Logo";

export function Nav() {
  const scrolled = useScrolled();
  const { choice, cycle } = useTheme();
  const next = choice === "system" ? "light" : choice === "light" ? "dark" : "system";
  const Icon = choice === "light" ? SunIcon : choice === "dark" ? MoonIcon : SystemIcon;

  return (
    <header className={`nav${scrolled ? " is-scrolled" : ""}`}>
      <div className="wrap nav-inner">
        <a className="brand" href="#top" aria-label="HomeOS, back to top">
          <LogoMark className="brand-mark" />
          HomeOS
        </a>
        <nav className="nav-links" aria-label="Sections">
          {NAV.map((n) => (
            <a key={n.href} href={n.href}>
              {n.label}
            </a>
          ))}
        </nav>
        <div className="nav-actions">
          <button
            type="button"
            className="theme-toggle"
            onClick={cycle}
            aria-label={`Theme: ${choice}. Switch to ${next}.`}
            title={`Theme: ${choice}`}
          >
            <AnimatePresence mode="wait" initial={false}>
              <m.span
                key={choice}
                style={{ display: "grid" }}
                initial={{ rotate: -90, scale: 0.6, opacity: 0 }}
                animate={{ rotate: 0, scale: 1, opacity: 1 }}
                exit={{ rotate: 90, scale: 0.6, opacity: 0 }}
                transition={{ duration: 0.2 }}
              >
                <Icon />
              </m.span>
            </AnimatePresence>
          </button>
          <a className="btn btn-primary btn-sm" href={APK_URL}>
            <DownloadIcon />
            <span>
              Get the app
            </span>
          </a>
        </div>
      </div>
    </header>
  );
}
