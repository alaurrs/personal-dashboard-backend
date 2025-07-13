package com.dashboard.backend.rag.service;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.rag.dto.InsightResponse;
import com.dashboard.backend.rag.dto.TrackInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagService {

    private final ChatClient chatClient;
    private final String dbSchema;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public RagService(ChatClient.Builder chatClientBuilder,
                      JdbcTemplate jdbcTemplate,
                      ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dbSchema = """
                CREATE TABLE public.listening_history (
                    user_id uuid NOT NULL,
                    track_id character varying NOT NULL,
                    played_at timestamp with time zone NOT NULL
                );
                CREATE TABLE public.tracks (
                    id character varying NOT NULL PRIMARY KEY,
                    name text NOT NULL,
                    album_id character varying,
                    duration_ms integer NOT NULL
                );
                CREATE TABLE public.albums (
                    id character varying NOT NULL PRIMARY KEY,
                    name text NOT NULL
                );
                CREATE TABLE public.artists (
                    id character varying NOT NULL PRIMARY KEY,
                    name text NOT NULL
                );
                CREATE TABLE public.track_artists (
                    track_id character varying NOT NULL,
                    artist_id character varying NOT NULL
                );
                CREATE TABLE public.track_genres (
                    track_id character varying NOT NULL,
                    genre character varying NOT NULL
                );
                """;
    }


    @SneakyThrows
    public InsightResponse askQuestion(User user, String question) {

        // === ÉTAPE 1 : L'IA GÉNÈRE LE SQL (AVEC UN PROMPT AMÉLIORÉ) ===

        String sqlPrompt = """
                Tu es un expert en SQL PostgreSQL. Ta seule mission est de générer UNE requête SQL pour répondre à la question de l'utilisateur.
                
                **CHECKLIST IMPÉRATIVE POUR TA REQUÊTE :**
                - [ ] La requête doit joindre `listening_history` avec `tracks`.
                - [ ] La requête doit joindre `tracks` avec `track_artists` ET `artists` pour obtenir les noms des artistes.
                - [ ] La requête doit joindre `tracks` avec `track_genres` pour obtenir les genres.
                - [ ] La requête doit joindre `tracks` avec `albums` pour obtenir le nom de l'album.
                - [ ] La requête doit TOUJOURS sélectionner les colonnes les plus détaillées possibles.
                - [ ] La requête doit compter les écoutes avec COUNT(*) AS listen_count.
                - [ ] Le `GROUP BY` doit être valide et inclure les colonnes non agrégées.
                - [ ] La requête doit filtrer pour l'utilisateur avec l'ID : {userId}.
                
                -- ✅ NOUVELLE RÈGLE DE FILTRAGE DYNAMIQUE --
                - [ ] **Analyse la question de l'utilisateur. Si elle contient un nom de genre (comme 'Pop', 'Rock', 'Jazz'), un nom d'artiste, ou une période de temps, ajoute une clause `WHERE` supplémentaire pour filtrer les résultats. 
                Par exemple, pour "chansons Pop", ajoute : `AND tg.genre ILIKE '%Pop%'`**
                
                Ta sortie doit être UNIQUEMENT du code SQL brut.
                
                Schéma de référence:
                ---
                {dbSchema}
                ---
                Question de l'utilisateur : {question}
                """;

        String sqlQuery = chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(sqlPrompt)
                        .param("dbSchema", this.dbSchema)
                        .param("userId", user.getId())
                        .param("question", question)
                )
                .call()
                .content();

        String cleanedSql = sqlQuery.trim().replace("`", "").replace("sql", "").trim();

        // === ÉTAPE 2 : JAVA EXÉCUTE LA REQUÊTE ET TRAITE LES DONNÉES ===

        List<Map<String, Object>> databaseResults = jdbcTemplate.queryForList(cleanedSql);
        log.info(databaseResults.toString());

        if (databaseResults.isEmpty()) {
            return new InsightResponse("Je n'ai pas trouvé d'informations pour ta demande.");
        }

        // Mapping manuel des résultats de la base de données vers TrackInfo
        List<TrackInfo> topTracks = databaseResults.stream()
                .map(row -> {
                    // Extraction des données avec gestion des noms de colonnes possibles
                    String title = getStringValue(row, "name", "track_name", "title");
                    String artist = getStringValue(row, "artist_name", "artist");
                    String genre = getStringValue(row, "genres", "genre");
                    int count = getIntValue(row, "listen_count", "count");

                    return new TrackInfo(title, artist, genre, count);
                })
                .collect(Collectors.toList());

        // === ÉTAPE 3 : L'IA GÉNÈRE LE RÉSUMÉ TEXTUEL ===

        String summary = chatClient.prompt()
                .user("Écris une phrase d'introduction courte et amicale pour une liste des morceaux favoris d'un utilisateur.")
                .call()
                .content();

        // === ÉTAPE 4 : JAVA CONSTRUIT LA RÉPONSE FINALE ===

        return new InsightResponse(summary, Optional.of(topTracks), Optional.empty(), Optional.empty());
    }

    // Méthodes utilitaires pour extraire les valeurs avec plusieurs noms de colonnes possibles
    private String getStringValue(Map<String, Object> row, String... columnNames) {
        for (String columnName : columnNames) {
            Object value = row.get(columnName);
            if (value != null) {
                return value.toString();
            }
        }
        return "Inconnu";
    }

    private int getIntValue(Map<String, Object> row, String... columnNames) {
        for (String columnName : columnNames) {
            Object value = row.get(columnName);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                try {
                    return Integer.parseInt(value.toString());
                } catch (NumberFormatException e) {
                    // Continue avec la prochaine colonne
                }
            }
        }
        return 0;
    }
}
