package com.dashboard.backend.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class TopTrackDto {

    private String trackId;
    private String trackName;
    private List<String> artistNames;
    private Long playCount;
}
