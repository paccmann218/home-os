# HomeOS landing page

The public page for HomeOS: what it is, the hardware it connects, the agents, privacy, results and the
phone app. Vite + React + TypeScript, built to static files.

```bash
cd landing
npm ci
npm run dev              # http://localhost:5173
npm run build            # type-check + production build into landing/dist
npm run preview          # serve the build at http://localhost:4173
npm run check:contrast   # WCAG audit of every text/background pairing, both themes
```

## Deploying

`landing/dist` is plain static files. Any static host works:

- **Vercel / Netlify:** root directory `landing`, build command `npm run build`, output `dist`.
- **GitHub Pages:** build, then publish `landing/dist`.

## The "Download the app" button

It points to `homeos-bridge.apk` on the repo's latest GitHub release:

```
https://github.com/shashwath1278/Graph-hackathon/releases/latest/download/homeos-bridge.apk
```

To make it work, build the Bridge app (`apps/phone`, `./gradlew assembleDebug` or `assembleRelease`) and
attach the APK to a GitHub release as `homeos-bridge.apk`. The repo must be public for visitors to download
it. To host the APK somewhere else, set `VITE_APK_URL` at build time:

```bash
VITE_APK_URL=https://example.com/homeos-bridge.apk npm run build
```

## Design system

- **Palette:** the phone app's own (`apps/phone/.../theme/Color.kt`), authored in OKLCH and contrast-audited.
  Two themes, Daylight and Night, follow the system unless the visitor picks one. Tokens live in
  `src/styles/tokens.css`; accents come in pairs (a bright fill tone and a deep ink tone), as in the app.
- **Type:** Bricolage Grotesque (display), Inter (text, tabular figures), JetBrains Mono (data). All three are
  bundled through Fontsource, so the page makes no third-party font requests.
- **Copy:** every claim and number is in `src/content.ts`, sourced from the main README and `docs/results`.
- **Motion (kept subtle, defined in `src/motion.ts` and `src/gsap.ts`):**
  - Framer Motion: sections rise in once as they scroll into view, with a short stagger; cards lift slightly on
    hover; the hero card's events slide in and its readings cross-fade; the theme icon turns as it swaps.
  - GSAP: the hero headline rises in word by word on load; the stats count up when reached; the privacy
    diagram builds itself once (nodes, then what crosses into the home, then what doesn't).
  - CSS: the watch's rings breathe at the pacer's 5.5 breaths/min, and the heart-rate trace scrolls.
  - Visitors who prefer reduced motion, and automated browsers (screenshot services, crawlers), get the
    finished page with no entrance animations, so nothing is ever captured half-revealed.
