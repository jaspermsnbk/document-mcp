package com.jaspermsnbk.ai.basic_mcp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("pdf_staging")
public record PdfStaging(
    @Id Long id,
    String filename,
    String sha256Hash,
    byte[] fileData,
    String status,
    String errorMsg,
    Instant submittedAt,
    Instant completedAt
) {}
