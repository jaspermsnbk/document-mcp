package com.jaspermsnbk.ai.basic_mcp.controller;

import com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument;
import com.jaspermsnbk.ai.basic_mcp.domain.PdfStaging;
import com.jaspermsnbk.ai.basic_mcp.dto.IngestAcceptedResponse;
import com.jaspermsnbk.ai.basic_mcp.dto.SearchResult;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.PdfStagingRepository;
import com.jaspermsnbk.ai.basic_mcp.service.AsyncIngestionService;
import com.jaspermsnbk.ai.basic_mcp.service.DocumentSearchService;
import com.jaspermsnbk.ai.basic_mcp.service.DuplicateDocumentException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AsyncIngestionService asyncIngestionService;
    @MockitoBean private DocumentRepository documentRepository;
    @MockitoBean private PdfStagingRepository stagingRepository;
    @MockitoBean private DocumentSearchService searchService;

    // -----------------------------------------------------------------------
    // POST /api/documents → 202 Accepted
    // -----------------------------------------------------------------------

    @Test
    void upload_validFile_returns202WithStagingId() throws Exception {
        when(asyncIngestionService.submit(any())).thenReturn(new IngestAcceptedResponse(7L, 42L));

        mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "bytes".getBytes())))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.stagingId").value(7))
            .andExpect(jsonPath("$.jobExecutionId").value(42));
    }

    @Test
    void upload_duplicateDocument_returns409() throws Exception {
        when(asyncIngestionService.submit(any()))
            .thenThrow(new DuplicateDocumentException("Document already ingested: test.pdf"));

        mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "bytes".getBytes())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DUPLICATE_DOCUMENT"));
    }

    @Test
    void upload_ioException_returns422() throws Exception {
        when(asyncIngestionService.submit(any())).thenThrow(new IOException("bad pdf"));

        mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", "bad.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{0})))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error").value("PROCESSING_ERROR"));
    }

    // -----------------------------------------------------------------------
    // GET /api/documents → paginated list
    // -----------------------------------------------------------------------

    @Test
    void list_returns200WithPageResponse() throws Exception {
        Instant now = Instant.parse("2026-01-15T12:00:00Z");
        when(documentRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
            new PdfDocument(1L, "doc1.pdf", "hash1", 2, 1024L, now),
            new PdfDocument(2L, "doc2.pdf", "hash2", 5, 2048L, now)
        )));

        mockMvc.perform(get("/api/documents").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void list_emptyRepository_returnsEmptyPage() throws Exception {
        when(documentRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/documents").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    // -----------------------------------------------------------------------
    // GET /api/documents/search
    // -----------------------------------------------------------------------

    @Test
    void search_returnsResults() throws Exception {
        when(searchService.search(anyString(), anyInt()))
            .thenReturn(List.of(new SearchResult("chunk text", "doc.pdf", 1, 0, 0.9)));

        mockMvc.perform(get("/api/documents/search").param("q", "machine learning"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].content").value("chunk text"));
    }

    // -----------------------------------------------------------------------
    // GET /api/documents/ingestion/{stagingId}/status
    // -----------------------------------------------------------------------

    @Test
    void stagingStatus_found_returnsStatus() throws Exception {
        PdfStaging staging = new PdfStaging(5L, "doc.pdf", "hash", new byte[0], "DONE", null, Instant.now(), Instant.now());
        when(stagingRepository.findById(5L)).thenReturn(Optional.of(staging));

        mockMvc.perform(get("/api/documents/ingestion/5/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.errorMsg").isEmpty());
    }

    @Test
    void stagingStatus_notFound_returns404() throws Exception {
        when(stagingRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/ingestion/99/status"))
            .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // POST /api/documents/ingestion/{stagingId}/retry
    // -----------------------------------------------------------------------

    @Test
    void retry_failedJob_returns202() throws Exception {
        when(asyncIngestionService.retry(5L)).thenReturn(new IngestAcceptedResponse(5L, 99L));

        mockMvc.perform(post("/api/documents/ingestion/5/retry"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.stagingId").value(5))
            .andExpect(jsonPath("$.jobExecutionId").value(99));
    }

    @Test
    void retry_nonFailedJob_returns400() throws Exception {
        when(asyncIngestionService.retry(anyLong()))
            .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Only FAILED jobs can be retried"));

        mockMvc.perform(post("/api/documents/ingestion/5/retry"))
            .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // DELETE /api/documents/{id}
    // -----------------------------------------------------------------------

    @Test
    void delete_existingDocument_returns204() throws Exception {
        mockMvc.perform(delete("/api/documents/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void delete_nonExistentDocument_returns404() throws Exception {
        doThrow(new ResponseStatusException(NOT_FOUND, "Document not found"))
            .when(asyncIngestionService).delete(anyLong());

        mockMvc.perform(delete("/api/documents/99"))
            .andExpect(status().isNotFound());
    }
}
