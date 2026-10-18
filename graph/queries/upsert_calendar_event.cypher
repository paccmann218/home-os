// [body graph] A calendar event is memory, not a hardcoded line. The person owns it.
MATCH (p:Person {alias: $alias})
MERGE (ev:Event {id: $id})
SET ev.kind = 'calendar', ev.source = $source, ev.title = $title, ev.at = $at
MERGE (p)-[:HAS_EVENT]->(ev)
RETURN ev.id AS id, ev.title AS title, ev.at AS at
