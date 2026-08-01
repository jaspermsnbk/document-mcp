package com.jaspermsnbk.ai.basic_mcp.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfProcessingServiceTest {

    private PdfProcessingService service;

    @BeforeEach
    void setUp() {
        service = new PdfProcessingService();
    }

    // -----------------------------------------------------------------------
    // computeHash
    // -----------------------------------------------------------------------

    @Test
    void computeHash_sameBytesProduceSameHash() {
        byte[] data = "hello world".getBytes();
        assertEquals(service.computeHash(data), service.computeHash(data));
    }

    @Test
    void computeHash_differentBytesProduceDifferentHash() {
        byte[] a = "hello".getBytes();
        byte[] b = "world".getBytes();
        assertNotEquals(service.computeHash(a), service.computeHash(b));
    }

    @Test
    void computeHash_outputIs64CharHex() {
        byte[] data = "test data for sha256".getBytes();
        String hash = service.computeHash(data);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"), "Hash must be lowercase hex: " + hash);
    }

    // -----------------------------------------------------------------------
    // chunk
    // -----------------------------------------------------------------------

    @Test
    void chunk_emptyMapReturnsEmptyList() {
        List<PdfProcessingService.ChunkData> result = service.chunk(Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void chunk_singleShortPageProducesOneChunk() {
        Map<Integer, String> pages = new LinkedHashMap<>();
        pages.put(1, "Short text that fits in one chunk.");
        List<PdfProcessingService.ChunkData> result = service.chunk(pages);
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).chunkIndex());
        assertEquals(1, result.get(0).pageNumber());
        assertEquals("Short text that fits in one chunk.", result.get(0).text());
    }

    @Test
    void chunk_longPageProducesMultipleChunksWithOverlap() {
        // Build a page text of 2000 characters so we expect multiple chunks.
        // CHUNK_SIZE=1000, CHUNK_STEP=800 → first chunk [0,1000), second [800,1800), third [1600,2000)
        String pageText = "A".repeat(2000);
        Map<Integer, String> pages = new LinkedHashMap<>();
        pages.put(1, pageText);

        List<PdfProcessingService.ChunkData> result = service.chunk(pages);

        // Should have more than 1 chunk
        assertTrue(result.size() > 1, "Expected multiple chunks for 2000-char text");

        // Second chunk should start at offset 800, so first char of second == first char of text at index 800
        // Both use 'A' so content overlap is all 'A's — verify chunk size of second chunk
        String secondChunkText = result.get(1).text();
        // Second chunk covers [800, 1800), length = 1000
        assertEquals(1000, secondChunkText.length());

        // All chunks belong to page 1
        result.forEach(c -> assertEquals(1, c.pageNumber()));
    }

    @Test
    void chunk_lastPartialChunkIsNotDropped() {
        // 1850 chars: step=800 → offsets 0, 800, 1600; last chunk is [1600,1850) = 250 chars
        String pageText = "B".repeat(1850);
        Map<Integer, String> pages = new LinkedHashMap<>();
        pages.put(1, pageText);

        List<PdfProcessingService.ChunkData> result = service.chunk(pages);

        // Last chunk should be 250 chars (1850 - 1600)
        PdfProcessingService.ChunkData last = result.get(result.size() - 1);
        assertEquals(250, last.text().length());
    }

    @Test
    void chunk_emptyPagesAreSkipped() {
        Map<Integer, String> pages = new LinkedHashMap<>();
        pages.put(1, "   ");   // whitespace only → trims to empty
        pages.put(2, "Real content");

        List<PdfProcessingService.ChunkData> result = service.chunk(pages);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).pageNumber());
    }

    @Test
    void chunk_chunkIndexIncreasesAcrossPages() {
        Map<Integer, String> pages = new LinkedHashMap<>();
        pages.put(1, "First page text.");
        pages.put(2, "Second page text.");

        List<PdfProcessingService.ChunkData> result = service.chunk(pages);
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).chunkIndex());
        assertEquals(1, result.get(1).chunkIndex());
    }

    // -----------------------------------------------------------------------
    // extractPages
    // -----------------------------------------------------------------------

    @Test
    void extractPages_returnsCorrectPageCountAndNonEmptyText() throws IOException {
        byte[] pdfBytes = buildTwoPagePdf();

        Map<Integer, String> pages = service.extractPages(pdfBytes);

        assertEquals(2, pages.size(), "Should have 2 pages");
        assertTrue(pages.containsKey(1));
        assertTrue(pages.containsKey(2));
        assertFalse(pages.get(1).isBlank(), "Page 1 text should not be blank");
        assertFalse(pages.get(2).isBlank(), "Page 2 text should not be blank");
    }

    @Test
    void extractPages_singlePagePdf() throws IOException {
        byte[] pdfBytes = buildSinglePagePdf("Hello from page one");

        Map<Integer, String> pages = service.extractPages(pdfBytes);

        assertEquals(1, pages.size());
        assertTrue(pages.get(1).contains("Hello from page one"),
                "Extracted text should contain the page content");
    }

    // -----------------------------------------------------------------------
    // PDF helper builders
    // -----------------------------------------------------------------------

    private static byte[] buildTwoPagePdf() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addTextPage(doc, "Page one content here");
            addTextPage(doc, "Page two content here");
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] buildSinglePagePdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addTextPage(doc, text);
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void addTextPage(PDDocument doc, String text) throws IOException {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            cs.newLineAtOffset(50, 700);
            cs.showText(text);
            cs.endText();
        }
    }
}
