package com.dashboard.backend.thirdparty.spotify.dto;

import com.dashboard.backend.thirdparty.spotify.ExternalUrls;
import com.dashboard.backend.thirdparty.spotify.Followers;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record SpotifyProfileDto (
    String id,
    @JsonProperty("display_name")
    String displayName,
    String email,
    String country,
    String product,
    @JsonProperty("external_urls") ExternalUrls externalUrls,
    Followers followers,

    List<Map<String, Object>> images

) {}