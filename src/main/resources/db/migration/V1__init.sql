-- Richiede l'estensione pgvector sul database Postgres
-- (Neon e Supabase la offrono già pronta all'uso; su un Postgres locale/Docker
--  serve l'immagine "ankane/pgvector" o installare l'estensione manualmente)
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(255) NOT NULL,       -- es. "cv.txt", "progetto-1-readme.md"
    content TEXT NOT NULL,
    embedding VECTOR(512) NOT NULL,     -- deve combaciare con voyage.dimensions
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Indice per la ricerca per similarità (coseno). Va ricreato/ottimizzato
-- quando la tabella cresce parecchio.
CREATE INDEX IF NOT EXISTS document_chunks_embedding_idx
    ON document_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
