// [body graph] Close an episode with its measured effect and quality. Bad episodes also get the
// :BadEpisode label so graph algorithms can run on "the bad ones" (labels are algorithm filters).
MATCH (ep:Episode {id: $id})
SET ep.end = $now, ep.status = 'closed', ep.effect = $effect, ep.quality = $quality
WITH ep WHERE $quality = 'bad'
SET ep:BadEpisode
RETURN ep.id AS id
