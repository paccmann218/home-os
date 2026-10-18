// [body graph] Pipes that observe this person.
MATCH (p:Person {alias: $alias})-[:OBSERVED_BY]->(s:Source)
RETURN s.id AS id, s.kind AS kind, s.via AS via
ORDER BY s.id
