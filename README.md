# basic-mcp

A Spring Boot MCP (Model Context Protocol) server that ingests PDF documents and exposes them for hybrid semantic + full-text search — both as an MCP tool server for AI clients (Claude Code, Claude Desktop, etc.) and as a standalone REST API.

## How it works

1. **Upload** a PDF via `POST /api/documents`. The file is hashed (SHA-256, for de-duplication) and staged in Postgres; the request returns immediately (`202 Accepted`) with a staging ID.
2. A **Spring Batch** job picks up the staged file asynchronously and runs two steps:
   - `extractAndChunkStep` — extracts text per page with PDFBox and splits it into overlapping chunks (1000 chars, 800-char stride).
   - `embedAndStoreStep` — embeds each chunk (via Ollama, `nomic-embed-text`) and writes it into a pgvector table, tagged with document/page/chunk metadata.
3. Ingestion status is tracked in a `pdf_staging` table (`PENDING` → `PROCESSING` → `DONE`/`FAILED`), and failed jobs can be retried without re-uploading.
4. **Search** fuses two retrieval methods with **Reciprocal Rank Fusion (RRF)**:
   - Vector similarity search over the pgvector embeddings.
   - Postgres full-text search (`tsvector`/`GIN` index) over chunk content.
5. Everything ingested is exposed to MCP clients as tools, and to regular HTTP clients as a REST API.

## Tech stack

- **Spring Boot 4.1** / Java 25
- **Spring AI** — MCP server (`spring-ai-starter-mcp-server-webmvc`), pgvector vector store, Ollama embeddings, Anthropic chat model
- **Spring Batch** (JDBC-backed job repository) for async PDF ingestion
- **PostgreSQL 17 + pgvector** for both relational data and vector storage
- **Flyway** for schema migrations
- **PDFBox** for text extraction
- **springdoc-openapi** for Swagger UI

## Prerequisites

- Java 25
- Docker (for Postgres via `infra/docker-compose.yml`)
- [Ollama](https://ollama.com) running locally with the `nomic-embed-text` model pulled (`ollama pull nomic-embed-text`)
- An Anthropic API key (used for the chat model)

## Running locally

1. Start Postgres (pgvector-enabled):
   ```bash
   docker compose -f infra/docker-compose.yml up -d
   ```
2. Export required environment variables:
   ```bash
   export ANTHROPIC_API_KEY=sk-...
   # optional overrides, defaults shown:
   export DB_USERNAME=postgres
   export DB_PASSWORD=postgres
   export OLLAMA_BASE_URL=http://localhost:11434
   ```
3. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```

Flyway runs automatically on startup and creates the schema (`documents`, `document_chunks`, `pdf_staging`, `vector_store`).

## REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/documents` | Upload a PDF (multipart `file`); returns `202` with a staging ID |
| `GET` | `/api/documents` | List ingested documents, paginated (`page`, `size`) |
| `GET` | `/api/documents/search` | Hybrid search (`q`, `limit`) |
| `GET` | `/api/documents/ingestion/{stagingId}/status` | Check ingestion job status |
| `POST` | `/api/documents/ingestion/{stagingId}/retry` | Retry a failed ingestion job |
| `DELETE` | `/api/documents/{id}` | Delete a document and its vector store entries |

Swagger UI is available at `/swagger-ui.html` when the app is running.

## MCP tools

Exposed to MCP clients via `spring-ai-starter-mcp-server-webmvc` (`DocumentMcpTools`):

- **`search_documents(query, limit?)`** — semantic + full-text hybrid search across all ingested PDFs
- **`list_documents()`** — list ingested documents with metadata (filename, page count, size, ingestion time)
- **`get_document_chunks(documentId)`** — retrieve all chunks of a specific document in order

## Configuration

Key settings in `src/main/resources/application.yaml`:

- `spring.datasource.*` — Postgres connection (`DB_USERNAME`/`DB_PASSWORD` env vars)
- `spring.ai.anthropic.api-key` — required, from `ANTHROPIC_API_KEY`
- `spring.ai.ollama.*` — embedding model config (`nomic-embed-text`, 768 dimensions)
- `spring.ai.vectorstore.pgvector.*` — HNSW index, cosine distance, schema managed by Flyway (`initialize-schema: false`)
- `management.endpoints.web.exposure.include` — actuator endpoints (`health`, `metrics`, `info`)

## Tests

```bash
./mvnw test
```

Covers document search (RRF fusion logic), PDF processing (extraction/chunking), ingestion service, and REST/MCP controllers.
