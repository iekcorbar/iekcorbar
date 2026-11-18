# Hihou - BedWars: costruire e gestire l'arena

Guida per lo staff: costruire un'arena, marcare i punti, gestire le partite e le impostazioni.

**Regola numero uno:** il wiki pubblico (iekcorbar.com/wiki, categoria "Hihou BedWars") è per i giocatori. Tutti i comandi di questa pagina NON vanno nel wiki — se un giocatore chiede un dettaglio da staff, rispondete in Discord, non adattate il wiki.

## 1. Come funziona un'arena

Un'arena BedWars è una raccolta di punti con un nome: lobby e spot spettatore, una regione per il reset della mappa, e per ogni squadra spawn, letto, marcatore shop e cassa di squadra, più i pad dei generatori. Tutto si marca in gioco con i comandi `/ih bw` (solo staff dove non detto diversamente).

## 2. Costruire l'arena

**1. Mappa.** Gli otto pezzi `bedwars-*` (lobby, quattro isole squadra, due mid diamante, un mid smeraldo) stanno in `plugins/WorldEdit/schematics/`. Incollateli con WorldEdit (`//schem load bedwars-01-rood` + `//paste -a`) nell'ordine stampato da `python -I scripts/make_bedwars_arena.py` — le isole condividono il layout, rispettate le distanze della checklist.

**2. Registrare.**

| Comando | Cosa fa |
|---|---|
| `/ih bw create <nome>` | Crea l'arena vuota. |
| `/ih bw set <arena> spawn\|spec <valore>` | Spot lobby e spot spettatori (stand dove serve). |
| `/ih bw set <arena> center <x y z>` + `size <r>` | La regione del reset post-partita. |
| `/ih bw set <arena> end <secondi>` | Sposta il momento della morte improvvisa (default 1500 s = 25 min: a quel punto tutti i letti ancora in piedi vengono distrutti; la partita termina di default a 2400 s). |
| `/ih bw set <arena> min <n>` | Minimo giocatori per l'auto-start. |
| `/ih bw team <arena> add <nome> <colore>` | Aggiunge una squadra; poi, stando sul punto: `spawn <nome>`, `bed <nome>` (guarda il letto), `shop <nome>` (guarda l'incudine), `chest <nome>` (guarda la cassa). `del <nome>` rimuove. |
| `/ih bw gen <arena> add iron\|gold <squadra>\|diamond\|emerald` | Marca il pad dei generatori sotto i piedi; ferro e oro hanno bisogno della squadra proprietaria. |

**3. Snapshot PRIMA della prima partita.** `/ih bw snapshot <arena> [lo] [hi]` cattura ogni blocco non-aria della regione. Dopo ogni partita il reset riporta **esattamente** quello stato — blocchi piazzati, rotti e fatti esplodere tornano indietro. Senza snapshot gira il vecchio percorso "lite" che rimuove solo i blocchi piazzati dai giocatori: le esplosioni restano visibili. Prendete lo snapshot a arena pulita.

## 4. Gestire le partite

| Comando | Cosa fa |
|---|---|
| `/ih bw list` / `info <arena>` | Panoramica e marchiature. |
| `/ih bw start <arena> [secondi]` | Forza il countdown (default 10 s); parte anche da solo quando la lobby è piena. |
| `/ih bw stop <arena>` | Chiude la partita in corso. |
| `/ih bw mode <arena> classic\|no-beds\|double-iron\|speed` | Forza la modalità della prossima partita (altrimenti ogni lancio ne pesca una a caso se `bw-random-modes` è attivo). |
| `/ih bw season` | Nuova stagione: azzera le statistiche (`bwstats.json`). |
| `/ih bw delete <arena>` | Dimentica l'arena (i blocchi restano; il reset ripristina ciò che lo snapshot conosce). |

I giocatori (per riferimento, quando chiedete "cosa vede lui"): `join`, `leave`, `rejoin`, `shop`, `kit`, `follow`, `party invite/join/ignore/leave`, `stats [giocatore|top]`.

## 5. Configurazione (`plugins/Hihou/config.yml`)

| Chiave | Default | Effetto |
|---|---|---|
| `bw-rewards.kill` / `.bed` / `.win` | 5 / 10 / 25 | Monete Vault per kill, letto rotto, vittoria. 0 = disattivato. |
| `bw-random-modes` | true | Pesca una modalità a caso a ogni lancio. |

**Lezione del 13-11 (vale per ogni plugin):** una chiave già presente esplicitamente nella config del server **vincente sulla vecchia sovrascrive il default nuovo della jar** — `saveResource` non tocca mai una config esistente. Dopo un deploy che cambia un default, controllate la config live e adeguate sul posto (poi `/sw reload`-style reload dove esiste, o riavvio).

## 6. Cronologia di una partita (per capire i report)

- Ore: ferro 2 pezzi/2 s (max 32 a terra), oro 2/6 s (max 7), diamante 1/30 s → tier 2 a 6 min (20 s) → tier 3 a 18 min (15 s), smeraldo 1/70 s → 12 min (50 s) → 24 min (30 s). I cambi di tier sono annunciati in chat.
- Morte con letto vivo: respawn dopo 5 s, kit nuovo distribuito a ogni spawn. Letto rotto: lo spettatore può volare vicino a chiunque con `/ih bw follow <giocatore>`.
- 25 min: tutti i letti ancora interi vengono rimossi (morte improvvisa, annunciato). Vittoria: 15 s di show di fuochi d'artificio nei colori della squadra, poi consegna delle casse di squadra e ritorno al mondo survival.
- La sidebar BedWars sostituisce il tab normale solo durante la partita e sparisce pulita a fine/uscita (se un giocatore esce in modo scorretto, il tab torna al prossimo join).

## 7. Note tecniche

- Hihou 0.6.1 (live dal 18-11): la prima jar di questa serie crashava all'enable (chiave PDC null sul Bed Breach Charge — ordine di inizializzazione in `BwGameManager`), riparato lo stesso giorno; se un deploy futuro fallisce all'enable, cercate il pattern "costruttore usa campo assegnato dopo" prima di toccare la config.
- Il Bed Breach Charge è un fire charge con PDC `hihou:breach`; gli item in circulation creati prima della fix NON sono difettosi — il check legge solo il tag.
- Stati per partita in `plugins/Hihou/arenas.json` (include gli snapshot) e `bwstats.json` (stagione). Backup prima di modifiche manuali: il plugin sovrascrive questi file a ogni cambio e allo shutdown.
