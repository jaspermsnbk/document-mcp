package com.jaspermsnbk.ai.basic_mcp.dto;

public record SearchResult(
    String content,
    String filename,
    int pageNumber,
    int chunkIndex,
    double score
) {}
