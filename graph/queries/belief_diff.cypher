// [body graph] What changed in HomeOS's beliefs about this person between $since and $now.
MATCH (p:Person {alias: $alias})-[r:HAS_CONDITION|PREFERS|DISLIKES|HAS_BASELINE]->(x)
WHERE (r.valid_from > $since AND r.valid_from <= $now) OR (r.valid_to > $since AND r.valid_to <= $now)
RETURN type(r) AS kind, coalesce(x.kind, x.name) AS about,
       CASE WHEN r.valid_from > $since THEN 'learned' ELSE 'retired' END AS change,
       r.min AS min, r.max AS max, r.mean AS mean, r.source AS source,
       CASE WHEN r.valid_from > $since THEN r.valid_from ELSE r.valid_to END AS at
ORDER BY at
