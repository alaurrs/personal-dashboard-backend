-- Ajout du champ image_url à la table artists
ALTER TABLE artists ADD COLUMN image_url VARCHAR(2048);

-- Index pour optimiser les requêtes avec image_url
CREATE INDEX IF NOT EXISTS idx_artists_image_url ON artists (image_url) WHERE image_url IS NOT NULL;
