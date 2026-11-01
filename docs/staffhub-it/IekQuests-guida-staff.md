# IekQuests - creare quest e conversazioni con gli NPC

Guida per lo staff: come aggiungere nuove quest con il Quest Wizard o con quests.yml. Versione del plugin: 0.8.0.

## 1. Cos'è IekQuests

IekQuests è il nostro plugin di quest (ha sostituito BetonQuest). Una quest è una conversazione con un NPC, un elenco di passaggi (stage), ricompense e, se serve, contratti ripetibili. Tutto è descritto in un file YAML oppure costruito a clic nel gioco con il Quest Wizard. Tutto ciò che il giocatore vede è in inglese.

### File sul server (plugins/IekQuests/)

| File | Cos'è | Chi lo scrive |
|---|---|---|
| quests.yml | Tutte le quest scritte a mano. Il wizard non lo tocca mai. | Tu / Claude |
| wizard-quests.yml | Quest create con il wizard in gioco. | Il plugin (non modificarlo a mano) |
| items.yml | Gli oggetti personalizzati (IekItems), vedi la guida "Oggetti personalizzati". | Editor in gioco o a mano |
| config.yml | Pulsanti dell'inventario, modalità predefinita, viaggi (travel), cartelle dei pack. | Tu |
| data.yml | Progressi dei giocatori (stage, tag, punti, cooldown). | Il plugin |

### Comandi e permessi

| Comando | Cosa fa | Permesso |
|---|---|---|
| /questbook (/iquests) | Apre il libro delle quest. | iekquests.use (tutti) |
| /questbook mode [gui/chat] | Cambia modalità: finestre (GUI) o conversazione in chat. | tutti |
| /questbook reload | Rilegge quests.yml, wizard-quests.yml e items.yml. Serve dopo ogni modifica ai file. | iekquests.reload |
| /questbook check [giocatore] | Mostra lo stato delle quest di un giocatore (debug). | iekquests.info |
| /questbook reset giocatore [quest] | Azzera i progressi (una quest o tutte). | iekquests.reset |
| /questwizard (/qwiz) | Apre il costruttore di quest in gioco. | iekquests.admin |
| /questwizard delete id | Elimina una quest creata con il wizard. | iekquests.admin |
| /iekitem (/iitem, /ii) | Browser ed editor degli oggetti personalizzati. | iekquests.items |
| /iekrestart minuti/secondi/now/cancel | Annuncia un riavvio con conto alla rovescia, poi spegne. | iekquests.admin |

iekquests.admin include tutto. I moderatori hanno info e reload.

## 2. Due modi per fare una quest

**A. Il Quest Wizard (il più facile, senza YAML).** Scrivi /questwizard oppure clicca la stella del Nether in /questbook. Un menu a clic ti guida: id, titolo, NPC, stage, scelte, contratti e ricompense. Le risposte di testo si scrivono in chat ("cancel" annulla un campo, "done" chiude un elenco a più righe). L'occhio di Ender salva e attiva subito. Il wizard scrive solo in wizard-quests.yml.

**B. Modificare quests.yml** (massima potenza: sequenze, elenchi di oggetti, scelte che saltano). Modifica il file, salva, lancia /questbook reload e controlla la console.

Attenzione: un id nel wizard e uno in quests.yml non devono coincidere. Usa lettere minuscole, numeri e trattini bassi.

## 3. Anatomia di una quest

Ogni quest sta sotto la chiave quests:. Ecco le chiavi:

| Chiave | Significato |
|---|---|
| title / subtitle | Mostrati nel libro. Colori con & (&6 oro, &a verde, &7 grigio). |
| icon | Oggetto mostrato nel libro: BOOK, IRON_SWORD, PUMPKIN... |
| npc-id | Id Citizens dell'NPC che dà la quest. Più NPC: npc-ids: "90,91". |
| order | Numero di ordinamento nel libro (basso = prima). |
| group | Testo opzionale per raggruppare le quest nel libro. |
| start | Condizioni di avvio, ad es. "!tag:igor_done". Vuoto = chiunque. |
| mode | player (default: sceglie il giocatore), gui o chat (forza una modalità). |
| random | true = come inizio sceglie uno stage a caso da random-stages. |
| journal | Righe di descrizione nel libro. |
| stages | I passaggi, eseguiti nell'ordine scritto (sezione 4). |
| rewards | Azioni eseguite una volta al completamento (sezione 6). |
| repeat | Contratti ripetibili che si sbloccano dopo il completamento (sezione 5). |

## 4. Stage (i passaggi)

Sotto stages: ogni passaggio ha una chiave (nome libero) e un tipo. Gli stage vanno dall'alto in basso. Uno stage può saltare con goto: altrostage oppure next:.

| type | Finito quando... | Chiavi extra |
|---|---|---|
| talk | Il giocatore clicca il pulsante (solo dialogo). | lines, label, voice |
| craft | Il giocatore POSSIEDE la quantità (anche craftando). Gli oggetti restano. | material, amount, name |
| deliver | Il giocatore consegna gli oggetti, che vengono tolti. | material, amount, name |
| deliverall | Più oggetti diversi insieme. | items: "MATERIALE quantità nome" |
| walk | Il giocatore sta entro radius blocchi da un punto. | location "x;y;z;mondo", radius, flat |
| kill | Il giocatore uccide amount mob di tipo mob. | mob (es. ZOMBIE o MONSTER), amount |
| menu | Non finisce mai da solo: un menu di scelte (guardie, negozi). | choices |

### Chiavi che ogni stage può avere

- label: testo del pulsante verde, ad es. "&aConsegna 6 zucche".
- lines: ciò che dice l'NPC, una riga di chat per voce.
- voice: evento sonoro riprodotto con la riga, ad es. steamworks:milo.line01.
- cond: lo stage vale solo se le condizioni sono vere.
- rewards: azioni eseguite al completamento dello stage.
- flat: true (walk): ignora l'altezza, utile per regioni che vanno da Y -64 a 319.

### Scelte (opzioni di dialogo)

Uno stage può mostrare opzioni cliccabili. Sono un elenco; ognuna può rispondere, eseguire azioni, saltare a un altro stage o chiudere:

```
choices:
  - label: "&2Guarda la bancarella"
    say: "&7Vorkan: &8Guarda, non respirare sul vetro."
    do: ["shop mi stations open shop-weapon-1 %player%"]
  - label: "&eDevo pensarci"
    cond: "!tag:vorkan_met"
    goto: intro
    close: true
    limit-per-day: 3
```

label = testo del pulsante, say = risposta, do = azioni, goto = stage di destinazione, cond = nasconde l'opzione se falsa, close = chiude la finestra, limit-per-day = usi massimi al giorno reale.

## 5. Contratti (lavori ripetibili)

Dopo la quest, repeat: offre lavori ripetibili per monete senza rigiocare la storia. Ognuno ha il suo cooldown.

```
repeat:
  labels:
    label: "&aAltre quattro etichette"
    type: deliver        # deliver (default) o walk
    material: PAPER
    amount: 4
    name: ""             # filtro nome opzionale
    cooldown: 900        # secondi (1320 = standard dei mercanti)
    daily: false         # true = una volta al giorno reale
    money: 150
    eco: 2               # elite coins
    rewards: ["point merchant_rep +1"]
```

Il cooldown parte dalla consegna.

## 6. Ricompense, azioni e condizioni

Si usano in rewards delle quest, rewards degli stage, do delle scelte e nelle sequenze. Un'azione per riga. %player% viene sostituito dal nome del giocatore. Ciò che non è nell'elenco viene eseguito come comando da console.

| Azione | Cosa fa |
|---|---|
| money N / pay N | Dà / toglie N monete (economia CMI). pay fallisce se non ci sono abbastanza soldi. |
| eco N / ecopay N | Dà / toglie N elite coins. |
| eloot file | Dà un oggetto personalizzato EliteMobs. |
| item id [N] | Dà un oggetto IekItems (vedi la guida Oggetti personalizzati). |
| takeitem id/MATERIALE N | Toglie oggetti dal giocatore. |
| give MATERIALE N | Dà oggetti vanilla. |
| title A/B | Titolo grande A con sottotitolo B (separati da | nel file). |
| sound ns:chiave | Riproduce un suono (vanilla o del pack). |
| say testo | Invia una riga in chat. |
| broadcast testo | Messaggio a tutto il server. |
| tag+ nome / tag- nome | Imposta / toglie un tag di questa quest per il giocatore. |
| point cat +N | Aggiunge punti a un contatore (reputazione, credito...). |
| effect TIPO sec amp | Effetto pozione, ad es. effect NIGHT_VISION 60. |
| xp N / heal / feed | Esperienza, cura completa, sazietà. |
| perm+ nodo / perm- nodo | Dà / toglie un permesso tramite LuckPerms. |
| travel destinazione [cost=N] [warmup=S] | Viaggio per i GIOCATORI (vedi sotto). |
| teleport x;y;z;mondo | Teletrasporta lo staff. I giocatori ricevono solo un punto sulla bussola (regola: i giocatori camminano). |
| compass x;y;z;mondo | Punta la bussola del giocatore. |
| shop comando | Esegue un comando negozio da console. |
| cmdplayer comando | Fa eseguire un comando al giocatore stesso. |
| joinjob / leavejob | Entra / esce da un lavoro (Jobs). |
| chat testo | Fa dire una riga in chat al giocatore. |
| sequence id | Avvia una sequenza temporizzata (sezione 8). |
| altro | Eseguito come comando da console, ad es. mi give SWORD CUTLASS %player%. |

### Condizioni

La virgola significa E. Metti ! davanti per negare (!tag:done).

| Condizione | Vera quando |
|---|---|
| tag:nome | Il giocatore ha il tag in QUESTA quest. |
| qtag:quest:nome | Ha il tag in un'ALTRA quest (collega le quest in catena). |
| quest:id | Quella quest è completata. |
| point:cat>=N | Il contatore ha raggiunto N. |
| item:MATERIALE>=N | Il giocatore porta almeno N oggetti. |
| money>=N / money<N | Saldo del giocatore. |
| perm:nodo | Il giocatore ha il permesso. |
| world:nome | Il giocatore è in quel mondo. |
| level>=N | Livello di esperienza almeno N. |
| gamemode:modo | Modalità di gioco (survival, creative...). |
| day / night | Ora del giorno nel gioco. |
| npc:ID | Solo per le scelte: l'opzione appare solo presso quell'NPC. |

## 7. Modalità GUI e modalità conversazione

Ci sono due modi di giocare una quest:

- **Modalità GUI:** la finestra con i pulsanti.
- **Modalità conversazione:** l'NPC parla in chat e ogni opzione è un pulsante CLICCABILE nella chat, per esempio [ Inizia la quest ], [ scelta ], [ Consegna ]. Non si scrive nulla.

Cambiare modalità:
- /questbook mode (alterna) oppure /questbook mode gui / chat.
- Il pulsante "Conversation mode" in alto nella finestra della quest.
- Il pulsante [GUI mode] in fondo al pannello in chat.

La scelta è ricordata per giocatore. Il valore predefinito per i nuovi giocatori è default-mode in config.yml (gui o chat). Una quest può imporre una modalità con mode: gui oppure mode: chat; nel wizard c'è il pulsante "Conversation mode" nella schermata principale (player, gui, chat).

I pulsanti vecchi in chat non funzionano più se lo stage è cambiato: il giocatore vede "That option is no longer available" e il pannello aggiornato.

## 8. Viaggi per i giocatori: l'azione travel

Sintassi: travel destinazione [cost=N] [warmup=S]. La destinazione è un nome definito in config.yml sotto travel.destinations, oppure direttamente x;y;z;mondo.

```
travel:
  enabled: true
  warmup-seconds: 3
  cooldown-seconds: 0
  destinations:
    gilda: "2902;88;-408;iekcorbarSeason2"
```

- Il giocatore deve restare fermo durante il warmup (3 secondi di default), altrimenti il viaggio si annulla.
- Il costo viene scalato solo alla partenza (cmi money take) e finisce in transactions.log.
- Per un NPC "maestro dei viaggi" metti travel nella lista do di una scelta, con close: true.
- teleport resta riservato allo staff: travel è l'eccezione voluta per i giocatori. Si disattiva con travel.enabled: false.

## 9. Oggetti personalizzati nelle quest

Gli oggetti creati con IekItems si chiedono con material: item:id (conta solo QUELL'oggetto, non i sosia) e si danno con l'azione item id N. Gli altri oggetti personalizzati del server (parti Steamworks, materiali MMOItems, bottiglie Sakagura) sono oggetti Minecraft normali con un nome: si chiedono con il materiale giusto e una parte del nome in name:.

```
craft_gear:
  type: deliver
  material: item:ingranaggio_rotto   # un oggetto IekItems
  amount: 4
dalle_parti:
  type: deliver
  material: PAPER                    # le parti Steamworks sono PAPER
  name: "Cogwheel"                   # il nome deve CONTENERE questo
  amount: 4
pacchetto:
  type: deliverall
  items:
    - "PAPER 4 Cogwheel"
    - "PAPER 2 Shaft"
    - "COD 1 =Tuna"                  # = iniziale = nome esatto
```

Come creare gli oggetti: guida "Oggetti personalizzati".

## 10. Sequenze (scene temporizzate)

Una sequenza esegue azioni su una linea del tempo. Si scrivono in fondo a quests.yml sotto sequences:, una riga secondi|azione, e si avviano con l'azione sequence id.

```
sequences:
  md_show:
    - "0|say &eMad Doctor: &7Resta perfettamente immobile."
    - "3|effect POISON 3 1"
    - "6|say &bMachine: &7Applico il veleno."
    - "9|teleport 100;64;-200;iekcorbarSeason2"
```

Voci e suoni vivono nel resource pack (sounds/make_voices.py). Nuove voci richiedono di ricostruire il pack: chiedi a Claude.

## 11. NPC (Citizens)

- Creare un NPC: /npc create Nome. Selezionarlo guardandolo e /npc select; /npc mostra l'id. Scrivi quel numero in npc-id.
- Skin: /npc skin --url link-al-png. Spostare: /npc move. Eliminare: /npc remove.
- Il wizard può creare l'NPC dove stai e collegarlo da solo.
- Un NPC può servire più quest (stesso npc-id): il giocatore vede un elenco.

## 12. Esempio completo

Una piccola quest: parla, porta 3 zucche, vieni pagato. Incollala sotto quests: (mantieni i due spazi di rientro!).

```
  aiuto_raccolto:
    title: "&6Aiutante del raccolto"
    subtitle: "Contadina Aiko"
    icon: PUMPKIN
    npc-id: 120
    order: 90
    start: "!tag:harvest_done"
    journal:
      - "Aiko ha bisogno di tre zucche per la festa."
    stages:
      talk:
        type: talk
        label: "&aCerto, ti aiuto."
        lines:
          - "&fAiko: &7La festa inizia stasera e il mio"
          - "&7campo di zucche è vuoto. Tre, per favore!"
      pumpkins:
        type: deliver
        material: PUMPKIN
        amount: 3
        label: "&aConsegna 3 zucche"
        lines:
          - "&fAiko: &7Tre intere? Meraviglioso."
    rewards:
      - "money 250"
      - "eco 5"
      - "sound steamworks:quest.complete"
      - "tag+ harvest_done"
    repeat:
      more:
        label: "&aAltre due zucche"
        material: PUMPKIN
        amount: 2
        cooldown: 1320
        money: 80
```

### Come metterla in produzione

1. Apri plugins/IekQuests/quests.yml, incolla, salva.
2. Lancia /questbook reload e guarda la console: il numero di quest deve salire. Gli avvisi indicano quest e problema.
3. Clicca l'NPC e percorri ogni stage. /questbook reset te aiuto_raccolto la riavvia.
4. Chiedi a Claude di fare il commit, così il file è in git.

## 13. Quando non funziona

| Sintomo | Causa probabile |
|---|---|
| Quest assente dal libro / NPC muto | Errore YAML (rientro, virgolette mancanti, tabulazione), npc-id errato o nessun /questbook reload. |
| Pulsante di avvio assente | La condizione start è falsa (un tag è già impostato: azzera il giocatore). |
| Oggetti non accettati | Materiale errato o il filtro name non coincide con il nome visibile. |
| Codici colore mostrati come &6 | Mancano le virgolette attorno alla riga, o refuso. |
| Lo stage non si completa mai | walk: mondo errato o raggio troppo piccolo (usa flat: true per regioni grandi). kill: nome del mob errato. |
| La ricompensa non fa nulla | Azione scritta male = viene eseguita come comando da console e fallisce. Controlla la console. |
| Il teletrasporto non fa nulla | teleport è solo per lo staff; per i giocatori usa travel o la bussola. |

Tieni sempre le righe di testo YAML tra virgolette doppie. Fai una copia di quests.yml prima di una modifica importante (quests.yml.bak-data). Un file rotto fa fallire tutto il reload; le vecchie quest restano caricate.
