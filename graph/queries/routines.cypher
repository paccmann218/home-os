// [home graph] Routines staged on a room.
MATCH (rt:Routine)-[:APPLIES_TO]->(room:Room {id: $room})
RETURN rt.id AS id, rt.name AS name, rt.tod AS tod, rt.note AS note
ORDER BY rt.id
