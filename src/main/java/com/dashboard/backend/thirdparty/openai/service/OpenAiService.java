package com.dashboard.backend.thirdparty.openai.service;

import com.dashboard.backend.rag.model.OpenAiEmbeddingRequest;
import com.dashboard.backend.rag.model.OpenAiEmbeddingResponse;
import com.dashboard.backend.thirdparty.openai.model.OpenAiChatRequest;
import com.dashboard.backend.thirdparty.openai.model.OpenAiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final RestTemplate restTemplate;
    @Value("${openai.api-key}")
    private String apiKey;

    public float[] getEmbedding(String input) {
        String url = "https://api.openai.com/v1/embeddings";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest("text-embedding-ada-002", input);
        HttpEntity<OpenAiEmbeddingRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<OpenAiEmbeddingResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, OpenAiEmbeddingResponse.class
        );

        List<Float> list = response.getBody().getData().get(0).getEmbedding();
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    public String askWithContext(List<String> docs, String question) {
        String context = docs.stream().collect(Collectors.joining("\n\n"));
        String prompt = String.format("""
        Voici les données personnelles de l'utilisateur :

        %s

        Question : %s

        Réponds de façon claire et concise.
        """, context, question);

        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        OpenAiChatRequest request = new OpenAiChatRequest("gpt-3.5-turbo", prompt);
        HttpEntity<OpenAiChatRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<OpenAiChatResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, OpenAiChatResponse.class
        );

        return response.getBody().getChoices().get(0).getMessage().getContent();
    }
}
