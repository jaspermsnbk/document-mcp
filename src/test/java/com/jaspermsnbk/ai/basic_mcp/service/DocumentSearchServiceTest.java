package com.jaspermsnbk.ai.basic_mcp.service;

import com.jaspermsnbk.ai.basic_mcp.dto.SearchResult;
import com.jaspermsnbk.ai.basic_mcp.service.DocumentSearchService.FtsRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentSearchServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    private DocumentSearchService service;

    @BeforeEach
    void setUp() {
        service = new DocumentSearchService(vectorStore, jdbcTemplate);
    }

    // -----------------------------------------------------------------------
    // Vector-only: FTS returns nothing
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void search_vectorOnly_mapsMetadataCorrectly() {
        Document doc = Document.builder()
            .text("Vector result")
            .metadata("document_id", "1")
            .metadata("chunk_index", "0")
            .metadata("page_number", "2")
            .metadata("filename", "report.pdf")
            .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());

        List<SearchResult> results = service.search("machine learning", 5);

        assertEquals(1, results.size());
        SearchResult r = results.get(0);
        assertEquals("Vector result", r.content());
        assertEquals("report.pdf", r.filename());
        assertEquals(2, r.pageNumber());
        assertEquals(0, r.chunkIndex());
        assertTrue(r.score() > 0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_vectorOnly_passesLimitAsTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());

        service.search("query", 10);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals(10, captor.getValue().getTopK());
    }

    // -----------------------------------------------------------------------
    // FTS-only: vector returns nothing
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void search_ftsOnly_returnsFtsResults() {
        FtsRow row = new FtsRow(1L, 0, 3, "FTS result text", "doc.pdf");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of(row));

        List<SearchResult> results = service.search("keyword", 5);

        assertEquals(1, results.size());
        assertEquals("FTS result text", results.get(0).content());
        assertEquals("doc.pdf", results.get(0).filename());
        assertEquals(3, results.get(0).pageNumber());
        assertTrue(results.get(0).score() > 0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_ftsOnly_passesLimitToJdbcQuery() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());

        service.search("query", 7);

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass((Class) Map.class);
        verify(jdbcTemplate).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));
        assertEquals(7, paramsCaptor.getValue().get("limit"));
        assertEquals("query", paramsCaptor.getValue().get("query"));
    }

    // -----------------------------------------------------------------------
    // Hybrid: same chunk appears in both → boosted RRF score
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void search_hybrid_sharedChunkHasHigherScoreThanUniqueChunks() {
        // chunk 1:0 appears in both vector and FTS (should be boosted)
        Document vectorShared = Document.builder().text("shared chunk").metadata("document_id", "1")
            .metadata("chunk_index", "0").metadata("page_number", "1").metadata("filename", "a.pdf").build();
        // chunk 2:0 appears only in vector
        Document vectorOnly = Document.builder().text("vector only").metadata("document_id", "2")
            .metadata("chunk_index", "0").metadata("page_number", "1").metadata("filename", "b.pdf").build();

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
            .thenReturn(List.of(vectorShared, vectorOnly));

        // FTS: shared chunk is rank 1, different chunk is rank 2
        FtsRow ftsShared = new FtsRow(1L, 0, 1, "shared chunk", "a.pdf");
        FtsRow ftsOnly  = new FtsRow(3L, 0, 1, "fts only text", "c.pdf");
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
            .thenReturn(List.of(ftsShared, ftsOnly));

        List<SearchResult> results = service.search("shared", 5);

        // The shared chunk should rank first (highest RRF score)
        assertEquals("shared chunk", results.get(0).content());
        // Its score should be greater than any unique chunk's score
        double sharedScore = results.get(0).score();
        results.stream().skip(1).forEach(r -> assertTrue(sharedScore > r.score(),
            "Shared chunk should outscore unique chunks"));
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void search_bothEmpty_returnsEmptyList() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());

        assertTrue(service.search("query", 5).isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void search_alwaysCallsBothSources() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());

        service.search("test", 5);

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verify(jdbcTemplate).query(anyString(), anyMap(), any(RowMapper.class));
    }
}
