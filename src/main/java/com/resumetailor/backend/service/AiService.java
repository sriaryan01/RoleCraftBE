package com.resumetailor.backend.service;

import com.resumetailor.backend.dto.TailorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final GeminiService geminiService;
    private final GroqService groqService;

    @Value("${ai.provider:groq}")
    private String provider;

    public AiService(GeminiService geminiService, GroqService groqService) {
        this.geminiService = geminiService;
        this.groqService = groqService;
    }

    public TailorResponse tailorResume(String resumeText, String jobDescription, boolean isLatex) {
        if ("groq".equalsIgnoreCase(provider)) {
            return groqService.tailorResume(resumeText, jobDescription, isLatex);
        }
        if ("gemini".equalsIgnoreCase(provider)) {
            return geminiService.tailorResume(resumeText, jobDescription, isLatex);
        }
        throw new IllegalStateException("Unsupported AI_PROVIDER: " + provider + ". Use gemini or groq.");
    }
}