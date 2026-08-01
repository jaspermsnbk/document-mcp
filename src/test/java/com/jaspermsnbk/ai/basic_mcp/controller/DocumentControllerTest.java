package com.jaspermsnbk.ai.basic_mcp.controller;

import com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument;
import com.jaspermsnbk.ai.basic_mcp.dto.UploadResponse;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import com.jaspermsnbk.ai.basic_mcp.service.DocumentIngestionService;
import com.jaspermsnbk.ai.basic_mcp.service.DuplicateDocumentException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentIngestionService ingestionService;

    @MockitoBean
    private DocumentRepository documentRepository;

    // -----------------------------------------------------------------------
    // POST /api/documents — happy path → 201
    // -----------------------------------------------------------------------

    @Test
    void uploadPdf_validFile_returns201WithBody() throws Exception {
        UploadResponse response = new UploadResponse(1L, "test.pdf", 3, 7);
        when(ingestionService.ingest(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
            "file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "PDF-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/documents").file(file))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.documentId").value(1))
            .andExpect(jsonPath("$.filename").value("test.pdf"))
            .andExpect(jsonPath("$.pageCount").value(3))
            .andExpect(jsonPath("$.chunkCount").value(7));
    }

    // -----------------------------------------------------------------------
    // POST /api/documents — duplicate → 409
    // -----------------------------------------------------------------------

    @Test
    void uploadPdf_duplicateDocument_returns409() throws Exception {
        when(ingestionService.ingest(any()))
            .thenThrow(new DuplicateDocumentException("Document already ingested: test.pdf"));

        MockMultipartFile file = new MockMultipartFile(
            "file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "PDF-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/documents").file(file))
            .andExpect(status().isConflict());
    }

    // -----------------------------------------------------------------------
    // POST /api/documents — IOException → 422
    // -----------------------------------------------------------------------

    @Test
    void uploadPdf_ioException_returns422() throws Exception {
        when(ingestionService.ingest(any()))
            .thenThrow(new IOException("Cannot read PDF"));

        MockMultipartFile file = new MockMultipartFile(
            "file", "broken.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{0}
        );

        mockMvc.perform(multipart("/api/documents").file(file))
            .andExpect(status().isUnprocessableEntity());
    }

    // -----------------------------------------------------------------------
    // GET /api/documents → 200 with JSON array
    // -----------------------------------------------------------------------

    @Test
    void listDocuments_returns200WithJsonArray() throws Exception {
        Instant now = Instant.parse("2026-01-15T12:00:00Z");
        PdfDocument doc1 = new PdfDocument(1L, "doc1.pdf", "hash1", 2, 1024L, now);
        PdfDocument doc2 = new PdfDocument(2L, "doc2.pdf", "hash2", 5, 2048L, now);
        when(documentRepository.findAll()).thenReturn(List.of(doc1, doc2));

        mockMvc.perform(get("/api/documents").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].filename").value("doc1.pdf"))
            .andExpect(jsonPath("$[0].pageCount").value(2))
            .andExpect(jsonPath("$[0].fileSizeBytes").value(1024))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].filename").value("doc2.pdf"));
    }

    @Test
    void listDocuments_emptyRepository_returnsEmptyArray() throws Exception {
        when(documentRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/documents").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
