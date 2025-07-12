package com.dashboard.backend.thirdparty.openai.service;

import com.dashboard.backend.rag.model.AnswerResponse;
import com.dashboard.backend.rag.model.OpenAiEmbeddingRequest;
import com.dashboard.backend.rag.model.OpenAiEmbeddingResponse;
import com.dashboard.backend.thirdparty.openai.model.OpenAiChatRequest;
import com.dashboard.backend.thirdparty.openai.model.OpenAiChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
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

    public AnswerResponse askWithContext(List<String> docs, String question) {
        String context = docs.stream().collect(Collectors.joining("\n\n"));
        String prompt = """
        Voici les données personnelles de l'utilisateur :

        %s

        Question : %s

        Nous sommes actuellement en juillet 2025. Si la question fait référence à "ce mois-ci", "ce mois", ou "actuellement", cela correspond à juillet 2025.

        Réponds de façon claire et concise. Formate ta réponse sous la forme d'un json comme ceci s'il s'agit d'une question concernant de près ou de loin des données musicales (Spotify...):
   
                    {
                      "summary": "Voici un résumé de tes habitudes d'écoute pour la période demandée...",
                      "topTracks": [ { "title": "Nom du morceau", "artist": "Nom de l'artiste", "genre": "pop", "count": 12 }, ... ],
                      "genres": [ { "name": "pop", "percentage": 45.3 }, { "name": "rock", "percentage": 23.7 }, ... ],
                      "period": "Juillet 2025"
                    }
                    
        Si la question ne concerne pas les données musicales, réponds simplement avec un texte clair et concis.
        Toutefois, si la question concerne les données musicales, retourne uniquement un objet JSON valide. N'ajoute aucun commentaire ni texte supplémentaire.
        
        INSTRUCTIONS IMPORTANTES :
        - Pour les topTracks : utilise les données exactes des tracks avec leurs compteurs d'écoute
        - Pour les genres : calcule les pourcentages basés sur le nombre total de tracks de chaque genre
        - Pour la période : utilise le format "Mois Année" (ex: "Juillet 2025")
        - Si les données concernent plusieurs mois, indique la plage (ex: "Avril - Juin 2025")
        - Le champ "genre" dans topTracks doit contenir le genre principal du morceau
        """.formatted(String.join("\n", context), question);

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

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(responseJson, AnswerResponse.class);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors du parsing JSON de la réponse IA", e);
        }
    }
}
