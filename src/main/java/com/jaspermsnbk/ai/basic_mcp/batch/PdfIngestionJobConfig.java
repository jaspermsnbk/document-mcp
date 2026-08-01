package com.jaspermsnbk.ai.basic_mcp.batch;

import com.jaspermsnbk.ai.basic_mcp.domain.DocumentChunk;
import com.jaspermsnbk.ai.basic_mcp.domain.PdfDocument;
import com.jaspermsnbk.ai.basic_mcp.domain.PdfStaging;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentChunkRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.PdfStagingRepository;
import com.jaspermsnbk.ai.basic_mcp.service.PdfProcessingService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration
public class PdfIngestionJobConfig {

    @Bean
    public TaskExecutorJobLauncher jobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("pdf-ingest-"));
        launcher.afterPropertiesSet();
        return launcher;
    }

    @Bean
    public Job pdfIngestionJob(JobRepository jobRepository, Step extractAndChunkStep,
                               Step embedAndStoreStep, JobCompletionListener listener) {
        return new JobBuilder("pdfIngestionJob", jobRepository)
            .listener(listener)
            .start(extractAndChunkStep)
            .next(embedAndStoreStep)
            .build();
    }

    @Bean
    public Step extractAndChunkStep(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    Tasklet extractAndChunkTasklet) {
        return new StepBuilder("extractAndChunkStep", jobRepository)
            .tasklet(extractAndChunkTasklet, transactionManager)
            .build();
    }

    @Bean
    public Step embedAndStoreStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  Tasklet embedAndStoreTasklet) {
        return new StepBuilder("embedAndStoreStep", jobRepository)
            .tasklet(embedAndStoreTasklet, transactionManager)
            .build();
    }

    @Bean
    @StepScope
    public Tasklet extractAndChunkTasklet(
        @Value("#{jobParameters['stagingId']}") Long stagingId,
        PdfStagingRepository stagingRepository,
        DocumentRepository documentRepository,
        DocumentChunkRepository chunkRepository,
        PdfProcessingService processingService,
        JdbcTemplate jdbcTemplate
    ) {
        return (contribution, chunkContext) -> {
            PdfStaging staging = stagingRepository.findById(stagingId).orElseThrow();
            jdbcTemplate.update("UPDATE pdf_staging SET status = 'PROCESSING' WHERE id = ?", stagingId);

            // idempotent: if the document already exists (prior failed run), skip extraction
            Optional<PdfDocument> existing = documentRepository.findBySha256Hash(staging.sha256Hash());
            long documentId;
            if (existing.isPresent()) {
                documentId = existing.get().id();
            } else {
                byte[] bytes = staging.fileData();
                Map<Integer, String> pages = processingService.extractPages(bytes);
                List<PdfProcessingService.ChunkData> chunks = processingService.chunk(pages);

                PdfDocument saved = documentRepository.save(new PdfDocument(
                    null, staging.filename(), staging.sha256Hash(),
                    pages.size(), bytes.length, Instant.now()
                ));
                documentId = saved.id();

                for (PdfProcessingService.ChunkData chunk : chunks) {
                    chunkRepository.save(new DocumentChunk(
                        null, documentId, chunk.chunkIndex(), chunk.pageNumber(), chunk.text()
                    ));
                }
            }

            chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext().putLong("documentId", documentId);

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    @StepScope
    public Tasklet embedAndStoreTasklet(
        @Value("#{jobParameters['stagingId']}") Long stagingId,
        @Value("#{jobExecutionContext['documentId']}") Long documentId,
        DocumentRepository documentRepository,
        DocumentChunkRepository chunkRepository,
        VectorStore vectorStore,
        JdbcTemplate jdbcTemplate
    ) {
        return (contribution, chunkContext) -> {
            PdfDocument doc = documentRepository.findById(documentId).orElseThrow();
            List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);

            List<Document> aiDocs = chunks.stream()
                .map(chunk -> new Document(
                    chunk.content(),
                    Map.of(
                        "document_id", String.valueOf(documentId),
                        "filename",    doc.filename(),
                        "page_number", String.valueOf(chunk.pageNumber()),
                        "chunk_index", String.valueOf(chunk.chunkIndex())
                    )
                )).toList();

            vectorStore.add(aiDocs);

            jdbcTemplate.update(
                "UPDATE pdf_staging SET status = 'DONE', completed_at = NOW() WHERE id = ?",
                stagingId
            );

            return RepeatStatus.FINISHED;
        };
    }
}
