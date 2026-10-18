// [home graph] Policies governing a room.
MATCH (pol:Policy)-[:GOVERNS]->(room:Room {id: $room})
RETURN pol.id AS id, pol.name AS name, pol.from_hour AS from_hour, pol.to_hour AS to_hour, pol.min_temp AS min_temp, pol.note AS note
ORDER BY pol.id
