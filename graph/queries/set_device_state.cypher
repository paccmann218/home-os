MATCH (d:Device {id: $device}) SET d += $state, d.updated = $now RETURN properties(d) AS state
