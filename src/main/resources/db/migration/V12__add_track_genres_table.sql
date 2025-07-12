-- Migration pour ajouter le support des genres musicaux aux tracks
-- Les genres sont récupérés depuis l'API Spotify via les artistes associés aux tracks

-- Table pour stocker les genres associés aux tracks
CREATE TABLE track_genres (
    track_id VARCHAR(255) NOT NULL,
    genre VARCHAR(100) NOT NULL,
    CONSTRAINT fk_track_genres_track_id FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    CONSTRAINT pk_track_genres PRIMARY KEY (track_id, genre)
);

-- Index pour améliorer les performances des requêtes sur les genres
CREATE INDEX idx_track_genres_genre ON track_genres(genre);
CREATE INDEX idx_track_genres_track_id ON track_genres(track_id);

-- Ajouter des genres aux artistes pour optimiser les futures requêtes
-- (les genres sont normalement récupérés depuis l'API Spotify des artistes)
ALTER TABLE artists ADD COLUMN IF NOT EXISTS genres TEXT[];

-- Index GIN pour les recherches dans les tableaux de genres des artistes
CREATE INDEX IF NOT EXISTS idx_artists_genres ON artists USING GIN(genres);
