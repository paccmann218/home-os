// [body graph] Privacy boundary, private side: remember WHY each shared constraint exists.
// The home graph only ever receives var/min/max; the reasons stay here.
UNWIND $exports AS e
CREATE (x:ConstraintExport {id: e.id, var: e.var, min: e.min, max: e.max, at: $now})
WITH x, e
UNWIND (CASE WHEN size(e.reasons) = 0 THEN [null] ELSE e.reasons END) AS reason
OPTIONAL MATCH (c:Condition {kind: reason})
FOREACH (cc IN CASE WHEN c IS NULL THEN [] ELSE [c] END | MERGE (x)-[:DERIVED_FROM]->(cc))
RETURN count(DISTINCT x) AS recorded
