package com.dashboard.backend.rag.model;

import java.util.List;

public record AnswerResponse(
        String summary,
        List<TrackStat> topTracks,
        List<GenreStat> genres,
        String period) {
}

record TrackStat(String title, String artist, String genre, int count) {
}

record GenreStat(String name, double percentage) {
}
