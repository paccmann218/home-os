MATCH (ep:Episode {id: $id}) SET ep.embedding = vecf32($vec) RETURN ep.id AS id
