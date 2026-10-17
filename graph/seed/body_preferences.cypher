// Seed baseline comfort preferences (bi-temporal: valid_from/valid_to, never overwritten).
MATCH (p:Person {alias: $alias})
UNWIND $preferences AS pref
MATCH (v:Var {name: pref.var})
OPTIONAL MATCH (p)-[old:PREFERS]->(v) WHERE old.valid_to > $now
WITH p, v, pref, old
WHERE old IS NULL
CREATE (p)-[:PREFERS {min: pref.min, max: pref.max, valid_from: $now, valid_to: $forever, source: 'default'}]->(v)
