// [body graph] Record a condition (bi-temporal: an active one is closed, never overwritten).
// Unknown kinds become new Condition nodes the person can teach shifts for later.
MATCH (p:Person {alias: $alias})
MERGE (c:Condition {kind: $kind})
ON CREATE SET c.label = $label, c.learned = true
WITH p, c
OPTIONAL MATCH (p)-[old:HAS_CONDITION]->(c) WHERE old.valid_to > $now
FOREACH (o IN CASE WHEN old IS NULL THEN [] ELSE [old] END | SET o.valid_to = $now, o.superseded = true)
CREATE (p)-[h:HAS_CONDITION {valid_from: $now, valid_to: $until, source: $source, confidence: $confidence, note: $note}]->(c)
RETURN c.kind AS kind, h.valid_to AS until
