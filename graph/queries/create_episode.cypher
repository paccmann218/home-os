// [body graph] Episodic memory: open an Episode and link it to shared context Band nodes.
// Episodes that share bands are "similar" through the graph, a few hops apart.
MATCH (p:Person {alias: $alias})
CREATE (ep:Episode {id: $id, kind: $kind, start: $start, summary: $summary, status: 'open', trigger: $trigger, room: $room})
CREATE (p)-[:HAD]->(ep)
WITH ep
UNWIND $bands AS bk
MERGE (b:Band {key: bk.key}) ON CREATE SET b.var = bk.var, b.level = bk.level
CREATE (ep)-[:IN_STATE {neglogp: coalesce(b.neglogp, 1.0)}]->(b)
RETURN ep.id AS id, count(b) AS bands
