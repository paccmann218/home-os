// [body graph] Track 1 algorithm tool: drivers of bad nights = PageRank over CO_OCCURS between
// notable bands (deviations seen together on bad nights), reported with lift over the base rate.
CALL algo.pageRank('Notable', 'CO_OCCURS') YIELD node, score
WHERE node.p_bad IS NOT NULL AND node.lift > 1.0
RETURN node.key AS band, score, node.p_bad AS p_bad, node.lift AS lift, node.n AS nights, node.n_bad AS bad_nights
ORDER BY score DESC, lift DESC
LIMIT $limit
