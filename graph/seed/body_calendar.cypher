// Seed: each person has a calendar memory node. Events attach later through HAS_EVENT.
MATCH (p:Person {alias: $alias})
MERGE (c:Calendar {id: 'google'})
SET c.source = 'google_calendar', c.name = 'Google Calendar'
MERGE (p)-[:HAS_MEMORY]->(c)
