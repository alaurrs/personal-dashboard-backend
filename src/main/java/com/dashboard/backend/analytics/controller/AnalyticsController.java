package com.dashboard.backend.analytics.controller;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.analytics.dto.TopArtistDto;
import com.dashboard.backend.analytics.dto.TopTrackDto;
import com.dashboard.backend.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/top-artists")
    public ResponseEntity<List<TopArtistDto>> getTopArtists(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "medium_term") String timeRange,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "cache") String source // NOUVEAU: Le paramètre qui guide le backend
    ) {
        if (!isValidTimeRange(timeRange)) {
            return ResponseEntity.badRequest().build();
        }

        List<TopArtistDto> topArtists;

        // Le backend choisit la stratégie en fonction de l'indice du frontend
        if ("history".equalsIgnoreCase(source)) {
            // Demande d'analyse approfondie
            topArtists = analyticsService.calculateTopArtistsFromHistory(user, timeRange, limit);
        } else {
            // Comportement par défaut : rapide, via le cache
            topArtists = analyticsService.getCachedTopArtistsForUser(user, timeRange, limit);
        }

        return ResponseEntity.ok(topArtists);
    }

    @GetMapping("/top-tracks")
    public ResponseEntity<List<TopTrackDto>> getTopTracks(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "medium_term") String timeRange,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (!isValidTimeRange(timeRange)) {
            return ResponseEntity.badRequest().build();
        }

        List<TopTrackDto> topTracks = analyticsService.getTopTracksForUser(user, timeRange, limit);
        return ResponseEntity.ok(topTracks);
    }

    private boolean isValidTimeRange(String timeRange) {
        return List.of("short_term", "medium_term", "all_time").contains(timeRange);
    }
}
