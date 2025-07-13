package com.dashboard.backend.thirdparty.openai.service;

import com.dashboard.backend.rag.model.AnswerResponse;
import com.dashboard.backend.rag.model.OpenAiEmbeddingRequest;
import com.dashboard.backend.rag.model.OpenAiEmbeddingResponse;
import com.dashboard.backend.thirdparty.openai.model.OpenAiChatRequest;
import com.dashboard.backend.thirdparty.openai.model.OpenAiChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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

    public AnswerResponse askWithContext(List<String> docs, String question) {
        String context = docs.stream().collect(Collectors.joining("\n\n"));

        // Lire le prompt depuis le fichier prompt.txt
        String promptTemplate = loadPromptTemplate();
        String prompt = promptTemplate.formatted(context, question);

        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        OpenAiChatRequest request = new OpenAiChatRequest("gpt-3.5-turbo", prompt);
        HttpEntity<OpenAiChatRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<OpenAiChatResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, OpenAiChatResponse.class
        );

        // Convert response to JSON string
        String responseJson = response.getBody().getChoices().get(0).getMessage().getContent();
        log.info("Réponse brute de l'IA : {}", responseJson);

        try {
            // Nettoyer le JSON avant parsing
            String cleanedJson = cleanJsonResponse(responseJson);
            log.info("JSON nettoyé : {}", cleanedJson);

            ObjectMapper mapper = new ObjectMapper();
            AnswerResponse result = mapper.readValue(cleanedJson, AnswerResponse.class);

            // Valider que la réponse est cohérente
            validateResponse(result);

            return result;
        } catch (IOException e) {
            log.error("Erreur lors du parsing JSON. JSON original: {}", responseJson, e);
            throw new RuntimeException("Erreur lors du parsing JSON de la réponse IA", e);
        }
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("static/prompt.txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier prompt.txt", e);
            throw new RuntimeException("Impossible de charger le template de prompt", e);
        }
    }

    private String cleanJsonResponse(String jsonResponse) {
        // Supprimer les espaces en début et fin
        String cleaned = jsonResponse.trim();

        // Supprimer les virgules en fin d'objet ou de tableau
        cleaned = cleaned.replaceAll(",\\s*}", "}");
        cleaned = cleaned.replaceAll(",\\s*]", "]");

        // S'assurer que la réponse commence et finit par des accolades
        if (!cleaned.startsWith("{")) {
            int startIndex = cleaned.indexOf("{");
            if (startIndex != -1) {
                cleaned = cleaned.substring(startIndex);
            }
        }

        if (!cleaned.endsWith("}")) {
            int endIndex = cleaned.lastIndexOf("}");
            if (endIndex != -1) {
                cleaned = cleaned.substring(0, endIndex + 1);
            }
        }

        return cleaned;
    }

    private void validateResponse(AnswerResponse response) {
        // Vérifier qu'il n'y a pas de champs null
        if (response.summary() == null) {
            throw new RuntimeException("Réponse IA invalide : summary est null");
        }

        // Si topTracks est présent, vérifier qu'il n'est pas null et que les autres champs requis sont aussi présents
        if (response.topTracks() != null) {
            if (response.period() == null) {
                throw new RuntimeException("Réponse IA invalide : topTracks présent mais period est null");
            }
            // Si c'est un format détaillé mais que les champs sont vides, c'est aussi invalide
            if (response.topTracks().isEmpty() && response.genres().isEmpty()) {
                throw new RuntimeException("Réponse IA invalide : format détaillé avec champs vides - devrait utiliser format simple");
            }
        }

        // Vérifier que si genres est présent, topTracks et period le sont aussi
        if (response.genres() != null && (response.topTracks() == null || response.period() == null)) {
            throw new RuntimeException("Réponse IA invalide : genres présent mais topTracks ou period manquant");
        }

        // Vérifier que si period est présent, topTracks et genres le sont aussi
        if (response.period() != null && (response.topTracks() == null)) {
            throw new RuntimeException("Réponse IA invalide : period présent mais topTracks ou genres manquant");
        }

        log.info("Validation réussie : réponse IA cohérente");
    }
}
