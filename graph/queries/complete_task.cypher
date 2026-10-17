MATCH (t:Task {id: $id}) WHERE t.status = 'claimed' AND t.worker = $worker
SET t.status = $status, t.done_at = $now, t.result_json = $result
RETURN t.id AS id
