// Seed: a sleep goal the morning query can compare against measured nights.
MATCH (p:Person {alias: $alias})
MERGE (g:Goal {id: 'sleep_hours'})
SET g.kind = 'sleep', g.target_hours = 7.5, g.source = 'default'
MERGE (p)-[:AIMS_FOR]->(g)
WITH p
MERGE (w:Goal {id: 'wake_buffer'})
SET w.kind = 'wake', w.minutes_before_event = 120, w.source = 'default'
MERGE (p)-[:AIMS_FOR]->(w)
