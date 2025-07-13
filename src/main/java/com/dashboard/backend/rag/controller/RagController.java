package com.dashboard.backend.rag.controller;

import com.dashboard.backend.rag.dto.InsightResponse;
import com.dashboard.backend.rag.model.AnswerResponse;
import com.dashboard.backend.rag.model.AskRequest;
import com.dashboard.backend.rag.model.QuestionRequest;
import com.dashboard.backend.User.model.User;
import com.dashboard.backend.rag.model.RagResponse;
import com.dashboard.backend.rag.service.RagService;
import com.dashboard.backend.security.UserPrincipal;
import com.dashboard.backend.thirdparty.openai.service.OpenAiService;
import com.dashboard.backend.thirdparty.supabase.service.SupabaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final OpenAiService openAiService;
    private final SupabaseService supabaseService;
    private final RagService ragService;

    @PostMapping("/ask")
    public ResponseEntity<AnswerResponse> ask(@RequestBody QuestionRequest questionRequest,
                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String question = questionRequest.getQuestion();

        float[] embedding = openAiService.getEmbedding(question);

        List<String> docs = supabaseService.queryRelevantDocuments(user.getId(), embedding);

        AnswerResponse answer = openAiService.askWithContext(docs, question);

        return ResponseEntity.ok(answer);
    }

    @PostMapping("/ask-v2")
    public ResponseEntity<InsightResponse> askV2(@RequestBody AskRequest askRequest, Authentication authentication) {
        User user = (User) authentication.getPrincipal();

            return ResponseEntity.ok(ragService.askQuestion(user, askRequest.question()));

    }
}
