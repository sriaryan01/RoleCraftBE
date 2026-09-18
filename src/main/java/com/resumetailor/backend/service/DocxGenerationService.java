package com.resumetailor.backend.service;

import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocxGenerationService {

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    public byte[] generateDocx(String resumeText) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String[] lines = resumeText.split("\n", -1);
            boolean expectingContactLine = false;

            for (String rawLine : lines) {
                String line = rawLine.stripTrailing();

                if (line.isBlank()) {
                    document.createParagraph();
                    continue;
                }

                if (line.startsWith("# ")) {
                    XWPFParagraph p = document.createParagraph();
                    p.setAlignment(ParagraphAlignment.CENTER);
                    p.setSpacingAfter(60);
                    XWPFRun run = p.createRun();
                    run.setText(line.substring(2).trim());
                    run.setBold(true);
                    run.setFontSize(20);
                    run.setFontFamily("Calibri");
                    expectingContactLine = true;
                } else if (line.startsWith("## ")) {
                    XWPFParagraph p = document.createParagraph();
                    p.setSpacingBefore(200);
                    p.setSpacingAfter(80);
                    p.setBorderBottom(Borders.SINGLE);
                    XWPFRun run = p.createRun();
                    run.setText(line.substring(3).trim().toUpperCase());
                    run.setBold(true);
                    run.setFontSize(12);
                    run.setColor("223A5E");
                    run.setFontFamily("Calibri");
                    expectingContactLine = false;
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    XWPFParagraph p = document.createParagraph();
                    p.setIndentationLeft(300);
                    addInlineRuns(p, "\u2022 " + line.substring(2).trim(), 11);
                    expectingContactLine = false;
                } else if (expectingContactLine) {
                    XWPFParagraph p = document.createParagraph();
                    p.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun run = p.createRun();
                    run.setText(line.trim());
                    run.setFontSize(10);
                    run.setColor("57544C");
                    run.setFontFamily("Calibri");
                    expectingContactLine = false;
                } else {
                    XWPFParagraph p = document.createParagraph();
                    addInlineRuns(p, line.trim(), 11);
                }
            }

            document.write(out);
            return out.toByteArray();
        }
    }

    /** Splits a line on **bold** markers and writes alternating normal/bold runs. */
    private void addInlineRuns(XWPFParagraph paragraph, String text, int fontSize) {
        Matcher matcher = BOLD_PATTERN.matcher(text);
        int lastEnd = 0;
        boolean wroteAny = false;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                writeRun(paragraph, text.substring(lastEnd, matcher.start()), false, fontSize);
                wroteAny = true;
            }
            writeRun(paragraph, matcher.group(1), true, fontSize);
            wroteAny = true;
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length() || !wroteAny) {
            writeRun(paragraph, text.substring(lastEnd), false, fontSize);
        }
    }

    private void writeRun(XWPFParagraph paragraph, String text, boolean bold, int fontSize) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setFontFamily("Calibri");
    }
}
