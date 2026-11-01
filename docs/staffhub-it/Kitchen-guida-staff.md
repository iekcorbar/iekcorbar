# Kitchen - cucina e agricoltura giapponese

Guida per lo staff: raccolti, postazioni, impostazioni, gara di cucina e risoluzione dei problemi.

## 1. Che cos'è Kitchen

Kitchen è il nostro plugin giapponese di agricoltura e cucina. I giocatori coltivano dieci raccolti (riso, soia, cavolo napa, cipollotto, daikon, tè, mais, pomodoro, peperoncino, sesamo), tagliano su un tagliere, cuociono in una pentola sul fuoco e mettono in tavola i piatti da condividere. Kitchen collabora con Steamworks (farina, mochi, tofu) e Sakagura (ingredienti).

### Comandi e permessi

| Comando | Cosa fa |
|---|---|
| /kitchen (/kt) help | Breve aiuto e link alla wiki. |
| /kitchen list | Tutti gli oggetti di Kitchen. |
| /kitchen give giocatore oggetto [quantità] | Dà un oggetto. |
| /kitchen take giocatore oggetto [quantità] | Toglie oggetti (senza quantità: tutti). |
| /kitchen giveall [giocatore] | Uno di ogni oggetto. |
| /kitchen info | Numero di raccolti e props caricati, e i tuoi XP di cucina. |
| /kitchen reload | Rilegge configurazione e ricette. |
| /kitchen comp start, stop, status, submit, leaderboard | Gara di cucina, vedi capitolo 4. |

| Permesso | Significato |
|---|---|
| kitchen.admin | Dare e togliere oggetti, ricaricare, gestire le gare. |

## 2. Raccolti e postazioni

Un raccolto è un blocco invisibile (structure void) sopra la terra coltivata, con accanto un modello visibile della pianta; i giocatori non possono romperlo né piazzarlo direttamente. Si raccoglie facendo clic destro sul terreno sotto il raccolto: la pianta viene raccolta e ricresce (oppure accetta la farina d'ossa). Il mais, tagliato, dà farina di mais. Macchie selvatiche compaiono al primo caricamento di un chunk. La terra ricca (dal compost) fa crescere più in fretta.

| Postazione | Cosa fa |
|---|---|
| Tagliere | Appoggia un ingrediente e taglia con un coltello (consuma durabilità). |
| Pentola | GUI a 27 slot: 6 ingredienti, contenitore, risultato. Cuoce solo con calore sotto. Continua a cuocere anche se nessuno guarda, finché la zona è caricata. |
| Stufa kamado | Scalda una pentola, oppure griglia da sola (4 oggetti, il doppio più veloce di un falò). |
| Tansu, cesto, casse, tatami, zabuton | Magazzino, arredi, cuscino per sedersi. |
| Pasti condivisi | Sneak + clic destro mette giù sushi, ramen, bento, dango o tè; gli altri prendono un boccone per clic. |

## 3. Impostazioni (config.yml e kitchen.yml)

| Chiave | Predefinito | Significato |
|---|---|---|
| crops.growth-minutes.raccolto | 16-26 | Minuti per fase di crescita per raccolto (riso 22, tè 26...). |
| crops.rich-soil-speed | 1.6 | Velocità di crescita su terra ricca. |
| crops.min-light | 9 | Luce minima per crescere. |
| wild-crops.enabled / chance-per-chunk | true / 0.12 | Raccolti selvatici nei nuovi chunk. |
| cooking-pot.default-seconds | 10 | Tempo di cottura predefinito. |
| compost.minutes | 20 | Tempo prima che il compost diventi terra ricca. |
| stations.break-hits | 3 | Colpi per rompere una postazione. |

Taglio e cottura sono ricette in plugins/Kitchen/kitchen.yml. Questo file non viene mai sovrascritto da un aggiornamento. Se sul server hai una copia tua, una nuova ricetta va aggiunta a mano (o tramite Claude); cancella il file per tornare al predefinito nel jar.

Collegamento con Steamworks: una macina trasforma il riso in farina, il martello a vapore fa il mochi, calderone e mixer fanno il tofu. Per questo Kitchen deve caricarsi prima di Steamworks; è già così.

## 4. Gara di cucina

Un admin la avvia con /kitchen comp start giorni [tema]. I giocatori presentano il loro miglior piatto tenendolo in mano con /kitchen comp submit. Il punteggio deriva da nutrizione più saturazione; conta solo la migliore presentazione. /kitchen comp stop chiude la gara e distribuisce i premi; il vincitore ottiene l'advancement Master Chef.

## 5. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Un raccolto cambia o scompare | Segnala posizione e raccolto a Claude; i raccolti sono salvati nel chunk e possono essere ridisegnati. |
| La pentola non cuoce | C'è calore sotto? Esiste una ricetta per esattamente questi ingredienti? L'orologio nella GUI dice cosa manca. |
| Ricetta mancante dopo un aggiornamento | Il kitchen.yml del server è vecchio: aggiungi le nuove righe oppure cancella il file e fai /kitchen reload. |
| La pianta fluttua o ha un aspetto strano | Segnalalo con uno screenshot; l'altezza è una costante e si può regolare. |
