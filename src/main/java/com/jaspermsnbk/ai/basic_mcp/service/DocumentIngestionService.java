package com.jaspermsnbk.ai.basic_mcp.service;

import com.jaspermsnbk.ai.basic_mcp.domain.DocumentChunk;
import com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument;
import com.jaspermsnbk.ai.basic_mcp.dto.UploadResponse;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentChunkRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIngestionService {

    private final PdfProcessingService pdfProcessingService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorStore vectorStore;

    public DocumentIngestionService(
        PdfProcessingService pdfProcessingService,
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository,
        VectorStore vectorStore
    ) {
        this.pdfProcessingService = pdfProcessingService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public UploadResponse ingest(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String hash = pdfProcessingService.computeHash(bytes);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.pdf";

        if (documentRepository.findBySha256Hash(hash).isPresent()) {
            throw new DuplicateDocumentException("Document already ingested: " + filename);
        }

        Map<Integer, String> pages = pdfProcessingService.extractPages(bytes);
        List<PdfProcessingService.ChunkData> chunks = pdfProcessingService.chunk(pages);

        PdfDocument saved = documentRepository.save(
            new PdfDocument(null, filename, hash, pages.size(), bytes.length, Instant.now())
        );

        List<Document> springAiDocuments = new ArrayList<>();
        for (PdfProcessingService.ChunkData chunk : chunks) {
            documentChunkRepository.save(
                new DocumentChunk(null, saved.id(), chunk.chunkIndex(), chunk.pageNumber(), chunk.text())
            );
            springAiDocuments.add(new Document(
                chunk.text(),
                Map.of(
                    "document_id", String.valueOf(saved.id()),
                    "filename",    filename,
                    "page_number", String.valueOf(chunk.pageNumber()),
                    "chunk_index", String.valueOf(chunk.chunkIndex())
                )
            ));
        }

        vectorStore.add(springAiDocuments);

        return new UploadResponse(saved.id(), saved.filename(), saved.pageCount(), chunks.size());
    }
}
