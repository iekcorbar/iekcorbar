# Sakagura - distillazione, giardino e gare

Guida per lo staff: comandi, processo di produzione, stagioni, gare e risoluzione dei problemi.

## 1. Che cos'è Sakagura

Sakagura è il nostro plugin di produzione di bevande e giardino (successore di Brewery). I giocatori coltivano raccolti e alberi da frutto, con cui producono sake, whisky, vino, tè e altro, distillano in un Steam Still e fanno invecchiare le bevande in botti. La qualità va da 1 a 10 (stelle). Ci sono 80 ricette, 48 raccolti da giardino e diverse specie di alberi. Bere dà effetti ma fa anche ubriacare.

### Comandi e permessi

| Comando | Cosa fa |
|---|---|
| /brew help / recipes | Aiuto e lista di tutte le ricette con difficoltà. (Alias: /sakagura) |
| /brew drunk | Mostra il tuo livello di ebbrezza. |
| /brew give giocatore bevanda_o_oggetto [qualità] [quantità] | Dà una bevanda. Un numero 1-10 è la qualità; sopra 10 è la quantità. Botti e oggetti del giardino vengono in pile da 64. |
| /brew take giocatore bevanda_o_oggetto [quantità] | Toglie oggetti (senza quantità: tutti). |
| /brew reload | Rilegge configurazione e recipes.yml. |
| /brew season status, list, crops, set stagione | Stagioni (ogni 7 giorni reali). Solo set è per admin. |
| /brew comp start, stop, status, submit, leaderboard | Gare, vedi capitolo 4. |

| Permesso | Significato |
|---|---|
| sakagura.admin | Dare bevande e oggetti, impostare l'ebbrezza, ricaricare, gestire le gare. |
| sakagura.compete | Partecipare alle gare (tutti). |

## 2. Come funziona la produzione

| Passo | Cosa |
|---|---|
| 1 | Calderone con acqua. Con calore sotto (fuoco, falò, lava, magma o fornace accesa) cuoce, senza calore mescola. |
| 2 | Gli ingredienti si aggiungono uno alla volta; aspetta i minuti reali giusti; preleva con una bottiglia di vetro (tre bottiglie per calderone). |
| 3 | Distilla in uno Steam Still: bottiglie più polvere di glowstone, calore sotto; un giro ogni 20 s. |
| 4 | Fai invecchiare in una botte di un dato legno. Tempo reale, anche se il giocatore è offline; 1 anno = 20 minuti. |

La qualità deriva da quattro parti: ingredienti (40%), tempo (25%), giri di distillazione (15%) e invecchiamento/legno (20%). Un preparato che non corrisponde a nulla diventa una Murky Brew. La bottiglia stessa indica il passo successivo. Le botti di Sakagura non sono mai usate come magazzino da Kura, quindi le bevande continuano a invecchiare.

### Modificare le ricette

Tutte le ricette stanno in plugins/Sakagura/recipes.yml. Un generatore produce quel file, quindi le modifiche a mano possono sparire con una nuova versione. Per aggiungere o cambiare una ricetta chiedi a Claude: la mette nel generatore, testa tutte le ricette e consegna un nuovo file. Metti il file sul server e usa /brew reload; non serve riavviare.

## 3. Giardino, alberi e stagioni

I raccolti crescono in tre fasi su terra coltivata e si raccolgono con un clic destro sulla pianta (o rompendola con un pugno). I raccolti fuori stagione crescono dal 30 al 70% più lentamente; i raccolti selvatici compaiono solo nella stagione corrente. Gli alberi da frutto (sakura, ume, mela, mikan, acero e altri) crescono in tre fasi di circa 20 minuti; gli alberi maturi si raccolgono con un clic destro sul tronco, tre pugni abbattono l'albero. Un acero non dà frutti.

| Stagione | Raccolti (esempi) |
|---|---|
| Primavera | ciliegia, ume, pesca, fragola, albicocca, mikan, kiwi, pomodoro |
| Estate | yuzu, mango, papaya, guava, rambutan, frutto del drago, vaniglia, peperoncino |
| Autunno | uva, mela, pera, cachi, fico, oliva, orzo, segale, luppolo |
| Inverno | zenzero, wasabi, daikon, riso, ginepro, mirtillo |

L'ebbrezza è salvata per giocatore: sopra 80 il nome visualizzato diventa temporaneamente "Definitely Fine". Bere troppo causa svenimenti e un postumo di sbornia (ritorno al punto di spawn).

## 4. Gare

Un admin avvia una gara di produzione con /brew comp start giorni [tema] (da 1 a 30 giorni; il tema è una bevanda specifica oppure "any"). I giocatori presentano la bevanda che tengono in mano con /brew comp submit. Punteggio = qualità x 10 + difficoltà x 5; conta solo la migliore presentazione. /brew comp stop la chiude e premia i primi 3 (denaro, monete elite e un titolo).

Nota: è un evento che distribuisce denaro e monete. Annuncialo su Discord e controlla con /brew comp status se ne è già in corso una.

## 5. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| La bevanda diventa Murky Brew | Ingredienti o ordine sbagliati; fai guardare al giocatore /zukan o /brew recipes. |
| La distillazione non parte | C'è calore sotto? C'è polvere di glowstone? Controlla che sia davvero uno Steam Still. |
| La botte non invecchia | È una vera Sake Cask (non un barile comune)? Il legno deve corrispondere alla ricetta. |
| Un raccolto sembra sparito o sbagliato | È di stagione? I chunk sono caricati? Segnalalo a Claude con la posizione. |
| Ricetta mancante | Il recipes.yml del server è vecchio: chiedi a Claude il nuovo file e /brew reload. |
