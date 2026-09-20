package com.resumetailor.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.resumetailor.backend.dto.TailorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
public class GroqService extends ResumeAiService {

    private final RestClient client;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.model:openai/gpt-oss-120b}")
    private String model;

    public GroqService(RestClient.Builder builder) {
        this.client = builder.baseUrl("https://api.groq.com/openai/v1").build();
    }

    @Override
    public TailorResponse tailorResume(String resumeText, String jobDescription, boolean isLatex) {
        requireApiKey();
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of(
            "role", "user",
            "content", buildPrompt(resumeText, jobDescription, isLatex))));
        body.put("temperature", 0.4);
        body.put("max_completion_tokens", 8192);
        if (!isLatex) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            String rawResponse = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseTailorJson(extractText(rawResponse), isLatex);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new IllegalStateException("Groq rate limit reached. Please try again later.");
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GROQ_API_KEY is not set. Add it to backend/.env or export it as an "
                            + "environment variable before starting the backend.");
        }
    }

    private String extractText(String rawResponse) {
        try {
            JsonNode root = mapper.readTree(rawResponse);
            JsonNode errorNode = root.path("error").path("message");
            if (!errorNode.isMissingNode()) {
                throw new IllegalStateException("Groq API error: " + errorNode.asText());
            }

            JsonNode textNode = root.path("choices").path(0).path("message").path("content");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                JsonNode finishReason = root.path("choices").path(0).path("finish_reason");
                throw new IllegalStateException(
                        "Groq returned no text (finishReason: " + finishReason.asText("unknown") + ").");
            }
            return textNode.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Groq API response: " + e.getMessage(), e);
        }
    }
}
