/**
 * Records every real input (watch HR, chips, questions, Health Connect batches, prompt answers)
 * to replay/recordings/<date>.jsonl when HOMEOS_RECORD=on, so a judge can replay a real session
 * through the full pipeline without any hardware (see replay/README.md).
 */
import fs from "node:fs";
import path from "node:path";
import { REPO_ROOT } from "../config.js";

const on = process.env.HOMEOS_RECORD === "on";
const dir = path.join(REPO_ROOT, "replay", "recordings");
let file: string | null = null;

export function record(kind: string, person: string, data: unknown): void {
  if (!on) return;
  if (!file) {
    fs.mkdirSync(dir, { recursive: true });
    file = path.join(dir, `${new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-")}.jsonl`);
  }
  fs.appendFileSync(file, JSON.stringify({ t: Date.now(), kind, person, data }) + "\n");
}
