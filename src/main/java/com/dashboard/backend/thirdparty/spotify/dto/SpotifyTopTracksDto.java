package com.dashboard.backend.thirdparty.spotify.dto;

import java.util.List;

public record SpotifyTopTracksDto(
        String href,
        List<SpotifyTrackDto> items,
        int limit,
        String next,
        int offset,
        String previous,
        int total
) {}