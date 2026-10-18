// [home graph] Size of the shared home memory.
MATCH (n)
WITH count(n) AS nodes
MATCH ()-[r]->()
RETURN nodes, count(r) AS edges
