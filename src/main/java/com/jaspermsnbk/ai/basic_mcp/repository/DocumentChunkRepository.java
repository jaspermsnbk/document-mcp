package com.jaspermsnbk.ai.basic_mcp.repository;

import com.jaspermsnbk.ai.basic_mcp.domain.DocumentChunk;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface DocumentChunkRepository extends ListCrudRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);
}
