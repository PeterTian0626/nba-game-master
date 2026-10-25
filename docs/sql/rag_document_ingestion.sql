BEGIN;

CREATE TABLE public.rag_document_ingestion (
    source_key TEXT PRIMARY KEY,
    content_hash TEXT NOT NULL
        CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    pipeline_version TEXT NOT NULL,
    chunk_count INTEGER NOT NULL
        CHECK (chunk_count >= 0),
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.rag_document_ingestion ENABLE ROW LEVEL SECURITY;

COMMIT;
