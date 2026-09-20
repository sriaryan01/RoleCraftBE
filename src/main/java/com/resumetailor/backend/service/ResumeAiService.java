package com.resumetailor.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.backend.dto.TailorResponse;

import java.util.ArrayList;
import java.util.List;

public abstract class ResumeAiService {

    protected final ObjectMapper mapper = new ObjectMapper();

    public abstract TailorResponse tailorResume(String resumeText, String jobDescription, boolean isLatex);

    protected String buildPrompt(String resumeText, String jobDescription, boolean isLatex) {
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
                - Treat the Skills section as a required tailoring target, not a copy-and-paste section.
                - First identify the job description's required and preferred technologies, tools, domains, and soft skills.
                - Compare those requirements with the original resume. Move supported matches to the beginning of the Skills section and use the job description's wording where it accurately describes an existing skill.
                - De-emphasize or move less relevant skills lower in the Skills section, but do not remove a skill if it is important to the original resume.
                - Never add a job-description skill just because the job asks for it. If the original resume does not support it, leave it out and mention the gap in the changes list.
                - Rewrite bullet points to use strong action verbs and, where the original supports it, quantify impact.
                - Mirror relevant keywords and phrasing from the job description naturally, only where truthful.
                - The tailored result must show visible changes when the job description emphasizes skills that are present in the original resume: reorder them, group them by relevance, or reflect them in the summary and experience bullets.
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

    protected TailorResponse parseTailorJson(String responseText, boolean isLatex) {
        String cleaned = responseText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }
        try {
            JsonNode node = mapper.readTree(cleaned);
            String tailoredResume = node.path("tailored_resume").asText();
            List<String> changes = new ArrayList<>();
            node.path("changes").forEach(change -> changes.add(change.asText()));
            if (tailoredResume.isBlank()) {
                throw new IllegalStateException("AI response was missing the tailored resume.");
            }
            return new TailorResponse(tailoredResume, changes, isLatex ? "latex" : "text");
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the AI response as JSON: " + e.getMessage(), e);
        }
    }
}