package com.dashboard.backend.thirdparty.spotify.dto;

import com.dashboard.backend.thirdparty.spotify.ExternalIds;
import com.dashboard.backend.thirdparty.spotify.ExternalUrls;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SpotifyTrackDto(
        SpotifyAlbumDto album,
        List<SpotifyArtistDto> artists,
        @JsonProperty("available_markets") List<String> availableMarkets,
        @JsonProperty("disc_number") int discNumber,
        @JsonProperty("duration_ms") int durationMs,
        boolean explicit,
        @JsonProperty("external_ids") ExternalIds externalIds,
        @JsonProperty("external_urls") ExternalUrls externalUrls,
        String href,
        String id,
        @JsonProperty("is_playable") boolean isPlayable,
        String name,
        int popularity,
        @JsonProperty("preview_url") String previewUrl,
        @JsonProperty("track_number") int trackNumber,
        String type,
        String uri,
        @JsonProperty("is_local") boolean isLocal
) {}