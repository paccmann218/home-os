// [home graph, write API] Who is in which room (from watch on-body / phone / mmWave).
MATCH (p:Person {alias: $alias})
OPTIONAL MATCH (p)-[old:IN]->(:Room)
DELETE old
WITH p
MATCH (r:Room {id: $room}) WHERE $present
CREATE (p)-[:IN {since: $now}]->(r)
RETURN r.id AS room
