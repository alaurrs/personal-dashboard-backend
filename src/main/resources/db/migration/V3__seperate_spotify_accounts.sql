-- Créer la table spotify_accounts
CREATE TABLE spotify_accounts (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                  spotify_user_id VARCHAR(255),
                                  spotify_email VARCHAR(255),
                                  display_name VARCHAR(255),
                                  access_token TEXT,
                                  refresh_token TEXT,
                                  token_expiry TIMESTAMP,
                                  linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  last_sync TIMESTAMP,
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  UNIQUE(user_id)
);

-- Migrer les données existantes
INSERT INTO spotify_accounts (user_id, spotify_email, access_token, refresh_token, token_expiry, linked_at)
SELECT id, 'unknown@spotify.com', spotify_access_token, spotify_refresh_token, spotify_token_expiry, created_at
FROM users
WHERE spotify_access_token IS NOT NULL;

-- Supprimer les colonnes Spotify de la table users
ALTER TABLE users
    DROP COLUMN IF EXISTS spotify_access_token,
    DROP COLUMN IF EXISTS spotify_refresh_token,
    DROP COLUMN IF EXISTS spotify_token_expiry;
