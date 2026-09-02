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
      primo — lo risolverà la hybrid search (vedi "Aggiornamenti tecnici").
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

## Fase 4 — Deploy — ✅ FATTO (2026-09-02)

**LIVE: https://rag-chatbot-0uwq.onrender.com**

Neon (Postgres + pgvector) + Render (Web Service Docker, free, Frankfurt) +
frontend servito da Spring da `src/main/resources/static/`. Repo
`AzureKodo502/Rag-chatbot` (privato). Fatto: `Dockerfile`, `V1`+`V2` su
Neon, KB re-ingerita (29 chunk), env var + Secret File `summary.json` su
Render, `/api/admin/ingest` protetto, CORS off di default, `API_BASE`
relativo. Verificato via curl: chat con fonti, Genera Riassunto, feedback →
Neon, sicurezza. Prima query "fredda" ~19s (Neon scale-to-zero), a caldo
sub-secondo.

Resta: test dell'utente da telefono ✅ (fatto, funziona); repo pubblico +
pulizia doc → Fase 5.

---

## Fase 5 — Rifinitura per il CV

Il grosso del lavoro prima di mettere il link nel CV.

- [ ] Repo GitHub da privato a **pubblico** — prima: audit dei doc
      (`HANDOFF.md` è un doc di lavoro interno, valutare se tenerlo fuori),
      README presentabile
- [ ] Sezione "Decisioni di design" nel README — dimostra giudizio
      ingegneristico, non solo capacità di scrivere codice
- [ ] GIF o breve video demo nel README
- [ ] **Keep-alive**: un ping periodico (cron esterno o UptimeRobot) a
      `/api/chat/status` per tenere Render sveglio, + una query banale che
      tiene caldo anche Neon, così la prima domanda di un recruiter non è
      lenta 15-20s
- [ ] **Pannello "Analytics" in-app (modalità sviluppatore)** — sezione nel
      sito, visibile solo con dev mode attiva, che legge
      `GET /api/admin/feedback` e mostra media stelle, distribuzione e
      commenti recenti, senza aprire l'endpoint JSON a mano
- [ ] **Posizione del pulsante "Genera Riassunto"** — ora sta sotto le chip;
      funziona ma si può valorizzare (box dedicato, CTA più evidente)
- [ ] **Resa su smartphone** — testato da 2 persone su telefono, funziona ma
      la visualizzazione mobile è migliorabile (barra in alto affollata,
      spaziature, dimensioni testo, la card feedback / il pannello riassunto
      su schermi stretti). Giro di rifinitura responsive.
- [ ] Cookie `SameSite=None; Secure` → `Lax` ora che il frontend è
      same-origin (ri-verificare la dev mode)
- [ ] Link aggiornato nel CV e nei profili (LinkedIn, GitHub) — **ultimo**

**Criterio di completamento**: apri il link davanti a un tech lead senza
dover spiegare o scusarti per niente.

---

## Fase 6 — Architettura configuration-driven (de-hardcoding)

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

## Aggiornamenti tecnici — in qualsiasi momento sul sito live

Non hanno un posto fisso nell'ordine: il sito è già online e funziona, questi
lo migliorano quando capita. Buoni argomenti da colloquio.

- [ ] **Hybrid search**: full-text search Postgres (`tsvector`/BM25) +
      similarità vettoriale, con re-ranking pesato — recupera meglio i nomi
      esatti di framework/librerie che il solo embedding a volte generalizza.
      Risolve anche i buchi su query molto corte (es. "con che votazione si è
      laureato" da sola non pesca il chunk giusto — verificato). Sai spiegare
      in 2 minuti perché hybrid invece di solo vettori, con un esempio.
- [ ] **Cache esatta** (non semantica) sulle domande delle chip preimpostate
      — hash map testo→risposta, azzera la latenza per le query più
      frequenti, senza la complessità di una cache semantica.

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
