// [home graph] Zones and the rooms inside them.
MATCH (z:Zone)-[:CONTAINS]->(room:Room)
RETURN z.id AS id, z.name AS name, collect(room.id) AS rooms
ORDER BY z.id
