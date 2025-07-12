package com.dashboard.backend.thirdparty.openai.model;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class OpenAiChatRequest {
    private final String model;
    private final List<Map<String, String>> messages;
    private final double temperature;

    public OpenAiChatRequest(String model, String prompt) {
        this.model = model;
        this.temperature = 0.7;
        this.messages = List.of(
                Map.of("role", "system", "content", "Tu es un assistant qui répond à des questions sur les données personnelles de l’utilisateur."),
                Map.of("role", "user", "content", prompt)
        );
    }
}
