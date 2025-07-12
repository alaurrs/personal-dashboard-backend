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

        // 1. Résumés par mois avec contexte enrichi
        Map<YearMonth, List<ListeningHistory>> byMonth = history.stream()
                .collect(Collectors.groupingBy(h -> YearMonth.from(h.getPlayedAt().atZone(ZoneId.systemDefault()).toLocalDate())));

        for (var entry : byMonth.entrySet()) {
            String summary = summarizeByMonthEnriched(entry.getKey(), entry.getValue());
            float[] embedding = openAiService.getEmbedding(summary);

            // Métadonnées enrichies pour le mois
            Map<String, Object> metadata = buildEnrichedMonthlyMetadata(entry.getKey(), entry.getValue());
            insertUserDocument(user.getId(), summary, "spotify", "monthly", embedding, metadata);

            // Générer aussi un résumé structuré spécialement pour les requêtes IA
            String structuredSummary = generateStructuredMonthlySummary(entry.getKey(), entry.getValue());
            float[] structuredEmbedding = openAiService.getEmbedding(structuredSummary);
            insertUserDocument(user.getId(), structuredSummary, "spotify", "monthly_structured", structuredEmbedding, metadata);
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

        // 3. Résumé global des top tracks avec contexte enrichi
        String globalTopTracks = summarizeTopTracksGlobalEnriched(history);
        embedding = openAiService.getEmbedding(globalTopTracks);
        metadata = buildEnrichedGlobalTracksMetadata(history);
        insertUserDocument(user.getId(), globalTopTracks, "spotify", "top_tracks_global", embedding, metadata);
    }

    /**
     * Génère un résumé mensuel structuré optimisé pour les requêtes IA.
     * Ce format inclut tous les détails nécessaires pour les réponses JSON.
     */
    private String generateStructuredMonthlySummary(YearMonth month, List<ListeningHistory> history) {
        // Analyser les tracks avec leurs détails complets
        Map<String, TrackPlayData> trackPlayData = history.stream()
                .collect(Collectors.groupingBy(
                    h -> h.getTrack().getId(),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        historyList -> {
                            ListeningHistory first = historyList.get(0);
                            List<String> artistNames = first.getTrack().getArtists().stream()
                                    .map(Artist::getName)
                                    .sorted()
                                    .toList();
                            Set<String> genres = first.getTrack().getGenres() != null ?
                                    first.getTrack().getGenres() : Set.of();
                            return new TrackPlayData(
                                    first.getTrack().getName(),
                                    artistNames,
                                    historyList.size(),
                                    genres
                            );
                        }
                    )
                ));

        // Top 10 tracks avec détails complets
        List<TrackPlayData> topTracks = trackPlayData.values().stream()
                .sorted((a, b) -> Integer.compare(b.playCount(), a.playCount()))
                .limit(10)
                .toList();

        // Analyser les genres avec pourcentages
        Map<String, Long> genreCount = trackPlayData.values().stream()
                .flatMap(data -> data.genres().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        long totalTracksWithGenres = genreCount.values().stream().mapToLong(Long::longValue).sum();

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("DONNÉES MUSICALES DÉTAILLÉES - %s %d\n\n",
                month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH),
                month.getYear()));

        summary.append("TOP TRACKS AVEC DÉTAILS :\n");
        for (TrackPlayData track : topTracks) {
            String genresList = track.genres().isEmpty() ? "genre inconnu" : String.join(", ", track.genres());
            summary.append(String.format("- \"%s\" par %s [%s] : %d écoutes\n",
                    track.trackName(),
                    String.join(", ", track.artistNames()),
                    genresList,
                    track.playCount()));
        }

        summary.append("\nRÉPARTITION DES GENRES :\n");
        genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .forEach(entry -> {
                    double percentage = totalTracksWithGenres > 0 ?
                            (entry.getValue() * 100.0) / totalTracksWithGenres : 0;
                    summary.append(String.format("- %s : %.1f%% (%d tracks)\n",
                            entry.getKey(), percentage, entry.getValue()));
                });

        summary.append(String.format("\nSTATISTIQUES GÉNÉRALES :\n"));
        summary.append(String.format("- Total écoutes : %d\n", history.size()));
        summary.append(String.format("- Tracks uniques : %d\n", trackPlayData.size()));
        summary.append(String.format("- Genres différents : %d\n", genreCount.size()));

        return summary.toString();
    }

    private String summarizeListening(YearMonth month, List<ListeningHistory> history) {
        Map<String, Long> artistCount = history.stream()
                .collect(Collectors.groupingBy(h -> h.getTrack().getArtists().stream()
                        .map(Artist::getName)
                        .collect(Collectors.joining(", ")), Collectors.counting()));

        String topArtists = artistCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
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
                .limit(15)
                .map(e -> e.getKey() + " (" + e.getValue() + " écoutes)")
                .collect(Collectors.joining(", "));

        // Top morceaux
        String topTracks = history.stream()
                .map(h -> h.getTrack().getName())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
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

    private String summarizeByMonthEnriched(YearMonth month, List<ListeningHistory> history) {
        // Compter les écoutes par track avec tous les artistes et genres
        Map<String, TrackPlayData> trackPlayData = history.stream()
                .collect(Collectors.groupingBy(
                    h -> h.getTrack().getId(),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        historyList -> {
                            ListeningHistory first = historyList.get(0);
                            List<String> artistNames = first.getTrack().getArtists().stream()
                                    .map(Artist::getName)
                                    .sorted()
                                    .toList();
                            Set<String> genres = first.getTrack().getGenres() != null ?
                                    first.getTrack().getGenres() : Set.of();
                            return new TrackPlayData(
                                    first.getTrack().getName(),
                                    artistNames,
                                    historyList.size(),
                                    genres
                            );
                        }
                    )
                ));

        // Top tracks avec leurs artistes
        String topTracksWithArtists = trackPlayData.values().stream()
                .sorted((a, b) -> Integer.compare(b.playCount(), a.playCount()))
                .limit(15)
                .map(data -> String.format("%s par %s (%d écoutes)",
                    data.trackName(),
                    String.join(", ", data.artistNames()),
                    data.playCount()))
                .collect(Collectors.joining(", "));

        // Top artistes globaux
        Map<String, Long> artistCount = history.stream()
                .flatMap(h -> h.getTrack().getArtists().stream())
                .collect(Collectors.groupingBy(Artist::getName, Collectors.counting()));

        String topArtists = artistCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .map(e -> e.getKey() + " (" + e.getValue() + " écoutes)")
                .collect(Collectors.joining(", "));

        // Analyser les genres les plus écoutés
        Map<String, Long> genreCount = trackPlayData.values().stream()
                .flatMap(data -> data.genres().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        String topGenres = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));

        String genreText = topGenres.isEmpty() ? "" :
                String.format(" Les genres musicaux dominants ont été : %s.", topGenres);

        return String.format("En %s %d, l'utilisateur a écouté %d morceaux au total. " +
                "Les artistes les plus écoutés ont été : %s. " +
                "Les morceaux favoris du mois : %s.%s",
                month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH),
                month.getYear(),
                history.size(),
                topArtists,
                topTracksWithArtists,
                genreText);
    }

    private String summarizeTopTracksGlobalEnriched(List<ListeningHistory> history) {
        // Même logique que pour le mois mais sur toute la période
        Map<String, TrackPlayData> trackPlayData = history.stream()
                .collect(Collectors.groupingBy(
                    h -> h.getTrack().getId(),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        historyList -> {
                            ListeningHistory first = historyList.get(0);
                            List<String> artistNames = first.getTrack().getArtists().stream()
                                    .map(Artist::getName)
                                    .sorted()
                                    .toList();
                            Set<String> genres = first.getTrack().getGenres() != null ?
                                    first.getTrack().getGenres() : Set.of();
                            return new TrackPlayData(
                                    first.getTrack().getName(),
                                    artistNames,
                                    historyList.size(),
                                    genres
                            );
                        }
                    )
                ));

        String topTracksWithArtists = trackPlayData.values().stream()
                .sorted((a, b) -> Integer.compare(b.playCount(), a.playCount()))
                .limit(10)
                .map(data -> String.format("%s par %s (%d fois)",
                    data.trackName(),
                    String.join(", ", data.artistNames()),
                    data.playCount()))
                .collect(Collectors.joining(", "));

        // Analyser les genres globaux
        Map<String, Long> genreCount = trackPlayData.values().stream()
                .flatMap(data -> data.genres().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        String genreAnalysis = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> String.format("%s (%d tracks)", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));

        String genreText = genreAnalysis.isEmpty() ? "" :
                String.format(" L'analyse des genres révèle une préférence pour : %s.", genreAnalysis);

        return "L'utilisateur a des morceaux favoris qu'il écoute en boucle : " + topTracksWithArtists +
               ". Ces données révèlent ses goûts musicaux et ses habitudes d'écoute répétitive." + genreText;
    }

    private Map<String, Object> buildEnrichedMonthlyMetadata(YearMonth month, List<ListeningHistory> history) {
        // Top artistes
        List<String> top5ArtistNames = history.stream()
                .flatMap(h -> h.getTrack().getArtists().stream())
                .collect(Collectors.groupingBy(Artist::getName, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        // Top tracks avec détails enrichis (incluant genres)
        List<Map<String, Object>> topTracksDetailed = history.stream()
                .collect(Collectors.groupingBy(
                    h -> h.getTrack().getId(),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        historyList -> {
                            ListeningHistory first = historyList.get(0);
                            return Map.of(
                                "trackName", first.getTrack().getName(),
                                "artistNames", first.getTrack().getArtists().stream()
                                        .map(Artist::getName).toList(),
                                "playCount", historyList.size(),
                                "genres", first.getTrack().getGenres() != null ?
                                        first.getTrack().getGenres() : Set.of()
                            );
                        }
                    )
                ))
                .values().stream()
                .sorted((a, b) -> Integer.compare((Integer) b.get("playCount"), (Integer) a.get("playCount")))
                .limit(10)
                .toList();

        // Analyser les genres du mois
        Map<String, Long> genreCount = history.stream()
                .filter(h -> h.getTrack().getGenres() != null)
                .flatMap(h -> h.getTrack().getGenres().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<String> topGenres = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        return Map.of(
                "month", month.toString(),
                "top_artists", top5ArtistNames,
                "top_genres", topGenres,
                "track_count", history.size(),
                "unique_tracks", history.stream().map(h -> h.getTrack().getId()).distinct().count(),
                "top_tracks_detailed", topTracksDetailed
        );
    }

    private Map<String, Object> buildEnrichedGlobalTracksMetadata(List<ListeningHistory> history) {
        // Statistiques globales enrichies
        Map<String, Long> trackCounts = history.stream()
                .collect(Collectors.groupingBy(h -> h.getTrack().getId(), Collectors.counting()));

        List<Map<String, Object>> topTracksDetailed = history.stream()
                .collect(Collectors.groupingBy(
                    h -> h.getTrack().getId(),
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        historyList -> {
                            ListeningHistory first = historyList.get(0);
                            return Map.of(
                                "trackId", first.getTrack().getId(),
                                "trackName", first.getTrack().getName(),
                                "artistNames", first.getTrack().getArtists().stream()
                                        .map(Artist::getName).toList(),
                                "playCount", historyList.size(),
                                "genres", first.getTrack().getGenres() != null ?
                                        first.getTrack().getGenres() : Set.of()
                            );
                        }
                    )
                ))
                .values().stream()
                .sorted((a, b) -> Integer.compare((Integer) b.get("playCount"), (Integer) a.get("playCount")))
                .limit(20)
                .toList();

        // Analyser la diversité des genres
        Map<String, Long> genreCount = history.stream()
                .filter(h -> h.getTrack().getGenres() != null)
                .flatMap(h -> h.getTrack().getGenres().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<Map<String, Object>> topGenresDetailed = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> Map.<String, Object>of(
                    "genre", entry.getKey(),
                    "trackCount", entry.getValue()
                ))
                .toList();

        return Map.of(
                "total_plays", history.size(),
                "unique_tracks", trackCounts.size(),
                "unique_genres", genreCount.size(),
                "top_tracks_detailed", topTracksDetailed,
                "top_genres_detailed", topGenresDetailed,
                "average_plays_per_track", history.size() / (double) trackCounts.size()
        );
    }

    // Record mis à jour pour inclure les genres
    private record TrackPlayData(String trackName, List<String> artistNames, int playCount, Set<String> genres) {}
}
