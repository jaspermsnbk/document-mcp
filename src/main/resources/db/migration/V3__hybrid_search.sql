ALTER TABLE document_chunks
    ADD COLUMN fts_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX document_chunks_fts_idx ON document_chunks USING gin(fts_vector);
