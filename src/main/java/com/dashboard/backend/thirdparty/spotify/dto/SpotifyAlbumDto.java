package com.dashboard.backend.thirdparty.spotify.dto;

import com.dashboard.backend.thirdparty.spotify.ExternalUrls;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SpotifyAlbumDto(
        @JsonProperty("album_type") String albumType,
        @JsonProperty("total_tracks") int totalTracks,
        @JsonProperty("available_markets") List<String> availableMarkets,
        @JsonProperty("external_urls") ExternalUrls externalUrls,
        String href,
        String id,
        List<ImageDto> images,
        String name,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("release_date_precision") String releaseDatePrecision,
        String type,
        String uri,
        List<SpotifyArtistDto> artists
) {}