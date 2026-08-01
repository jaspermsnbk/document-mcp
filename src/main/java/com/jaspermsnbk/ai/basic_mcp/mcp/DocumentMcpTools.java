package com.jaspermsnbk.ai.basic_mcp.mcp;

import com.jaspermsnbk.ai.basic_mcp.dto.ChunkInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.DocumentInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.SearchResult;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentChunkRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import com.jaspermsnbk.ai.basic_mcp.service.DocumentSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DocumentMcpTools {

    private final DocumentSearchService searchService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public DocumentMcpTools(
        DocumentSearchService searchService,
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository
    ) {
        this.searchService = searchService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    @Tool(name = "search_documents", description = "Semantic search across all ingested PDF documents. Returns relevant text chunks with source metadata.")
    public List<SearchResult> searchDocuments(
        @ToolParam(description = "Natural language search query") String query,
        @ToolParam(description = "Maximum number of results to return (default 5)", required = false) Integer limit
    ) {
        return searchService.search(query, Objects.requireNonNullElse(limit, 5));
    }

    @Tool(name = "list_documents", description = "List all PDF documents that have been ingested into the system, with their metadata.")
    public List<DocumentInfo> listDocuments() {
        return documentRepository.findAll().stream()
            .map(doc -> new DocumentInfo(doc.id(), doc.filename(), doc.pageCount(), doc.fileSizeBytes(), doc.ingestedAt()))
            .toList();
    }

    @Tool(name = "get_document_chunks", description = "Retrieve all text chunks from a specific PDF document in order. Use list_documents first to get a document ID.")
    public List<ChunkInfo> getDocumentChunks(
        @ToolParam(description = "The numeric document ID obtained from list_documents") Long documentId
    ) {
        return documentChunkRepository.findByDocumentIdOrderByChunkIndex(documentId).stream()
            .map(chunk -> new ChunkInfo(chunk.chunkIndex(), chunk.pageNumber(), chunk.content()))
            .toList();
    }
}
