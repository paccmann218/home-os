// WCAG contrast audit for every text/background pairing the page draws, in both themes.
// Reads the tokens straight out of src/styles/tokens.css. Run: npm run check:contrast
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const css = fs.readFileSync(path.join(here, "..", "src", "styles", "tokens.css"), "utf8");

function block(selector) {
  const i = css.indexOf(selector);
  if (i < 0) throw new Error(`selector not found: ${selector}`);
  const open = css.indexOf("{", i);
  let depth = 0;
  for (let j = open; j < css.length; j++) {
    if (css[j] === "{") depth++;
    if (css[j] === "}" && --depth === 0) return css.slice(open + 1, j);
  }
  throw new Error("unbalanced braces");
}
function tokens(body) {
  const out = {};
  for (const m of body.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})\b/g)) out[m[1]] = m[2];
  return out;
}
const lum = (hex) => {
  const c = [1, 3, 5].map((k) => parseInt(hex.slice(k, k + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
};
const ratio = (a, b) => {
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
};

const themes = {
  daylight: tokens(block(':root[data-theme="light"]')),
  night: tokens(block(':root[data-theme="dark"]')),
};

// [foreground, background, minimum]. 4.5 for body text, 3 for large display text and UI marks.
const PAIRS = [
  ["ink", "bg", 4.5], ["ink", "surface", 4.5], ["ink", "surface-2", 4.5], ["ink", "bg-sunk", 4.5],
  ["ink-2", "bg", 4.5], ["ink-2", "surface", 4.5], ["ink-2", "surface-2", 4.5], ["ink-2", "bg-sunk", 4.5],
  ["ink-3", "bg", 4.5], ["ink-3", "surface", 4.5], ["ink-3", "surface-2", 4.5], ["ink-3", "bg-sunk", 4.5],
  ["accent", "bg", 4.5], ["accent", "surface", 4.5], ["on-accent", "accent", 4.5],
  ["teal", "surface", 4.5], ["violet", "surface", 4.5], ["amber", "surface", 4.5], ["red", "surface", 4.5], ["green", "surface", 4.5],
  ["teal", "surface-2", 4.5], ["violet", "surface-2", 4.5], ["amber", "surface-2", 4.5], ["red", "surface-2", 4.5], ["green", "surface-2", 4.5],
  ["on-fill", "teal-fill", 4.5], ["on-fill", "violet-fill", 4.5], ["on-fill", "amber-fill", 4.5], ["on-fill", "red-fill", 4.5], ["on-fill", "green-fill", 4.5],
];

let failures = 0;
for (const [name, t] of Object.entries(themes)) {
  console.log(`\n${name}`);
  for (const [fg, bg, min] of PAIRS) {
    if (!t[fg] || !t[bg]) {
      console.log(`  ?  ${fg} on ${bg}: token missing`);
      failures++;
      continue;
    }
    const r = ratio(t[fg], t[bg]);
    const ok = r >= min;
    if (!ok) failures++;
    console.log(`  ${ok ? "ok" : "FAIL"}  ${fg.padEnd(10)} on ${bg.padEnd(10)} ${r.toFixed(2)}:1 (need ${min})`);
  }
}
// The phone mockup draws the app's own fixed colours.
const fixed = [["#1e1813", "#fffbf5"], ["#4a423a", "#fffbf5"], ["#fffbf5", "#9c4713"], ["#9c4713", "#fce4c6"], ["#1e1813", "#eda85c"], ["#4a423a", "#f3eadd"]];
console.log("\nphone mockup (app palette)");
for (const [fg, bg] of fixed) {
  const r = ratio(fg, bg);
  if (r < 4.5) failures++;
  console.log(`  ${r >= 4.5 ? "ok" : "FAIL"}  ${fg} on ${bg} ${r.toFixed(2)}:1`);
}
console.log(failures ? `\n${failures} pairing(s) below target` : "\nall pairings pass");
process.exit(failures ? 1 : 0);
