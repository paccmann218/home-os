// Seed: household role. The home graph never receives this label.
MATCH (p:Person {alias: $alias})
MERGE (r:Role {id: 'resident'})
SET r.name = 'Resident'
MERGE (p)-[:HAS_ROLE]->(r)
