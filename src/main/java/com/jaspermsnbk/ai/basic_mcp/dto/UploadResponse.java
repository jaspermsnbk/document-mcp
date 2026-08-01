package com.jaspermsnbk.ai.basic_mcp.dto;

public record UploadResponse(
    Long documentId,
    String filename,
    int pageCount,
    int chunkCount
) {}
