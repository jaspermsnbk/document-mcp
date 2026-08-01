package com.jaspermsnbk.ai.basic_mcp.mcp;

import com.jaspermsnbk.ai.basic_mcp.domain.DocumentChunk;
import com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument;
import com.jaspermsnbk.ai.basic_mcp.dto.ChunkInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.DocumentInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.SearchResult;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentChunkRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentMcpToolsTest {

    @Mock private VectorStore vectorStore;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;

    private DocumentMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new DocumentMcpTools(vectorStore, documentRepository, documentChunkRepository);
    }

    // -----------------------------------------------------------------------
    // searchDocuments — happy path with metadata
    // -----------------------------------------------------------------------

    @Test
    void searchDocuments_mapsResultFieldsCorrectly() {
        Document aiDoc = Document.builder()
            .text("Relevant chunk text")
            .metadata("filename", "report.pdf")
            .metadata("page_number", "3")
            .metadata("chunk_index", "7")
            .metadata("distance", 0.85)
            .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(aiDoc));

        List<SearchResult> results = tools.searchDocuments("machine learning", 3);

        assertEquals(1, results.size());
        SearchResult r = results.get(0);
        assertEquals("Relevant chunk text", r.content());
        assertEquals("report.pdf", r.filename());
        assertEquals(3, r.pageNumber());
        assertEquals(7, r.chunkIndex());
        assertEquals(0.85, r.score(), 1e-9);
    }

    @Test
    void searchDocuments_metadataWithStringNumbers_parsedToInt() {
        Document aiDoc = Document.builder()
            .text("Another chunk")
            .metadata("filename", "doc.pdf")
            .metadata("page_number", "10")   // String representation
            .metadata("chunk_index", "42")   // String representation
            .metadata("distance", 0.5)
            .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(aiDoc));

        List<SearchResult> results = tools.searchDocuments("query", 5);
        assertEquals(10, results.get(0).pageNumber());
        assertEquals(42, results.get(0).chunkIndex());
    }

    @Test
    void searchDocuments_missingMetadata_defaultsToZeroAndEmptyString() {
        Document aiDoc = Document.builder()
            .text("Chunk with no metadata")
            .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(aiDoc));

        List<SearchResult> results = tools.searchDocuments("query", 5);
        assertEquals(1, results.size());
        SearchResult r = results.get(0);
        assertEquals("", r.filename());
        assertEquals(0, r.pageNumber());
        assertEquals(0, r.chunkIndex());
        assertEquals(0.0, r.score(), 1e-9);
    }

    // -----------------------------------------------------------------------
    // searchDocuments — null limit defaults to topK=5
    // -----------------------------------------------------------------------

    @Test
    void searchDocuments_nullLimit_defaultsToTopK5() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        tools.searchDocuments("some query", null);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals(5, captor.getValue().getTopK());
    }

    @Test
    void searchDocuments_explicitLimit_usedAsTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        tools.searchDocuments("query", 10);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals(10, captor.getValue().getTopK());
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

        List<DocumentInfo> result = tools.listDocuments();

        assertTrue(result.isEmpty());
    }

    @Test
    void listDocuments_multipleDocuments_allMapped() {
        Instant now = Instant.now();
        PdfDocument d1 = new PdfDocument(1L, "a.pdf", "h1", 1, 100L, now);
        PdfDocument d2 = new PdfDocument(2L, "b.pdf", "h2", 2, 200L, now);
        PdfDocument d3 = new PdfDocument(3L, "c.pdf", "h3", 3, 300L, now);
        when(documentRepository.findAll()).thenReturn(List.of(d1, d2, d3));

        List<DocumentInfo> result = tools.listDocuments();

        assertEquals(3, result.size());
        assertEquals("b.pdf", result.get(1).filename());
    }

    // -----------------------------------------------------------------------
    // getDocumentChunks
    // -----------------------------------------------------------------------

    @Test
    void getDocumentChunks_mapsChunkInfoFieldsCorrectly() {
        DocumentChunk c1 = new DocumentChunk(10L, 5L, 0, 1, "First chunk text");
        DocumentChunk c2 = new DocumentChunk(11L, 5L, 1, 1, "Second chunk text");
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndex(5L))
            .thenReturn(List.of(c1, c2));

        List<ChunkInfo> result = tools.getDocumentChunks(5L);

        assertEquals(2, result.size());

        ChunkInfo first = result.get(0);
        assertEquals(0, first.chunkIndex());
        assertEquals(1, first.pageNumber());
        assertEquals("First chunk text", first.content());

        ChunkInfo second = result.get(1);
        assertEquals(1, second.chunkIndex());
        assertEquals(1, second.pageNumber());
        assertEquals("Second chunk text", second.content());
    }

    @Test
    void getDocumentChunks_noChunks_returnsEmptyList() {
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndex(99L))
            .thenReturn(List.of());

        List<ChunkInfo> result = tools.getDocumentChunks(99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getDocumentChunks_passesDocumentIdToRepository() {
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndex(42L))
            .thenReturn(List.of());

        tools.getDocumentChunks(42L);

        verify(documentChunkRepository).findByDocumentIdOrderByChunkIndex(42L);
    }
}
