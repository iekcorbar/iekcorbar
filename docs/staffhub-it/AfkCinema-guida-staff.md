# AfkCinema - telecamera e trailer

Guida per lo staff: comandi, impostazioni, trailer e risoluzione dei problemi.

## 1. Che cos'è AfkCinema

AfkCinema riprende i giocatori AFK e crea inquadrature cinematografiche per i trailer. Quando qualcuno resta fermo per 180 secondi il plugin piazza una controfigura (NPC di Citizens con la skin del giocatore) e fa orbitare la telecamera attorno a lui. Lo staff può registrare e riprodurre percorsi di camera, e c'è un menu steampunk che può fluttuare nel mondo come pannello.

### Comandi e permessi

| Comando | Cosa fa |
|---|---|
| /cine afk [giocatore] | Avvia la camera AFK su di te (admin: su un altro). |
| /cine stop | Ferma le riprese. |
| /cine auto | Attiva o disattiva le riprese automatiche in AFK per te. |
| /cine menu / screen | Pannello a vapore; menu fluttuante e cliccabile davanti a te. |
| /cine record nome | Registra un percorso di camera camminando o volando (admin). |
| /cine pad add / speel / verwijder / info | Gestisce e riproduce i percorsi. |
| /cine lijst / wis / reload | Elenca, cancella, ricarica la configurazione. |

| Permesso | Significato |
|---|---|
| afkcinema.cine | Attivo di default: ogni giocatore può riprendere se stesso. |
| afkcinema.admin | Solo op: riprendere altri, registrare e cancellare percorsi, ricaricare. |

## 2. Impostazioni (config.yml)

| Chiave | Predefinito | Significato |
|---|---|---|
| afk-seconds | 180 | Quanto si resta fermi prima che parta la camera. |
| broadcast-afk | | Messaggio al server quando inizia l'AFK. |
| cine.orbit-speed / radius | orbit | Velocità e raggio dell'orbita. |
| cine.orbit-breathe / crane | true | Distanza "che respira" e movimento a gru. |
| cine.afk-shot | | orbit oppure pad:nome: un percorso personalizzato per le riprese AFK. |
| cine.pad-speed / loop / easing | | Velocità, ripetizione (avanti e indietro, loop, una volta) e easing dei percorsi. |
| cine.screen-clicks | | I menu fluttuanti sono cliccabili. |

I percorsi stanno in plugins/AfkCinema/pads.yml (curva Catmull-Rom morbida tra i punti). La camera si ferma quando è attivo /afk di CMI. Citizens è una dipendenza facoltativa: senza Citizens il plugin riprende comunque, ma senza controfigura.

## 3. Per i trailer

Registra un percorso (/cine record castello), riproducilo (/cine pad speel castello) e cattura lo schermo con Replay Mod o un registratore. Tieni il movimento lento (pad-speed basso) e l'easing attivo per un risultato cinematografico. Per dare sempre quel percorso ai giocatori AFK: cine.afk-shot: pad:castello.

## 4. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Nessuna controfigura | Citizens è caricato? Se no: installalo o accetta di restare senza. |
| La camera non parte | Il giocatore non ha afkcinema.cine, auto è spento o /afk di CMI è attivo. |
| Il giocatore non torna dalla camera | /cine stop; muoversi o scrivere in chat. Se persiste: segnalalo. |
| Il menu fluttuante non fa nulla | cine.screen-clicks è attivo? Solo il proprietario usa il proprio schermo. |
