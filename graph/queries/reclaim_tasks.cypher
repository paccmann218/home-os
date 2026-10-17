// [home graph] A crashed agent's claimed task goes back on the board when its lease expires.
MATCH (t:Task {status: 'claimed'}) WHERE t.lease_until < $now
SET t.status = 'open', t.reclaimed = coalesce(t.reclaimed, 0) + 1
RETURN count(t) AS reclaimed
