-- =================================================================
-- V2: Création de la table pour le cache des "Top Artists"
--
-- Cette table stocke les résultats des appels à l'API de Spotify
-- pour les artistes préférés d'un utilisateur, afin d'éviter
-- des appels répétés et d'améliorer les performances.
-- =================================================================

-- 1. Création de la table principale
CREATE TABLE cached_top_artists (
    -- Clé primaire unique pour chaque entrée de cache
                                    id UUID PRIMARY KEY,

    -- Clé étrangère vers l'utilisateur à qui appartiennent ces données
    -- ON DELETE CASCADE supprime automatiquement le cache si l'utilisateur est supprimé.
                                    user_id UUID NOT NULL,

    -- La période de temps concernée (ex: 'last_month', 'last_6_months')
                                    time_range VARCHAR(50) NOT NULL,

    -- L'identifiant de l'artiste sur Spotify
                                    artist_id_spotify VARCHAR(255) NOT NULL,

    -- Le nom de l'artiste
                                    artist_name VARCHAR(255) NOT NULL,

    -- L'URL de l'image de l'artiste (peut être longue)
                                    artist_image_url VARCHAR(2048),

    -- Le classement de l'artiste dans la liste pour cette période
                                    artist_rank INT NOT NULL,

    -- L'horodatage de la dernière mise à jour, essentiel pour la logique de cache
                                    last_updated_at TIMESTAMPTZ NOT NULL,

    -- Contrainte de clé étrangère pour lier à la table des utilisateurs
                                    CONSTRAINT fk_cached_top_artists_user
                                        FOREIGN KEY (user_id)
                                            REFERENCES users(id)
                                            ON DELETE CASCADE
);

-- 2. Création d'un index pour optimiser les requêtes
-- Cet index accélérera considérablement les recherches les plus courantes,
-- qui consistent à trouver le cache pour un utilisateur et une période donnés.
CREATE INDEX idx_cached_top_artists_user_timerange
    ON cached_top_artists (user_id, time_range);

-- Commentaire pour la base de données (optionnel mais recommandé)
COMMENT ON TABLE cached_top_artists IS 'Table de cache pour les artistes préférés des utilisateurs, récupérés depuis l''API Spotify.';
