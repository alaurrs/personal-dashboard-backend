package com.dashboard.backend.analytics.service;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.analytics.dto.TopArtistDto;
import com.dashboard.backend.analytics.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final AnalyticsRepository analyticsRepository;

    public List<TopArtistDto> getTopArtistsForUser(User user, String timeRange, int limit) {
        Instant endDate = Instant.now();
        Instant startDate = switch (timeRange) {
            case "last_month" -> endDate.minus(30, ChronoUnit.DAYS);
            case "last_6_months" -> endDate.minus(180, ChronoUnit.DAYS);
            // "all_time"
            default -> Instant.EPOCH;
        };

        return analyticsRepository.findTopArtistsByPlayCount(user.getId(), startDate, endDate, limit);
    }
}
