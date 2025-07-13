package com.dashboard.backend.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GenreInfo(
        String name,
        @JsonProperty("percentage") Double percentage
) {}