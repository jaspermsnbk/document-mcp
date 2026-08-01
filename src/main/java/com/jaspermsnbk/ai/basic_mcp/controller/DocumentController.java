package com.jaspermsnbk.ai.basic_mcp.controller;

import com.jaspermsnbk.ai.basic_mcp.dto.DocumentInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.UploadResponse;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import com.jaspermsnbk.ai.basic_mcp.service.DocumentIngestionService;
import com.jaspermsnbk.ai.basic_mcp.service.DuplicateDocumentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    private final DocumentIngestionService ingestionService;
    private final DocumentRepository documentRepository;

    public DocumentController(DocumentIngestionService ingestionService, DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ingestionService.ingest(file));
        } catch (DuplicateDocumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatusCode.valueOf(422)).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<DocumentInfo>> list() {
        List<DocumentInfo> docs = documentRepository.findAll().stream()
            .map(doc -> new DocumentInfo(doc.id(), doc.filename(), doc.pageCount(), doc.fileSizeBytes(), doc.ingestedAt()))
            .toList();
        return ResponseEntity.ok(docs);
    }
}
