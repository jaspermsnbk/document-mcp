package com.jaspermsnbk.ai.basic_mcp.service;

import com.jaspermsnbk.ai.basic_mcp.domain.DocumentChunk;
import com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument;
import com.jaspermsnbk.ai.basic_mcp.dto.UploadResponse;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentChunkRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock private PdfProcessingService pdfProcessingService;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private VectorStore vectorStore;

    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(
            pdfProcessingService, documentRepository, documentChunkRepository, vectorStore
        );
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    void ingest_happyPath_savesDocumentAndChunksAndCallsVectorStore() throws IOException {
        byte[] pdfBytes = buildMinimalPdf();
        String hash = "abc123hash";

        // Stub pdf service
        when(pdfProcessingService.computeHash(pdfBytes)).thenReturn(hash);
        when(pdfProcessingService.extractPages(pdfBytes))
            .thenReturn(java.util.Map.of(1, "Some page text"));
        when(pdfProcessingService.chunk(any()))
            .thenReturn(List.of(new PdfProcessingService.ChunkData(0, 1, "Some page text")));

        // Stub repository: no duplicate
        when(documentRepository.findBySha256Hash(hash)).thenReturn(Optional.empty());

        // Saved document with id=1
        PdfDocument saved = new PdfDocument(1L, "test.pdf", hash, 1, pdfBytes.length, Instant.now());
        when(documentRepository.save(any(PdfDocument.class))).thenReturn(saved);

        // Stub chunk repo
        when(documentChunkRepository.save(any(DocumentChunk.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
            "file", "test.pdf", "application/pdf", pdfBytes
        );

        UploadResponse response = service.ingest(file);

        // Verify response populated
        assertEquals(1L, response.documentId());
        assertEquals("test.pdf", response.filename());
        assertEquals(1, response.pageCount());
        assertEquals(1, response.chunkCount());

        // Verify document was saved once
        verify(documentRepository, times(1)).save(any(PdfDocument.class));

        // Verify chunk was saved once
        ArgumentCaptor<DocumentChunk> chunkCaptor = ArgumentCaptor.forClass(DocumentChunk.class);
        verify(documentChunkRepository, times(1)).save(chunkCaptor.capture());
        DocumentChunk savedChunk = chunkCaptor.getValue();
        assertEquals(1L, savedChunk.documentId());
        assertEquals(0, savedChunk.chunkIndex());
        assertEquals(1, savedChunk.pageNumber());
        assertEquals("Some page text", savedChunk.content());

        // Verify vectorStore.add was called with one document
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> vectorCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(vectorCaptor.capture());
        List<org.springframework.ai.document.Document> aiDocs = vectorCaptor.getValue();
        assertEquals(1, aiDocs.size());
        assertEquals("Some page text", aiDocs.get(0).getText());
        assertEquals("1", aiDocs.get(0).getMetadata().get("document_id"));
        assertEquals("test.pdf", aiDocs.get(0).getMetadata().get("filename"));
    }

    // -----------------------------------------------------------------------
    // Duplicate detection
    // -----------------------------------------------------------------------

    @Test
    void ingest_duplicateDocument_throwsDuplicateDocumentExceptionAndDoesNotSave()
        throws IOException {
        byte[] pdfBytes = buildMinimalPdf();
        String hash = "dup-hash-xyz";

        when(pdfProcessingService.computeHash(pdfBytes)).thenReturn(hash);
        when(documentRepository.findBySha256Hash(hash))
            .thenReturn(Optional.of(
                new PdfDocument(42L, "existing.pdf", hash, 1, 100L, Instant.now())
            ));

        MockMultipartFile file = new MockMultipartFile(
            "file", "existing.pdf", "application/pdf", pdfBytes
        );

        assertThrows(DuplicateDocumentException.class, () -> service.ingest(file));

        // Must not proceed to save
        verify(documentRepository, never()).save(any());
        verify(documentChunkRepository, never()).save(any());
        verify(vectorStore, never()).add(any());
    }

    // -----------------------------------------------------------------------
    // Filename fallback
    // -----------------------------------------------------------------------

    @Test
    void ingest_nullOriginalFilename_usesUnknownPdfFallback() throws IOException {
        byte[] pdfBytes = buildMinimalPdf();
        String hash = "fallback-hash";

        // Use a Mockito mock of MultipartFile so that getOriginalFilename() returns null
        org.springframework.web.multipart.MultipartFile fileMock =
            mock(org.springframework.web.multipart.MultipartFile.class);
        when(fileMock.getBytes()).thenReturn(pdfBytes);
        when(fileMock.getOriginalFilename()).thenReturn(null);

        when(pdfProcessingService.computeHash(pdfBytes)).thenReturn(hash);
        when(pdfProcessingService.extractPages(pdfBytes))
            .thenReturn(java.util.Map.of(1, "text"));
        when(pdfProcessingService.chunk(any()))
            .thenReturn(List.of(new PdfProcessingService.ChunkData(0, 1, "text")));
        when(documentRepository.findBySha256Hash(hash)).thenReturn(Optional.empty());

        PdfDocument saved = new PdfDocument(2L, "unknown.pdf", hash, 1, pdfBytes.length, Instant.now());
        when(documentRepository.save(any(PdfDocument.class))).thenReturn(saved);
        when(documentChunkRepository.save(any(DocumentChunk.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        UploadResponse response = service.ingest(fileMock);
        assertEquals("unknown.pdf", response.filename());

        // Capture the PdfDocument passed to save and check its filename
        ArgumentCaptor<PdfDocument> docCaptor = ArgumentCaptor.forClass(PdfDocument.class);
        verify(documentRepository).save(docCaptor.capture());
        assertEquals("unknown.pdf", docCaptor.getValue().filename());
    }

    // -----------------------------------------------------------------------
    // Helper: create a valid minimal PDF byte array
    // -----------------------------------------------------------------------

    private static byte[] buildMinimalPdf() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Minimal test PDF");
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
