package com.dashboard.backend.User.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spotify_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpotifyAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "spotify_user_id")
    private String spotifyUserId;

    @Column(name = "spotify_email")
    private String spotifyEmail;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "access_token", length = 1000)
    private String accessToken;

    @Column(name = "refresh_token", length = 1000)
    private String refreshToken;

    @Column(name = "token_expiry")
    private Instant tokenExpiry;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    @Column(name = "last_sync")
    private LocalDateTime lastSync;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructeur personnalisé
    public SpotifyAccount(User user, String spotifyUserId, String spotifyEmail) {
        this.user = user;
        this.spotifyUserId = spotifyUserId;
        this.spotifyEmail = spotifyEmail;
        this.linkedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Méthodes utilitaires
    public boolean isTokenExpired() {
        return tokenExpiry != null && tokenExpiry.isBefore(Instant.now());
    }

    public boolean isLinked() {
        return accessToken != null && !accessToken.isEmpty();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
