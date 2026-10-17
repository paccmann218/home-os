// [body graph] End a condition now (history is kept).
MATCH (p:Person {alias: $alias})-[h:HAS_CONDITION]->(c:Condition {kind: $kind})
WHERE h.valid_to > $now
SET h.valid_to = $now, h.ended_by = $source
RETURN count(h) AS ended
