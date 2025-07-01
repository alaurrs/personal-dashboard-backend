package com.dashboard.backend.thirdparty.spotify.dto;

import com.dashboard.backend.thirdparty.spotify.ExternalUrls;
import com.dashboard.backend.thirdparty.spotify.Followers;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SpotifyArtistDto(
        @JsonProperty("external_urls") ExternalUrls externalUrls,
        Followers followers,
        List<String> genres,
        String href,
        String id,
        List<ImageDto> images,
        String name,
        int popularity,
        String type,
        String uri
) {}
