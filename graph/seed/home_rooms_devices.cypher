// Seed: the home, its rooms and devices (shared graph home:<id>).
MERGE (h:Home {id: $home})
WITH h
UNWIND $rooms AS r
MERGE (room:Room {id: r.id}) SET room.name = r.name
MERGE (h)-[:HAS]->(room)
WITH h
UNWIND $devices AS d
MATCH (room:Room {id: d.room})
MERGE (dev:Device {id: d.id})
ON CREATE SET dev += d.state
SET dev.kind = d.kind, dev.driver = d.driver, dev.name = d.name, dev.min = d.min, dev.max = d.max
MERGE (room)-[:HAS]->(dev)
