MATCH (d:Decision)-[:FOR]->(ep:Episode)
OPTIONAL MATCH (d)-[:CHOSE]->(i:Intervention)
RETURN d.id AS id, d.at AS at, ep.kind AS kind, ep.summary AS summary, collect(i.label) AS chose, d.rationale AS rationale,
       d.arbitration AS arbitration
ORDER BY d.at DESC
LIMIT $limit
