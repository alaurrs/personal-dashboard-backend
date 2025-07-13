package com.dashboard.backend.rag.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpotifyTool {

    private final JdbcTemplate jdbcTemplate;

    public record TopArtistsRequest(UUID userId, int limit) {}

    public record SqlQueryRequest(String query) {}
    private final ObjectMapper objectMapper;



    public String executeListeningHistoryQuery(SqlQueryRequest request) {
        String query = request.query();
        System.out.println("✅ Outil d'exécution SQL appelé avec la requête : " + query);

        if (!query.trim().toLowerCase().startsWith("select")) {
            throw new IllegalArgumentException("Seules les requêtes SELECT sont autorisées.");
        }

        try {
            List<Map<String, Object>> databaseResults = jdbcTemplate.queryForList(query);

            // ✅ MODIFICATION : On convertit la liste de résultats en une chaîne JSON.
            return objectMapper.writeValueAsString(databaseResults);

        } catch (JsonProcessingException e) {
            return "{\"error\": \"Erreur lors de la conversion des résultats en JSON\"}";
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'exécution de la requête SQL : " + e.getMessage(), e);
        }
    }
}
