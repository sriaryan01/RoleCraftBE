package com.resumetailor.backend.controller;

import com.resumetailor.backend.dto.ErrorResponse;
import com.resumetailor.backend.dto.TailorResponse;
import com.resumetailor.backend.service.GeminiService;
import com.resumetailor.backend.service.DocxGenerationService;
import com.resumetailor.backend.service.ResumeExtractionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResumeController {

    private final ResumeExtractionService extractionService;
    private final GeminiService geminiService;
    private final DocxGenerationService docxGenerationService;

    public ResumeController(ResumeExtractionService extractionService,
                             GeminiService geminiService,
                             DocxGenerationService docxGenerationService) {
        this.extractionService = extractionService;
        this.geminiService = geminiService;
        this.docxGenerationService = docxGenerationService;
    }

    @PostMapping(value = "/tailor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TailorResponse> tailor(
            @RequestParam("resume") MultipartFile resumeFile,
            @RequestParam("jobDescription") String jobDescription) throws Exception {

        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description is required.");
        }

        ResumeExtractionService.ExtractedResume extracted = extractionService.extractText(resumeFile);
        TailorResponse response = geminiService.tailorResume(extracted.text(), jobDescription, extracted.isLatex());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tailor/docx")
    public ResponseEntity<byte[]> downloadDocx(@RequestBody Map<String, String> body) throws Exception {
        String text = body.get("tailoredResume");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No tailored resume text provided.");
        }
        byte[] docx = docxGenerationService.generateDocx(text);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tailored-resume.docx\"")
                .body(docx);
    }

    @PostMapping("/tailor/tex")
    public ResponseEntity<byte[]> downloadTex(@RequestBody Map<String, String> body) {
        String text = body.get("tailoredResume");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No tailored resume text provided.");
        }
        byte[] tex = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-tex"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tailored-resume.tex\"")
                .body(tex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleServerError(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Unexpected error: " + e.getMessage()));
    }
}
