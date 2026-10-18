// Seed: a zone groups rooms so later queries can ask "what is in the sleeping zone?"
MERGE (z:Zone {id: 'sleeping'})
SET z.name = 'Sleeping zone'
WITH z
MATCH (room:Room {id: 'bedroom'})
MERGE (z)-[:CONTAINS]->(room)
WITH z
MATCH (h:Home)
MERGE (h)-[:HAS_ZONE]->(z)
