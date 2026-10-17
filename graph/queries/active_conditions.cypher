// [body graph] Conditions in force now, expanded through IMPLIES (fever => sick).
MATCH (p:Person {alias: $alias})-[hc:HAS_CONDITION]->(c0:Condition)
WHERE hc.valid_from <= $now AND hc.valid_to > $now
OPTIONAL MATCH (c0)-[:IMPLIES*1..3]->(parent:Condition)
RETURN c0.kind AS kind, c0.label AS label, hc.valid_from AS since, hc.valid_to AS until,
       hc.source AS source, hc.confidence AS confidence, collect(parent.kind) AS implies
