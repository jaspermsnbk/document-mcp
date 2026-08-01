package com.jaspermsnbk.ai.basic_mcp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("document_chunks")
public record DocumentChunk(
    @Id Long id,
    Long documentId,
    int chunkIndex,
    int pageNumber,
    String content
) {}
