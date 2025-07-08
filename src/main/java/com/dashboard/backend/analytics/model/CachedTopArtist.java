package com.dashboard.backend.analytics.model;

import com.dashboard.backend.User.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cached_top_artists")
@Getter
@Setter
public class CachedTopArtist {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String timeRange; // Ex: "last_6_months"

    @Column(nullable = false)
    private String artistIdSpotify;

    @Column(nullable = false)
    private String artistName;

    private String artistImageUrl;

    @Column(name = "artist_rank", nullable = false)
    private int rank;

    @Column(nullable = false)
    private Instant lastUpdatedAt;
}
