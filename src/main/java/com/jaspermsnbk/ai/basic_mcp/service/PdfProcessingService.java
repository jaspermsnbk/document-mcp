package com.jaspermsnbk.ai.basic_mcp.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfProcessingService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_STEP = 800;

    public record ChunkData(int chunkIndex, int pageNumber, String text) {}

    public String computeHash(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Map<Integer, String> extractPages(byte[] bytes) throws IOException {
        Map<Integer, String> pages = new LinkedHashMap<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.put(i, stripper.getText(doc));
            }
        }
        return pages;
    }

    public List<ChunkData> chunk(Map<Integer, String> pages) {
        List<ChunkData> result = new ArrayList<>();
        int globalChunkIndex = 0;
        for (Map.Entry<Integer, String> entry : pages.entrySet()) {
            int pageNum = entry.getKey();
            String pageText = entry.getValue();
            for (int start = 0; start < pageText.length(); start += CHUNK_STEP) {
                int end = Math.min(start + CHUNK_SIZE, pageText.length());
                String chunk = pageText.substring(start, end).trim();
                if (!chunk.isEmpty()) {
                    result.add(new ChunkData(globalChunkIndex++, pageNum, chunk));
                }
                if (end >= pageText.length()) break;
            }
        }
        return result;
    }
}
