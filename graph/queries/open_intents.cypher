MATCH (a:Agent)-[:POSTED]->(i:Intent {status: 'open'})-[:ON]->(r:Room {id: $room})
WHERE i.valid_to > $now
RETURN i.id AS id, i.var AS var, i.target AS target, i.device AS device, i.intervention AS intervention,
       i.priority AS priority, a.owner AS owner, i.episode AS episode
ORDER BY i.priority DESC, i.created ASC
