# Roadmap — RAG Portfolio Chatbot

Principio guida: costruire prima una fetta sottile ma **reale** end-to-end
(vertical slice), poi allargare. Mai testare il sistema con knowledge base
vuota — senza contenuto vero non puoi giudicare la qualità del retrieval,
ottieni solo il fallback "non ho informazioni" per ogni domanda.

---

## Fase 0 — Contenuti reali (PRIMA di scrivere altro codice)

Senza questo, tutto il resto è impossibile da testare seriamente.

- [ ] `cv.txt` — percorso accademico, competenze tecniche, obiettivi
- [ ] `progetto1.txt` — nome, stack, problema risolto, decisioni tecniche
- [ ] `progetto2.txt` — idem
- [ ] `progetto3-questo-chatbot.txt` — sì, anche questo progetto va nella
      sua stessa knowledge base (meta, ma è esattamente cosa chiederà un
      recruiter: "come funziona questo chatbot?")
- [ ] Rileggere ogni file chiedendosi: "un tech lead che legge solo questo
      chunk isolato capirebbe di cosa parlo?" (i chunk vengono recuperati
      SENZA il contesto del documento intero)

**Criterio di completamento**: hai testo vero, non placeholder, per
tutte e 3 le fonti.

---

## Fase 1 — Vertical slice (walking skeleton)

Obiettivo: dimostrare a te stesso che la pipeline RAG funziona davvero,
prima di aggiungere qualunque feature.

- [ ] Postgres + pgvector locale (Docker), schema applicato
- [ ] Ingestion dei contenuti reali della Fase 0 tramite l'endpoint admin
- [ ] Backend: verifica manuale via `curl`/Postman di 5-6 domande reali
      (non le pillole — prova anche domande "cattive": fuori tema,
      ambigue, in inglese)
- [ ] Collegare il frontend già pronto al backend locale, stesso giro di
      domande ma dal browser

**Criterio di completamento**: fai una domanda vera su un tuo progetto e
la risposta è corretta, pertinente, e cita la fonte giusta. Se non lo è,
il problema è quasi sempre nella Fase 0 (contenuto scritto male o chunk
troppo grandi/piccoli), non nel codice.

---

## Fase 2 — Sicurezza di base (Tier 1, alto valore / basso sforzo)

Già implementate nello scheletro fornito, da verificare/rifinire:

- [ ] Rate limiting per IP (3/min, 10/giorno) — verificato manualmente
      superando i limiti
- [ ] Validazione lunghezza input (300 caratteri)
- [ ] System prompt hardening con regole anti-injection

Da aggiungere in questa fase:

- [ ] Cookie di sessione `HttpOnly`/`SameSite` combinato con l'IP per il
      rate limiting (doppio livello)
- [ ] Delimitatori XML/Markdown espliciti nel system prompt attorno al
      contesto recuperato (`<context>...</context>`), per separare
      chiaramente istruzioni da dati
- [ ] Pre-filtering leggero: lista di pattern noti ("ignora le istruzioni
      precedenti", "sei ora...", ecc.) bloccati prima di chiamare l'LLM

**Criterio di completamento**: provi tu stesso 3-4 tentativi di prompt
injection e il bot resta in tema senza rivelare le istruzioni.

---

## Fase 3 — UX che fa la differenza nel colloquio

- [x] **Arricchire i contenuti in `content/`** — FATTO (2026-08-31).
      Aggiunte a `cv.txt` le sezioni: formazione (UniCal→Pegaso, 102/110),
      contatti (solo email, per privacy), lingue (IT madrelingua / rumeno /
      EN B2), esperienza lavorativa (ristorazione, McDonald's, KFC indet.),
      disponibilità/preavviso, cittadinanza, patente, competenze estese,
      repository GitHub, metodo di lavoro (no test automatici / no CI/CD).
      Aggiunti a `progetto1/2/3`: metriche concrete, link GitHub, tesi =
      progetto2, parametri esatti dei modelli, stato deploy. KB passata da
      15 a 29 chunk. `top-k` alzato 4→6 e `delay-between-chunks-ms` 15s→21s
      (Voyage free tier = 3 RPM). Tutto verificato con un giro di domande
      da recruiter/tech lead.
      Limite noto residuo: query che uniscono due argomenti scorrelati
      (es. "modelli AI + link GitHub") a volte recuperano bene solo il
      primo — lo risolve la hybrid search della Fase 4.
- [x] Schermata cold start (skeleton loading mentre il backend si sveglia
      da sleep su free tier) — FATTO (2026-08-31). Overlay `#cold-start`
      in `frontend/index.html`: skeleton shimmer + messaggi a rotazione
      ("😴 Sto svegliando il backend…" → "☕…" → "🛠️…" → "⏳…"),
      `wakeBackend()` ritenta `/status` ogni 2s fino a 90s, poi fade;
      se fallisce mostra messaggio d'errore + pulsante "Riprova".
      Rispetta `prefers-reduced-motion`.
- [x] **Toggle IT/EN** — FATTO (2026-09-01). Segmented control a pillola
      (stile iOS) nella barra in alto, `frontend/index.html`. Cambia SOLO i
      testi dell'interfaccia (title, `<html lang>`, eyebrow/h1/intro, le 3
      chip, placeholder, "Invia", label msg, contatori rate limit, messaggi
      di lock, messaggi cold start, alert dev mode) via dizionario
      `STRINGS = { it, en }` + `applyLanguage()`. Scelta salvata in
      `localStorage['lang']`, reload al cambio. Decisione di design: il
      toggle NON forza la lingua della risposta dell'LLM — quella continua a
      seguire la lingua della domanda (regola nel system prompt, backend non
      toccato). Verificato in browser IT↔EN + layout mobile.
- [x] **Sistema di feedback a stelle + campo testo facoltativo** — FATTO
      (2026-09-01). Card non bloccante (`#feedback-card` in
      `frontend/index.html`) sopra la barra di input: titolo + X, rating 1-5
      stelle, textarea facoltativa (max 1000), "Inoltra"/"Submit" (attivo
      solo con ≥1 stella), "Non ora"/"Not now" e "Non chiedermelo più"/"Don't
      ask again". Compare dopo 3 domande completate (`FEEDBACK_AFTER_INTERACTIONS`,
      conteggio cumulativo per browser in `localStorage['fb_interactions']`).
      "Non ora" / X → cooldown 48h (`fb_state='snoozed'` + `fb_snooze_until`);
      "Non chiedermelo più" → `fb_state='never'`; invio riuscito →
      `fb_state='done'`. Testi via dizionario `STRINGS` (IT/EN).
      Backend: `POST /api/feedback` (pubblico, anti-spam 20/giorno per IP non
      persistito) → tabella `feedback` (`V2__feedback.sql`, da applicare a
      mano come V1). NIENTE dati identificativi salvati (no IP, no cookie):
      solo voto, commento, lingua UI, n° interazioni, timestamp.
      `GET /api/admin/feedback` protetto dal token modalità sviluppatore →
      riepilogo (media, conteggio, distribuzione) + ultimi 500 voti.
- [x] **Pulsante "Genera Riassunto"** — FATTO (2026-09-02). Pulsante sotto
      le chip → `POST /api/summary` → pannello strutturato nella chat
      (In breve, Formazione, Punti di forza, Dove sto crescendo, Progetti,
      Stack, Logistica, Contatto). **Ibrido:** le sezioni sensibili sono
      testo curato in `summary.json` (gitignorato); formazione/progetti/stack
      le genera l'LLM sull'intera KB, con **cache per lingua** lato server
      (`SummaryService`) — dopo la 1ª richiesta tutti ricevono la stessa
      scheda a costo zero. Niente rate limit sull'endpoint (la cache è la
      protezione). `POST /api/admin/summary/refresh` (token dev mode) svuota
      la cache e ricarica `summary.json` da disco. i18n IT/EN: le intestazioni
      stanno nel frontend, i contenuti arrivano già nella lingua giusta.

**Criterio di completamento**: faresti vedere questa versione a un
recruiter senza sentirti in imbarazzo per qualche dettaglio grezzo.

---

> **Nota sull'ordine (2026-09-02):** deploy anticipato. Il prodotto è
> funzionante e rifinito con contenuti reali, quindi va messo online SUBITO
> (il link serve nel CV ora). Hybrid search e cache diventano aggiornamenti
> applicabili in qualsiasi momento sul sito già live.

## Fase 4 — Deploy

- [ ] Database managed (Neon o Supabase) con pgvector abilitato; applicare
      `V1__init.sql` e `V2__feedback.sql` sul DB di produzione
- [ ] Backend su Render o Railway, variabili d'ambiente configurate
      (`ANTHROPIC_API_KEY`, `VOYAGE_API_KEY`, `DB_*`, `ADMIN_BYPASS_PASSWORD`)
- [ ] `summary.json` in produzione: montato come file o passato via
      `SUMMARY_STATIC_FILE` (oggi è gitignorato e letto dalla working dir)
- [ ] Frontend servito come statico dallo stesso backend (risolve anche i
      cookie cross-site della dev mode) o su hosting separato; aggiornare
      `API_BASE` in `index.html` (oggi hard-coded `http://localhost:8080`)
- [ ] Popolare la KB sul DB di produzione (re-ingest dei 4 file → altra
      quota Voyage; con il free tier a 3 RPM sono ~10 min)
- [ ] `/api/admin/ingest` protetto (token dev mode) o rimosso prima di
      rendere pubblico
- [ ] `@CrossOrigin(originPatterns = "*")` → limitato al dominio reale su
      tutti i controller (`Chat`, `Admin`, `Feedback`, `Summary`, `Ingestion`)
- [ ] Test in produzione da un dispositivo non tuo: cold start reale, rate
      limit reale, 5-6 domande, feedback, Genera Riassunto (IT + EN)

**Criterio di completamento**: il link nel CV funziona da un dispositivo
che non è il tuo, senza VPN, senza sessione già "calda".

---

## Fase 5 — Differenziatori tecnici (aggiornamenti sul sito live)

Applicabili in qualsiasi momento dopo il deploy, senza fretta.

- [ ] **Hybrid search**: full-text search Postgres (`tsvector`/BM25) +
      similarità vettoriale, con re-ranking pesato — recupera meglio
      nomi esatti di framework/librerie che il solo embedding a volte
      generalizza troppo. Risolve anche i buchi su query corte (es. "con
      che votazione si è laureato" da sola non recupera il chunk giusto).
- [ ] Cache esatta (non semantica) sulle domande delle pillole
      preimpostate — hash map testo→risposta, azzera la latenza solo per
      le query più frequenti, senza la complessità di una vera cache
      semantica

**Criterio di completamento**: sai spiegare in 2 minuti perché hai scelto
hybrid search invece di solo vettori, con un esempio concreto in cui il
solo retrieval semantico avrebbe fallito.

---

## Fase 6 — Rifinitura per il CV

- [ ] Sezione "Decisioni di design" nel README (vedi sotto) — dimostra
      giudizio ingegneristico, non solo capacità di scrivere codice
- [ ] GIF o breve video demo nel README
- [ ] Link aggiornato nel CV e nei profili (LinkedIn, GitHub)
- [ ] **Pannello "Analytics" in-app (modalità sviluppatore)** — sezione
      dentro il sito, visibile solo con dev mode attiva, che legge
      `GET /api/admin/feedback` e mostra media stelle, distribuzione e
      commenti recenti in modo leggibile, senza aprire l'endpoint JSON o il
      database a mano. L'endpoint protetto esiste già dalla Fase 3: questa è
      solo comodità di lettura.
- [ ] **Posizione del pulsante "Genera Riassunto"** — ora sta sotto le chip
      suggerite; funziona ma si può valorizzare meglio (es. box dedicato,
      call-to-action più evidente). Da rivedere in questa fase di rifinitura.

---

## Fase 7 — Architettura configuration-driven (de-hardcoding)

**Non è un prodotto da vendere.** È separare **codice**, **dati** e
**policy**: oggi identità, contenuti e comportamento del bot sono sparsi tra
`ChatService`, `index.html` e `application.yml`. L'obiettivo è che il codice
non sappia nulla di "Oleksandr" — tutto ciò che definisce *questo* chatbot
vive in un unico punto di configurazione. Sul CV vale come dimostrazione di
saper progettare un sistema estensibile invece che cablato; `summary.json` e
`content/` sono già un primo passo in questa direzione.

Superficie di configurazione (minima e sensata, niente di più):

- [ ] **Identità**: nome, email di contatto, titolo e intro del sito → config,
      non costanti nel codice
- [ ] **Comportamento**: le regole del system prompt (scope del bot, cosa
      rifiuta, difese anti-injection) come template esterno, non stringa in
      `ChatService`; si aggiunge/toglie una regola senza ricompilare
- [ ] **Lingua**: lingue attive e lingua di default come config (oggi IT/EN
      sono cablate nel dizionario `STRINGS` del frontend)
- [ ] **Stile di scrittura**: lunghezza massima risposta, tono
      (formale / diretto), markdown on-off, prima / terza persona →
      parametri, non valori fissi
- [ ] **Knowledge base**: cartella sorgente configurabile + ingest in blocco
      di tutti i file (oggi è manuale, 1 alla volta); supporto `.md` oltre a
      `.txt`
- [ ] **UI**: chip suggerite e tema (colori) serviti al frontend via
      `GET /api/config`, così `index.html` non ha più costanti personali
- [ ] `application.example.yml` + `.env.example` commentati; sezione README
      "come adattarlo a te"
- [ ] Pulizia dovuta comunque: via i `System.out.println` di debug in
      `VectorRepository`, i riferimenti personali in commenti e log

**Criterio di completamento**: cambi un file di configurazione (più le chiavi
API) e il chatbot parla di un'altra persona — senza aprire un `.java` e senza
ricompilare.

**Oltre, non pianificato:** da qui si potrebbe un domani arrivare a un vero
multi-tenant ospitato ("configura il tuo chatbot", un utente per tenant, con
auth e onboarding). Fuori scope: sarebbe un prodotto vero, con infrastruttura,
billing e GDPR di conseguenza. Resta un'ipotesi lontana, non un obiettivo.

---

## Deciso di NON implementare (e perché — da scrivere nel README)

Sapere cosa **non** costruire, e argomentarlo, è a sua volta un segnale
di maturità in un colloquio.

- **Pipeline a due step con LLM-giudice per prompt injection**: raddoppia
  le chiamate API/i costi per un rischio reale basso (nessun dato
  sensibile, nessuna azione distruttiva). Rate limiting + system prompt
  + pre-filtering coprono la maggior parte dei casi con costo marginale.
- **Output sanitization anti-allucinazione "vera"**: richiederebbe un
  altro giudice LLM o un sistema NLI dedicato — sproporzionato per un
  chatbot che parla solo di contenuti pubblici e a basso rischio (il
  proprio CV).
- **Semantic caching vero** (similarità vettoriale sulle query in cache):
  il beneficio è marginale al volume di traffico atteso (decine di
  visite, non migliaia/secondo); la cache esatta sulle pillole copre la
  maggior parte del beneficio pratico.
- **Dashboard con path di navigazione dettagliato per singolo
  visitatore**: solleva questioni GDPR reali (tracciamento di persone
  identificabili in UE) per un beneficio limitato. Meglio log aggregati
  anonimi (conteggi, non percorsi individuali).
- **Esportazione PDF della conversazione**: bassa probabilità che venga
  usata da un recruiter, non prioritaria rispetto al resto.
