// [body graph] Track 1 algorithm tool: episode types = community detection (label propagation)
// over episodes and the bands they share. Writes the community onto each episode.
CALL algo.labelPropagation({nodeLabels: ['Episode', 'Band'], relationshipTypes: ['IN_STATE'], maxIterations: 20})
YIELD node, communityId
WITH node, communityId WHERE node:Episode
SET node.community = communityId
WITH communityId, collect(node) AS eps
UNWIND eps AS ep
MATCH (ep)-[:IN_STATE]->(b:Band) WHERE b.var <> 'kind'
WITH communityId, size(eps) AS size, b.key AS band, count(*) AS c,
     sum(CASE WHEN ep.quality = 'bad' THEN 1 ELSE 0 END) AS bad
ORDER BY communityId, c DESC
WITH communityId, size, collect(band)[0..4] AS signature, sum(bad) AS bad_hits
RETURN communityId, size, signature
ORDER BY size DESC
LIMIT 8
