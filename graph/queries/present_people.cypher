// [home graph] Who is in the room right now.
MATCH (r:Room {id: $room})<-[i:IN]-(p:Person)
RETURN p.alias AS alias, i.since AS since
