// [body graph] Track 1 algorithm tool: the KINDS of bad episodes this person has = community
// detection (label propagation) over bad episodes and the deviations (notable bands) they share.
// Each kind is described by the deviations most over-represented in it versus all bad episodes.
CALL algo.labelPropagation({nodeLabels: ['BadEpisode', 'Notable'], relationshipTypes: ['IN_STATE'], maxIterations: 10})
YIELD node, communityId
WITH node, communityId WHERE node:BadEpisode
SET node.community = communityId
WITH communityId, collect(node) AS eps
WHERE size(eps) >= 2
MATCH (x:BadEpisode)
WITH communityId, eps, count(x) AS allBad
UNWIND eps AS ep
MATCH (ep)-[:IN_STATE]->(b:Notable)
WITH communityId, size(eps) AS size, allBad, b, count(*) AS inKind
MATCH (other:BadEpisode)-[:IN_STATE]->(b)
WITH communityId, size, allBad, b, inKind, count(other) AS inAll
WITH communityId, size, b.key AS band, toFloat(inKind) / size AS share,
     toFloat(inKind) / size - toFloat(inAll) / allBad AS distinct
ORDER BY communityId, distinct DESC, share DESC
WITH communityId, size, collect(band)[0..2] AS signature, collect(share)[0..2] AS shares
RETURN communityId, size, signature, shares
ORDER BY size DESC
LIMIT 8
