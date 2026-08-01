package com.jaspermsnbk.ai.basic_mcp.service;

import com.jaspermsnbk.ai.basic_mcp.dto.SearchResult;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentSearchService {

    private static final int RRF_K = 60;

    private static final String FTS_SQL = """
        SELECT dc.document_id, dc.chunk_index, dc.page_number, dc.content, d.filename
        FROM document_chunks dc
        JOIN documents d ON d.id = dc.document_id
        WHERE dc.fts_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(dc.fts_vector, plainto_tsquery('english', :query)) DESC
        LIMIT :limit
        """;

    private final VectorStore vectorStore;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DocumentSearchService(VectorStore vectorStore, NamedParameterJdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchResult> search(String query, int limit) {
        // Vector search — Spring AI embeds the query internally
        List<org.springframework.ai.document.Document> vectorHits = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(limit).build()
        );

        // Full-text search
        List<FtsRow> ftsHits = jdbcTemplate.query(FTS_SQL, Map.of("query", query, "limit", limit),
            (rs, rowNum) -> new FtsRow(
                rs.getLong("document_id"),
                rs.getInt("chunk_index"),
                rs.getInt("page_number"),
                rs.getString("content"),
                rs.getString("filename")
            ));

        // RRF fusion: score = Σ 1/(60 + rank) across both lists
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, SearchResult> content = new LinkedHashMap<>();

        for (int i = 0; i < vectorHits.size(); i++) {
            var doc = vectorHits.get(i);
            String key = key(doc.getMetadata());
            scores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            content.putIfAbsent(key, toResult(doc));
        }

        for (int i = 0; i < ftsHits.size(); i++) {
            FtsRow row = ftsHits.get(i);
            String key = row.documentId() + ":" + row.chunkIndex();
            scores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            content.putIfAbsent(key, new SearchResult(row.content(), row.filename(), row.pageNumber(), row.chunkIndex(), 0.0));
        }

        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(e -> {
                SearchResult base = content.get(e.getKey());
                return new SearchResult(base.content(), base.filename(), base.pageNumber(), base.chunkIndex(), e.getValue());
            })
            .toList();
    }

    private String key(Map<String, Object> meta) {
        return meta.getOrDefault("document_id", "") + ":" + meta.getOrDefault("chunk_index", "");
    }

    private SearchResult toResult(org.springframework.ai.document.Document doc) {
        var meta = doc.getMetadata();
        return new SearchResult(
            doc.getText(),
            (String) meta.getOrDefault("filename", ""),
            parseIntMeta(meta, "page_number"),
            parseIntMeta(meta, "chunk_index"),
            0.0
        );
    }

    private int parseIntMeta(Map<String, Object> meta, String key) {
        Object val = meta.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    // Package-private so tests can construct instances directly
    record FtsRow(long documentId, int chunkIndex, int pageNumber, String content, String filename) {}
}
