// Seed: house policies. Priority is a number the arbiter can read; the reason stays out of this graph.
MERGE (quiet:Policy {id: 'quiet_hours'})
SET quiet.name = 'Quiet hours', quiet.from_hour = 22, quiet.to_hour = 7, quiet.note = 'Prefer lower airflow and light'
MERGE (sick:Policy {id: 'warm_floor'})
SET sick.name = 'Warm floor', sick.min_temp = 26, sick.note = 'Anonymous floor when a high-priority constraint is present'
WITH quiet, sick
MATCH (room:Room {id: 'bedroom'})
MERGE (quiet)-[:GOVERNS]->(room)
MERGE (sick)-[:GOVERNS]->(room)
