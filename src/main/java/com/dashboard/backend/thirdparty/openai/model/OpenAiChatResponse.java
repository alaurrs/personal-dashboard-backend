package com.dashboard.backend.thirdparty.openai.model;

import lombok.Getter;

import java.util.List;

@Getter
public class OpenAiChatResponse {
    private List<Choice> choices;

    @Getter
    public static class Choice {
        private Message message;
    }

    @Getter
    public static class Message {
        private String role;
        private String content;
    }
}
