// [home graph, write API] Privacy boundary, shared side: replace a person's active constraints
// with anonymous var/min/max ranges plus a priority number (so conflicts can be resolved).
// No condition, symptom or reason is ever written here.
MATCH (p:Person {alias: $alias}), (r:Room {id: $room})
OPTIONAL MATCH (p)-[:HAS_CONSTRAINT]->(old:Constraint) WHERE old.valid_to > $now
FOREACH (o IN CASE WHEN old IS NULL THEN [] ELSE [old] END | SET o.valid_to = $now)
WITH DISTINCT p, r
UNWIND $exports AS e
CREATE (p)-[:HAS_CONSTRAINT]->(c:Constraint {id: e.id, var: e.var, min: e.min, max: e.max, priority: e.priority, valid_from: $now, valid_to: $until})-[:FOR_ROOM]->(r)
RETURN count(c) AS published
