package com.resumetailor.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.backend.dto.TailorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String model;

    public GeminiService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://generativelanguage.googleapis.com/v1beta").build();
    }

    public TailorResponse tailorResume(String resumeText, String jobDescription, boolean isLatex) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set. Get a free key at https://aistudio.google.com/apikey and export it as an environment variable before starting the backend.");
        }

        String prompt = buildPrompt(resumeText, jobDescription, isLatex);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "responseMimeType", "application/json"
                )
        );

        String rawResponse = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        String geminiText = extractTextFromResponse(rawResponse);
        return parseTailorJson(geminiText, isLatex);
    }

    private String buildPrompt(String resumeText, String jobDescription, boolean isLatex) {
        String formatInstructions = isLatex
                ? """
                  - The original resume is written in LaTeX source. Your \
                  "tailored_resume" value MUST be complete, valid, compilable LaTeX \
                  source: keep the same \\documentclass, preamble, packages, and overall \
                  structure/commands as the original, editing only the content inside \
                  (text, \\item bullets, section ordering, emphasis). Do not convert it \
                  to plain text and do not drop any packages the document relies on.
                  """
                : """
                  - Format "tailored_resume" as clean Markdown so it can be rendered as \
                  a proper document: start with "# Full Name", then a single line with \
                  contact info (email, phone, location, links) separated by " · ", then \
                  "## Section Name" headings (e.g. Summary, Skills, Experience, \
                  Education, Projects — only sections that fit the original content), \
                  "- " bullet points for achievements/responsibilities, and "**bold**" \
                  for job titles/company names/degree names within a line. Do not use \
                  tables, images, or nested bullet lists.
                  """;

        return """
                You are an expert resume writer and career coach. Rewrite the given resume so it is tightly \
                tailored to the given job description, while staying strictly truthful to the facts already \
                present in the original resume. Do not invent employers, titles, dates, metrics, or skills \
                that are not supported by the original resume.

                Rules:
                - Reorder and re-emphasize existing experience/skills to match what the job description asks for.
                - Rewrite bullet points to use strong action verbs and, where the original supports it, quantify impact.
                - Mirror relevant keywords and phrasing from the job description naturally, only where truthful.
                %s\
                - Do not add a section that did not exist in some form in the original unless it is just a \
                reorganization (e.g. pulling out a Skills section from scattered mentions).
                - Keep total length comparable to the original (do not pad).

                Respond with ONLY a JSON object, no markdown fences, no preamble, matching exactly this shape:
                {"tailored_resume": "<the full rewritten resume as a single string, with \\n for line breaks>", \
                "changes": ["<short bullet describing one specific change and why, 3 to 6 of these>"]}

                --- ORIGINAL RESUME ---
                %s

                --- JOB DESCRIPTION ---
                %s
                """.formatted(formatInstructions, resumeText, jobDescription);
    }

    private String extractTextFromResponse(String rawResponse) {
        try {
            JsonNode root = mapper.readTree(rawResponse);

            JsonNode errorNode = root.path("error").path("message");
            if (!errorNode.isMissingNode()) {
                throw new IllegalStateException("Gemini API error: " + errorNode.asText());
            }

            JsonNode textNode = root.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                JsonNode finishReason = root.path("candidates").path(0).path("finishReason");
                throw new IllegalStateException(
                        "Gemini returned no text (finishReason: " + finishReason.asText("unknown") + ").");
            }
            return textNode.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gemini API response: " + e.getMessage(), e);
        }
    }

    private TailorResponse parseTailorJson(String geminiText, boolean isLatex) {
        String cleaned = geminiText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }
        try {
            JsonNode node = mapper.readTree(cleaned);
            String tailoredResume = node.path("tailored_resume").asText();
            List<String> changes = new ArrayList<>();
            node.path("changes").forEach(c -> changes.add(c.asText()));
            if (tailoredResume.isBlank()) {
                throw new IllegalStateException("Gemini response was missing the tailored resume.");
            }
            return new TailorResponse(tailoredResume, changes, isLatex ? "latex" : "text");
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Gemini's response as JSON: " + e.getMessage(), e);
        }
    }
}
