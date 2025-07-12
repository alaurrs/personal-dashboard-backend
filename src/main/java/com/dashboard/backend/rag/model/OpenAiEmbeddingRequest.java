package com.dashboard.backend.rag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OpenAiEmbeddingRequest {
    private String model;
    private List<String> input;

    public OpenAiEmbeddingRequest(String model, String input) {
        this.model = model;
        this.input = List.of(input);
    }
}
