package com.jaspermsnbk.ai.basic_mcp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("documents")
public record PdfDocument(
    @Id Long id,
    String filename,
    String sha256Hash,
    int pageCount,
    long fileSizeBytes,
    Instant ingestedAt
) {}
