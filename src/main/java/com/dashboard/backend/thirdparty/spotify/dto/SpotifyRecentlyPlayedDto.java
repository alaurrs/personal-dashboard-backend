package com.dashboard.backend.thirdparty.spotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record SpotifyRecentlyPlayedDto(
        List<Item> items,
        String next,
        Cursors cursors,
        int limit,
        String href
) {

    /** Représente une seule écoute dans l'historique. */
    public record Item(
            TrackDto track,
            @JsonProperty("played_at") Instant playedAt
    ) {}

    /** Représente les informations détaillées sur un morceau. */
    public record TrackDto(
            String id,
            String name,
            AlbumDto album,
            List<ArtistDto> artists,
            @JsonProperty("duration_ms") int durationMs,
            boolean explicit,
            @JsonProperty("external_urls") ExternalUrlsDto externalUrls,
            @JsonProperty("preview_url") String previewUrl
    ) {}

    /** Représente les informations sur l'album du morceau. */
    public record AlbumDto(
            String id,
            String name,
            List<ImageDto> images,
            @JsonProperty("release_date") String releaseDate
    ) {}

    /**
     * Représente un artiste. Renommé en ArtistDto pour éviter les conflits
     * avec ton entité JPA `Artist`.
     */
    public record ArtistDto(
            String id,
            String name
    ) {}

    /** Représente une image (pochette d'album, photo d'artiste). */
    public record ImageDto(
            String url,
            int height,
            int width
    ) {}

    /** Représente les URLs externes (ex: lien vers la page Spotify). */
    public record ExternalUrlsDto(
            String spotify
    ) {}

    /** Représente les curseurs pour la pagination. */
    public record Cursors(
            String after,
            String before
    ) {}
}