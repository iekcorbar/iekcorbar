# Ranghi e permessi (LuckPerms)

Guida per lo staff: chi può fare cosa, come assegnare un rango e cosa lasciare stare.

## 1. Come funziona

Tutti i permessi del server passano da LuckPerms. Si assegna a un giocatore un gruppo (rango); il gruppo contiene i permessi. I gruppi sono salvati nel database, quindi sono uguali ovunque. Non dare mai permessi sciolti a un giocatore quando un gruppo va bene.

### I gruppi

| Gruppo | Cos'è | Aggiunto rispetto a quello sotto |
|---|---|---|
| default | Tutti. Permessi da giocatore di tutti i plugin. | - |
| engineer | Moderatore. | relics.build, relics.give, wildlife.zoo.staff, iekquests.info, iekquests.reload, iekcorbar.webstats, satchel.admin |
| admin | Eredita tutto da engineer. | gouw.admin, gouw.bypass, hihou.admin, iekquests.admin, additions.admin, power.admin, afkcinema.admin |
| owner | Proprietario. Peso massimo (1100), nome visualizzato e prefisso a ingranaggio propri. | Stile proprio; i diritti arrivano dal gruppo admin. |

Nota: i nomi sopra sono i nomi dei gruppi in LuckPerms. Lo staff mostra anche un prefisso nella lista tab.

## 2. Comandi di tutti i giorni

| Comando | Cosa fa |
|---|---|
| /lp user nome info | Mostra gruppi e permessi di un giocatore. Guarda sempre prima. |
| /lp user nome parent add gruppo | Aggiunge un gruppo a un giocatore. |
| /lp user nome parent remove gruppo | Toglie un gruppo. |
| /lp group gruppo info | Mostra cosa contiene un gruppo. |
| /lp user nome permission info | Mostra i permessi sciolti di un giocatore. |
| /lp sync | Rilegge tutto dal database. Fallo dopo una modifica fatta fuori dal gioco. |
| /tab reload | Aggiorna i prefissi nella lista tab dopo un cambio di rango. |

### Assegnare un rango, passo per passo

1. Scrivi /lp user nome info e controlla che sia il giocatore giusto e quali gruppi ha già.
2. Scrivi /lp user nome parent add engineer (o il gruppo che intendi).
3. Controlla con /lp user nome info che il gruppo ci sia. Fai /tab reload per il prefisso.
4. Dì al giocatore che ha un nuovo rango e cosa comporta.

## 3. Cose da sapere

| Tema | Spiegazione |
|---|---|
| OP non è un rango | OP scavalca LuckPerms. Preferisci dare il gruppo a OP. Imposta OP solo se lo chiede il proprietario. |
| Caratteri jolly (*) | Non dare * né plugin.*. Un errore concederebbe troppo. |
| Prefisso in chat | Il prefisso compare nella lista tab, non in chat. |
| Livello sopra la testa | Non vedi mai il tuo nome sopra la tua testa (vanilla). L'etichetta "Combat lvl" viene da EliteMobs, non da LuckPerms o TAB. |
| Permessi sciolti sui giocatori | Alcuni giocatori hanno di proposito un nodo sciolto (per esempio una ricompensa di quest). Non toglierli senza controllare perché ci sono. |
| Scale di gradi | I gradi dei lavori e i gradi di tempo di gioco usano entrambi gruppi; non vanno mescolati (parent add contro parent set). Non è ancora deciso: chiedi al proprietario prima di cambiarli. |

### Quando un membro dello staff se ne va

1. Togli il gruppo staff con /lp user nome parent remove engineer (o admin).
2. Controlla che non sia OP (chiedi al proprietario o a Claude di guardare ops.json).
3. Controlla i suoi diritti nello Staff Panel del sito e informa il proprietario.

## 4. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Un giocatore non ha un comando | Controlla con /lp user nome info che il gruppo ci sia; poi /lp sync. |
| Il prefisso non cambia | /tab reload. |
| Lo staff non riesce a fare qualcosa che dovrebbe | Segnala il plugin e il comando a Claude; Claude trova il nodo giusto e lo mette nel gruppo. |
| Non sai quale nodo serve | Non tirare a indovinare. Chiedi a Claude. |

Nota: prima di una grande pulizia Claude fa un backup delle tabelle di LuckPerms. Non fare tu modifiche in blocco.
