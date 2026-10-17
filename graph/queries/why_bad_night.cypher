// [body graph] Track 1 graph algorithm as an agent tool: the most likely cause chains for an
// episode. IN_STATE edges carry neglogp = -ln P(bad | band) (learned nightly by the Scientist),
// structural edges cost 0, so the lightest path is the most probable actionable cause:
// bad night -> band (e.g. room_temp:warm) -> var -> device/habit lever.
MATCH (ep:Episode {id: $episode}), (l:Lever)
CALL algo.SPpaths({sourceNode: ep, targetNode: l,
                   relTypes: ['IN_STATE', 'OF_VAR', 'CONTROLLED_BY', 'OF_HABIT'],
                   weightProp: 'neglogp', relDirection: 'outgoing', pathCount: 1, maxLen: 4})
YIELD path, pathWeight
RETURN [n IN nodes(path) | coalesce(n.key, n.id, n.name)] AS chain, pathWeight,
       HomeOS.likelihood(pathWeight) AS likelihood
ORDER BY pathWeight ASC
LIMIT 3
