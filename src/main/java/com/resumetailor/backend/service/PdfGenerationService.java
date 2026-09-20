package com.resumetailor.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfGenerationService {

    private static final float MARGIN = 46;
    private static final float BODY_SIZE = 10.5f;
    private static final float LINE_HEIGHT = 15;

    public byte[] generatePdf(byte[] originalPdf, String resumeText) throws IOException {
        try (PDDocument source = Loader.loadPDF(originalPdf);
             PDDocument output = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDRectangle pageSize = source.getNumberOfPages() > 0
                    ? source.getPage(0).getMediaBox()
                    : PDRectangle.A4;
            List<RenderLine> lines = renderLines(resumeText, pageSize.getWidth() - (MARGIN * 2));
            PDPage page = new PDPage(pageSize);
            output.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(output, page);
            float y = pageSize.getHeight() - MARGIN;

            for (RenderLine line : lines) {
                if (y < MARGIN) {
                    stream.close();
                    page = new PDPage(pageSize);
                    output.addPage(page);
                    stream = new PDPageContentStream(output, page);
                    y = pageSize.getHeight() - MARGIN;
                }
                stream.beginText();
                stream.setFont(line.font(), line.size());
                stream.setLeading(LINE_HEIGHT);
                stream.newLineAtOffset(MARGIN + line.indent(), y);
                stream.showText(toPdfText(line.text()));
                stream.endText();
                y -= line.spacingAfter();
            }
            stream.close();
            output.save(out);
            return out.toByteArray();
        }
    }

    private List<RenderLine> renderLines(String resumeText, float maxWidth) throws IOException {
        List<RenderLine> lines = new ArrayList<>();
        PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        for (String rawLine : resumeText.replace("\r", "").split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                lines.add(new RenderLine(" ", regular, BODY_SIZE, 8, 0));
                continue;
            }

            boolean heading = line.startsWith("#");
            boolean bullet = line.startsWith("- ") || line.startsWith("* ");
            String text = line.replaceFirst("^#{1,6}\\s*", "");
            if (bullet) {
                text = "• " + text.substring(2).trim();
            }

            PDFont font = heading ? bold : regular;
            float size = heading ? (line.startsWith("# ") ? 17 : 12) : BODY_SIZE;
            float indent = bullet ? 10 : 0;
            float spacing = heading ? 21 : LINE_HEIGHT;
            lines.addAll(wrap(text, font, size, maxWidth - indent, indent, spacing));
        }
        return lines;
    }

    private List<RenderLine> wrap(String text, PDFont font, float size, float maxWidth,
                                  float indent, float spacing) throws IOException {
        List<RenderLine> wrapped = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && width(candidate, font, size) > maxWidth) {
                wrapped.add(new RenderLine(current.toString(), font, size, spacing, indent));
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            wrapped.add(new RenderLine(current.toString(), font, size, spacing, indent));
        }
        return wrapped;
    }

    private float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(toPdfText(text)) / 1000 * size;
    }

    private String toPdfText(String text) {
        return text.replace("**", "").replaceAll("[^\\x20-\\x7E]", "?");
    }

    private record RenderLine(String text, PDFont font, float size, float spacingAfter, float indent) {
    }
}