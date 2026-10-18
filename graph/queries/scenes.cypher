// [home graph] Scenes in a room and the devices they use.
MATCH (s:Scene)-[:STAGED_IN]->(room:Room {id: $room})
OPTIONAL MATCH (s)-[:USES]->(d:Device)
RETURN s.id AS id, s.name AS name, s.tod AS tod, collect(DISTINCT d.id) AS devices
ORDER BY s.id
