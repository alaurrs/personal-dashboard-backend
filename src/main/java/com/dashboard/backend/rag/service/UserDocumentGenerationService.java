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

import java.time.Instant;
import java.time.LocalDate;
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
        log.info("🎯 Génération RAG exhaustive pour {}", user.getEmail());

        List<ListeningHistory> history = listeningHistoryRepository.findByUser(user);
        if (history.isEmpty()) return;

        // Nettoyer d'abord les anciennes données obsolètes
        cleanupObsoleteDocuments(user.getId());

        // 1. Générer les rapports pour chaque mois disponible
        generateMonthlyReports(user, history);

        // 2. Générer le rapport des dernières 24h
        generateLast24HoursReport(user, history);

        // 3. Générer le rapport des 7 derniers jours
        generateLast7DaysReport(user, history);

        // 4. Conserver les rapports globaux existants
        generateGlobalReports(user, history);

        log.info("✅ Génération RAG exhaustive terminée pour {}", user.getEmail());
    }

    /**
     * Nettoie les documents obsolètes selon les règles de rétention
     */
    private void cleanupObsoleteDocuments(UUID userId) {
        log.info("🧹 Nettoyage des documents obsolètes pour user {}", userId);

        // Supprimer les rapports quotidiens de plus de 7 jours
        String cleanupDaily = """
            DELETE FROM user_documents 
            WHERE user_id = ? 
            AND summary_type = 'daily' 
            AND metadata->>'date' < ?
        """;
        String sevenDaysAgo = LocalDate.now().minusDays(7).toString();
        int deletedDaily = jdbc.update(cleanupDaily, userId, sevenDaysAgo);

        // Supprimer les anciens rapports hebdomadaires (garder seulement les 4 dernières semaines)
        String cleanupWeekly = """
            DELETE FROM user_documents 
            WHERE user_id = ? 
            AND summary_type = 'weekly' 
            AND metadata->>'week_start' < ?
        """;
        String fourWeeksAgo = LocalDate.now().minusWeeks(4).toString();
        int deletedWeekly = jdbc.update(cleanupWeekly, userId, fourWeeksAgo);

        log.info("🗑️ Supprimé {} rapports quotidiens et {} rapports hebdomadaires obsolètes",
                deletedDaily, deletedWeekly);
    }

    /**
     * Génère tous les rapports mensuels pour chaque mois de données
     */
    private void generateMonthlyReports(User user, List<ListeningHistory> history) {
        Map<YearMonth, List<ListeningHistory>> byMonth = history.stream()
                .collect(Collectors.groupingBy(h -> YearMonth.from(h.getPlayedAt().atZone(ZoneId.systemDefault()).toLocalDate())));

        for (var entry : byMonth.entrySet()) {
            YearMonth month = entry.getKey();
            List<ListeningHistory> monthHistory = entry.getValue();

            log.info("📅 Génération rapport mensuel pour {}", month);

            // Rapport mensuel enrichi
            String summary = summarizeByMonthEnriched(month, monthHistory);
            float[] embedding = openAiService.getEmbedding(summary);
            Map<String, Object> metadata = buildEnrichedMonthlyMetadata(month, monthHistory);
            insertUserDocument(user.getId(), summary, "spotify", "monthly", embedding, metadata);

            // Rapport mensuel structuré pour l'IA
            String structuredSummary = generateStructuredMonthlySummary(month, monthHistory);
            float[] structuredEmbedding = openAiService.getEmbedding(structuredSummary);
            insertUserDocument(user.getId(), structuredSummary, "spotify", "monthly_structured", structuredEmbedding, metadata);
        }
    }

    /**
     * Génère le rapport des dernières 24 heures
     */
    private void generateLast24HoursReport(User user, List<ListeningHistory> history) {
        Instant now = Instant.now();
        Instant yesterday = now.minusSeconds(24 * 60 * 60); // 24 heures en secondes

        List<ListeningHistory> last24Hours = history.stream()
                .filter(h -> h.getPlayedAt().isAfter(yesterday))
                .collect(Collectors.toList());

        if (last24Hours.isEmpty()) {
            log.info("⏸️ Aucune donnée pour les dernières 24h");
            return;
        }

        log.info("🕐 Génération rapport 24h ({} écoutes)", last24Hours.size());

        String summary = generateLast24HoursSummary(last24Hours);
        float[] embedding = openAiService.getEmbedding(summary);

        Map<String, Object> metadata = Map.of(
                "period_type", "daily",
                "date", LocalDate.now().toString(),
                "total_plays", last24Hours.size(),
                "period_start", yesterday.toString(),
                "period_end", now.toString()
        );

        insertUserDocument(user.getId(), summary, "spotify", "daily", embedding, metadata);
    }

    /**
     * Génère le rapport des 7 derniers jours
     */
    private void generateLast7DaysReport(User user, List<ListeningHistory> history) {
        Instant now = Instant.now();
        Instant weekAgo = now.minusSeconds(7 * 24 * 60 * 60); // 7 jours en secondes

        List<ListeningHistory> lastWeek = history.stream()
                .filter(h -> h.getPlayedAt().isAfter(weekAgo))
                .collect(Collectors.toList());

        if (lastWeek.isEmpty()) {
            log.info("⏸️ Aucune donnée pour les 7 derniers jours");
            return;
        }

        log.info("📊 Génération rapport hebdomadaire ({} écoutes)", lastWeek.size());

        String summary = generateLast7DaysSummary(lastWeek);
        float[] embedding = openAiService.getEmbedding(summary);

        Map<String, Object> metadata = Map.of(
                "period_type", "weekly",
                "week_start", weekAgo.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                "week_end", now.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                "total_plays", lastWeek.size()
        );

        insertUserDocument(user.getId(), summary, "spotify", "weekly", embedding, metadata);
    }

    /**
     * Génère les rapports globaux (patterns d'écoute par heure, top tracks all-time)
     */
    private void generateGlobalReports(User user, List<ListeningHistory> history) {
        // Patterns d'écoute par heure
        Map<Integer, Long> byHour = history.stream()
                .collect(Collectors.groupingBy(h -> h.getPlayedAt().atZone(ZoneId.systemDefault()).getHour(), Collectors.counting()));
        String hourlySummary = summarizeByHour(byHour);
        float[] embedding = openAiService.getEmbedding(hourlySummary);
        Map<String, Object> hourlyMetadata = Map.of(
                "pattern_type", "hourly_listening",
                "top_hours", byHour.entrySet().stream()
                        .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                        .limit(3)
                        .map(e -> String.format("entre %dh et %dh (%d écoutes)", e.getKey(), e.getKey() + 1, e.getValue()))
                        .collect(Collectors.joining(", "))
        );
        insertUserDocument(user.getId(), hourlySummary, "spotify", "hourly_patterns", embedding, hourlyMetadata);

        // Top tracks all-time
        String globalTopTracks = summarizeTopTracksGlobalEnriched(history);
        embedding = openAiService.getEmbedding(globalTopTracks);
        Map<String, Object> globalMetadata = buildEnrichedGlobalTracksMetadata(history);
        insertUserDocument(user.getId(), globalTopTracks, "spotify", "top_tracks_global", embedding, globalMetadata);
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
                                    genres,
                                    first.getTrack().getAlbum().getName(),
                                    first.getTrack().getDurationMs()
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

        summary.append("TOP TRACKS AVEC DÉTAILS COMPLETS :\n");
        for (TrackPlayData track : topTracks) {
            String genresList = track.genres().isEmpty() ? "genre inconnu" : String.join(", ", track.genres());
            String durationText = String.format("(%d:%02d)", track.durationMs() / 60000, (track.durationMs() % 60000) / 1000);
            summary.append(String.format("- \"%s\" par %s [Album: %s] [Genres: %s] [Durée: %s] : %d écoutes\n",
                    track.trackName(),
                    String.join(", ", track.artistNames()),
                    track.albumName() != null ? track.albumName() : "Album inconnu",
                    genresList,
                    durationText,
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
                                    genres,
                                    first.getTrack().getAlbum().getName(),
                                    first.getTrack().getDurationMs()
                            );
                        }
                    )
                ));

        // Top artistes avec détails
        Map<String, Long> artistCount = history.stream()
                .flatMap(h -> h.getTrack().getArtists().stream())
                .collect(Collectors.groupingBy(Artist::getName, Collectors.counting()));

        String topArtists = artistCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .map(e -> e.getKey() + " (" + e.getValue() + " écoutes)")
                .collect(Collectors.joining(", "));

        // Top morceaux avec détails complets (titre, artiste, album, genre, durée)
        String topTracks = trackPlayData.values().stream()
                .sorted((a, b) -> Integer.compare(b.playCount(), a.playCount()))
                .limit(10)
                .map(data -> {
                    String genresList = data.genres().isEmpty() ? "genre inconnu" : String.join(", ", data.genres());
                    String albumInfo = data.albumName() != null ? data.albumName() : "Album inconnu";
                    String durationText = String.format("(%d:%02d)", data.durationMs() / 60000, (data.durationMs() % 60000) / 1000);
                    return String.format("'%s' par %s [Album: %s] [Genre: %s] [Durée: %s] (%d fois)",
                        data.trackName(),
                        String.join(", ", data.artistNames()),
                        albumInfo,
                        genresList,
                        durationText,
                        data.playCount());
                })
                .collect(Collectors.joining(", "));

        // Analyser les genres dominants
        Map<String, Long> genreCount = trackPlayData.values().stream()
                .flatMap(data -> data.genres().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        String topGenres = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));

        String genreText = topGenres.isEmpty() ? "" :
                String.format(" Les genres musicaux dominants ont été : %s.", topGenres);

        return String.format("En %s %d, l'utilisateur a surtout écouté les artistes suivants : %s. " +
                "Les morceaux les plus joués ont été : %s.%s",
                month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH),
                month.getYear(),
                topArtists,
                topTracks,
                genreText);
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
                                    genres,
                                    first.getTrack().getAlbum().getName(),
                                    first.getTrack().getDurationMs()
                            );
                        }
                    )
                ));

        // Top tracks avec détails complets (titre, artiste, album, genre, durée)
        String topTracks = trackPlayData.values().stream()
                .sorted((a, b) -> Integer.compare(b.playCount(), a.playCount()))
                .limit(10)
                .map(data -> {
                    String genresList = data.genres().isEmpty() ? "genre inconnu" : String.join(", ", data.genres());
                    String albumInfo = data.albumName() != null ? data.albumName() : "Album inconnu";
                    String durationText = String.format("(%d:%02d)", data.durationMs() / 60000, (data.durationMs() % 60000) / 1000);
                    return String.format("'%s' par %s [Album: %s] [Genre: %s] [Durée: %s] (%d fois)",
                        data.trackName(),
                        String.join(", ", data.artistNames()),
                        albumInfo,
                        genresList,
                        durationText,
                        data.playCount());
                })
                .collect(Collectors.joining(", "));

        return "L'utilisateur a écouté en boucle les titres suivants au cours des derniers mois : " + topTracks + ".";
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
                                    genres,
                                    first.getTrack().getAlbum().getName(),
                                    first.getTrack().getDurationMs()
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
                                    genres,
                                    first.getTrack().getAlbum().getName(),
                                    first.getTrack().getDurationMs()
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

        // Top tracks avec détails enrichis (incluant genres, album et durée)
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
                                        first.getTrack().getGenres() : Set.of(),
                                "albumName", first.getTrack().getAlbum().getName() != null ?
                                        first.getTrack().getAlbum().getName() : "Album inconnu",
                                "durationMs", first.getTrack().getDurationMs()
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
                                        first.getTrack().getGenres() : Set.of(),
                                "albumName", first.getTrack().getAlbum().getName() != null ?
                                        first.getTrack().getAlbum().getName() : "Album inconnu",
                                "durationMs", first.getTrack().getDurationMs()
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

    /**
     * Génère un résumé des dernières 24 heures
     */
    private String generateLast24HoursSummary(List<ListeningHistory> last24Hours) {
        if (last24Hours.isEmpty()) return "Aucune écoute dans les dernières 24 heures.";

        // Analyser les données des dernières 24h
        Map<String, TrackPlayData> trackPlayData = last24Hours.stream()
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
                                    genres,
                                    first.getTrack().getAlbum().getName(),
                                    first.getTrack().getDurationMs()
                            );
                        }
                    )
                ));

        // Top tracks des dernières 24h
        String topTracks = trackPlayData.values().stream()
                .sorted((a, b) -> Integer.compare(b.playCount(), a.playCount()))
                .limit(10)
                .map(data -> String.format("%s par %s (%d fois)",
                    data.trackName(),
                    String.join(", ", data.artistNames()),
                    data.playCount()))
                .collect(Collectors.joining(", "));

        // Top artistes des dernières 24h
        Map<String, Long> artistCount = last24Hours.stream()
                .flatMap(h -> h.getTrack().getArtists().stream())
                .collect(Collectors.groupingBy(Artist::getName, Collectors.counting()));

        String topArtists = artistCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> e.getKey() + " (" + e.getValue() + " écoutes)")
                .collect(Collectors.joining(", "));

        // Analyser les heures d'écoute
        Map<Integer, Long> hourlyListening = last24Hours.stream()
                .collect(Collectors.groupingBy(
                    h -> h.getPlayedAt().atZone(ZoneId.systemDefault()).getHour(),
                    Collectors.counting()
                ));

        String peakHours = hourlyListening.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> String.format("%dh (%d écoutes)", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));

        return String.format("RAPPORT DES DERNIÈRES 24 HEURES :\n\n" +
                "Total d'écoutes : %d\n" +
                "Morceaux uniques : %d\n\n" +
                "TOP MORCEAUX ÉCOUTÉS :\n%s\n\n" +
                "TOP ARTISTES :\n%s\n\n" +
                "HEURES DE POINTE :\n%s",
                last24Hours.size(),
                trackPlayData.size(),
                topTracks,
                topArtists,
                peakHours);
    }

    /**
     * Génère un résumé des 7 derniers jours
     */
    private String generateLast7DaysSummary(List<ListeningHistory> lastWeek) {
        if (lastWeek.isEmpty()) return "Aucune écoute dans les 7 derniers jours.";

        // Analyser les données de la semaine
        Map<String, TrackPlayData> trackPlayData = lastWeek.stream()
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
                                    genres,
                                    first.getTrack().getAlbum().getName(),
                                    first.getTrack().getDurationMs()
                            );
                        }
                    )
                ));

        // Top tracks de la semaine
        String topTracks = trackPlayData.values().stream()
                .sorted((a, b) -> Integer.compare(b.playCount(), a.playCount()))
                .limit(15)
                .map(data -> String.format("%s par %s (%d fois)",
                    data.trackName(),
                    String.join(", ", data.artistNames()),
                    data.playCount()))
                .collect(Collectors.joining(", "));

        // Top artistes de la semaine
        Map<String, Long> artistCount = lastWeek.stream()
                .flatMap(h -> h.getTrack().getArtists().stream())
                .collect(Collectors.groupingBy(Artist::getName, Collectors.counting()));

        String topArtists = artistCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .map(e -> e.getKey() + " (" + e.getValue() + " écoutes)")
                .collect(Collectors.joining(", "));

        // Analyser les genres de la semaine
        Map<String, Long> genreCount = trackPlayData.values().stream()
                .flatMap(data -> data.genres().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        String topGenres = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> String.format("%s (%d tracks)", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));

        // Analyser les patterns quotidiens
        Map<String, Long> dailyListening = lastWeek.stream()
                .collect(Collectors.groupingBy(
                    h -> h.getPlayedAt().atZone(ZoneId.systemDefault()).toLocalDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRENCH),
                    Collectors.counting()
                ));

        String dailyPattern = dailyListening.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> String.format("%s (%d écoutes)", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));

        String genreText = topGenres.isEmpty() ? "" :
                String.format("\n\nTOP GENRES :\n%s", topGenres);

        return String.format("RAPPORT DES 7 DERNIERS JOURS :\n\n" +
                "Total d'écoutes : %d\n" +
                "Morceaux uniques : %d\n" +
                "Artistes différents : %d\n\n" +
                "TOP MORCEAUX DE LA SEMAINE :\n%s\n\n" +
                "TOP ARTISTES :\n%s%s\n\n" +
                "JOURS LES PLUS ACTIFS :\n%s",
                lastWeek.size(),
                trackPlayData.size(),
                artistCount.size(),
                topTracks,
                topArtists,
                genreText,
                dailyPattern);
    }
    // Record mis à jour pour inclure toutes les informations obligatoires du morceau
    private record TrackPlayData(
            String trackName,
            List<String> artistNames,
            int playCount,
            Set<String> genres,
            String albumName,
            int durationMs  // Changé de Integer à int pour correspondre au modèle Track
    ) {}
}
