MATCH (r:Room)-[:HAS]->(d:Device)
OPTIONAL MATCH (d)-[l:LEASED_BY]->(a:Agent) WHERE l.until > $now
RETURN r.id AS room, d.id AS id, properties(d) AS state, a.id AS leased_by
ORDER BY d.id
