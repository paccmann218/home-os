// [body graph] Playbook versions, oldest to newest along SUPERSEDES.
MATCH (p:Playbook)
OPTIONAL MATCH (p)-[:SUPERSEDES]->(older:Playbook)
RETURN p.name AS name, p.v AS version, older.v AS superseded
ORDER BY p.name, p.v
