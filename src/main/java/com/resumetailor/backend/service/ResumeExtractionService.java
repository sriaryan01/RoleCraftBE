package com.resumetailor.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class ResumeExtractionService {

    private static final long MAX_FILE_SIZE_BYTES = 8L * 1024 * 1024; // 8 MB

    public ExtractedResume extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File is too large (max 8 MB).");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);

        String text;
        boolean isLatex = false;
        if (filename.endsWith(".pdf")) {
            text = extractPdf(file);
        } else if (filename.endsWith(".docx")) {
            text = extractDocx(file);
        } else if (filename.endsWith(".tex")) {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
            isLatex = true;
        } else if (filename.endsWith(".txt") || filename.endsWith(".md")) {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unsupported file type. Please upload a .pdf, .docx, .tex, or .txt resume.");
        }

        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Could not find any readable text in the uploaded file.");
        }

        // A .txt/.md file can still contain raw LaTeX source — detect it either way.
        if (!isLatex && looksLikeLatex(text)) {
            isLatex = true;
        }

        return new ExtractedResume(text, isLatex);
    }

    private boolean looksLikeLatex(String text) {
        return text.contains("\\documentclass") || text.contains("\\begin{document}");
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream(); PDDocument document = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream(); XWPFDocument document = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                sb.append(paragraph.getText()).append("\n");
            }
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row ->
                        row.getTableCells().forEach(cell -> sb.append(cell.getText()).append(" ")));
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public record ExtractedResume(String text, boolean isLatex) {
    }
}
