// [body graph] Habit levers this person tracks.
MATCH (p:Person {alias: $alias})-[:TRACKS]->(h:Habit)
RETURN h.id AS id, h.name AS name
ORDER BY h.id
