// [body graph] Track 2 memory over time: what HomeOS believed about this person at time $at
// (bi-temporal edges: valid_from <= at < valid_to).
MATCH (p:Person {alias: $alias})-[r:HAS_CONDITION|PREFERS|DISLIKES|HAS_BASELINE]->(x)
WHERE r.valid_from <= $at AND r.valid_to > $at
RETURN type(r) AS kind, coalesce(x.kind, x.name) AS about, r.min AS min, r.max AS max,
       r.mean AS mean, r.sd AS sd, r.source AS source, r.valid_from AS since
ORDER BY kind, about
