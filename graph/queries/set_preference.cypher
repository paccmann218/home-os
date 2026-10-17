// [body graph] Learn/teach a comfort preference. Closes the old belief, keeps it for belief_diff.
MATCH (p:Person {alias: $alias}), (v:Var {name: $var})
OPTIONAL MATCH (p)-[old:PREFERS]->(v) WHERE old.valid_to > $now
WITH p, v, old, coalesce(old.min, $min) AS oldMin, coalesce(old.max, $max) AS oldMax
FOREACH (o IN CASE WHEN old IS NULL THEN [] ELSE [old] END | SET o.valid_to = $now)
CREATE (p)-[n:PREFERS {min: coalesce($min, oldMin), max: coalesce($max, oldMax), valid_from: $now, valid_to: $forever, source: $source}]->(v)
RETURN n.min AS min, n.max AS max
