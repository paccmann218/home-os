// Seed: named scenes. A scene is a bundle of device intents, not a setpoint by itself.
MERGE (wake:Scene {id: 'wake'})
SET wake.name = 'Wake', wake.tod = 'morning'
MERGE (sleep:Scene {id: 'sleep'})
SET sleep.name = 'Sleep', sleep.tod = 'night'
MERGE (recover:Scene {id: 'recover'})
SET recover.name = 'Recover', recover.tod = 'any'
WITH wake, sleep, recover
MATCH (room:Room {id: 'bedroom'})
MERGE (wake)-[:STAGED_IN]->(room)
MERGE (sleep)-[:STAGED_IN]->(room)
MERGE (recover)-[:STAGED_IN]->(room)
WITH wake, sleep
MATCH (clock:Device {id: 'clock'})
MERGE (wake)-[:USES]->(clock)
MATCH (light:Device {id: 'light'})
MERGE (sleep)-[:USES]->(light)
