package com.dashboard.backend.rag.dto;

public record TrackInfo(
        String title,
        String artist,
        String genre,
        int count
) {}