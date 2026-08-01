package com.jaspermsnbk.ai.basic_mcp.controller;

import com.jaspermsnbk.ai.basic_mcp.dto.DocumentInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.IngestAcceptedResponse;
import com.jaspermsnbk.ai.basic_mcp.dto.PageResponse;
import com.jaspermsnbk.ai.basic_mcp.dto.SearchResult;
import com.jaspermsnbk.ai.basic_mcp.dto.StagingStatusResponse;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.PdfStagingRepository;
import com.jaspermsnbk.ai.basic_mcp.service.AsyncIngestionService;
import com.jaspermsnbk.ai.basic_mcp.service.DocumentSearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final AsyncIngestionService asyncIngestionService;
    private final DocumentRepository documentRepository;
    private final PdfStagingRepository stagingRepository;
    private final DocumentSearchService searchService;

    public DocumentController(
        AsyncIngestionService asyncIngestionService,
        DocumentRepository documentRepository,
        PdfStagingRepository stagingRepository,
        DocumentSearchService searchService
    ) {
        this.asyncIngestionService = asyncIngestionService;
        this.documentRepository = documentRepository;
        this.stagingRepository = stagingRepository;
        this.searchService = searchService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestAcceptedResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(asyncIngestionService.submit(file));
    }

    @GetMapping
    public ResponseEntity<PageResponse<DocumentInfo>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "ingestedAt"));
        Page<com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument> docs = documentRepository.findAll(pageable);
        List<DocumentInfo> content = docs.getContent().stream()
            .map(doc -> new DocumentInfo(doc.id(), doc.filename(), doc.pageCount(), doc.fileSizeBytes(), doc.ingestedAt()))
            .toList();
        return ResponseEntity.ok(new PageResponse<>(content, docs.getTotalElements(), docs.getTotalPages(), page));
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResult>> search(
        @RequestParam String q,
        @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(searchService.search(q, limit));
    }

    @GetMapping("/ingestion/{stagingId}/status")
    public ResponseEntity<StagingStatusResponse> stagingStatus(@PathVariable Long stagingId) {
        return stagingRepository.findById(stagingId)
            .map(s -> ResponseEntity.ok(new StagingStatusResponse(s.status(), s.errorMsg())))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/ingestion/{stagingId}/retry")
    public ResponseEntity<IngestAcceptedResponse> retry(@PathVariable Long stagingId) {
        return ResponseEntity.accepted().body(asyncIngestionService.retry(stagingId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        asyncIngestionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
