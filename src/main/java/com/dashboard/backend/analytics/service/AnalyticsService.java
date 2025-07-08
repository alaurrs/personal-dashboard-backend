package com.dashboard.backend.analytics.service;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.analytics.dto.TopArtistDto;
import com.dashboard.backend.analytics.dto.TopTrackDto;
import com.dashboard.backend.analytics.dto.TopTrackProjection;
import com.dashboard.backend.analytics.model.CachedTopArtist;
import com.dashboard.backend.analytics.repository.CachedTopArtistRepository;
import com.dashboard.backend.analytics.repository.AnalyticsRepository;
import com.dashboard.backend.thirdparty.spotify.SpotifyDataService;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyArtistDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopArtistsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final CachedTopArtistRepository cachedTopArtistRepository;
    private final AnalyticsRepository analyticsRepository;
    private final SpotifyDataService spotifyDataService;

    private static final Duration CACHE_DURATION = Duration.ofHours(24);

    /**
     * Méthode principale pour l'affichage UI, optimisée pour la vitesse via le cache.
     */
    @Transactional
    public List<TopArtistDto> getCachedTopArtistsForUser(User user, String timeRange, int limit) {
        Optional<Instant> lastUpdate = cachedTopArtistRepository.findLastUpdateForUserAndTimeRange(user.getId(), timeRange);

        if (lastUpdate.isPresent() && Duration.between(lastUpdate.get(), Instant.now()).compareTo(CACHE_DURATION) < 0) {
            log.info("✅ Cache HIT pour les top artistes de l'utilisateur : {}", user.getEmail());
            return cachedTopArtistRepository.findByUser_IdAndTimeRangeOrderByRankAsc(user.getId(), timeRange)
                    .stream()
                    .limit(limit)
                    .map(this::mapCachedToDto)
                    .toList();
        }

        log.info("❌ Cache MISS pour les top artistes de l'utilisateur : {}", user.getEmail());
        SpotifyTopArtistsDto spotifyResponse = spotifyDataService.getTopArtists(user, timeRange, 50)
                .orElseThrow(() -> new RuntimeException("Impossible de récupérer les top artistes depuis Spotify."));

        saveTopArtistsToCache(user, timeRange, spotifyResponse.items());

        return spotifyResponse.items().stream()
                .limit(limit)
                .map(this::mapSpotifyItemToDto)
                .toList();
    }

    public List<TopTrackDto> getTopTracksForUser(User user, String timeRange, int limit) {
        log.info("Récupération des top tracks pour l'utilisateur : {}", user.getEmail());
        Instant endDate = Instant.now();
        Instant startDate = switch (timeRange) {
            case "last_month" -> endDate.minus(30, ChronoUnit.DAYS);
            case "last_6_months" -> endDate.minus(180, ChronoUnit.DAYS);
            default -> Instant.EPOCH;
        };

        return analyticsRepository.findTopTracksByPlayCount(user.getId(), startDate, endDate, limit)
                .stream()
                .map(this::convertToTopTrackDto)
                .toList();
    }

    /**
     * Calcule les top artistes en se basant sur l'historique interne.
     * C'est ici que votre logique existante est préservée et valorisée.
     */
    public List<TopArtistDto> calculateTopArtistsFromHistory(User user, String timeRange, int limit) {
        log.info("Calcul des top artistes depuis l'historique pour {}", user.getEmail());
        Instant endDate = Instant.now();
        Instant startDate = switch (timeRange) {
            case "last_month" -> endDate.minus(30, ChronoUnit.DAYS);
            case "last_6_months" -> endDate.minus(180, ChronoUnit.DAYS);
            default -> Instant.EPOCH;
        };
        return analyticsRepository.findTopArtistsByPlayCount(user.getId(), startDate, endDate, limit);
    }

    private void saveTopArtistsToCache(User user, String timeRange, List<SpotifyArtistDto> artists) {
        log.info("Mise à jour du cache des top artistes pour {}", user.getEmail());
        cachedTopArtistRepository.deleteByUser_IdAndTimeRange(user.getId(), timeRange);

        AtomicInteger rank = new AtomicInteger(1);
        Instant now = Instant.now();

        List<CachedTopArtist> cacheEntries = artists.stream().map(item -> {
            CachedTopArtist entry = new CachedTopArtist();
            entry.setUser(user);
            entry.setTimeRange(timeRange);
            entry.setArtistIdSpotify(item.id());
            entry.setArtistName(item.name());
            if (item.images() != null && !item.images().isEmpty()) {
                entry.setArtistImageUrl(item.images().getFirst().url());
            }
            entry.setRank(rank.getAndIncrement());
            entry.setLastUpdatedAt(now);
            return entry;
        }).collect(Collectors.toList());

        cachedTopArtistRepository.saveAll(cacheEntries);
    }

    private TopArtistDto mapCachedToDto(CachedTopArtist cachedArtist) {
        return TopArtistDto.builder()
                .artistId(cachedArtist.getArtistIdSpotify())
                .artistName(cachedArtist.getArtistName())
                .playCount(0L)
                .artistImageUrl(cachedArtist.getArtistImageUrl())
                .build();
    }

    private TopArtistDto mapSpotifyItemToDto(SpotifyArtistDto item) {
        String imageUrl = null;
        if (item.images() != null && !item.images().isEmpty()) {
            imageUrl = item.images().getFirst().url();
        }

        return TopArtistDto.builder()
                .artistId(item.id())
                .artistName(item.name())
                .playCount(0L)
                .artistImageUrl(imageUrl)
                .build();
    }

    /**
     * Convertit une projection TopTrackProjection en TopTrackDto
     * en transformant la chaîne d'artistes concaténée en liste
     */
    private TopTrackDto convertToTopTrackDto(TopTrackProjection projection) {
        List<String> artistNames = List.of(projection.getArtistNames().split(", "));

        return TopTrackDto.builder()
                .trackId(projection.getTrackId())
                .trackName(projection.getTrackName())
                .artistNames(artistNames)
                .playCount(projection.getPlayCount())
                .build();
    }
}
