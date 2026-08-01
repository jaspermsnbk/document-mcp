package com.jaspermsnbk.ai.basic_mcp.mcp;

import com.jaspermsnbk.ai.basic_mcp.dto.ChunkInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.DocumentInfo;
import com.jaspermsnbk.ai.basic_mcp.dto.SearchResult;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentChunkRepository;
import com.jaspermsnbk.ai.basic_mcp.repository.DocumentRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DocumentMcpTools {

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public DocumentMcpTools(
        VectorStore vectorStore,
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository
    ) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    @Tool(name = "search_documents", description = "Semantic search across all ingested PDF documents. Returns relevant text chunks with source metadata.")
    public List<SearchResult> searchDocuments(
        @ToolParam(description = "Natural language search query") String query,
        @ToolParam(description = "Maximum number of results to return (default 5)", required = false) Integer limit
    ) {
        int topK = Objects.requireNonNullElse(limit, 5);
        return vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(topK).build()
        ).stream().map(doc -> {
            var meta = doc.getMetadata();
            Object scoreVal = meta.getOrDefault("distance", 0.0);
            double score = scoreVal instanceof Number n ? n.doubleValue() : 0.0;
            return new SearchResult(
                doc.getText(),
                (String) meta.getOrDefault("filename", ""),
                parseIntMeta(meta, "page_number"),
                parseIntMeta(meta, "chunk_index"),
                score
            );
        }).toList();
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

    private int parseIntMeta(java.util.Map<String, Object> meta, String key) {
        Object val = meta.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}
