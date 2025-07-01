package com.dashboard.backend.thirdparty.spotify;

public record ExternalIds(
        String isrc,
        String ean,
        String upc
) {}