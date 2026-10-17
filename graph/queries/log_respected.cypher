// [body graph] Which shared constraints bounded this decision. Own constraints link back to
// their private reasons (SAME_AS -> ConstraintExport -DERIVED_FROM-> Condition); others' don't.
MATCH (d:Decision {id: $decision})
UNWIND $respected AS r
CREATE (d)-[:RESPECTED]->(cr:ConstraintRef {id: r.id, var: r.var, min: r.min, max: r.max, mine: r.mine})
WITH cr
OPTIONAL MATCH (x:ConstraintExport {id: cr.id})
FOREACH (xx IN CASE WHEN x IS NULL THEN [] ELSE [x] END | MERGE (cr)-[:SAME_AS]->(xx))
RETURN count(cr) AS refs
