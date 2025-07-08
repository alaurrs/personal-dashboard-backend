-- Met à jour les dates created_at et updated_at pour tous les users existants
UPDATE users
SET created_at = NOW(),
    updated_at = NOW()
WHERE created_at IS NULL OR updated_at IS NULL;

