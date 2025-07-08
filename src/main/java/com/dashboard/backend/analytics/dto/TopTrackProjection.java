package com.dashboard.backend.analytics.dto;

public interface TopTrackProjection {
    String getTrackId();
    String getTrackName();
    String getArtistNames(); // Chaîne concaténée d'artistes
    Long getPlayCount();
}
