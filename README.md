# RAG Portfolio Chatbot

Chatbot che risponde a domande sul mio percorso, i miei progetti e lo stack
tecnico usato, basandosi su Retrieval-Augmented Generation: le risposte sono
generate a partire dai contenuti reali del mio CV/README, non inventate.

## Stack

- **Backend**: Spring Boot 3 (Java 21), REST API
- **Vector store**: PostgreSQL + estensione `pgvector`
- **Embeddings**: Voyage AI (Anthropic non espone un endpoint di embeddings
  proprio, e consiglia Voyage AI come provider)
- **Generazione risposta**: Claude (Anthropic Messages API)
- **Frontend**: pagina singola HTML/CSS/JS, nessun framework richiesto

## Struttura del progetto

```
rag-portfolio-chatbot/
├── pom.xml
├── src/main/java/com/portfolio/ragchatbot/
│   ├── RagChatbotApplication.java
│   ├── controller/
│   │   ├── ChatController.java       -> POST /api/chat
│   │   └── IngestionController.java  -> POST/DELETE /api/admin/ingest
│   ├── service/
│   │   ├── EmbeddingService.java     -> chiama Voyage AI
│   │   ├── AnthropicClient.java      -> chiama Claude
│   │   ├── ChatService.java          -> orchestrazione RAG
│   │   └── IngestionService.java     -> chunking + salvataggio
│   ├── repository/VectorRepository.java -> query pgvector via JdbcTemplate
│   └── model/DocumentChunk.java
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/V1__init.sql     -> schema + estensione pgvector
│   ├── db/migration/V2__feedback.sql -> tabella feedback
│   ├── knowledge-base/cv-sample.txt  -> SOSTITUISCI con i tuoi contenuti
│   └── static/index.html             -> pagina chat, servita da Spring su /
├── Dockerfile                        -> build multi-stage per il deploy
└── summary.json                      -> sezioni statiche "Genera Riassunto" (gitignorato)
```

## Setup locale

### 1. Database

Serve un Postgres con estensione `pgvector`. Il modo più veloce è Docker:

```bash
docker run --name rag-postgres -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=ragchatbot -p 5432:5432 -d ankane/pgvector
```

Poi applica le migrazioni (il progetto non usa Flyway: si lanciano a mano,
in ordine):

```bash
psql -h localhost -U postgres -d ragchatbot -f src/main/resources/db/migration/V1__init.sql
psql -h localhost -U postgres -d ragchatbot -f src/main/resources/db/migration/V2__feedback.sql
```

### 2. Variabili d'ambiente

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=ragchatbot
export DB_USER=postgres
export DB_PASSWORD=postgres
export ANTHROPIC_API_KEY=sk-ant-...
export VOYAGE_API_KEY=...
```

Chiavi:
- Claude: console.anthropic.com
- Voyage AI: dashboard.voyageai.com (offre crediti gratuiti iniziali)

Opzionale: `SUMMARY_STATIC_FILE` per spostare il file con le sezioni statiche
della scheda "Genera Riassunto" (default `summary.json` nella working dir).

### 2b. File `summary.json`

La feature "Genera Riassunto" legge le sezioni curate a mano (in breve, punti
di forza, aree di crescita, logistica, contatto) da `summary.json` nella
radice del progetto. È gitignorato come `content/` perché contiene
formulazioni su una persona reale. Struttura: `{ "it": {...}, "en": {...} }`
con le chiavi `inBreve`, `puntiDiForza[]`, `doveStaCrescendo[]`, `logistica`,
`contatto`. Le sezioni formazione/progetti/stack le genera l'LLM dalla KB.
Senza questo file, `POST /api/summary` risponde 502.

### 3. Avvio

Da IntelliJ (run di `RagChatbotApplication`) o da terminale con Maven:

```bash
mvn spring-boot:run
```

Il frontend è servito dallo stesso backend: apri **http://localhost:8080**.

### 4. Popolare la knowledge base

Per ora l'ingestion si fa a mano chiamando l'endpoint admin (in futuro puoi
automatizzarla con un piccolo script che legge tutti i file in
`knowledge-base/` e li invia uno per uno):

```bash
curl -X POST http://localhost:8080/api/admin/ingest \
  -H "Content-Type: application/json" \
  -d '{"source": "cv.txt", "text": "...contenuto del tuo CV..."}'
```

Ripeti per ogni documento (CV, README progetto 1, README progetto 2, testo
di presentazione, descrizione di questo stesso progetto).

### 5. Provare il frontend

Apri **http://localhost:8080** — il frontend è una risorsa statica servita
da Spring, stessa origine delle API.

## Deploy

Stack: **Neon** (Postgres + pgvector), **Render** (backend, free tier),
frontend servito da Spring, build via `Dockerfile`.

### 1. Database su Neon

1. Crea un progetto su [neon.tech](https://neon.tech) (free)
2. Nel SQL Editor: `CREATE EXTENSION IF NOT EXISTS vector;`
3. Applica lo schema (dal SQL Editor incolla il contenuto di
   `db/migration/V1__init.sql` e poi `V2__feedback.sql`)
4. Dalla pagina "Connection Details" prendi host, database, user, password

### 2. Repo su GitHub

Il progetto va su un repo Git (Render deploya da lì). `content/` e
`summary.json` restano fuori (gitignorati).

### 3. Backend su Render

1. New → Web Service → collega il repo GitHub
2. Runtime: **Docker** (il `Dockerfile` viene rilevato)
3. Variabili d'ambiente:

   | Variabile | Valore |
   |---|---|
   | `ANTHROPIC_API_KEY` | la tua chiave Claude |
   | `VOYAGE_API_KEY` | la tua chiave Voyage |
   | `ADMIN_BYPASS_PASSWORD` | una password a scelta (dev mode) |
   | `DB_HOST` `DB_NAME` `DB_USER` `DB_PASSWORD` | da Neon |
   | `DB_SSLMODE` | `require` |
   | `SUMMARY_STATIC_FILE` | `/etc/secrets/summary.json` |

4. Secret Files → aggiungi `summary.json` con il contenuto del file locale
5. Deploy. Al primo avvio Render fa la build Docker (~3-5 min).

### 4. Popolare la KB in produzione

```bash
BASE=https://tuo-servizio.onrender.com ADMIN_PW=... bash reingest.sh
```

(oppure attiva la dev mode dal sito e chiama `/api/admin/ingest` a mano per
i 4 file). ~10 min per il rate limit di Voyage.

### 5. Test

Da un dispositivo che non è il tuo, senza VPN: cold start, 5-6 domande
(incluse un paio fuori tema), feedback, "Genera Riassunto" IT + EN.

## Note sul deploy già gestite nel codice

- `/api/admin/ingest` (POST e DELETE) è protetto dal token dev mode
- CORS è disattivo di default (stessa origine); si abilita solo con
  `CORS_ALLOWED_ORIGINS` per uno sviluppo con frontend su altra origine
- La porta viene da `$PORT` (Render la assegna)
- L'IP per il rate limiting è letto da `X-Forwarded-For` (necessario dietro
  il proxy di Render)

## Sicurezza implementata

- **Chiavi API mai esposte**: `ANTHROPIC_API_KEY` e `VOYAGE_API_KEY` vivono solo
  nel backend (variabili d'ambiente); il frontend non le vede mai.
- **Rate limiting per IP** (`RateLimitService`, in-memory con Caffeine):
  default 3 richieste/minuto e 10/giorno per IP, configurabile in
  `application.yml` sotto `rag.rate-limit`. L'IP viene letto da
  `X-Forwarded-For` quando presente (necessario dietro proxy come
  Render/Railway), altrimenti da `getRemoteAddr()`.
- **Validazione input**: lunghezza massima configurabile (default 300
  caratteri, `rag.input.max-length`), controllata **prima** di chiamare
  embedding o LLM, per non sprecare token su input abusivi o troppo lunghi.
- **System prompt hardening**: il prompt in `ChatService` istruisce il
  modello a ignorare tentativi di prompt injection, a non uscire dal
  contesto professionale e a non rivelare le istruzioni di sistema stesse.
  Nota realistica: nessun prompt hardening è impenetrabile al 100%, ma
  combinato con rate limiting e limite di caratteri riduce drasticamente
  superficie e convenienza di un attacco.

## Feature di trasparenza (per chi guarda con occhio tecnico)

- **Citazione delle fonti**: ogni risposta include l'elenco dei chunk
  recuperati (`sources` nella risposta JSON), con file di origine,
  frammento di testo e punteggio di similarità coseno. Nel frontend è
  mostrato in un pannello espandibile "fonte (N)" sotto ogni risposta.
- **Ispezione query vettoriale**: lo switch "ispeziona query vettoriale"
  in alto mostra, per ogni risposta, il modello di embedding usato, il
  modello di generazione, il `top-k`, e i tempi di retrieval/generazione
  in millisecondi (`debug` nella risposta JSON).

## Possibili estensioni future

- Hybrid search (full-text + vettoriale) per i nomi esatti di
  framework/librerie che il solo embedding a volte generalizza
- Streaming della risposta (Server-Sent Events) invece di attendere la
  risposta completa
- Architettura configuration-driven: rendere identità, regole e contenuti
  configurazione esterna, così lo stesso codice serve chatbot diversi
