# Gradi - da Adventure a Jōki Tennō

Guida per lo staff: la scala dei gradi, i vantaggi per grado, come modificarli e risoluzione dei problemi.

## 1. Come funzionano i gradi

I giocatori iniziano come Adventure e salgono automaticamente in base al tempo di gioco attraverso cinque gradi giapponesi. CMI conta il tempo di gioco e fa l'avanzamento (Ranks.yml); a ogni passo CMI imposta il gruppo LuckPerms con parent set rank_N. Ogni gruppo di grado eredita dal precedente, quindi un grado più alto ha tutto ciò che hanno i più bassi più qualcosa in più. Lo staff è fuori dalla scala (gruppi engineer, admin, owner).

| Grado | Gruppo | Tempo di gioco | Lavori max | Home | Chunk Gouw | Zaino |
|---|---|---|---|---|---|---|
| Adventure | default | 0 | 0 | 3 | 22 | - |
| Kōsei | rank_1 | 1 h | 1 | 5 | 30 | - |
| Kikai-shi | rank_2 | 2 h | 2 | 7 | 40 | Canvas |
| Tetsu-bushi | rank_3 | 3 h | 3 | 9 | 55 | Copper |
| Karakuri Daimyō | rank_4 | 4 h | 4 | 12 | 75 | Iron |
| Jōki Tennō | rank_5 | 5 h | 5 | 15 | 100 | Diamond |

A ogni avanzamento il giocatore riceve anche una Rank-up Crate (un oggetto Relics casuale) e un messaggio a tutto il server. Ogni grado sblocca inoltre nuovi lavori (per esempio grado 1: contadino, pescatore, minatore).

## 2. Dove sta cosa

| Cosa | Dove |
|---|---|
| Quando si sale | CMI: plugins/CMI/Settings/Ranks.yml (PlayTime in secondi, Commands all'avanzamento). |
| Cosa può/ha un grado | LuckPerms: gruppi rank_1 - rank_5 (nodi come jobs.max.N, cmi.command.sethome.N, gouw.chunks.N, jobs.join.*). |
| Rank-up crate | Relics: plugins/Relics/crates.yml, cassa "rankup". |
| Limite Gouw | Gouw 0.3.0: il gouw.chunks.N più alto del giocatore, mai sotto max-chunks della configurazione (22). |
| Nome visualizzato | Nodo displayname. nel gruppo. |

Nota: LuckPerms usa MySQL; i vecchi file yaml in plugins/LuckPerms sono obsoleti e non contano.

## 3. Modificare qualcosa in un grado

Tutte le modifiche passano da LuckPerms con il tuo account staff in gioco, senza riavvio:

| Obiettivo | Comando |
|---|---|
| Dare un permesso a un grado | /lp group rank_3 permission set gouw.chunks.60 true |
| Togliere un permesso | /lp group rank_3 permission unset gouw.chunks.60 |
| Vedere cosa ha un grado | /lp group rank_3 permission info |
| Dare un grado a un giocatore | /lp user NOME parent set rank_3 |
| Cosa ha effettivamente un giocatore | /lp user NOME permission check gouw.chunks.55 |
| Caricare le modifiche dal database | /lp sync |

Per cambiare la cassa o lo zaino all'avanzamento: modifica le righe sotto il grado corrispondente in Ranks.yml (per esempio satchel give iron [playerName]) e usa /cmi reload. Il contenuto della cassa si modifica in crates.yml; viene riletto a ogni consegna.

Nota: sulla scala usa sempre parent set (non parent add), altrimenti un giocatore ha due gradi insieme. Fai un export o una copia del database prima di grandi modifiche.

## 4. Gruppi staff

| Gruppo | Scopo |
|---|---|
| engineer | Costruire, testare e permessi di contenuto: i *.admin dei nostri plugin, relics.build/give, zukan.cheat, comandi di moderazione (kick, ban, mute, jail). |
| admin | Tutto ciò che ha engineer più gouw.admin/bypass, additions, power, hihou, iekquests.admin. |
| owner | Tutti i permessi (*). |

Lo staff quindi resta fuori dalla scala del tempo di gioco. Un gruppo staff si assegna con /lp user NOME parent add engineer (qui add, perché esiste accanto al grado del giocatore) e si toglie con parent remove.

## 5. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Il giocatore non sale di grado | Controlla il suo tempo di gioco (CMI conta in secondi) e se il tempo AFK conta. Impostalo a mano: /cmi rankset NOME. |
| Il giocatore ha due gradi | Da qualche parte è stato usato parent add. Rimuovi quello sbagliato: /lp user NOME parent remove rank_X. |
| Il limite Gouw resta 22 | Gouw 0.3.0 è in funzione? Il gruppo ha un gouw.chunks.N? C'è stato un /lp sync? |
| Nessuna cassa o zaino all'avanzamento | Hai fatto /cmi reload? La cassa "rankup" è in crates.yml? Il nome del tier Satchel è giusto? Prova con un account di test. |
| Un nuovo nodo non funziona | /lp sync e poi /lp user NOME permission check. Controlla l'ortografia del nodo. |
