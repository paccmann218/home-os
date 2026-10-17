MATCH (ep:Episode {id: $id}) SET ep.end = $now, ep.status = 'closed', ep.effect = $effect, ep.quality = $quality
RETURN ep.id AS id
