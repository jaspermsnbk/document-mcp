package com.jaspermsnbk.ai.basic_mcp.mcp;

import com.jaspermsnbk.ai.basic_mcp.domain.DocumentChunk;
import com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument;
import com.jaspermsnbk.ai.basic_mcp.dto.ChunkInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.DocumentInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.SearchResult;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentChunkRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import com.jaspermsnbk.ai.basic_mcp.service.DocumentSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentMcpToolsTest {

    @Mock private DocumentSearchService searchService;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;

    private DocumentMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new DocumentMcpTools(searchService, documentRepository, documentChunkRepository);
    }

    // -----------------------------------------------------------------------
    // searchDocuments — delegates to DocumentSearchService
    // -----------------------------------------------------------------------

    @Test
    void searchDocuments_delegatesToSearchService() {
        SearchResult result = new SearchResult("chunk text", "report.pdf", 3, 7, 0.85);
        when(searchService.search("machine learning", 3)).thenReturn(List.of(result));

        List<SearchResult> results = tools.searchDocuments("machine learning", 3);

        assertEquals(1, results.size());
        assertEquals("chunk text", results.get(0).content());
        verify(searchService).search("machine learning", 3);
    }

    @Test
    void searchDocuments_nullLimit_defaultsTo5() {
        when(searchService.search(any(), eq(5))).thenReturn(List.of());

        tools.searchDocuments("query", null);

        verify(searchService).search("query", 5);
    }

    @Test
    void searchDocuments_explicitLimit_passedThrough() {
        when(searchService.search(any(), eq(10))).thenReturn(List.of());

        tools.searchDocuments("query", 10);

        verify(searchService).search("query", 10);
    }

    // -----------------------------------------------------------------------
    // listDocuments
    // -----------------------------------------------------------------------

    @Test
    void listDocuments_mapsDocumentInfoFieldsCorrectly() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        PdfDocument doc = new PdfDocument(5L, "annual-report.pdf", "hash123", 12, 204800L, now);
        when(documentRepository.findAll()).thenReturn(List.of(doc));

        List<DocumentInfo> result = tools.listDocuments();

        assertEquals(1, result.size());
        DocumentInfo info = result.get(0);
        assertEquals(5L, info.id());
        assertEquals("annual-report.pdf", info.filename());
        assertEquals(12, info.pageCount());
        assertEquals(204800L, info.fileSizeBytes());
        assertEquals(now, info.ingestedAt());
    }

    @Test
    void listDocuments_emptyRepository_returnsEmptyList() {
        when(documentRepository.findAll()).thenReturn(List.of());

        assertTrue(tools.listDocuments().isEmpty());
    }

    @Test
    void listDocuments_multipleDocuments_allMapped() {
        Instant now = Instant.now();
        when(documentRepository.findAll()).thenReturn(List.of(
            new PdfDocument(1L, "a.pdf", "h1", 1, 100L, now),
            new PdfDocument(2L, "b.pdf", "h2", 2, 200L, now),
            new PdfDocument(3L, "c.pdf", "h3", 3, 300L, now)
        ));

        List<DocumentInfo> result = tools.listDocuments();

        assertEquals(3, result.size());
        assertEquals("b.pdf", result.get(1).filename());
    }

    // -----------------------------------------------------------------------
    // getDocumentChunks
    // -----------------------------------------------------------------------

    @Test
    void getDocumentChunks_mapsChunkInfoFieldsCorrectly() {
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndex(5L)).thenReturn(List.of(
            new DocumentChunk(10L, 5L, 0, 1, "First chunk text"),
            new DocumentChunk(11L, 5L, 1, 1, "Second chunk text")
        ));

        List<ChunkInfo> result = tools.getDocumentChunks(5L);

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).chunkIndex());
        assertEquals("First chunk text", result.get(0).content());
        assertEquals(1, result.get(1).chunkIndex());
        assertEquals("Second chunk text", result.get(1).content());
    }

    @Test
    void getDocumentChunks_noChunks_returnsEmptyList() {
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndex(99L)).thenReturn(List.of());

        assertTrue(tools.getDocumentChunks(99L).isEmpty());
    }

    @Test
    void getDocumentChunks_passesDocumentIdToRepository() {
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndex(42L)).thenReturn(List.of());

        tools.getDocumentChunks(42L);

        verify(documentChunkRepository).findByDocumentIdOrderByChunkIndex(42L);
    }
}
