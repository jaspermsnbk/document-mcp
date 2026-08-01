package com.jaspermsnbk.ai.basic_mcp.service;

import com.jaspermsnbk.ai.basic_mcp.domain.PdfStaging;
import com.jaspermsnbk.ai.basic_mcp.dto.IngestAcceptedResponse;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.PdfStagingRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;

@Service
public class AsyncIngestionService {

    private final PdfProcessingService processingService;
    private final DocumentRepository documentRepository;
    private final PdfStagingRepository stagingRepository;
    private final TaskExecutorJobLauncher jobLauncher;
    private final Job pdfIngestionJob;
    private final JdbcTemplate jdbcTemplate;

    public AsyncIngestionService(
        PdfProcessingService processingService,
        DocumentRepository documentRepository,
        PdfStagingRepository stagingRepository,
        TaskExecutorJobLauncher jobLauncher,
        Job pdfIngestionJob,
        JdbcTemplate jdbcTemplate
    ) {
        this.processingService = processingService;
        this.documentRepository = documentRepository;
        this.stagingRepository = stagingRepository;
        this.jobLauncher = jobLauncher;
        this.pdfIngestionJob = pdfIngestionJob;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestAcceptedResponse submit(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String hash = processingService.computeHash(bytes);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.pdf";

        if (documentRepository.findBySha256Hash(hash).isPresent()) {
            throw new DuplicateDocumentException("Document already ingested: " + filename);
        }
        boolean activeStaging = stagingRepository.findBySha256Hash(hash).stream()
            .anyMatch(s -> !"FAILED".equals(s.status()));
        if (activeStaging) {
            throw new DuplicateDocumentException("Document already queued: " + filename);
        }

        PdfStaging staging = stagingRepository.save(
            new PdfStaging(null, filename, hash, bytes, "PENDING", null, Instant.now(), null)
        );
        return new IngestAcceptedResponse(staging.id(), launch(staging.id()));
    }

    public IngestAcceptedResponse retry(Long stagingId) {
        PdfStaging staging = stagingRepository.findById(stagingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staging job not found"));
        if (!"FAILED".equals(staging.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only FAILED jobs can be retried");
        }
        jdbcTemplate.update(
            "UPDATE pdf_staging SET status = 'PENDING', error_msg = NULL, completed_at = NULL WHERE id = ?",
            stagingId
        );
        return new IngestAcceptedResponse(stagingId, launch(stagingId));
    }

    public void delete(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        jdbcTemplate.update(
            "DELETE FROM vector_store WHERE metadata->>'document_id' = ?",
            documentId.toString()
        );
        documentRepository.deleteById(documentId);
    }

    private long launch(Long stagingId) {
        try {
            JobParameters params = new JobParametersBuilder()
                .addLong("stagingId", stagingId)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
            return jobLauncher.run(pdfIngestionJob, params).getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch ingestion job for staging ID " + stagingId, e);
        }
    }
}
