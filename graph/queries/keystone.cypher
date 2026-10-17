// [body graph] Track 1 algorithm tool: keystone context = betweenness centrality on the
// episode -> deviation -> lever graph. High score = the deviation most cause chains run through.
// $labels / $rels are the subset of [Episode, Notable, Var, DeviceRef, Habit] / [IN_STATE, OF_VAR,
// OF_HABIT, CONTROLLED_BY] that exist in this person's graph (FalkorDB rejects unknown ones).
CALL algo.betweenness({nodeLabels: $labels, relationshipTypes: $rels, samplingSize: 256, samplingSeed: 7})
YIELD node, score
WHERE node:Notable AND score > 0
RETURN node.key AS band, score, node.p_bad AS p_bad, node.lift AS lift
ORDER BY score DESC
LIMIT $limit
