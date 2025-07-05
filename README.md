# 🎵 Mon Application Spring Boot + Spotify

Ce projet est une application Spring Boot connectée à une base de données PostgreSQL (via Supabase) et intégrée à l'API Spotify.

---

## ⚙️ Prérequis

- Java 21
- Maven 3.9+
- Docker (optionnel, pour exécution en container)
- Compte Supabase (base PostgreSQL)
- Application Spotify (avec client ID / secret)
- Frontend Angular (facultatif pour démarrer le backend)

---

## 📁 Configuration de l’environnement

Avant de lancer l’application, crée un fichier nommé `.env.local` à la racine du projet :

```env
# .env.local
DB_HOST=aws-0-eu-west-3.pooler.supabase.com
DB_PORT=5432
DB_NAME=postgres
DB_USER=postgres.xxxxxxxxx
DB_PASSWORD=xxxxxxxxxxxx

JWT_SECRET=xxxxxxxxxxxxx

SPOTIFY_CLIENT_ID=xxxxxxxxxxxxx
SPOTIFY_CLIENT_SECRET=xxxxxxxxxxxx
SPOTIFY_REDIRECT_URI=http://127.0.0.1:8080/api/spotify/auth/callback

FRONTEND_URL=http://localhost:4200
