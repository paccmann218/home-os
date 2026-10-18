// Seed: habit levers. Episodes connect to these through bands; the nodes exist so algorithms have endpoints.
MATCH (p:Person {alias: $alias})
MERGE (bed:Habit:Lever {id: 'bedtime'})
SET bed.name = 'Bedtime'
MERGE (caff:Habit:Lever {id: 'late_caffeine'})
SET caff.name = 'Late caffeine'
MERGE (train:Habit:Lever {id: 'evening_training'})
SET train.name = 'Evening training'
MERGE (p)-[:TRACKS]->(bed)
MERGE (p)-[:TRACKS]->(caff)
MERGE (p)-[:TRACKS]->(train)
