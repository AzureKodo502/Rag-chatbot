-- Tabella per il feedback a stelle raccolto dalla card in-app.
-- Come V1, va applicata a mano (niente Flyway nel progetto):
--   psql -h localhost -U postgres -d ragchatbot -f src/main/resources/db/migration/V2__feedback.sql
--
-- Privacy: NIENTE dati identificativi. Nessun IP, nessun user agent, nessun
-- cookie di sessione. Solo il voto, un commento facoltativo scritto
-- volontariamente dall'utente, la lingua UI e quante domande aveva fatto
-- quando ha lasciato il feedback (utile per capire "a che punto" ingaggia).
CREATE TABLE IF NOT EXISTS feedback (
    id BIGSERIAL PRIMARY KEY,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    lang VARCHAR(5),
    interactions SMALLINT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
