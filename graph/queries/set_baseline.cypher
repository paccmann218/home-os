// [body graph] Semantic memory: a person's baseline for a signal, bi-temporal.
MATCH (p:Person {alias: $alias}), (s:Signal {name: $signal})
OPTIONAL MATCH (p)-[old:HAS_BASELINE]->(s) WHERE old.valid_to > $now
FOREACH (o IN CASE WHEN old IS NULL THEN [] ELSE [old] END | SET o.valid_to = $now)
CREATE (p)-[b:HAS_BASELINE {mean: $mean, sd: $sd, n: $n, valid_from: $now, valid_to: $forever, source: $source}]->(s)
RETURN b.mean AS mean, b.sd AS sd
