package com.resumetailor.backend.controller;

import com.resumetailor.backend.dto.ErrorResponse;
import com.resumetailor.backend.dto.TailorResponse;
import com.resumetailor.backend.service.AiService;
import com.resumetailor.backend.service.DocxGenerationService;
import com.resumetailor.backend.service.PdfGenerationService;
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

    private static final java.util.regex.Pattern JOB_DESCRIPTION_SIGNAL = java.util.regex.Pattern.compile(
            "\\b(role|responsibilit(?:y|ies)|requirements?|qualifications?|experience|skills?|developer|engineer|intern|position|work|build|design|develop|manage|team|candidate|knowledge|proficient|fresher)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private final ResumeExtractionService extractionService;
    private final AiService aiService;
    private final DocxGenerationService docxGenerationService;
    private final PdfGenerationService pdfGenerationService;

    public ResumeController(ResumeExtractionService extractionService,
                             AiService aiService,
                             DocxGenerationService docxGenerationService,
                             PdfGenerationService pdfGenerationService) {
        this.extractionService = extractionService;
        this.aiService = aiService;
        this.docxGenerationService = docxGenerationService;
        this.pdfGenerationService = pdfGenerationService;
    }

    @PostMapping(value = "/tailor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TailorResponse> tailor(
            @RequestParam("resume") MultipartFile resumeFile,
            @RequestParam("jobDescription") String jobDescription) throws Exception {

        if (!isMeaningfulJobDescription(jobDescription)) {
            throw new IllegalArgumentException(
                    "Please enter a meaningful job description with responsibilities, skills, experience, or role details.");
        }

        ResumeExtractionService.ExtractedResume extracted = extractionService.extractText(resumeFile);
        TailorResponse response = aiService.tailorResume(extracted.text(), jobDescription, extracted.isLatex());
        return ResponseEntity.ok(response);
    }

    private boolean isMeaningfulJobDescription(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return false;
        }
        String normalized = jobDescription.trim().replaceAll("\\s+", " ");
        int wordCount = normalized.split(" ").length;
        return normalized.length() >= 30 && wordCount >= 6 && JOB_DESCRIPTION_SIGNAL.matcher(normalized).find();
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

    @PostMapping(value = "/tailor/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> downloadPdf(
            @RequestPart("resume") MultipartFile originalResume,
            @RequestParam("tailoredResume") String tailoredResume) throws Exception {
        if (originalResume == null || originalResume.isEmpty()
                || originalResume.getOriginalFilename() == null
                || !originalResume.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("The original PDF resume is required.");
        }
        if (tailoredResume == null || tailoredResume.isBlank()) {
            throw new IllegalArgumentException("No tailored resume text provided.");
        }
        byte[] pdf = pdfGenerationService.generatePdf(originalResume.getBytes(), tailoredResume);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tailored-resume.pdf\"")
                .body(pdf);
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
