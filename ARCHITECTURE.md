# Architecture du projet

Ce document décrit l'architecture du projet de tableau de bord personnel.

## Vue d'ensemble

Le projet est une application backend basée sur [Spring Boot](https://spring.io/projects/spring-boot) qui expose une API REST pour alimenter un tableau de bord personnel. Il gère l'authentification des utilisateurs, la collecte de données provenant de diverses sources (par exemple, Spotify) et fournit des points de terminaison pour que le frontend puisse afficher ces données.

L'application utilise une base de données PostgreSQL pour la persistance des données et [Flyway](https://flywaydb.org/) pour les migrations de schémas de base de données. L'authentification est gérée à l'aide de [JSON Web Tokens (JWT)](https://jwt.io/).

## Classe principale

### `BackendApplication.java`
**Rôle** : Point d'entrée de l'application Spring Boot
**Annotations** :
- `@SpringBootApplication` : Active la configuration automatique Spring Boot
- `@EnableConfigurationProperties` : Active les propriétés de configuration pour JWT et Spotify
- `@EnableCaching` : Active le système de cache Spring
- `@EnableScheduling` : Active les tâches planifiées

**Méthodes** :
- `main(String[] args)` : Point d'entrée principal qui démarre l'application Spring Boot

## Structure des modules

### `src/main/java/com/dashboard/backend/User`

Ce module gère tout ce qui concerne les utilisateurs, y compris l'authentification, les données utilisateur et les interactions avec Spotify.

#### Classes de requête/réponse

**`AuthResponse.java`**
- **Type** : Record Java
- **Rôle** : Encapsule la réponse d'authentification
- **Champs** :
  - `String token` : Le token JWT généré après authentification réussie

**`LoginRequest.java`**
- **Type** : Record Java
- **Rôle** : Modélise le corps de la requête de connexion
- **Champs** :
  - `String email` : Email de l'utilisateur
  - `String password` : Mot de passe de l'utilisateur

**`RegisterRequest.java`**
- **Type** : Record Java
- **Rôle** : Modélise le corps de la requête d'inscription
- **Champs** :
  - `String email` : Email souhaité
  - `String password` : Mot de passe souhaité
  - `String fullName` : Nom complet de l'utilisateur

#### `controller/`

**`UserController.java`**
- **Rôle** : Contrôleur REST pour les opérations utilisateur
- **Annotations** : `@RestController`, `@RequestMapping("/api/user")`, `@CrossOrigin`
- **Dépendances** : `UserRepository`, `JwtService`, `SpotifyAccountService`

**Méthodes** :
- `getCurrentUser(HttpServletRequest request)` : 
  - **Endpoint** : `GET /api/user/me`
  - **Rôle** : Récupère les informations de l'utilisateur authentifié
  - **Processus** : Extrait le token JWT, valide le token, récupère l'utilisateur par email, convertit en DTO
  - **Retour** : `ResponseEntity<UserDto>` ou erreur

- `getSpotifyStatus(HttpServletRequest request)` :
  - **Endpoint** : `GET /api/user/me/spotify-status`
  - **Rôle** : Vérifie le statut de liaison Spotify de l'utilisateur
  - **Processus** : Valide le token, récupère l'utilisateur, vérifie la liaison Spotify
  - **Retour** : `ResponseEntity<Map<String, Object>>` avec `hasSpotifyLinked`, `spotifyEmail`, `displayName`

#### `dto/`

**`UserDto.java`**
- **Type** : Record Java
- **Rôle** : Objet de transfert de données pour les utilisateurs
- **Champs** :
  - `String id` : ID de l'utilisateur (UUID converti en String)
  - `String email` : Email de l'utilisateur
  - `String fullName` : Nom complet

**Méthodes** :
- `from(User user)` : Méthode statique de conversion depuis une entité User vers UserDto

#### `model/`

**`User.java`**
- **Type** : Entité JPA
- **Table** : `users`
- **Annotations** : `@Entity`, `@Table`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`

**Champs** :
- `UUID id` : Identifiant unique (généré automatiquement)
- `String email` : Email unique et obligatoire
- `String passwordHash` : Hash du mot de passe (colonne `password_hash`)
- `String fullName` : Nom complet de l'utilisateur
- `LocalDateTime createdAt` : Date de création
- `LocalDateTime updatedAt` : Date de dernière mise à jour

**Constructeurs** :
- `User()` : Constructeur par défaut
- `User(String email, String passwordHash, String fullName)` : Constructeur personnalisé

**Méthodes de cycle de vie JPA** :
- `@PrePersist prePersist()` : Définit `createdAt` et `updatedAt` avant insertion
- `@PreUpdate preUpdate()` : Met à jour `updatedAt` avant modification

**`SpotifyAccount.java`**
- **Type** : Entité JPA
- **Rôle** : Représente un compte Spotify lié à un utilisateur
- **Champs principaux** :
  - ID utilisateur Spotify
  - Tokens d'accès et de rafraîchissement
  - Email Spotify
  - Nom d'affichage

**`ListeningHistory.java`**
- **Type** : Entité JPA
- **Rôle** : Stocke l'historique d'écoute Spotify
- **Relations** : Liée aux entités `User` et `Track`

**`Track.java`**
- **Type** : Entité JPA
- **Rôle** : Représente un morceau de musique
- **Champs** : Nom, durée, popularité
- **Relations** : Liée à `Album` et `Artist`

**`Artist.java`**
- **Type** : Entité JPA
- **Rôle** : Représente un artiste musical
- **Champs** : Nom, ID Spotify, genres, popularité

**`Album.java`**
- **Type** : Entité JPA
- **Rôle** : Représente un album musical
- **Champs** : Nom, date de sortie, type d'album

#### `repository/`

**`UserRepository.java`**
- **Type** : Interface Spring Data JPA
- **Étend** : `JpaRepository<User, UUID>`
- **Méthodes personnalisées** :
  - `Optional<User> findByEmail(String email)` : Recherche par email

**`SpotifyAccountRepository.java`**
- **Type** : Interface Spring Data JPA
- **Rôle** : Opérations CRUD pour SpotifyAccount

**`ListeningHistoryRepository.java`**
- **Type** : Interface Spring Data JPA
- **Rôle** : Opérations CRUD pour ListeningHistory

**`TrackRepository.java`**
- **Type** : Interface Spring Data JPA
- **Rôle** : Opérations CRUD pour Track

**`ArtistRepository.java`**
- **Type** : Interface Spring Data JPA
- **Rôle** : Opérations CRUD pour Artist

**`AlbumRepository.java`**
- **Type** : Interface Spring Data JPA
- **Rôle** : Opérations CRUD pour Album

### `src/main/java/com/dashboard/backend/analytics`

Ce module est conçu pour l'analyse des données futures.

#### Structure préparée
- `config/` : Configuration spécifique à l'analyse
- `controller/` : Endpoints d'API pour l'analyse
- `dto/` : Objets de transfert pour l'analyse
- `model/` : Entités de données d'analyse
- `repository/` : Accès aux données d'analyse
- `service/` : Logique métier d'analyse

### `src/main/java/com/dashboard/backend/config`

**`SecurityConfig.java`**
- **Rôle** : Configuration de la sécurité Spring Security
- **Méthodes** :
  - Configuration de la chaîne de filtres
  - Définition des endpoints publics/protégés
  - Configuration du JwtAuthenticationFilter
  - Définition de l'AuthenticationProvider

**`WebConfig.java`**
- **Rôle** : Configuration CORS et mappings web
- **Méthodes** :
  - Configuration CORS pour le frontend
  - Mappages de contrôleurs globaux

### `src/main/java/com/dashboard/backend/controller`

**`ActuatorController.java`**
- **Rôle** : Endpoints de santé de l'application
- **Endpoints** :
  - `/actuator/health` : Vérification de l'état de santé

**`SpotifyController.java`**
- **Rôle** : Gestion OAuth2 avec Spotify
- **Endpoints** :
  - `/spotify/login` : Initie la connexion Spotify
  - `/spotify/callback` : Gère le callback OAuth2
  - `/spotify/top-artists` : Récupère les artistes favoris

### `src/main/java/com/dashboard/backend/exception`

**`GlobalExceptionHandler.java`**
- **Rôle** : Gestionnaire global d'exceptions
- **Annotations** : `@ControllerAdvice`
- **Méthodes** : Gestion centralisée des erreurs avec réponses JSON cohérentes

**`ResourceNotFoundException.java`**
- **Type** : Exception personnalisée
- **Usage** : Ressource non trouvée en base de données

**`UnauthorizedOperationException.java`**
- **Type** : Exception personnalisée
- **Usage** : Opération non autorisée

### `src/main/java/com/dashboard/backend/rag`

**`controller/RagController.java`**
- **Rôle** : Contrôleur pour les questions RAG
- **Endpoints** :
  - `POST /rag/ask` : Traite les questions utilisateur

**`dto/AskRequest.java`**
- **Type** : Record de requête
- **Champs** : Question de l'utilisateur

**`dto/RagResponse.java`**
- **Type** : Record de réponse
- **Champs** : Réponse générée

**`service/RagService.java`**
- **Rôle** : Logique métier RAG
- **Méthodes** : Interaction avec LLM et base vectorielle

### `src/main/java/com/dashboard/backend/security`

**`CustomUserDetailsService.java`**
- **Rôle** : Service de chargement des détails utilisateur
- **Implémente** : `UserDetailsService`
- **Méthodes** :
  - `loadUserByUsername(String email)` : Charge un utilisateur par email

**`JwtAuthenticationFilter.java`**
- **Rôle** : Filtre d'authentification JWT
- **Étend** : `OncePerRequestFilter`
- **Méthodes** :
  - `doFilterInternal()` : Traite chaque requête pour validation JWT

**`JwtTokenProvider.java`**
- **Rôle** : Utilitaire pour les tokens JWT
- **Méthodes** :
  - `generateToken()` : Génère un token JWT
  - `validateToken()` : Valide un token JWT
  - `extractClaims()` : Extrait les claims du token

**`UserPrincipal.java`**
- **Rôle** : Représentation de l'utilisateur authentifié
- **Implémente** : `UserDetails`
- **Méthodes** : Implémentation des méthodes UserDetails

### `src/main/java/com/dashboard/backend/service`

**`SpotifyService.java`**
- **Rôle** : Service d'interaction avec l'API Spotify
- **Méthodes** :
  - Récupération des données utilisateur
  - Gestion des tokens d'accès
  - Traitement de l'historique d'écoute

### `src/main/java/com/dashboard/backend/thirdparty`

**`spotify/SpotifyApi.java`**
- **Rôle** : Client HTTP pour l'API Spotify
- **Méthodes** :
  - Appels HTTP vers Spotify
  - Gestion de l'authentification OAuth2
  - Désérialisation des réponses JSON

**`spotify/dto/`**
- **Rôle** : DTOs pour les réponses Spotify
- **Classes** : `SpotifyUser`, `TopArtistsResponse`, etc.

## Base de données

### Migrations Flyway
Scripts versionnés dans `src/main/resources/db/migration/` :
- `V1__create_user_table.sql` : Création table users
- `V2__add_spotify_columns_user_table.sql` : Ajout colonnes Spotify
- `V3__separate_spotify_accounts.sql` : Séparation comptes Spotify
- `V4__create_spotify_history_tables.sql` : Tables historique
- `V5__set_user_dates.sql` : Ajout dates utilisateur

### Entités JPA
Gestion automatique via Spring Data JPA avec annotations :
- `@Entity` : Marque les classes comme entités
- `@Table` : Spécifie le nom de table
- `@Id` : Définit la clé primaire
- `@GeneratedValue` : Génération automatique d'ID
- `@Column` : Configuration des colonnes

## Sécurité

### Flux d'authentification JWT
1. **Inscription/Connexion** : Validation des credentials
2. **Génération JWT** : Création du token avec claims utilisateur
3. **Stockage côté client** : Token dans localStorage/sessionStorage
4. **Validation requêtes** : `JwtAuthenticationFilter` vérifie chaque requête
5. **Contexte sécurité** : Définition de l'utilisateur authentifié

### Configuration Spring Security
- **Endpoints publics** : `/auth/login`, `/auth/register`
- **Endpoints protégés** : Tous les autres nécessitent JWT valide
- **CORS** : Configuration pour frontend sur port 4200

## Déploiement

### Conteneurisation Docker
- **Dockerfile** : Image Spring Boot avec OpenJDK
- **docker-compose.yml** : Orchestration app + PostgreSQL
- **Variables d'environnement** : Configuration flexible

### Configuration par environnement
- **application.properties** : Configuration par défaut
- **application-prod.properties** : Configuration production
- **Fichier .env** : Variables sensibles (tokens, mots de passe)

## Gestion des erreurs

### Exception Handler Global
- **Centralisation** : Toutes les erreurs passent par `GlobalExceptionHandler`
- **Réponses cohérentes** : Format JSON standardisé
- **Codes HTTP** : Mapping approprié des exceptions vers codes de statut
- **Logging** : Enregistrement des erreurs pour debugging
