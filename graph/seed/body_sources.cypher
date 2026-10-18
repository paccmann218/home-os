// Seed: where this person's facts come from. Ingest writes episodes; these nodes name the pipes.
MATCH (p:Person {alias: $alias})
MERGE (ring:Source {id: 'gabit_ring'})
SET ring.kind = 'wearable', ring.via = 'health_connect'
MERGE (watch:Source {id: 'galaxy_watch'})
SET watch.kind = 'wearable', watch.via = 'websocket'
MERGE (cal:Source {id: 'google_calendar'})
SET cal.kind = 'calendar', cal.via = 'api'
MERGE (p)-[:OBSERVED_BY]->(ring)
MERGE (p)-[:OBSERVED_BY]->(watch)
MERGE (p)-[:OBSERVED_BY]->(cal)
