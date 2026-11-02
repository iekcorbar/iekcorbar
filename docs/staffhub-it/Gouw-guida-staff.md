# Gouw - rivendicare e gestire il territorio

Guida per lo staff: gestire i claim, usare il bypass e le impostazioni.

## 1. Che cos'è Gouw

Gouw è il nostro plugin di claim (il successore di Lands). Un giocatore rivendica il terreno chunk per chunk (16x16 blocchi). Nel suo gouw solo lui e le persone di cui si fida possono costruire, aprire contenitori o combattere. I claim stanno in un semplice file, plugins/Gouw/claims.json, non in MySQL. I vecchi claim di Lands sono stati importati al primo avvio.

### Cosa protegge un gouw

- Rompere e piazzare blocchi, svuotare e riempire secchi.
- Contenitori, porte, pulsanti, letti, incudini e altri blocchi interattivi.
- Attacchi ad animali e mob all'interno, e combattimenti tra giocatori (con eccezioni per flag).
- Le esplosioni non possono rimuovere blocchi rivendicati; i pistoni non possono trascinare blocchi oltre il confine.

## 2. Comandi

| Comando | Cosa fa |
|---|---|
| /gouw (/g, /claim) | Apre il menu. Quasi tutto si fa cliccando qui. |
| /gouw claim | Rivendica il chunk in cui ti trovi. |
| /gouw autoclaim r | Rivendica ogni chunk entro il raggio r (1 = 3x3, 2 = 5x5). |
| /gouw unclaim [all] | Restituisce questo chunk o tutto. |
| /gouw trust / untrust nome | Aggiunge o toglie un membro. |
| /gouw members, info, list, near, border, rename, leave | Panoramiche e piccoli aiuti. |
| /gouw admin delete nome | Cancella i claim di un giocatore. Solo dopo una richiesta confermata. |
| /gouw admin import [force] | Rilegge il vecchio database di Lands. |
| /gouw admin reload | Rilegge la configurazione. |
| /gouw admin bypass | Attiva/disattiva il passare e costruire in qualsiasi gouw (per ispezioni). |

| Permesso | Significato |
|---|---|
| gouw.claim | Rivendicare (tutti). |
| gouw.admin | Chunk illimitati, cancellare claim, import, reload. |
| gouw.chunks.N | Limite di chunk per rango (per esempio gouw.chunks.55); vince il numero più alto. |
| gouw.bypass | Può usare l'interruttore del bypass. |

Nota: il bypass è un interruttore per singolo giocatore, non un diritto permanente. Attivalo per ispezionare o riparare, poi spegnilo.

## 3. Il menu e i flag

Il menu ha tre pagine: principale (statistiche, claim, autoclaim 5x5, mostra confine), flag e una pagina staff. I membri fidati ottengono tutto di default; i quattro flag Build, Open, Mobs e Players stringono o allargano questo comportamento per ogni gouw. Per aggiungere un membro si scrive il nome in chat (cancel annulla). La pagina staff mostra ogni gouw come testa (clic = elimina), l'interruttore del bypass, il controllo di scadenza e la reimportazione di Lands.

## 4. Impostazioni (config.yml)

| Chiave | Predefinito | Cosa fa |
|---|---|---|
| bonus-max | 100 | Massimo di chunk bonus acquistabili (/gouw admin buychunks giocatore n costo, /gouw admin addchunks giocatore n). |
| max-chunks | 16 | Chunk per giocatore. Staff con gouw.admin: illimitati. |
| enter-messages | true | Action bar e particelle quando si entra nel gouw di un altro. |
| expire-inactive-days | 0 | I gouw dei giocatori offline da tanto tempo vengono liberati. 0 = spento (attualmente spento). |
| import.lands-database | plugins/Lands/Data/database_v2.db | Dove stavano i vecchi dati di Lands. |

Nota: attenzione alla scadenza. I proprietari online e quelli senza ultimo accesso noto non vengono mai toccati, ma un valore sbagliato può comunque liberare molti claim. Chiedi prima al proprietario.

## 5. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Un giocatore non può costruire nel proprio gouw | È davvero il proprietario? Controlla con /gouw info. Un membro potrebbe non avere il flag Build. |
| Un vecchio claim di Lands sembra sbagliato | Guarda con bypass e /gouw info; correggi con /gouw admin; se hai dubbi chiedi a Claude. |
| I claim sembrano spariti dopo un crash | claims.json è il file; chiedi a Claude di rimettere una copia. Non creare mai un nuovo file da solo. |
| Il sito mostra un numero di claim sbagliato | Il sito legge claims.json tramite SiteSync; aspetta la prossima sincronizzazione. |
