/** Which of the given labels / relationship types exist in a person's graph (algorithms reject unknown ones). */
import { run, agentUser } from "./db.js";
import { bodyGraph } from "../config.js";

export async function present(alias: string, labels: string[], rels: string[]) {
  const opts = { as: agentUser(alias), readOnly: true };
  const [ls, rs] = await Promise.all([
    run(bodyGraph(alias), "CALL db.labels() YIELD label RETURN collect(label) AS l", {}, opts),
    run(bodyGraph(alias), "CALL db.relationshipTypes() YIELD relationshipType RETURN collect(relationshipType) AS r", {}, opts),
  ]);
  const haveL = new Set<string>(ls[0]?.l ?? []);
  const haveR = new Set<string>(rs[0]?.r ?? []);
  return { labels: labels.filter((x) => haveL.has(x)), rels: rels.filter((x) => haveR.has(x)) };
}

export const KEYSTONE_LABELS = ["Episode", "Notable", "Var", "DeviceRef", "Habit"];
export const KEYSTONE_RELS = ["IN_STATE", "OF_VAR", "OF_HABIT", "CONTROLLED_BY"];
