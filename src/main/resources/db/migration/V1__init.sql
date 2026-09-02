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

-- Niente indice ANN (ivfflat/hnsw) di proposito: la knowledge base è piccola
-- (~30 chunk). Un indice ivfflat tarato per grandi dataset, su poche righe,
-- produce ricerche approssimate che possono restituire risultati vuoti. Con
-- questi volumi una scansione sequenziale è già istantanea e sempre esatta.
-- Aggiungere un indice (e sceglierne i parametri) solo se la tabella cresce
-- di ordini di grandezza.
