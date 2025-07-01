package com.dashboard.backend.thirdparty.spotify.dto;

import java.util.List;

public record SpotifyTopArtistsDto(
        String href,
        List<SpotifyArtistDto> items,
        int limit,
        String next,
        int offset,
        String previous,
        int total
) {}