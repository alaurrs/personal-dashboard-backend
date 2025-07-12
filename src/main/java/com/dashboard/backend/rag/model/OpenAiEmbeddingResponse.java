package com.dashboard.backend.rag.model;

import lombok.Getter;

import java.util.List;

@Getter
public class OpenAiEmbeddingResponse {

    private List<EmbeddingData> data;

    @Getter
    public static class EmbeddingData {
        private List<Float> embedding;
        private int index;
    }
}
