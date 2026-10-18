import { APK_URL, GITHUB_URL, WORLD_URL } from "../content";
import { LogoMark } from "./Logo";

export function Footer() {
  return (
    <footer className="footer">
      <div className="wrap footer-grid">
        <div>
          <a className="brand" href="#top" aria-label="HomeOS, back to top">
            <LogoMark className="brand-mark" />
            HomeOS
          </a>
          <p style={{ marginTop: "1rem" }}>Built for Graph Hacks 2026 (WeMakeDevs × FalkorDB)</p>
        </div>
        <nav className="footer-links" aria-label="Footer">
          <a href={GITHUB_URL}>Source on GitHub</a>
          <a href={`${GITHUB_URL}#run-it`}>Run it locally</a>
          <a href={APK_URL}>Android app</a>
          <a href={WORLD_URL}>3D world</a>
        </nav>
      </div>
    </footer>
  );
}
