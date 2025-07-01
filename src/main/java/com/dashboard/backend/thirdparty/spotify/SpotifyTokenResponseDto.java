package com.dashboard.backend.thirdparty.spotify;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpotifyTokenResponseDto(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("scope") String scope
) {}