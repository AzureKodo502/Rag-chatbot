# RAG Portfolio Chatbot

**EN** · [Read in English](README.en.md)

Chatbot che risponde a domande sul mio percorso, i miei progetti e lo stack
tecnico usato, basandosi su Retrieval-Augmented Generation: le risposte sono
generate a partire dai contenuti reali del mio CV/README, non inventate.

## Live

**https://rag-chatbot-0uwq.onrender.com** — provalo direttamente, non serve
scaricare o configurare nulla. (Free tier: se non viene usato da un po' il
primo caricamento può metterci qualche secondo a svegliarsi.)

## Demo

Una domanda con risposta, la scheda "Genera Riassunto", poi la
modalità sviluppatore con il pannello analytics (voti dei feedback e domande
più frequenti).



https://github.com/user-attachments/assets/17589a5f-299f-4992-8d06-438226bc71ea



## Stack

- **Backend**: Spring Boot 3 (Java 21), REST API
- **Vector store**: PostgreSQL + estensione `pgvector`
- **Embeddings**: Voyage AI (Anthropic non espone un endpoint di embeddings
  proprio, e consiglia Voyage AI come provider)
- **Generazione risposta**: Claude (Anthropic Messages API)
- **Frontend**: pagina singola HTML/CSS/JS, nessun framework richiesto

## Decisioni di design

Alcune scelte che ho fatto di proposito, col motivo dietro — comprese le cose
che ho deciso di **non** costruire.

- **RAG, non fine-tuning.** Non ho riaddestrato nessun modello: le risposte
  vengono da retrieval + un prompt, non da pesi modificati. Per un caso come
  questo, dove i contenuti cambiano (aggiorno un file, re-ingest, fine), è la
  scelta giusta: costa meno, aggiorno la knowledge base in 10 minuti e posso
  citare la fonte esatta di ogni risposta. Il fine-tuning avrebbe senso se
  dovessi cambiare lo *stile* del modello, non le *informazioni* che ha.
- **pgvector su Postgres, non un vector DB dedicato.** Ho scartato apposta
  Pinecone/Weaviate/Qdrant: con poche centinaia di chunk avrei aggiunto un
  servizio, un account e una fattura in più per un problema che Postgres con
  un'estensione risolve benissimo. Se la knowledge base crescesse di ordini
  di grandezza rivaluterei — oggi sarebbe complessità comprata in anticipo
  per un problema che non ho.
- **Niente indice ANN sulla tabella dei chunk.** Storia vera: all'inizio
  avevo l'indice `ivfflat` (quello "giusto" per pgvector) e il retrieval
  restituiva sistematicamente zero risultati anche con dati validi. Un
  indice approssimato, tarato per dataset grandi, su una tabella di 15-30
  righe fa ricerche imprecise che a volte non trovano niente. L'ho tolto:
  con questi volumi una scansione sequenziale è istantanea *ed esatta*. Non
  l'ho letto su un blog, l'ho scoperto debuggando un bug vero.
- **Ogni chunk deve reggersi da solo.** Il retrieval recupera un pezzo di
  testo isolato dal resto del documento: se scrivo "Progetto 1" senza il
  nome per esteso, il modello riceve quel pezzo e non sa cosa significhi.
  Per questo ogni sezione della knowledge base ripete il nome completo del
  progetto o dell'argomento, anche a costo di essere ridondante quando la
  leggi tutta insieme.
- **Rate limiting su due livelli, IP e sessione.** Da solo l'IP non basta
  (una NAT aziendale o una VPN mette più persone dietro lo stesso
  indirizzo), da sola la sessione nemmeno (un cookie si cancella). Li
  combino: basta che uno dei due sfori per bloccare. Anche qui un bug vero
  a monte: la prima versione aveva un off-by-one che bloccava la 3ª
  richiesta legittima su un limite di 3 — trovato e corretto verificando a
  mano il conteggio.
- **Niente secondo LLM a fare da giudice sul prompt injection.** Avrei
  potuto mandare ogni domanda a un modello "guardiano" prima di rispondere.
  Raddoppia le chiamate e i costi per un rischio che qui è basso: il bot
  parla solo del mio percorso pubblico, non ha accesso a dati sensibili né
  esegue azioni. Rate limiting + regole nel system prompt + un pre-filtro
  con pattern noti coprono la maggior parte dei tentativi a costo quasi
  zero.
- **La scheda "Genera Riassunto" è ibrida apposta.** Punti di forza e aree
  di crescita li ho scritti io, a mano, e restano fissi: su un argomento
  delicato come parlare di me stesso e dei miei limiti voglio controllare
  esattamente cosa viene detto, non sperare che il modello lo dica bene.
  Formazione, progetti e stack invece li genera l'LLM dalla knowledge base,
  perché sono fatti oggettivi dove la generazione automatica non rischia di
  scrivere qualcosa di storto — e intanto la scheda mostra una vera
  capacità di sintesi del sistema, non solo domanda-risposta.
- **Free tier ovunque, gestendo i cold start invece di pagarli via.**
  Database e backend sono su piani gratuiti che si addormentano dopo pochi
  minuti di inattività. Avrei potuto pagare per tenerli sempre accesi; ho
  preferito risolverlo con una schermata di caricamento onesta (più un ping
  periodico che li tiene svegli il più possibile) invece di spendere per un
  problema che con un po' di UX si gestisce gratis. Se questo progetto
  dovesse reggere traffico vero, la prima cosa che cambierei è proprio
  questa.
- **Niente framework frontend.** HTML/CSS/JS puri, zero build step, zero
  `node_modules`. Per una pagina con questa complessità (una chat, un paio
  di pannelli, pochi stati) un framework avrebbe aggiunto peso senza
  aggiungere niente che non sapessi già fare a mano.
- **Analytics senza tracciare le persone.** Salvo il voto del feedback, il
  testo del commento, le domande fatte — mai un IP, mai un cookie di
  sessione, mai niente che colleghi due azioni alla stessa persona. Non è
  timidezza: un chatbot legato a un CV riceve poche decine di visite, non
  mi serve un profilo per visitatore, e tracciarle comunque solleverebbe
  questioni di privacy vere per un beneficio che non esiste.

## Struttura del progetto

```
rag-portfolio-chatbot/
├── pom.xml
├── Dockerfile                             -> build multi-stage per il deploy
├── .github/workflows/keep-alive.yml       -> ping periodico di backup (Render + Neon)
├── summary.json                           -> sezioni statiche "Genera Riassunto" (gitignorato)
├── src/main/java/com/portfolio/ragchatbot/
│   ├── RagChatbotApplication.java
│   ├── config/WebConfig.java              -> CORS (disattivo di default, stessa origine)
│   ├── controller/
│   │   ├── ChatController.java            -> POST /api/chat, GET /api/chat/status
│   │   ├── AdminController.java           -> POST /api/admin/login (modalità sviluppatore)
│   │   ├── IngestionController.java       -> POST/DELETE /api/admin/ingest (protetto)
│   │   ├── FeedbackController.java        -> POST /api/feedback, GET /api/admin/feedback
│   │   ├── QuestionLogController.java     -> GET /api/admin/questions (domande più frequenti)
│   │   ├── SummaryController.java         -> POST /api/summary, POST /api/admin/summary/refresh
│   │   ├── HealthController.java          -> GET /api/health (keep-alive + sveglia Neon)
│   │   └── HttpRequests.java              -> helper condivisi: cookie, IP del client
│   ├── service/
│   │   ├── EmbeddingService.java          -> chiama Voyage AI
│   │   ├── AnthropicClient.java           -> chiama Claude
│   │   ├── ChatService.java               -> orchestrazione RAG + system prompt
│   │   ├── IngestionService.java          -> chunking + salvataggio
│   │   ├── FeedbackService.java           -> validazione + anti-spam feedback
│   │   ├── SummaryService.java            -> scheda "Genera Riassunto" (statico + LLM), cache
│   │   ├── PromptGuardService.java        -> pre-filtro pattern di prompt injection
│   │   ├── RateLimitService.java          -> rate limit a due livelli, IP + sessione
│   │   └── AdminSessionService.java       -> token della modalità sviluppatore
│   ├── repository/
│   │   ├── VectorRepository.java          -> query pgvector via JdbcTemplate
│   │   ├── FeedbackRepository.java
│   │   └── QuestionLogRepository.java
│   └── model/DocumentChunk.java
└── src/main/resources/
    ├── application.yml
    ├── db/migration/
    │   ├── V1__init.sql                   -> schema + estensione pgvector
    │   ├── V2__feedback.sql               -> tabella feedback
    │   └── V3__question_log.sql           -> tabella question_log
    ├── knowledge-base/cv-sample.txt       -> SOSTITUISCI con i tuoi contenuti
    └── static/index.html                  -> pagina chat, servita da Spring su /
```

## Guida Setup Locale

Il link in cima è già live: questa sezione (e la successiva su Deploy) non
serve per provare il chatbot, ma per chi vuole eseguirlo in locale o capire
come l'ho distribuito in produzione.

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

## Privacy e dati raccolti

Ai fini di miglioramento e monitoraggio delle prestazioni il sistema salva
**solo**:

- il **testo delle domande** poste al chatbot, per capire cosa viene chiesto
  più spesso — senza niente che le colleghi a chi le ha scritte;
- la **valutazione** del feedback e l'eventuale **testo del
  commento**, che è facoltativo.

Non viene salvato nient'altro: nessun indirizzo IP, nessun dato personale,
nessun identificativo che permetta di ricondurre due azioni alla stessa
persona. Le tabelle `question_log` e `feedback` non hanno nemmeno una colonna
per l'IP o per la sessione.

L'unico cookie è `rag_session_id`, un identificatore casuale usato
**esclusivamente** per il rate limiting (evitare che la stessa sessione superi
il limite di richieste): non contiene informazioni personali e non serve a
tracciare la navigazione. Anche l'indirizzo IP viene usato solo per il rate
limiting, in memoria e sul momento, e non viene mai scritto da nessuna parte.

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

## Come è stato sviluppato

Questo progetto è stato costruito in coppia con un agente di programmazione
(Claude Code). Le decisioni di architettura (la separazione degli endpoint di
keep-alive per non consumare le risorse gratuite del database, il
privacy-by-design nei log, il rate limiting a due livelli), l'individuazione e
la diagnosi dei bug — come quello dell'indice `ivfflat` che restituiva zero
risultati, o l'off-by-one nel rate limiter — e la validazione di ogni scelta
sono mie; l'IA ha accelerato la scrittura del codice. L'obiettivo era usare
l'IA dove fa risparmiare tempo, sul codice ripetitivo e sul boilerplate,
senza delegarle il ragionamento.
