package com.dashboard.backend.analytics.controller;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.analytics.dto.TopArtistDto;
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
            @AuthenticationPrincipal User user, // Injecté par Spring Security
            @RequestParam(defaultValue = "last_6_months") String timeRange,
            @RequestParam(defaultValue = "10") int limit
    ) {
        // Valider les paramètres (peut être fait avec @Validated)
        if (!List.of("last_month", "last_6_months", "all_time").contains(timeRange)) {
            return ResponseEntity.badRequest().build();
        }

        List<TopArtistDto> topArtists = analyticsService.getTopArtistsForUser(user, timeRange, limit);
        return ResponseEntity.ok(topArtists);
    }
}
