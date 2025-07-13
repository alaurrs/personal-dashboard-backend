package com.dashboard.backend.rag.dto;

import com.dashboard.backend.User.model.Track;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record InsightResponse(
        String summary,
        Optional<List<TrackInfo>> topTracks,
        Optional<List<GenreInfo>> genres,
        Optional<String> period
) {
    public InsightResponse(String summary) {
        this(summary, Optional.empty(), Optional.empty(), Optional.empty());
    }
}