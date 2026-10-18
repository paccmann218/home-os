// [body graph] What this person is aiming for.
MATCH (p:Person {alias: $alias})-[:AIMS_FOR]->(g:Goal)
RETURN g.id AS id, g.kind AS kind, g.target_hours AS target_hours, g.minutes_before_event AS minutes_before_event, g.source AS source
ORDER BY g.id
