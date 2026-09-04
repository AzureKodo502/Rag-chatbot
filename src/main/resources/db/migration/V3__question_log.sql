-- Log delle domande poste al chatbot, per capire cosa chiedono davvero i
-- visitatori (sezione "Domande più frequenti" del pannello Analytics).
-- Come V1/V2, va applicata a mano (niente Flyway nel progetto).
--
-- Privacy: NIENTE dati identificativi. Nessun IP, nessun cookie di sessione.
-- Solo il testo della domanda, la lingua UI e quando è stata posta.
CREATE TABLE IF NOT EXISTS question_log (
    id BIGSERIAL PRIMARY KEY,
    question TEXT NOT NULL,
    lang VARCHAR(5),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
