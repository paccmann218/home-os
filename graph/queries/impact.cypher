// [home graph] BFS from a device: everything a change would reach (vars, body signals),
// and everyone present who would feel it. Used before acting and in explanations.
MATCH (d:Device {id: $device})
CALL algo.bfs(d, 2, 'CONTROLS') YIELD nodes
WITH d, nodes
OPTIONAL MATCH (d)-[:CONTROLS]->(v:Var)-[:AFFECTS]->(s:Signal)
OPTIONAL MATCH (d)<-[:HAS]-(r:Room)<-[:IN]-(p:Person)
RETURN d.id AS device, [n IN nodes | coalesce(n.name, n.id)] AS reach,
       collect(DISTINCT s.name) AS signals, collect(DISTINCT p.alias) AS people
