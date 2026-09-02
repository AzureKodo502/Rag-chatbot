# Handoff — RAG Portfolio Chatbot

Questo file esiste per far ripartire il lavoro (con Claude Code o in una nuova
chat) esattamente da dove si era interrotto, senza dover riscoprire tutto da
zero. Leggilo per intero prima di proporre modifiche.

## Chi sono e perché costruisco questo

Sono uno sviluppatore neolaureato in Informatica, in cerca di lavoro come
Junior Backend/Software Developer (ho un part-time indeterminato nel
frattempo). Sto candidandomi con un CV che linka due repository GitHub
esistenti (un e-commerce Java/Spring Boot/JavaFX, una simulazione economica
Python) e sto costruendo **questo chatbot RAG come terzo progetto**, per
mostrare competenze AI/LLM oltre al backend puro. Il link a questo progetto
finirà anch'esso nel CV, quindi deve reggere bene anche a un tech lead che
apre il codice sorgente, non solo a chi lo prova in chat.

Livello: prima esperienza pratica seria con backend/Docker/API/deploy. Se
proponi comandi, spiega cosa fanno; non dare per scontato nulla su
Docker/PowerShell/IntelliJ.

## Stack

Backend Spring Boot 3 (Java 21) in IntelliJ, PostgreSQL + pgvector (Docker
locale, volume persistente `rag-postgres-data`), embeddings via Voyage AI
(`voyage-3-lite`, 512 dimensioni), generazione risposte via Claude (Anthropic
Messages API, modello `claude-sonnet-4-6`), frontend HTML/CSS/JS standalone
(`frontend/index.html`, stile "notebook tecnico" chat in stile terminale).
Ambiente: Windows, PowerShell dentro IntelliJ.

## Stato: Fase 0 e Fase 1 — COMPLETATE

- Contenuti reali scritti in `content/` (cv.txt, progetto1.txt, progetto2.txt,
  progetto3-questo-chatbot.txt — quest'ultimo ingestito con `source =
  "progetto3.txt"`). Cartella `content/` è in `.gitignore`, mai committare
  dati personali.
- Ogni sezione `[Nome Progetto - Argomento]` include il nome per esteso del
  progetto (non solo "Progetto 1"), perché il chunking è paragraph-aware
  (spacca per riga vuota) e ogni chunk deve reggersi da solo.
- Postgres + pgvector locale, ingestion di tutti e 4 i file completata e
  verificata (conteggi per fonte confermati via SQL).
- Pipeline RAG end-to-end funzionante: retrieval + generazione con Claude,
  citazioni delle fonti, pannello debug, tutto verificato manualmente e da
  browser.

### Bug reali risolti in Fase 0/1 (utile per non riproporli come "novità")

1. **Lombok incompatibile** col JDK usato da IntelliJ → rimosso dal `pom.xml`
   (non veniva usato nel codice comunque).
2. **Ingestion 400 Bad Request** → `Get-Content` di PowerShell restituisce
   stringhe "arricchite" che `ConvertTo-Json` serializza come oggetto
   `{"value": "..."}` invece di stringa semplice. Fix: usare
   `[System.IO.File]::ReadAllText(...)` invece di `Get-Content`.
3. **Rate limit Voyage AI (429)** su account senza metodo di pagamento (3
   RPM). Fix: retry con backoff esponenziale in `EmbeddingService`, più un
   ritardo fisso tra chunk in `IngestionService` durante l'ingestion.
4. **Retrieval restituiva sempre 0 risultati** nonostante dati presenti e
   validi → causato dall'indice `ivfflat` creato su una tabella con solo 15
   righe (index con `lists=100` tarato per dataset grandi produce ricerche
   approssimate che su pochi dati possono restituire risultati vuoti).
   Fix: `DROP INDEX`, rimosso anche dallo schema (`V1__init.sql`) — con
   poche centinaia di chunk una scansione sequenziale è già istantanea e
   sempre esatta.
5. **CORS "protocol schemes" error** aprendo `frontend/index.html` come file
   locale → `API_URL` era un percorso relativo (`/api/chat`), risolto in
   `file:///C:/api/chat`. Fix: URL assoluto (`http://localhost:8080/api/chat`).
6. **input_type embedding sbagliato**: si usava `"document"` sia per
   indicizzare sia per le query utente. Voyage ottimizza diversamente le due
   cose. Fix: `EmbeddingService.embedDocument()` vs `embedQuery()`.

## Stato: Fase 2 (sicurezza + UX) — COMPLETATA (verificata a runtime 2026-08-31)

### Completato e verificato

- Rate limiting Tier 1 di base (3/min, 10/giorno)
- System prompt hardening con 5 regole anti-injection, verificato con
  domande "cattive" reali
- Delimitatori XML (`<context>...</context>`) nel system prompt
- Pre-filtering leggero (`PromptGuardService`) contro pattern noti di
  injection, con test reale che ha bloccato un tentativo prima di chiamare
  l'LLM (zero costo API)
- Textarea che cresce e va a capo (non più input a riga singola), Invio per
  inviare / Shift+Invio per andare a capo
- Contatore caratteri 0/300 live

### Fix di Fase 2 — VERIFICATI A RUNTIME (2026-08-31)

Tutti e 5 i punti sono stati provati con backend Spring Boot avviato +
Postgres Docker + browser reale (frontend servito da `http://127.0.0.1:8000`,
che rispetto a `localhost:8080` è cross-site come il caso `file://`).
Esito test:
- off-by-one: 3 richieste consecutive → tutte HTTP 200 (la 3ª con
  `rem-min=0`), la 4ª → 429. Prima del fix la 3ª era 429.
- contatore: a video ora "3/3 richieste al minuto · 1/10 oggi".
- messaggio giornaliero: `formatDateTime` → "31/08 alle 00:02",
  lock message completo verificato via console.
- dev mode: login cross-site → chat successiva HTTP 200 con
  `X-Admin-Bypass: true` e badge "🔓 dev mode attiva" dopo reload; il
  cookie `rag_admin_token` (SameSite=None; Secure) viene inviato dal
  browser anche cross-site. Con `SameSite=Lax` non sarebbe partito.

Nota minore NON risolta (fuori scope): le richieste già bloccate (429)
continuano comunque a incrementare il contatore giornaliero. Un abuser
che martella l'API mentre è rate-limited brucia la propria quota
giornaliera più in fretta. Il frontend blocca l'input quindi un utente
normale non ci arriva. Se si vuole sistemare: in `RateLimitService`
non contare le richieste quando `!allowed`.

Per demo/test: riavviare il backend azzera i contatori (Caffeine in
memoria). Durante questa sessione la quota giornaliera dell'IP `127.0.0.1`
è stata consumata dai test.

1. **Bug off-by-one nel rate limiting** — CORRETTO in
   `RateLimitService.combine()`. La vecchia condizione `allowed =
   remainingMinute > 0 && remainingDay > 0` rifiutava l'ultima richiesta
   legittima (la 3ª arrivava con `used == limit` → `remaining == 0` →
   bloccata). Ora: `allowed = minuteUsed <= perMinuteLimit && dayUsed <=
   perDayLimit`. Il messaggio d'errore viene impostato solo quando
   `!allowed`. Rifattorizzato anche il calcolo di `remaining` per usare
   `Math.max(ipUsed, sessionUsed)` invece di due `Math.min` annidati.
   **Verifica**: con "1/3 richieste al minuto" a video, il 3° messaggio
   deve andare a buon fine e poi l'input si blocca con countdown.

2. **Testo contatore minuto** — FATTO. `renderRateCounter()` in
   `frontend/index.html` ora dice `"N/3 richieste al minuto"`.

3. **Messaggio limite giornaliero** — FATTO. Aggiunta la funzione
   `formatDateTime(epochMs)` in `frontend/index.html` (`"DD/MM alle
   HH:MM"`), usata in `refreshLockAndCountdown()` al posto di
   `formatClock`. Nuovo testo: `"Limite giornaliero raggiunto —
   sbloccherai nuove richieste il DD/MM alle HH:MM."`

4. **Modalità sviluppatore non bypassava il rate limit** — DIAGNOSI +
   FIX. Causa: i cookie `rag_admin_token` e `rag_session_id` erano
   `SameSite=Lax`. Il frontend è aperto come `file://` (o comunque da
   un'origine diversa dal backend), quindi ogni `fetch` verso
   `localhost:8080` è **cross-site**: con `SameSite=Lax` il browser NON
   rimanda il cookie, così `isAdmin` in `ChatController.chat()` restava
   sempre `false`. Fix: entrambi i cookie ora sono `SameSite=None; Secure`
   (`AdminController` e `ChatController`). `Secure` è accettato anche su
   `http://localhost`; in produzione il backend è su HTTPS.
   - Stessa causa spiega perché il rate-limit "per sessione" non ha mai
     morso finora: restava attivo solo il layer per IP.
   - **Verifica**: attiva dev mode → deve comparire il badge "🔓 dev mode
     attiva" e il contatore "🔓 modalità sviluppatore — richieste
     illimitate"; i messaggi successivi non devono più dare 429. In
     DevTools → Application → Cookies → `http://localhost:8080` devono
     esserci `rag_admin_token` e `rag_session_id` con SameSite=None.
   - Se anche con `SameSite=None; Secure` il cookie non venisse
     memorizzato (alcuni browser sono restrittivi coi cookie su richieste
     partite da `file://`), il fix definitivo è servire `index.html` come
     risorsa statica di Spring Boot sulla stessa origine — è già previsto
     nella fase di deploy della ROADMAP.

5. **Bug latente in `application.yml`** — CORRETTO. La chiave
   `admin.bypass-password` era annidata per errore sotto `voyage:`
   (diventava `voyage.admin.bypass-password`). Funzionava solo grazie al
   relaxed binding sulla env var `ADMIN_BYPASS_PASSWORD`. Ora `admin:` è
   una sezione top-level, coerente con `@Value("${admin.bypass-password:}")`
   in `AdminSessionService`.

Nota: il "cookie di sessione HttpOnly/SameSite" della roadmap Fase 2 è di
fatto già coperto dal rate-limit dual-layer (IP + `rag_session_id`).

## File toccati in Fase 2 e Fase 3 (per orientarsi rapidamente)

- `src/main/java/com/portfolio/ragchatbot/service/RateLimitService.java`
- `src/main/java/com/portfolio/ragchatbot/service/PromptGuardService.java` (nuovo)
- `src/main/java/com/portfolio/ragchatbot/service/AdminSessionService.java` (nuovo)
- `src/main/java/com/portfolio/ragchatbot/controller/AdminController.java` (nuovo)
- `src/main/java/com/portfolio/ragchatbot/controller/ChatController.java`
- `src/main/java/com/portfolio/ragchatbot/service/ChatService.java` (system prompt)
- `src/main/java/com/portfolio/ragchatbot/service/AnthropicClient.java`
  (`base-url` → `messages-url`)
- `src/main/resources/application.yml` (`rag.rate-limit`, `admin.bypass-password`
  spostata top-level, `retrieval.top-k` 4→6, `ingestion.delay-between-chunks-ms`
  15s→21s, `anthropic.messages-url`)
- `frontend/index.html` (textarea, contatori, dev mode, schermata cold start,
  toggle IT/EN + dizionario `STRINGS` i18n, card di feedback a stelle)
- `content/*.txt` (Fase 3: sezioni CV arricchite + link GitHub + metriche progetti)
- `.claude/launch.json` (nuovo, helper di test — non essenziale)
- `src/main/resources/db/migration/V2__feedback.sql` (nuovo — tabella
  `feedback`, da applicare a mano)
- `src/main/java/com/portfolio/ragchatbot/repository/FeedbackRepository.java` (nuovo)
- `src/main/java/com/portfolio/ragchatbot/service/FeedbackService.java` (nuovo)
- `src/main/java/com/portfolio/ragchatbot/controller/FeedbackController.java` (nuovo)
- `summary.json` (nuovo, radice, gitignorato — sezioni statiche della scheda)
- `src/main/java/com/portfolio/ragchatbot/service/SummaryCard.java` (nuovo)
- `src/main/java/com/portfolio/ragchatbot/service/SummaryService.java` (nuovo)
- `src/main/java/com/portfolio/ragchatbot/controller/SummaryController.java` (nuovo)
- `src/main/java/com/portfolio/ragchatbot/repository/VectorRepository.java`
  (aggiunto `findAll()`)

## Decisioni di design già prese (non riproporle da zero)

Vedi `ROADMAP.md` per l'elenco completo di cosa è stato deciso di **non**
implementare e perché (pipeline injection a due step con LLM-giudice,
semantic caching vero, dashboard con tracciamento individuale dei
visitatori) — sono scelte di scope consapevoli per un progetto portfolio,
non cose dimenticate.

## Stato: Fase 3 — arricchimento contenuti FATTO (2026-08-31)

Primo punto della Fase 3 completato: i file in `content/` sono stati
arricchiti partendo dal CV reale (`content/Oleksandr_Bevtsyk_Curriculum_2.pdf`,
in cartella gitignored) e dalle risposte dirette dell'utente.

- `cv.txt`: da 4 a 15 sezioni. Nuove: formazione (UniCal→Pegaso, 102/110,
  2022-2026 — corretto da 2021-2026 il 2026-09-02), contatti (SOLO email
  `bevtsik98@gmail.com` — regola privacy),
  lingue (IT madrelingua / rumeno / EN B2, nessuna certificazione),
  esperienza lavorativa (cameriere 2021-22, McDonald's set-dic 2024, KFC
  da dic 2024 tuttora a tempo indeterminato — mai lavorato come
  sviluppatore), disponibilità (subito se urgente, altrimenti ~15gg
  preavviso + trasferimento), cittadinanza (ucraina, obiettivo italiana),
  residenza/patente (Lamezia Terme, patente B, automunito), competenze
  estese (C++, Linux, Pandas, JDBC, FXML/Scene Builder), repository GitHub
  (profilo AzureKodo502 + 2 link), metodo di lavoro (no test automatici,
  no CI/CD, workflow Git sì).
- `progetto1/2/3.txt`: aggiunte metriche concrete dal CV, link GitHub,
  identificazione tesi = progetto2 ("Simulazione Gestionale Azienda
  Agricola"), parametri esatti dei modelli (voyage-3-lite 512d,
  claude-sonnet-4-6, chunk 220/overlap 40), stato deploy.
- KB ri-ingerita da zero: **29 chunk** (cv 14, p1 5, p2 4, p3 6). Sorgenti
  invariate: `cv.txt`, `progetto1.txt`, `progetto2.txt`, `progetto3.txt`.

### Modifiche di configurazione (richiedono restart backend)

- `rag.retrieval.top-k`: 4 → **6** (migliora il recall su domande generiche
  tipo "che esperienza lavorativa ha").
- `rag.ingestion.delay-between-chunks-ms`: 15000 → **21000**. 15s davano 4
  richieste/minuto e sfondavano il limite Voyage free tier (3 RPM) quasi
  a ogni ingestion.
- `application.yml`: `anthropic.base-url` **rinominata in
  `anthropic.messages-url`** (+ modifica corrispondente in
  `AnthropicClient.java`). Motivo: la env var standard `ANTHROPIC_BASE_URL`
  (impostata da alcune shell, incluse quelle degli strumenti AI) veniva
  mappata da Spring su `anthropic.base-url` via relaxed binding,
  sovrascrivendo il valore YAML e mandando le richieste a
  `https://api.anthropic.com` senza `/v1/messages` → 404 Cloudflare.
  Con IntelliJ non capitava (env pulito); è emerso avviando il backend da
  terminale. `voyage.base-url` ha lo stesso rischio teorico ma
  `VOYAGE_BASE_URL` non è una env var diffusa — lasciata così, valutare
  se rinominarla per coerenza.

### Verifica (giro domande recruiter/tech lead, backend + KB reali)

OK: voto+università, titolo tesi, contatti (dà solo email, rifiuta
telefono/indirizzo), test automatici (risposta onesta), lingue, esperienza
lavorativa, disponibilità al trasferimento (con la sfumatura urgenza/
preavviso), cittadinanza, modelli AI esatti, link GitHub, deploy,
tentativo di prompt injection (bloccato da PromptGuard).

### Incongruenze CV ↔ contenuto da decidere (non bloccanti)

- **UniCal non è sul CV** (il CV cita solo Pegaso 09/2021–06/2026). Il bot
  ora dice "UniCal poi Pegaso". Se un recruiter confronta, nota la
  differenza: valutare se aggiungere UniCal anche al CV.
- Date cameriere: CV dice 06/2021–06/2022, l'utente aveva detto 2019–2021.
  Nel contenuto sono state usate le date del CV.

### Schermata cold start — FATTA (2026-08-31)

`frontend/index.html`: nuovo overlay `#cold-start` mostrato al load (prima
ancora che parta il JS, così non lampeggia la UI "rotta"). Skeleton con
shimmer che ricalca intro + chip; sotto, dot pulsante + messaggio a
rotazione per tempo trascorso ("😴 Sto svegliando il backend…" a 0s,
"☕…" a 8s, "🛠️…" a 22s, "⏳…" a 45s). `wakeBackend()` sostituisce la
vecchia `checkStatusOnLoad()` al load: ritenta `GET /status` (timeout 12s
per tentativo, pausa 2s) finché risponde o fino a 90s; al successo applica
gli header rate-limit e fa il fade dell'overlay (min 450ms di permanenza
per non sfarfallare col backend caldo); al fallimento mostra
"⚠️ Non riesco a contattare il backend…" + pulsante "Riprova".
`prefers-reduced-motion` disattiva shimmer e pulse. `checkStatusOnLoad()`
resta usata solo dal flusso di login modalità sviluppatore.
Verificato in browser: backend caldo (fade rapido), skeleton visibile,
stato di errore, pulsante Riprova.

Retrieval: "con che votazione si è laureato" da sola non recupera il
chunk giusto (query troppo corta/sparsa) — atteso, lo risolve la hybrid
search (ora Fase 5, post-deploy). "Che voto ha preso alla laurea" invece
funziona.

## Stato: Fase 3 — toggle IT/EN FATTO (2026-09-01)

Terzo punto della Fase 3 completato. Solo `frontend/index.html`, nessuna
modifica al backend.

- Segmented control a pillola (stile iOS) nella barra in alto, come primo
  elemento del gruppo di destra. `IT | EN` con riquadro scorrevole sul
  segmento attivo; rispetta `prefers-reduced-motion`. Adattato alla palette
  esistente (`--ink` / `--paper`, font monospace).
- Tutti i testi UI passano da un dizionario `STRINGS = { it: {...}, en:
  {...} }`. `applyLanguage()` popola al load: `<title>`, `<html lang>`,
  eyebrow / h1 / paragrafo intro, le 3 chip (che in EN mandano la domanda
  in inglese all'LLM), placeholder textarea, "Invia", label "tu"/"assistente",
  "fonte (N)" / "similarità", pannello debug, contatori rate limit, messaggi
  di lock (minuto + giornaliero, con `formatDateTime` che ora usa "alle"/"at"),
  messaggi cold start + "Riprova", prompt/alert modalità sviluppatore,
  messaggi d'errore.
- Scelta salvata in `localStorage['lang']` (`it` default), **reload della
  pagina al cambio** — così chip e titolo compaiono già tradotti e non c'è
  stato stale. `try/catch` attorno a localStorage.
- **Decisione di design (presa dall'utente):** il toggle governa SOLO la UI.
  La lingua della risposta dell'LLM continua a seguire la lingua della
  domanda (regola già nel system prompt di `ChatService.java`, non toccata).
  Quindi un visitatore che scrive in spagnolo riceve risposta in spagnolo
  anche col toggle su IT/EN: accettato come comportamento (recruiter di
  altra lingua serviti meglio; nessun rischio di sicurezza, niente viene
  "eluso").
- Verificato in browser: IT↔EN con reload e persistenza, slider che scorre,
  tutte le stringhe tradotte, nessun errore JS (a parte il fetch `/status`
  fallito perché il backend era spento), layout mobile 375px senza overflow
  orizzontale.

## Stato: Fase 3 — feedback a stelle FATTO lato codice (2026-09-01)

Quarto punto della Fase 3. Tocca frontend + backend + un nuovo file di
migrazione DB.

### ⚠️ Da fare a mano prima di testare/deployare

La migrazione `src/main/resources/db/migration/V2__feedback.sql` va applicata
manualmente (il progetto non usa Flyway, esattamente come V1):

    psql -h localhost -U postgres -d ragchatbot -f src/main/resources/db/migration/V2__feedback.sql

Crea la tabella `feedback`. Senza questo, `POST /api/feedback` va in errore
500 al primo invio.

### Frontend (`frontend/index.html`)

- Card `#feedback-card` come primo figlio di `<form>` (sopra `.input-row`).
  Tutti i `<button>` hanno `type="button"` — dentro un form altrimenti
  sarebbero submit e manderebbero la chat.
- Struttura: header (titolo + `✕`), `.fb-stars` (5 bottoni, hover+click,
  `role=radiogroup`), `.fb-comment` (textarea facoltativa, `maxlength=1000`),
  `.fb-actions` (Inoltra a sinistra, "Non ora" + "Non chiedermelo più" a
  destra), `.fb-done` / `.fb-error` (stati post-invio).
- Trigger: `recordInteractionAndMaybePrompt()` chiamato nel `submit` handler
  della chat SOLO su risposta `res.ok`. Incrementa
  `localStorage['fb_interactions']`; se ≥ `FEEDBACK_AFTER_INTERACTIONS` (3) e
  lo stato lo consente, mostra la card (una volta per pageview:
  `feedbackShownThisView`).
- Stato in `localStorage`: `fb_interactions` (int cumulativo per browser),
  `fb_state` (`snoozed` | `done` | `never`; assente = da proporre),
  `fb_snooze_until` (epoch ms). "Non ora" e `✕` → snooze 48h
  (`FEEDBACK_SNOOZE_MS`). "Non chiedermelo più" → `never`. Invio 2xx **o
  429** → `done` + schermata "Grazie" (429 = "già abbastanza feedback da
  questo IP di recente", per l'utente non c'è nulla da correggere). Solo
  4xx≠429 / 5xx / errore di rete → riga d'errore, bottone riabilitato, stato
  non toccato.
- Mostra la card con un reflow forzato (`void offsetWidth`) invece di
  `requestAnimationFrame`: rAF non parte se il tab è in background (era un
  bug emerso nei test).
- Stringhe IT/EN nel dizionario `STRINGS` (chiavi `fb*`), applicate da
  `applyLanguage()`. `lang` inviato al backend è la lingua UI selezionata.

### Backend (nuovi file)

- `resources/db/migration/V2__feedback.sql` — tabella `feedback` (id, rating
  1-5 con CHECK, comment, lang, interactions, created_at). **Nessun dato
  identificativo**: no IP, no user agent, no session id (scelta privacy,
  coerente con la roadmap).
- `repository/FeedbackRepository.java` — `save(...)`, `findRecent(limit)`,
  `summary()` (media arrotondata + distribuzione 1..5) via `JdbcTemplate`.
- `service/FeedbackService.java` — validazione (rating obbligatorio 1-5,
  commento trim→null se vuoto, cap 1000, lang normalizzata a it/en/null),
  anti-spam Caffeine `MAX_PER_IP_PER_DAY = 8` (IP usato solo come chiave in
  memoria a scadenza, mai salvato). Ritorna `Result.Saved|Rejected|TooMany`.
- `controller/FeedbackController.java` —
  - `POST /api/feedback` pubblico (CORS come gli altri controller): 201 se
    salvato, 400 se rifiutato, 429 se troppi.
  - `GET /api/admin/feedback` protetto: legge il cookie `rag_admin_token` e
    `adminSessionService.isValidToken(...)`; 401 se non valido. Ritorna
    `{ summary: { count, average, distribution }, items: [...] }` (ultimi
    500, `createdAt` ISO).

### Verificato

**Frontend** (browser, static server):
- Card compare dopo 3 interazioni, stelle (hover/click/aria), submit attivo
  solo con ≥1 stella, i18n IT↔EN completo, layout coerente con lo stile.
- "Non ora" / `✕` → snooze 48h in localStorage, non riappare; "Non
  chiedermelo più" → `never`; invio fallito → riga d'errore + bottone
  riabilitato, stato NON marcato `done`.
- Invio riuscito (fetch stubbata a 201) → `data-state=thanks`,
  "Grazie"/"Thanks", `fb_state=done`, card sparisce dopo ~1.9s.

**Backend** (migrazione V2 applicata a mano, backend + Docker su, `curl`):
- `POST /api/feedback` valido → 201 + riga in tabella `feedback`
- rating mancante / rating 9 → 400 con messaggio
- `comment` assente → salvato `NULL` (non stringa vuota)
- invio oltre `MAX_PER_IP_PER_DAY` (ora 20) dallo stesso IP → 429; il
  contatore Caffeine si azzera al restart del backend. **NB**: durante i
  test `curl` è facile saturarlo per `127.0.0.1` e poi vedere l'errore nel
  browser — riavviare il backend.
- `GET /api/admin/feedback` senza dev mode → 401; con cookie
  `rag_admin_token` valido → 200 JSON `{summary:{count,average,distribution},
  items:[...]}` (più recenti prima, `createdAt` ISO)
- `mvn clean compile` OK (17 sorgenti)
- righe di test poi ripulite (`TRUNCATE feedback RESTART IDENTITY`)

**Non verificato**: giro completo browser vero → backend vero in un colpo
solo (il Browser pane in-app non raggiunge `localhost:8080` dell'host). Da
fare al volo dal browser normale: fai 3 domande, compila la card, invia,
controlla la schermata "Grazie" e la riga in `feedback`.

## Stato: Fase 3 — "Genera Riassunto" FATTO lato codice (2026-09-02)

Quinto e ultimo punto della Fase 3. Frontend + backend + un nuovo file
gitignorato.

### ⚠️ Da fare a mano prima di testare/deployare

`summary.json` (radice del progetto, **gitignorato**) deve esistere: contiene
le sezioni statiche curate della scheda (IT + EN). Già creato in locale. Path
configurabile con `SUMMARY_STATIC_FILE` / `summary.static-file` (default
`summary.json`, relativo alla working dir del backend). In deploy va fornito
come file montato o env var — nota per la fase di deploy.

### Come funziona (ibrido, deciso dall'utente)

- **Sezioni statiche** (testo curato in `summary.json`): `inBreve`,
  `puntiDiForza[]`, `doveStaCrescendo[]`, `logistica`, `contatto`. Il testo
  è stato scritto e approvato dall'utente (8 punti di forza grezzi → 4
  ancorati a evidenze; 9 aree → 3; caratteriali riformulati o omessi).
- **Sezioni generate** dall'LLM sull'INTERA KB (`VectorRepository.findAll()`,
  non top-k): `formazione`, `progetti[]`, `stack`. Prompt in
  `SummaryService.GEN_SYSTEM_TEMPLATE`: output solo JSON, niente contatti,
  niente invenzioni.
- **Cache per lingua** in `SummaryService` (`ConcurrentHashMap`): la prima
  richiesta `it`/`en` genera, tutte le altre servono la cache → max 2
  chiamate API per riavvio backend. Per questo l'endpoint **non** passa dal
  rate limiter.
- `POST /api/summary` `{lang}` → `SummaryCard` completa (statico + generato).
  `POST /api/admin/summary/refresh` (cookie `rag_admin_token`) → svuota cache
  generata **e** ricarica `summary.json` da disco.

### File

- `summary.json` (nuovo, radice, gitignorato) + voce in `.gitignore`
- `service/SummaryCard.java`, `service/SummaryService.java`,
  `controller/SummaryController.java` (nuovi)
- `repository/VectorRepository.java` — aggiunto `findAll()`
- `application.yml` — sezione `summary.static-file`
- `frontend/index.html` — pulsante `#summary-btn` sotto le chip, pannello
  `.msg.summary` reso in `#log`, stringhe `summary*` in `STRINGS` (le
  intestazioni sezione stanno qui, i contenuti arrivano dal backend nella
  lingua giusta), `summaryDone` in stato, `summaryBtn` in lock/unlock

### Verificato

- **Frontend** (static server, backend giù): pulsante + i18n label, path
  d'errore (pending rimosso, messaggio d'errore, pulsante riabilitato),
  rendering del pannello con payload mockato — 8 sezioni nell'ordine giusto,
  pulsante nascosto dopo il successo, `fb_interactions` incrementato, nessun
  errore JS.
- **Backend** (`curl`, backend + Docker su, `mvn clean compile` OK, 20 sorgenti):
  - `POST /api/summary {lang:"it"}` 1ª volta → 200, `generatedFresh:true`, ~9s;
    formazione/progetti/stack sensati e ancorati alla KB, JSON pulito.
  - 2ª chiamata `it` → `generatedFresh:false`, ~3ms (cache).
  - `{lang:"en"}` → 200, sezioni generate in inglese, statiche dal file EN.
  - `POST /api/admin/summary/refresh` → 401 senza dev mode; 200 con cookie
    `rag_admin_token`; chiamata successiva → `generatedFresh:true` (rigenera).
  - Prompt stretto dopo un 1º giro in cui lo `stack` usciva a 30 voci: ora
    max ~12-14, raggruppate con " · ".

**Ancora da fare**: giro completo browser vero → backend vero (il Browser
pane in-app non raggiunge `localhost:8080`).

**Re-ingest FATTO (2026-09-02, ~10 min):** wipe + 4 file, KB di nuovo a 29
chunk (cv 14, p1 5, p2 4, p3 6), cache summary rigenerata. Correzioni ora
nel DB e verificate nella scheda generata:
- `progetto3`: `top-k = 4` → `6`; "versionato con Flyway" → "script SQL
  numerati applicati a mano, senza tool di migrazione" (il progetto NON usa
  Flyway)
- `cv.txt`: rimosso "FastAPI"; periodo universitario `2021`→`2022`
  (vero: 2022-2026, 4 anni — l'utente allinea il CV PDF)
- Lo `stack` generato è sceso da ~30 voci a ~13-15, raggruppate con " · ",
  senza FastAPI/Flyway/librerie di dettaglio.

### Ritocco UX (2026-09-02)

Il pulsante "Genera Riassunto" NON scrolla più la pagina: prima
`renderSummary` e il pending facevano `window.scrollTo(bottom)` e il recruiter
si ritrovava in fondo. Ora la pagina resta ferma, il pannello compare sotto
(ancora in vista) e si scorre a leggere. Posizione del pulsante (sotto le
chip): funziona, da valorizzare in Fase 6 (nota in ROADMAP).

## Riepilogo stato (2026-09-02) e prossimi passi

**Fatto:** Fase 0, 1, 2 (verificate a runtime). **Fase 3 COMPLETA**:
arricchimento `content/` + cold start + toggle IT/EN + feedback a stelle +
Genera Riassunto — tutto verificato, feedback e riassunto anche e2e via
`curl`. KB re-ingerita con i contenuti corretti. Manca solo il giro nel
browser vero (feedback + riassunto).

**ROADMAP riordinata (2026-09-02):** deploy anticipato.
- **Fase 4 = Deploy** (era Fase 5). Il prodotto è pronto, il link serve nel
  CV ora.
- **Fase 5 = Differenziatori tecnici** (hybrid search + cache esatta — era
  Fase 4). Aggiornamenti applicabili in qualsiasi momento sul sito live.
- Fase 6 = Rifinitura CV (invariata; contiene il pannello Analytics in-app e
  il ritocco alla posizione del pulsante Riassunto).
- **Fase 7 = Architettura configuration-driven** (de-hardcoding). NON un
  prodotto da vendere: separare codice / dati / policy. Config minima e
  sensata — identità, comportamento (regole system prompt), lingua, stile di
  scrittura, KB, UI. Criterio: cambi un file di config e il bot parla di
  un'altra persona, senza toccare `.java`. Multi-tenant SaaS = ipotesi
  lontana citata nella roadmap, esplicitamente non pianificata.

## Fase 4 — Deploy: preparazione codice FATTA (2026-09-02)

Stack scelto: **Neon** (DB) + **Render** (backend, free) + frontend servito
da Spring + **Dockerfile**.

**Repo GitHub (privato):** https://github.com/AzureKodo502/Rag-chabot
— pushato il 2026-09-02 (`main`, 3 commit). Verificato che il tree remoto
NON contiene `content/`, `summary.json`, `.idea/`, `target/`.
NB: il nome ha un refuso ("chabot" invece di "chatbot") — rinominabile su
GitHub prima che vada nel CV (redirect automatico + aggiornare il remote).

### Modifiche fatte (verificate: `mvn package` online OK; smoke test del jar
su :8090; **`docker build` OK + immagine avviata** contro il Postgres locale
via `host.docker.internal` → parte in ~2s su JRE 21, `/` serve l'app,
`/api/chat/status` 200, `/api/admin/ingest` 401)

- **Frontend spostato**: `frontend/index.html` → `src/main/resources/static/
  index.html`. Spring lo serve su `/`. `API_BASE` da `http://localhost:8080`
  → `""` (relativo, stessa origine). La cartella `frontend/` non esiste più.
- **`Dockerfile`** multi-stage (maven:3.9-temurin-21 → temurin-21-jre),
  `-XX:MaxRAMPercentage=75` per i 512 MB di Render. `.dockerignore`.
- **CORS**: rimosso `@CrossOrigin` da tutti i controller; nuova
  `config/WebConfig.java` (`WebMvcConfigurer`) che abilita CORS su `/api/**`
  SOLO se `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`) è
  valorizzato. Default vuoto = stessa origine, nessun header CORS.
  Verificato: preflight da origine estranea → 403.
- **`/api/admin/ingest` protetto**: `IngestionController` ora controlla il
  cookie `rag_admin_token` (come Feedback/Summary). POST e DELETE → 401
  senza dev mode. **Lo script `reingest.sh` ora fa login prima** (accetta
  `BASE` e `ADMIN_PW` per puntare a produzione).
- **`application.yml`**: `server.port: ${PORT:8080}`;
  `spring.datasource.url` con `?sslmode=${DB_SSLMODE:disable}` (Neon =
  `require`); `hikari.maximum-pool-size: ${DB_POOL_SIZE:5}` (Neon free ha
  poche connessioni); sezione `app.cors.allowed-origins`.
- **`.gitattributes`** (`eol=lf`), **`.claude/launch.json`** → config
  "attach" a `http://localhost:8080` (il frontend-static non serve più).
- README: sezione Deploy riscritta con i passi Neon/Render + tabella env var.

### DA FARE (richiede account dell'utente — non fattibile da qui)

1. ~~Repo GitHub + push~~ — FATTO (2026-09-02)
2. Neon: progetto → `CREATE EXTENSION vector` → applica `V1` + `V2` →
   copiare host/db/user/password
3. Render: Web Service da Docker, env var (vedi tabella nel README), Secret
   File `summary.json` + `SUMMARY_STATIC_FILE=/etc/secrets/summary.json`
4. Dopo il 1° deploy: `BASE=https://... ADMIN_PW=... bash reingest.sh`
5. Test da un altro dispositivo

### Env var necessarie su Render

`ANTHROPIC_API_KEY`, `VOYAGE_API_KEY`, `ADMIN_BYPASS_PASSWORD`, `DB_HOST`,
`DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSLMODE=require`,
`SUMMARY_STATIC_FILE=/etc/secrets/summary.json`. (`PORT` la mette Render.)

### Note / follow-up non bloccanti

- I cookie `rag_admin_token` e `rag_session_id` sono ancora
  `SameSite=None; Secure`. Con frontend same-origin `SameSite=Lax` sarebbe
  più corretto — cambio piccolo, da fare in Fase 6 (serve ri-verificare la
  dev mode).
- `readCookie` duplicato in 3 controller (Feedback, Summary, Ingestion) —
  estrarre in un helper quando si tocca quella zona (Fase 7 pulizia).
- Build locale: `mvn package` offline fallisce (surefire non in cache);
  usare online, oppure `mvn compile` per il check veloce. Il Docker build
  gira online e fa il `package` completo.

**Prossimo passo concreto:** l'utente crea repo GitHub + account Neon +
Render, poi push e deploy.

**Come ripartire:** basta leggere questo file + `ROADMAP.md` + il codice.
Niente da ricordare a memoria oltre a questo. Le note persistenti stanno
anche in `.claude/projects/.../memory/`.

### Trappole da tenere a mente

- **`ANTHROPIC_BASE_URL`**: se avvii il backend da un terminale che ha
  questa env var (es. shell di tool AI), Spring la mappa e rompe le
  chiamate a Claude (404). Da IntelliJ non capita. Config key rinominata
  `anthropic.base-url` → `anthropic.messages-url` per mitigare; `unset`
  la var se serve.
- **Voyage AI free tier = 3 richieste/minuto.** L'ingestion si autoregola
  a `delay-between-chunks-ms` (21s). Mai lanciare due ingestion insieme.
- **Re-ingestion**: `DELETE` poi `POST` su `/api/admin/ingest` per ognuno
  dei 4 file (no dedup, no delete-by-source → sempre wipe totale + ricarico
  dei 4). Con delay 21s: ~10 min. KB attuale: 29 chunk.
- **Privacy**: il bot deve dare solo l'email come contatto, mai
  telefono/indirizzo (garantito dal non metterli nei `content/*.txt`).
- **`.claude/launch.json`** (config `frontend-static`): serve `frontend/`
  su `http://127.0.0.1:8000` per testare in un browser vero senza le
  restrizioni di `file://`. Opzionale.

### Decisioni chiuse (2026-09-01)

- **UniCal**: l'utente aggiunge UniCal al CV (percorso completo UniCal →
  trasferimento a Pegaso). Il contenuto in `content/cv.txt` resta com'è
  ("UniCal poi Pegaso"); una volta aggiornato il PDF, CV e bot sono
  allineati. Nessun disclaimer nel bot.
- **Date "cameriere"**: confermate quelle del CV (2021-2022). Il 2019-2021
  detto a voce si ignora.
