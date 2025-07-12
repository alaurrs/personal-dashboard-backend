package com.dashboard.backend.rag.service;

import com.dashboard.backend.User.model.Artist;
import com.dashboard.backend.User.model.ListeningHistory;
import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.ListeningHistoryRepository;
import com.dashboard.backend.thirdparty.openai.service.OpenAiService;
import com.nimbusds.jose.shaded.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDocumentGenerationService {
    private final JdbcTemplate jdbc;
    private final OpenAiService openAiService;
    private final ListeningHistoryRepository listeningHistoryRepository;

    @Transactional
    public void generateFromListeningHistory(User user) {
        log.info("🎯 Génération RAG pour {}", user.getEmail());

        List<ListeningHistory> history = listeningHistoryRepository.findByUser(user);
        if (history.isEmpty()) return;

        // 1. Résumés par mois
        Map<YearMonth, List<ListeningHistory>> byMonth = history.stream()
                .collect(Collectors.groupingBy(h -> YearMonth.from(h.getPlayedAt().atZone(ZoneId.systemDefault()).toLocalDate())));
        for (var entry : byMonth.entrySet()) {
            String summary = summarizeByMonth(entry.getKey(), entry.getValue());
            float[] embedding = openAiService.getEmbedding(summary);
            List<String> top5ArtistNames = entry.getValue().stream()
                    .flatMap(h -> h.getTrack().getArtists().stream())
                    .collect(Collectors.groupingBy(Artist::getName, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .toList();

            Map<String, Object> metadata = Map.of(
                    "month", entry.getKey().toString(),
                    "top_artists", top5ArtistNames,
                    "track_count", history.size()
            );
            insertUserDocument(user.getId(), summary, "spotify", "monthly", embedding, metadata);
        }

        // 2. Résumé global par heure (ex : "écoute le matin")
        Map<Integer, Long> byHour = history.stream()
                .collect(Collectors.groupingBy(h -> h.getPlayedAt().atZone(ZoneId.systemDefault()).getHour(), Collectors.counting()));
        String hourlySummary = summarizeByHour(byHour);
        float[] embedding = openAiService.getEmbedding(hourlySummary);
        Map<String, Object> metadata = Map.of(
                "top_hours", byHour.entrySet().stream()
                        .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                        .limit(3)
                        .map(e -> String.format("entre %dh et %dh (%d écoutes)", e.getKey(), e.getKey() + 1, e.getValue()))
                        .collect(Collectors.joining(", "))
        );
        insertUserDocument(user.getId(), hourlySummary, "spotify", "hourly", embedding, metadata);

        String globalTopTracks = summarizeTopTracksGlobal(history);
        embedding = openAiService.getEmbedding(globalTopTracks);
        metadata = Map.of(
                "top_tracks", history.stream()
                        .map(h -> h.getTrack().getName())
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                        .entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(10)
                        .map(e -> e.getKey() + " (" + e.getValue() + " fois)")
                        .collect(Collectors.joining(", "))
        );
        insertUserDocument(user.getId(), globalTopTracks, "spotify", "top_tracks_global", embedding, metadata);
    }

    private String summarizeListening(YearMonth month, List<ListeningHistory> history) {
        Map<String, Long> artistCount = history.stream()
                .collect(Collectors.groupingBy(h -> h.getTrack().getArtists().stream()
                        .map(Artist::getName)
                        .collect(Collectors.joining(", ")), Collectors.counting()));

        String topArtists = artistCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + " écoutes)")
                .collect(Collectors.joining(", "));

        return "En " + month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + month.getYear()
                + ", l’utilisateur a principalement écouté : " + topArtists + ".";
    }

    private void insertUserDocument(UUID userId, String content, String source, String summaryType, float[] embedding, Map<String, Object> metadata) {

        if (documentExists(userId, summaryType, content)) {
            log.info("⏩ Document déjà existant, insertion ignorée.");
            return;
        }

        String pgVector = IntStream.range(0, embedding.length)
                .mapToObj(i -> String.format(Locale.US, "%.6f", embedding[i]))
                .collect(Collectors.joining(", ", "[", "]"));

        String sql = """
        INSERT INTO user_documents (id, user_id, source, summary_type, content, embedding, metadata)
        VALUES (?, ?, ?, ?, ?, CAST(? AS vector), ?::jsonb)
    """;

        String metadataJson = new Gson().toJson(metadata); // ou Jackson si tu préfères

        jdbc.update(sql, UUID.randomUUID(), userId, source, summaryType, content, pgVector, metadataJson);
    }
    private String summarizeByMonth(YearMonth month, List<ListeningHistory> history) {
        // Top artistes
        String topArtists = history.stream()
                .flatMap(h -> h.getTrack().getArtists().stream())
                .collect(Collectors.groupingBy(Artist::getName, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + " écoutes)")
                .collect(Collectors.joining(", "));

        // Top morceaux
        String topTracks = history.stream()
                .map(h -> h.getTrack().getName())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + " fois)")
                .collect(Collectors.joining(", "));

        return "En " + month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + month.getYear() +
                ", l’utilisateur a surtout écouté les artistes suivants : " + topArtists + ". " +
                "Les morceaux les plus joués ont été : " + topTracks + ".";
    }

    private String summarizeByHour(Map<Integer, Long> hourCount) {
        String topHours = hourCount.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> String.format("entre %dh et %dh (%d écoutes)", e.getKey(), e.getKey()+1, e.getValue()))
                .collect(Collectors.joining(", "));

        return "L’utilisateur écoute principalement de la musique " + topHours + ".";
    }

    private String summarizeTopTracksGlobal(List<ListeningHistory> history) {
        String topTracks = history.stream()
                .map(h -> h.getTrack().getName())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> e.getKey() + " (" + e.getValue() + " fois)")
                .collect(Collectors.joining(", "));

        return "L’utilisateur a écouté en boucle les titres suivants au cours des derniers mois : " + topTracks + ".";
    }

    private boolean documentExists(UUID userId, String summaryType, String content) {
        String sql = """
        SELECT EXISTS (
            SELECT 1 FROM user_documents
            WHERE user_id = ? AND summary_type = ? AND content = ?
        )
    """;
        return jdbc.queryForObject(sql, Boolean.class, userId, summaryType, content);
    }
}
