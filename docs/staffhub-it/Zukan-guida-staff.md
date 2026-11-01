# Zukan - libro delle ricette

Guida per lo staff: apertura, catalogo, impostazioni e risoluzione dei problemi.

## 1. Che cos'è Zukan

Zukan è il nostro libro delle ricette: un elenco ricercabile di ogni oggetto del gioco (vanilla e tutto ciò che viene dai nostri plugin) con come si fa e a cosa serve. È un normale menu a baule da 54 slot, disegnato dal resource pack come uno schermo di ottone. I giocatori lo aprono con /zukan (anche /recipes, /jei), con il libro Zukan (libro + bussola) oppure con F tenendo il libro.

### Comandi e permessi

| Comando | Cosa fa | Permesso |
|---|---|---|
| /zukan [parole] | Apre l'elenco, eventualmente già filtrato. | zukan.use |
| /zukan hand / uses | Ricette o usi dell'oggetto che hai in mano. | zukan.use |
| /zukan reload | Ricostruisce il catalogo. | zukan.admin |
| Modalità cheat (pulsante) | Clicca un oggetto per prenderlo. | zukan.cheat |

## 2. Come si costruisce il catalogo

Zukan costruisce un proprio elenco: 2 secondi dopo l'avvio e di nuovo dopo 90 secondi (quando i modelli degli oggetti FMM sono pronti), e con /zukan reload. Legge dal gioco le ricette vanilla (fabbricazione, fusione, pozioni, tagliapietre, smithing). I nostri plugin (Steamworks, Kitchen, Sakagura, Relics, Kura, Wildlife, Satchel, Additions, Power) si registrano con i propri oggetti e le ricette delle macchine; Zukan interroga ogni plugin registrato. Un plugin che dipende da un altro viene mostrato come add-on di quello.

| Chiave (config.yml) | Predefinito | Significato |
|---|---|---|
| excluded-providers | [] | Plugin che NON vuoi mostrare. |
| ignored-namespaces | [mmoitems] | Famiglie di oggetti tenute fuori dall'elenco (MMOItems è troppo grande). |
| max-choices | 24 | Opzioni tra cui uno slot può ruotare. |
| gui-style | steampunk | steampunk (con resource pack) oppure plain (senza). |
| swap-key | true | F con il libro in mano apre Zukan. |
| book-recipe | true | Il libro si può fabbricare. |

Nota: MMOItems è tenuto fuori dall'elenco di proposito; trova quegli oggetti con /mi browse. Per un nuovo oggetto di un plugin non devi cambiare Zukan: lo segnala il plugin stesso.

## 3. Uso da parte dei giocatori

Clic su un oggetto per la ricetta, clic destro per i suoi usi, shift+clic per il segnalibro. Cerca con parole, #gruppo o @plugin. Schede in alto per tipo di ricetta; l'icona della stazione mostra dove si fabbrica. In un Crafting Terminal di Kura "Move ingredients" riempie la griglia dalla rete. Le ricette sono anche sul sito sotto Play > Recipes.

## 4. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Oggetto o ricetta mancante | /zukan reload; controlla che il plugin sia caricato. Un nuovo oggetto di un altro plugin può comparire solo dopo un riavvio. |
| Lo schermo appare grigio/semplice | Resource pack non caricato sul client; /resourcepacks reload e riavvia il client. Oppure gui-style è plain. |
| L'icona è un cubo viola | Manca la definizione nel pack per quell'oggetto: segnala il nome a Claude. |
| Manca il pulsante cheat | Il giocatore non ha zukan.cheat (solo staff). |
