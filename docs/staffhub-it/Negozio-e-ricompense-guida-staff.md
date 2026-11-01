# Negozio e ricompense

Guida per lo staff: creare articoli del negozio, kit, casse e ricompense senza programmare.

## 1. Panoramica

Una ricompensa o un articolo del negozio è di solito uno o più comandi di console che il server esegue per un giocatore. Non scrivi codice: scegli quale comando consegna l'oggetto. Gli stessi comandi si usano nel negozio del sito, nelle quest e nelle casse.

| Dove si imposta | Cosa |
|---|---|
| Staff Panel > Shop | Aggiungere o cambiare articoli: nome, prezzo, valuta, comando di consegna. Il sito li legge in diretta; non c'è nulla da pubblicare. |
| Staff Panel > Shop Settings | I comandi che tolgono denaro o monete. |
| quests.yml | Ricompense delle quest (vedi la guida IekQuests). |
| plugins/Relics/crates.yml | Casse: pacchetti di reliquie, dischi ed estrazioni casuali. |
| Moderation > Shop purchases | Panoramica di ciò che hanno comprato i giocatori. |

## 2. Valute

Il negozio ha tre valute, tutte guadagnate in gioco:

| Valuta | Cos'è | Si toglie con |
|---|---|---|
| Balance | Il denaro di gioco (economia CMI). | cmi money take {player} {amount} |
| Gears | La valuta del sito. | Gestita dal sito. |
| Elite coins | Le monete di EliteMobs. | em money remove {player} {amount} |

Nota: EliteMobs non ha un comando take. Le Elite coins si tolgono con em money remove. È ciò che contiene il campo coins in Shop Settings.

Pagare con balance o Elite coins funziona solo se l'acquirente è online. Se è offline il sito rifiuta l'acquisto: non viene addebitato nulla e non viene consegnato nulla.

## 3. Il comando di consegna

Nel campo giveCommand di un articolo scrivi cosa deve eseguire il server. {player} diventa il nome dell'acquirente. Concatena più comandi con ;;.

| Cosa vuoi dare | Comando |
|---|---|
| Oggetto MMOItems | mi give SWORD CUTLASS {player} |
| Zaino (Satchel) | satchel give canvas {player} |
| Cassa Relics | relics crate {player} nomecassa |
| Reliquia casuale | relics random {player} common (rare, epic) |
| Kit CMI | kit nomekit {player} |
| Oggetto EliteMobs | em loot give {player} file.yml |
| Kit e reliquia insieme | kit iek_copper {player};;relics random {player} common |

Nota: prova ogni comando tu stesso dalla console con il tuo nome. L'ordine degli argomenti dei kit dipende dalla versione di CMI: prova kit nome giocatore e kit giocatore nome finché uno funziona, e usa quello.

Se il giocatore è offline quando il comando dovrebbe partire, il plugin lo mette in coda e lo esegue al successivo accesso, compresa l'intera catena ;;.

## 4. Casse (Relics)

Una cassa è un pacchetto in plugins/Relics/crates.yml. La consegni con /relics crate giocatore nome. Ogni riga di un pacchetto ha una di queste forme:

| Riga | Significato |
|---|---|
| relic:nome | Dà esattamente quella reliquia. |
| disc:nome | Dà un disco musicale. |
| random:common (oppure rare/epic) | Estrae una reliquia casuale da quel gruppo. |
| random:common:collezione | Lo stesso, all'interno di una collezione. |
| once: true | La cassa si può aprire una sola volta per giocatore. |

I gruppi per random stanno nella configurazione di Relics sotto random.common, random.rare e random.epic. Le nuove reliquie vi entrano solo se le aggiungi tu. Dopo una modifica il proprietario o Claude ricarica o riavvia Relics.

## 5. Aggiungere un nuovo articolo

1. Decidi cosa riceve il giocatore e scrivi il comando. Provalo in console col tuo nome.
2. Vai su Staff Panel > Shop e scegli Add Item. Compila nome, breve descrizione, valuta, prezzo e giveCommand.
3. Compralo tu con un account di prova o chiedi a un collega. Controlla di ricevere l'oggetto e che la quantità sia giusta.
4. Controlla sotto Moderation > Shop purchases che l'acquisto sia stato registrato.

### Prezzi di esempio per i pacchetti

| Pacchetto | Valuta | Prezzo | Comando |
|---|---|---|---|
| Copper Pouch | balance | 250 | kit iek_copper {player};;relics random {player} common |
| Bronze Crate | balance | 750 | kit iek_bronze {player};;relics random {player} common |
| Steel Case | balance | 2000 | kit iek_steel {player};;relics random {player} rare |
| Gold Vault | coins | 15 | kit iek_gold {player};;relics random {player} epic |
| Lucky Dip | coins | 3 | relics random {player} rare |

Nota: sono suggerimenti. I prezzi si cambiano nel negozio stesso, senza pubblicare nulla. I kit (iek_copper e così via) devono prima esistere in gioco: creali con /kit create e aggiungi gli oggetti.

## 6. Quando qualcosa non va

| Sintomo | Causa |
|---|---|
| Acquisto rifiutato | L'acquirente è offline (balance e coins richiedono che sia online). |
| Addebitato, nessun oggetto | Il comando di consegna è sbagliato. Provalo in console e guarda il log. |
| Il kit non arriva | Ordine degli argomenti o cooldown del kit; prova entrambi gli ordini. |
| Le monete non vengono tolte | Il comando coins-take deve essere em money remove. |
| Reliquia mancante nella cassa | Nome non esattamente come in /relics list. |
