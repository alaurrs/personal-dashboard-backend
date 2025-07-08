package com.dashboard.backend.analytics.repository;

import com.dashboard.backend.User.model.ListeningHistory;
import com.dashboard.backend.analytics.dto.TopArtistDto;
import com.dashboard.backend.analytics.dto.TopTrackProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface AnalyticsRepository extends JpaRepository<ListeningHistory, UUID> {

    @Query(value = """
        SELECT
            a.id AS artistId,
            a.name AS artistName,
            COUNT(lh.id) AS playCount,
            a.image_url AS artistImageUrl
        FROM listening_history lh
        JOIN tracks t ON lh.track_id = t.id
        JOIN track_artists ta ON t.id = ta.track_id
        JOIN artists a ON ta.artist_id = a.id
        WHERE lh.user_id = :userId
          AND lh.played_at BETWEEN :startDate AND :endDate
        GROUP BY a.id, a.name, a.image_url
        ORDER BY playCount DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<TopArtistDto> findTopArtistsByPlayCount(
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT
            t.id AS trackId,
            t.name AS trackName,
            STRING_AGG(DISTINCT a.name, ', ' ORDER BY a.name) AS artistNames,
            COUNT(DISTINCT lh.id) AS playCount
        FROM listening_history lh
        JOIN tracks t ON lh.track_id = t.id
        JOIN track_artists ta ON t.id = ta.track_id
        JOIN artists a ON ta.artist_id = a.id
        WHERE lh.user_id = :userId
          AND lh.played_at BETWEEN :startDate AND :endDate
        GROUP BY t.id, t.name
        ORDER BY playCount DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<TopTrackProjection> findTopTracksByPlayCount(
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("limit") int limit
    );
}
