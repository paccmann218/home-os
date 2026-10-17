// [body graph] Track 1 algorithm tool: top drivers of bad episodes = PageRank over CO_OCCURS.
CALL algo.pageRank('Band', 'CO_OCCURS') YIELD node, score
WHERE node.p_bad IS NOT NULL
RETURN node.key AS band, score, node.p_bad AS p_bad, node.n AS nights
ORDER BY score DESC
LIMIT $limit
