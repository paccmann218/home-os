// [body graph] Track 1 algorithm tool: keystone context = betweenness centrality on the
// episode -> band -> lever graph. High score = the band most cause chains run through.
CALL algo.betweenness({nodeLabels: ['Episode', 'Band', 'Var', 'DeviceRef', 'Habit'],
                       relationshipTypes: ['IN_STATE', 'OF_VAR', 'OF_HABIT', 'CONTROLLED_BY'],
                       samplingSize: 256, samplingSeed: 7})
YIELD node, score
WHERE node:Band AND score > 0
RETURN node.key AS band, score, node.p_bad AS p_bad
ORDER BY score DESC
LIMIT $limit
