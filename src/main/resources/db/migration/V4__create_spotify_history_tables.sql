-- =================================================================
-- V2: Création des tables pour l'historique et l'analyse Spotify
-- =================================================================

-- Table pour stocker les informations sur les artistes
CREATE TABLE IF NOT EXISTS artists (
                                       id VARCHAR(255) PRIMARY KEY, -- ID Spotify de l'artiste
                                       name TEXT NOT NULL
);

-- Table pour stocker les informations sur les albums
CREATE TABLE IF NOT EXISTS albums (
                                      id VARCHAR(255) PRIMARY KEY, -- ID Spotify de l'album
                                      name TEXT NOT NULL
    -- Tu pourras ajouter d'autres colonnes comme release_date, cover_image_url plus tard
);

-- Table de jointure pour la relation Many-to-Many entre Albums et Artistes
CREATE TABLE IF NOT EXISTS album_artists (
                                             album_id VARCHAR(255) NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
                                             artist_id VARCHAR(255) NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
                                             PRIMARY KEY (album_id, artist_id)
);

-- Table pour stocker les informations sur les morceaux (tracks)
CREATE TABLE IF NOT EXISTS tracks (
                                      id VARCHAR(255) PRIMARY KEY, -- ID Spotify du morceau
                                      name TEXT NOT NULL,
                                      album_id VARCHAR(255) REFERENCES albums(id) ON DELETE SET NULL, -- Si un album est supprimé, on ne supprime pas le morceau
                                      duration_ms INT NOT NULL
);

-- Table de jointure pour la relation Many-to-Many entre Tracks et Artistes
CREATE TABLE IF NOT EXISTS track_artists (
                                             track_id VARCHAR(255) NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                                             artist_id VARCHAR(255) NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
                                             PRIMARY KEY (track_id, artist_id)
);

-- Table de faits : l'historique d'écoute
-- C'est la table la plus importante pour tes analyses.
CREATE TABLE IF NOT EXISTS listening_history (
                                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- Clé étrangère vers ta table users existante
                                                 track_id VARCHAR(255) NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                                                 played_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- --- Optimisations : Ajout d'index ---
-- Ces index sont CRUCIAUX pour la performance des futures requêtes d'analyse.
CREATE INDEX IF NOT EXISTS idx_listening_history_user_id ON listening_history (user_id);
CREATE INDEX IF NOT EXISTS idx_listening_history_played_at ON listening_history (played_at);
CREATE INDEX IF NOT EXISTS idx_album_artists_artist_id ON album_artists (artist_id);
CREATE INDEX IF NOT EXISTS idx_track_artists_artist_id ON track_artists (artist_id);

-- Ajout d'un index unique pour éviter les doublons d'écoutes
-- Un utilisateur ne peut pas avoir écouté un morceau au même instant exact (à la milliseconde près)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_listening_event ON listening_history (user_id, played_at);

