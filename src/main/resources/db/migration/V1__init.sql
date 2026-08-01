CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE documents (
    id              BIGSERIAL    PRIMARY KEY,
    filename        VARCHAR(500) NOT NULL,
    sha256_hash     CHAR(64)     NOT NULL UNIQUE,
    page_count      INTEGER      NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    ingested_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE document_chunks (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT    NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER   NOT NULL,
    page_number INTEGER   NOT NULL,
    content     TEXT      NOT NULL
);

CREATE INDEX document_chunks_document_id_idx ON document_chunks(document_id);

-- Spring AI vector store table (initialize-schema=false so Flyway owns it)
-- embedding dimensions (768) must match the configured PostgresML model output
CREATE TABLE vector_store (
    id        UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT        NOT NULL,
    metadata  JSONB,
    embedding VECTOR(768) NOT NULL
);

CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);
CREATE INDEX vector_store_metadata_idx  ON vector_store USING gin(metadata);
