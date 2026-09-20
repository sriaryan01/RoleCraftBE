[1mdiff --git a/src/main/java/com/resumetailor/backend/config/CorsConfig.java b/src/main/java/com/resumetailor/backend/config/CorsConfig.java[m
[1mindex a3cdc6e..cf7f180 100644[m
[1m--- a/src/main/java/com/resumetailor/backend/config/CorsConfig.java[m
[1m+++ b/src/main/java/com/resumetailor/backend/config/CorsConfig.java[m
[36m@@ -11,7 +11,7 @@[m [mimport java.util.Arrays;[m
 @Configuration[m
 public class CorsConfig {[m
 [m
[31m-    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:5175,https://role-craft-bfpwswu49-sriaryan01s-projects.vercel.app}")[m
[32m+[m[32m    @Value("${app.cors.allowed-origins:http://localhost:5174,http://localhost:5175,https://role-craft-bfpwswu49-sriaryan01s-projects.vercel.app,http://localhost:5173}")[m
     private String allowedOrigins;[m
 [m
     @Bean[m
[1mdiff --git a/src/main/java/com/resumetailor/backend/controller/ResumeController.java b/src/main/java/com/resumetailor/backend/controller/ResumeController.java[m
[1mindex d58bf41..2dab76d 100644[m
[1m--- a/src/main/java/com/resumetailor/backend/controller/ResumeController.java[m
[1m+++ b/src/main/java/com/resumetailor/backend/controller/ResumeController.java[m
[36m@@ -2,8 +2,9 @@[m [mpackage com.resumetailor.backend.controller;[m
 [m
 import com.resumetailor.backend.dto.ErrorResponse;[m
 import com.resumetailor.backend.dto.TailorResponse;[m
[31m-import com.resumetailor.backend.service.GeminiService;[m
[32m+[m[32mimport com.resumetailor.backend.service.AiService;[m
 import com.resumetailor.backend.service.DocxGenerationService;[m
[32m+[m[32mimport com.resumetailor.backend.service.PdfGenerationService;[m
 import com.resumetailor.backend.service.ResumeExtractionService;[m
 import org.springframework.http.HttpHeaders;[m
 import org.springframework.http.HttpStatus;[m
[36m@@ -23,15 +24,18 @@[m [mpublic class ResumeController {[m
             java.util.regex.Pattern.CASE_INSENSITIVE);[m
 [m
     private final ResumeExtractionService extractionService;[m
[31m-    private final GeminiService geminiService;[m
[32m+[m[32m    private final AiService aiService;[m
     private final DocxGenerationService docxGenerationService;[m
[32m+[m[32m    private final PdfGenerationService pdfGenerationService;[m
 [m
     public ResumeController(ResumeExtractionService extractionService,[m
[31m-                             GeminiService geminiService,[m
[31m-                             DocxGenerationService docxGenerationService) {[m
[32m+[m[32m                             AiService aiService,[m
[32m+[m[32m                             DocxGenerationService docxGenerationService,[m
[32m+[m[32m                             PdfGenerationService pdfGenerationService) {[m
         this.extractionService = extractionService;[m
[31m-        this.geminiService = geminiService;[m
[32m+[m[32m        this.aiService = aiService;[m
         this.docxGenerationService = docxGenerationService;[m
[32m+[m[32m        this.pdfGenerationService = pdfGenerationService;[m
     }[m
 [m
     @PostMapping(value = "/tailor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)[m
[36m@@ -45,7 +49,7 @@[m [mpublic class ResumeController {[m
         }[m
 [m
         ResumeExtractionService.ExtractedResume extracted = extractionService.extractText(resumeFile);[m
[31m-        TailorResponse response = geminiService.tailorResume(extracted.text(), jobDescription, extracted.isLatex());[m
[32m+[m[32m        TailorResponse response = aiService.tailorResume(extracted.text(), jobDescription, extracted.isLatex());[m
         return ResponseEntity.ok(response);[m
     }[m
 [m
[36m@@ -86,6 +90,25 @@[m [mpublic class ResumeController {[m
                 .body(tex);[m
     }[m
 [m
[32m+[m[32m    @PostMapping(value = "/tailor/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)[m
[32m+[m[32m    public ResponseEntity<byte[]> downloadPdf([m
[32m+[m[32m            @RequestPart("resume") MultipartFile originalResume,[m
[32m+[m[32m            @RequestParam("tailoredResume") String tailoredResume) throws Exception {[m
[32m+[m[32m        if (originalResume == null || originalResume.isEmpty()[m
[32m+[m[32m                || originalResume.getOriginalFilename() == null[m
[32m+[m[32m                || !originalResume.getOriginalFilename().toLowerCase().endsWith(".pdf")) {[m
[32m+[m[32m            throw new IllegalArgumentException("The original PDF resume is required.");[m
[32m+[m[32m        }[m
[32m+[m[32m        if (tailoredResume == null || tailoredResume.isBlank()) {[m
[32m+[m[32m            throw new IllegalArgumentException("No tailored resume text provided.");[m
[32m+[m[32m        }[m
[32m+[m[32m        byte[] pdf = pdfGenerationService.generatePdf(originalResume.getBytes(), tailoredResume);[m
[32m+[m[32m        return ResponseEntity.ok()[m
[32m+[m[32m                .contentType(MediaType.APPLICATION_PDF)[m
[32m+[m[32m                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tailored-resume.pdf\"")[m
[32m+[m[32m                .body(pdf);[m
[32m+[m[32m    }[m
[32m+[m
     @ExceptionHandler(IllegalArgumentException.class)[m
     public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {[m
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));[m
[1mdiff --git a/src/main/java/com/resumetailor/backend/service/AiService.java b/src/main/java/com/resumetailor/backend/service/AiService.java[m
[1mnew file mode 100644[m
[1mindex 0000000..8322529[m
[1m--- /dev/null[m
[1m+++ b/src/main/java/com/resumetailor/backend/service/AiService.java[m
[36m@@ -0,0 +1,30 @@[m
[32m+[m[32mpackage com.resumetailor.backend.service;[m
[32m+[m
[32m+[m[32mimport com.resumetailor.backend.dto.TailorResponse;[m
[32m+[m[32mimport org.springframework.beans.factory.annotation.Value;[m
[32m+[m[32mimport org.springframework.stereotype.Service;[m
[32m+[m
[32m+[m[32m@Service[m
[32m+[m[32mpublic class AiService {[m
[32m+[m
[32m+[m[32m    private final GeminiService geminiService;[m
[32m+[m[32m    private final GroqService groqService;[m
[32m+[m
[32m+[m[32m    @Value("${ai.provider:groq}")[m
[32m+[m[32m    private String provider;[m
[32m+[m
[32m+[m[32m    public AiService(GeminiService geminiService, GroqService groqService) {[m
[32m+[m[32m        this.geminiService = geminiService;[m
[32m+[m[32m        this.groqService = groqService;[m
[32m+[m[32m    }[m
[32m+[m
[32m+[m[32m    public TailorResponse tailorResume(String resumeText, String jobDescription, boolean isLatex) {[m
[32m+[m[32m        if ("groq".equalsIgnoreCase(provider)) {[m
[32m+[m[32m            return groqService.tailorResume(resumeText, jobDescription, isLatex);[m
[32m+[m[32m        }[m
[32m+[m[32m        if ("gemini".equalsIgnoreCase(provider)) {[m
[32m+[m[32m            return geminiService.tailorResume(resumeText, jobDescription, isLatex);[m
[32m+[m[32m        }[m
[32m+[m[32m        throw new IllegalStateException("Unsupported AI_PROVIDER: " + provider + ". Use gemini or groq.");[m
[32m+[m[32m    }[m
[32m+[m[32m}[m
\ No newline at end of file[m
[1mdiff --git a/src/main/java/com/resumetailor/backend/service/GeminiService.java b/src/main/java/com/resumetailor/backend/service/GeminiService.java[m
[1mindex 9c3d92a..7695e4c 100644[m
[1m--- a/src/main/java/com/resumetailor/backend/service/GeminiService.java[m
[1m+++ b/src/main/java/com/resumetailor/backend/service/GeminiService.java[m
[36m@@ -1,45 +1,37 @@[m
 package com.resumetailor.backend.service;[m
 [m
 import com.fasterxml.jackson.databind.JsonNode;[m
[31m-import com.fasterxml.jackson.databind.ObjectMapper;[m
 import com.resumetailor.backend.dto.TailorResponse;[m
 import org.springframework.beans.factory.annotation.Value;[m
 import org.springframework.http.MediaType;[m
[31m-import org.springframework.web.client.HttpClientErrorException;[m
 import org.springframework.stereotype.S