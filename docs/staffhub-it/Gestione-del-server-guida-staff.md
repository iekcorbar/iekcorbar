# Gestire il server

Guida per lo staff: avviare, fermare, annunciare i riavvii, ricaricare e controllare.

## 1. La regola d'oro

Avvia e ferma il server solo tramite il pannello Pterodactyl (i pulsanti Start, Stop e Restart). Se il server viene avviato in un altro modo, per esempio direttamente sulla VPS, funziona, ma il pannello non lo sa: il pulsante Stop allora non fa nulla.

Nota: nel pannello Stop non funziona più? Il server è stato avviato fuori dal pannello. Fallo fermare in modo pulito (tramite il proprietario o Claude) e poi premi tu Start nel pannello.

## 2. Annunciare un riavvio

Usa /iekrestart in gioco (permesso iekquests.admin). I giocatori ricevono messaggi e un titolo con il conto alla rovescia.

| Comando | Effetto |
|---|---|
| /iekrestart 5 | Conto alla rovescia da 5 minuti (da 1 a 60). |
| /iekrestart 90 | Conto alla rovescia da 90 secondi (da 61 a 3600 sono secondi). |
| /iekrestart now | Ferma subito. |
| /iekrestart cancel | Annulla un conto alla rovescia in corso. |

Dopo lo stop il server deve ripartire. Se non torna su da solo, premi Start nel pannello. Se è tornato su ma Stop non funziona, è stato avviato fuori dal pannello: vedi la regola d'oro.

## 3. Controllare dopo un avvio

Un avvio dura circa un minuto. Nella console aspetti la riga Done (xx s)! For help, type "help".

- C'è la riga Done? Allora il server è pronto. Non entrare né ricaricare prima.
- Cerca SEVERE nel log. Se compaiono righe, passale a Claude.
- Dopo uno scambio di plugin controlla nel log che ci sia la nuova versione (Enabling Nome vX).
- Verifica che i giocatori possano entrare e che i plugin appena modificati funzionino.

## 4. Ricaricare senza riavviare

Molte configurazioni si ricaricano in gioco. Sostituire un jar o aggiungere un plugin richiede sempre un riavvio. Non usare il /reload generale: rompe i plugin.

| Comando | Ricarica |
|---|---|
| /questbook reload | Quest (quests.yml e wizard) |
| /mi reload | MMOItems |
| /lp sync | Permessi LuckPerms |
| /tab reload | TAB (prefissi, lista tab) |
| /mine reload | PyroMining |
| /wildlife reload | Wildlife |
| /brew reload | Sakagura |
| /sk reload script | Uno script Skript |
| /fmm reload | FreeMinecraftModels (nuovi modelli) |
| /resourcepacks reload | Il resource pack. I giocatori devono riscaricarlo (riavviare il client). |

## 5. Cosa lo staff non fa da solo

Queste cose passano dal proprietario o da Claude, in un ordine fisso, così il server non riparte mai senza un plugin:

| Passo | Cosa |
|---|---|
| 1 | Prima carica i nuovi file sul server (i vecchi restano). |
| 2 | Ferma il server. |
| 3 | Solo ora rimuovi il VECCHIO jar o la vecchia configurazione. |
| 4 | Avvia il server e controlla il log: versione, Done, nessun SEVERE. |

- Non cancellare né sovrascrivere cartelle di plugin in funzione senza una copia.
- Un nuovo resource pack: il file va nella cartella mixer del Resource Pack Manager, il vecchio fuori, poi /resourcepacks reload.
- Claude non tocca il database del sito né Plesk. Lo fa il proprietario.

## 6. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Il server non parte | Guarda in fondo alla console; passa le ultime righe a Claude. |
| Stop non fa nulla | Vedi la regola d'oro: avviato fuori dal pannello. |
| Il server rallenta dopo molto tempo | Pianifica un riavvio in un momento tranquillo e annuncialo. |
| Un plugin non fa nulla dopo il reload | Controlla la console per errori; alcune modifiche richiedono un riavvio. |
| I giocatori vedono vecchi modelli o suoni | Ricarica il pack e riavvia il client. |
