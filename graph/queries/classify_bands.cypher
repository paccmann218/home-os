// [body graph] Mark context bands that deviate from the person's usual as :Notable
// ($defaults = usual levels per variable). Graph algorithms run on notable bands only.
MATCH (b:Band) WHERE b.notable IS NULL
SET b.notable = b.var <> 'kind' AND NOT b.level IN coalesce($defaults[b.var], [])
WITH b WHERE b.notable
SET b:Notable
RETURN count(b) AS notable
