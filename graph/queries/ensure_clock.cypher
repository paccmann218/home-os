// [home graph] Bedroom Google Clock. State (the two alarms) is written only after the person confirms.
MATCH (room:Room {id: 'bedroom'})
MERGE (d:Device {id: 'clock'})
SET d.kind = 'alarm', d.driver = 'virtual', d.name = 'Google Clock', d.min = 0, d.max = 1
MERGE (room)-[:HAS]->(d)
RETURN d.id AS id
