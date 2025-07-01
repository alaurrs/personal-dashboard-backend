package com.dashboard.backend.thirdparty.spotify;

public record Followers(
        String href,
        int total
) {}