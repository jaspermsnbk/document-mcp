package com.jaspermsnbk.ai.basic_mcp.dto;

import java.time.Instant;

public record DocumentInfo(
    Long id,
    String filename,
    int pageCount,
    long fileSizeBytes,
    Instant ingestedAt
) {}
