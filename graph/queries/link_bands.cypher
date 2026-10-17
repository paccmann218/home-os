// [body graph] Give new Band nodes their explanation edges: Band -OF_VAR-> Var (a device lever)
// or Band -OF_HABIT-> Habit (a behaviour lever). Structural edges cost 0 in cause chains.
MATCH (b:Band) WHERE NOT (b)-[:OF_VAR]->() AND NOT (b)-[:OF_HABIT]->()
OPTIONAL MATCH (v:Var {name: b.var})
FOREACH (x IN CASE WHEN v IS NULL THEN [] ELSE [v] END | MERGE (b)-[r:OF_VAR]->(x) SET r.neglogp = 0.0)
WITH b, v
WHERE v IS NULL AND b.var IN $habits
MERGE (h:Habit:Lever {name: b.var})
MERGE (b)-[r:OF_HABIT]->(h) SET r.neglogp = 0.0
RETURN count(b) AS linked
