# Steamworks - vapore e ingranaggi

Guida per lo staff: comandi, funzionamento, impostazioni e risoluzione dei problemi.

## 1. Che cos'è Steamworks

Steamworks è il nostro plugin di vapore e ingranaggi: alberi, ruote dentate, ruote idrauliche, mulini a vento e macchine a vapore azionano reti di componenti. Le macchine (macina, martello a vapore, ruote frantumatrici, ventola, pressa, trapano, sega, Karakuri Arm) funzionano con quella velocità; ci sono nastri trasportatori, treni con stazioni e segnali, minerali (zinco, stagno, zolfo) e petrolio con raffineria. L'idea viene dal gioco Create, ma tutto il codice, i modelli e i testi sono nostri. Non serve nessuna mod.

### Comandi e permessi

| Comando | Cosa fa |
|---|---|
| /steamworks (/sw) help / info | Aiuto; info sul componente che guardi (velocità, carico, orientamento). |
| /sw list | Tutti i componenti. |
| /sw give giocatore componente [quantità] | Dà un componente, materiale o veicolo. |
| /sw giveall [giocatore] | Uno di tutto, in shulker box con nome. |
| /sw manual | Dà il Manuale dell'Ingegnere interattivo. |
| /sw reload | Rilegge config.yml e machines.yml. |
| /swpower (/pwr, /swp) list / give / reload | L'estensione Power: turbina idraulica, pressa, ticker, volano, imbuto a setaccio. |

| Permesso | Significato |
|---|---|
| steamworks.admin | Dare, ricaricare. |
| power.admin | Lo stesso per /swpower. |

Nota: usa /swpower o /pwr, mai /power: quel comando appartiene a ValhallaMMO.

## 2. Come funziona, in breve

| Concetto | Spiegazione |
|---|---|
| Velocità (RPM) | Le fonti mettono in moto la rete; le ruote dentate cambiano il rapporto (grande-piccola = 2:1). |
| Carico (stress) | Ogni macchina costa carico; le fonti forniscono capacità. Troppo = la rete si blocca, non si rompe nulla. |
| Sovravelocità | Sopra la velocità massima cade il componente più veloce. |
| Chiave a vapore | Ruota un componente (attenzione alla faccia), sneak + clic lo rimuove conservando il componente. |
| Indicatori | Gli indicatori di velocità e stress mostrano i valori di ciò su cui clicchi. |

Fonti: manovella, ruota idraulica, mulino a vento (gira l'albero verso il basso con la chiave in una torre), macchina a vapore con caldaia, turbina idraulica (Power) e volano come accumulatore. Macchine: macina (azionamento laterale), martello a vapore, ruote frantumatrici (coppia), ventola (fondere, lavare, infestare), mixer + calderone (ottone, bevande), pressa, trapano, sega, nastri, imbuti, deposito + espulsore, pompe e valvole, Karakuri Arm con programmi su libro o scheda perforata. Treni: locomotiva, vagoni, stazioni con tabella del nome, segnali, pilota automatico con un orologio, tabelle di linea con la carta. Minerali e petrolio: zinco, stagno, zolfo (stati dei note block), giacimenti per chunk, pompa a bilanciere, serbatoi, raffineria; i rifiuti inquinano i chunk.

## 3. Impostazioni

| File / chiave | Significato |
|---|---|
| config.yml | Numeri per componente (capacità, velocità massima, portata, velocità); sounds.machine-volume (0 = silenzio); docs-url. |
| machines.yml | Tutte le ricette delle macchine. La copia del server non viene mai sovrascritta: dopo un aggiornamento dev'essere fresca o cancellata. |
| plugins/Steamworks/ | trains.yml (treni), giacimenti di minerali e petrolio nei chunk stessi. |
| Modelli FMM | plugins/FreeMinecraftModels/models/steamworks/. A ogni aggiornamento sostituisci l'intera cartella, poi /fmm reload. |
| Resource pack | È nel nostro pack unico (minerali, blocchi, suoni). Sostituzione: carica, rimuovi il vecchio, /resourcepacks reload. |

Una macchina rompe solo dove chi l'ha piazzata può rompere (si interrogano i plugin di protezione, risposta ricordata per 10 minuti). I treni sono salvati nel mondo e tornano quando il chunk si carica. Sul server TechCore riscrive i nostri jar; quindi non confrontare mai sullo sha1 dell'intero file, ma sulle classi.

## 4. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| La rete è ferma o "jam" | Clicca con un indicatore: carico sopra la capacità, fonti opposte o un anello di ingranaggi. Rimuovi una macchina o aggiungi una fonte. |
| Un componente è viola o invisibile | Cartella modelli FMM non fresca o pack non caricato: sostituisci la cartella, /fmm reload, riavvia il client. |
| Una ricetta non fa nulla | machines.yml del server vecchio: sostituiscilo o cancellalo, /sw reload. |
| Un componente guarda dalla parte sbagliata | Chiave a vapore: clicca la faccia giusta; sneak + clic lo rimuove. |
| I giocatori duplicano componenti | Il clic centrale funziona solo in creativa; segnala subito se funziona in sopravvivenza. |
| Il server scatta all'avvio | Normale: i modelli si caricano a gruppi. Se persiste, misura con il monitor e segnalalo. |
