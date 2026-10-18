// Seed: the bedroom clock is the device a morning decision acts on. Alarms themselves are written later.
MATCH (room:Room {id: 'bedroom'})
MERGE (clock:Device {id: 'clock'})
SET clock.kind = 'alarm', clock.driver = 'virtual', clock.name = 'Google Clock'
MERGE (room)-[:HAS]->(clock)
WITH clock
MERGE (v:Var {name: 'wake_time'})
MERGE (clock)-[k:CONTROLS]->(v) SET k.strength = 1.0
WITH clock
MATCH (morning:Routine {id: 'morning'})
MERGE (morning)-[:TRIGGERS]->(clock)
