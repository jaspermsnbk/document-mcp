CREATE TABLE pdf_staging (
    id           BIGSERIAL    PRIMARY KEY,
    filename     VARCHAR(500) NOT NULL,
    sha256_hash  CHAR(64)     NOT NULL,
    file_data    BYTEA        NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_msg    TEXT,
    submitted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX pdf_staging_sha256_idx ON pdf_staging(sha256_hash);
CREATE INDEX pdf_staging_status_idx ON pdf_staging(status);
