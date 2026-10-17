// [body graph] An executed Action (device command or watch pacer) belonging to a Decision.
MATCH (d:Decision {id: $decision}), (i:Intervention {name: $intervention})
CREATE (a:Action {id: $id, at: $now, device: $device, applied_json: $applied, evaluated: false, eval_due: $eval_due})
CREATE (a)-[:PART_OF]->(d)
CREATE (a)-[:INSTANCE_OF]->(i)
RETURN a.id AS id
