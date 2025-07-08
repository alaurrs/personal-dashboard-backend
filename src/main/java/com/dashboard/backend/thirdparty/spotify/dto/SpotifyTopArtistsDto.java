package com.dashboard.backend.thirdparty.spotify.dto;

import com.dashboard.backend.analytics.dto.TopArtistDto;

import java.util.List;

public record SpotifyTopArtistsDto(
        String href,
        List<SpotifyArtistDto> items,
        int limit,
        String next,
        int offset,
        String previous,
        int total
) {

    public TopArtistDto getTopArtistDto() {
        return new TopArtistDto() {
            @Override
            public String getArtistId() {
                return items.getFirst().id();
            }

            @Override
            public String getArtistName() {
                return items.getFirst().name();
            }

            @Override
            public Long getPlayCount() {
                // Assuming play count is not available in this DTO, returning null
                return null;
            }
        };
    }
}