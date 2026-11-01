# Oggetti personalizzati - creare e consegnare

Guida per lo staff: come creare nuovi oggetti in gioco con IekItems (dentro IekQuests) o con MMOItems, e come usarli in quest, negozi e ricompense.

## 1. Quale plugin crea gli oggetti?

| Famiglia di oggetti | Creati con | Come si consegnano |
|---|---|---|
| Oggetti di quest, ricompense, oggetti con azioni al clic destro | IekItems (in IekQuests) | /iekitem give giocatore id [N] |
| Armi, armature, strumenti, cibo, gemme, materiali (circa 1.080 oggetti in 49 file) | MMOItems | /mi give TIPO ID [giocatore] |
| Macchine, nastri, chiavi inglesi | Steamworks | /sw give ... |
| Props, collezioni, casse | Relics | /relics give ... e /relics crate giocatore nome |
| Zaini | Satchel | /satchel give tier giocatore |
| Bevande, oggetti del giardino | Sakagura | /brew give ... |
| Bottino dei boss | EliteMobs | /em loot give giocatore file.yml |
| Pulsanti dell'inventario (profilo, warp...) | IekQuests (config.yml) | Automatico all'ingresso |

Gli oggetti dei nostri plugin non si creano a mano: si consegnano soltanto. Per creare qualcosa di nuovo usa IekItems (semplice, tutto in gioco) oppure MMOItems (statistiche da RPG complete).

## 2. IekItems - l'editor in gioco

IekItems vive dentro IekQuests. Le definizioni sono in plugins/IekQuests/items.yml. Ogni oggetto porta il proprio id nei dati nascosti, quindi le quest lo riconoscono esattamente.

### Aprire l'editor

Scrivi /iekitem (alias /iitem, /ii). Si apre il browser con tutti gli oggetti. Permesso: iekquests.items (oppure iekquests.admin).

- Clic su un oggetto = modificarlo. Shift+clic = ricevere una copia.
- **+ New item**: scrivi un id in chat (a-z, 0-9, _) e poi lo modifichi.
- **From my hand**: l'oggetto che hai in mano diventa un IekItem (nome, lore, incantesimi e modello vengono copiati).
- **Search**: filtra per parte dell'id.
- /iekitem edit id apre direttamente un oggetto.

### Passo per passo: creare un oggetto

1. /iekitem, poi **+ New item**, scrivi per esempio ingranaggio_rotto.
2. **Material**: scrivi il materiale (per esempio IRON_INGOT) oppure hand.
3. **Name**: il nome visibile, con i colori &.
4. **Lore**: una riga per messaggio; "done" termina, "clear" svuota, "cancel" annulla.
5. **Model**: scegli il modello (sezione 3).
6. Prova con **Get a copy**. Il pannello a sinistra in alto mostra l'anteprima.

### I campi dell'editor

| Campo | Cosa fa |
|---|---|
| Material, Name, Lore | Aspetto di base. |
| Enchants | Righe come "sharpness 3". |
| Glow | Riflesso incantato senza incantesimi. |
| Unbreakable | Infrangibile. |
| Consume on use | Un esemplare si consuma a ogni clic destro. |
| Model | Modello 3D dal resource pack (sezione 3). |
| Attributes | Righe come "attack_damage 7 add_number mainhand" (attributo, valore, operazione, slot). |
| Flags | Nasconde parti del tooltip: HIDE_ATTRIBUTES, HIDE_ENCHANTS... |
| Right-click actions | Azioni eseguite con il clic destro (stessa sintassi delle ricompense delle quest, per esempio effect REGENERATION 10 0). |
| Use cooldown / Use permission / Use sound / Use message | Pausa tra gli usi, permesso richiesto, suono e messaggio. |
| Soulbound | L'oggetto resta al giocatore dopo la morte. |
| Undroppable | Non si può buttare a terra. |
| Color | Colore esadecimale per armature di cuoio e pozioni (es. ff8800). |
| Head texture | Valore base64 per le teste. |
| Custom model data / Max stack size | Numero per pack vecchio stile / dimensione massima della pila. |
| Get a copy / Delete | Prendi un esemplare / elimina (shift+clic). |

Nota: in "tag+" e "point" nelle azioni al clic destro i dati non vengono salvati: solo le quest hanno progressi.

### Il file items.yml

Tutto ciò che fa l'editor si può scrivere nel file. Esempio:

```
items:
  spada_esempio:
    material: IRON_SWORD
    name: "&bSpada d'esempio"
    lore:
      - "&7Un esempio da items.yml."
    enchants:
      sharpness: 2
    attributes:
      - "attack_damage 7 add_number mainhand"
    flags: [HIDE_ATTRIBUTES]
  bottiglia_grazie:
    material: HONEY_BOTTLE
    name: "&eBottiglia di ringraziamento"
    glow: true
    consume: true
    use:
      - "say &eGrazie!"
      - "effect REGENERATION 10 0"
```

Dopo ogni modifica a mano: /iekitem reload (o /questbook reload).

## 3. Assegnare un modello (aspetto 3D)

Nell'editor clicca **Model**. Compare l'elenco di tutti i modelli di oggetto presenti nei resource pack di plugins/ResourcePackManager/mixer/ (e di plugins/IekQuests/packs/). Ogni voce mostra il TUO oggetto con quel modello come anteprima.

- Clic su un modello = assegnato.
- **Search**: filtra per nome. **Type a model id**: scrivi l'id completo (per esempio mmoitems:sword/long_sword).
- **Remove model**: torna all'aspetto vanilla.
- **Rescan packs**: rilegge i pack dopo averne caricato uno nuovo (la prima scansione richiede qualche secondo).
- Si vede il modello solo se il tuo client ha caricato il pack.
- Le cartelle da scandire si cambiano con pack-dirs in config.yml.

Per un modello davvero nuovo: dai a Claude l'idea o un'immagine. Viene generato file modello + texture, il pack riceve un nuovo numero BUILD e tu lo carichi sul server (cartella mixer) e ricarichi il Resource Pack Manager. I giocatori devono riscaricare il pack. Modelli e texture non si copiano mai da altri server o mod: li disegniamo o generiamo noi.

## 4. Usare gli oggetti IekItems nelle quest

- **Ricompensa:** azione item id [N] (nel wizard: pulsante viola "+ Item reward").
- **Consegna o richiesta:** material: item:id nello stage deliver/craft (nel wizard: pulsante viola "Custom item"). Conta solo quell'oggetto, non i sosia.
- **Togliere:** takeitem id N.
- Nel wizard, i pulsanti con il frammento di ametista aprono lo stesso browser come selettore: puoi anche crearne uno nuovo, modificarlo del tutto (modello compreso) e premere **Select this item**.

## 5. MMOItems - per statistiche da RPG

MMOItems crea armi, armature, cibo, materiali e strumenti con statistiche. Ha un editor in gioco, quindi puoi creare un oggetto senza aprire file.

### Il sistema degli ID

Ogni MMOItem ha un TIPO e un ID. Il tipo decide quali statistiche può avere; l'ID è il nome unico in MAIUSCOLE. Esempio: tipo SWORD, id CUTLASS.

Tipi sul server: sword, long_sword, thrusting_sword, katana, dagger, axe, greataxe, hammer, greathammer, spear, halberd, lance, whip, bow, crossbow, musket, staff, greatstaff, wand, tome, lute, shield, armor, gauntlet, ring, talisman, ornament, catalyst, off_catalyst, tool, consumable, material, gem_stone, fish, block, miscellaneous e i tipi dei lavori (00_job_miner, 00_job_chef...).

I file stanno in plugins/MMOItems/item/tipo.yml sul server. La copia del server è la verità.

### Creare in gioco

1. /mi create SWORD LA_MIA_LAMA - l'oggetto esiste (base).
2. /mi edit SWORD LA_MIA_LAMA - si apre un menu con tutte le statistiche del tipo.
3. Clicca una statistica: la chat chiede il valore; scrivilo. "cancel" annulla. Clic destro di solito la rimuove.
4. /mi give SWORD LA_MIA_LAMA tuonome. /mi browse SWORD elenca tutte le spade.
5. Dopo una modifica: /mi reload e /mi give di nuovo (le copie vecchie nell'inventario possono restare indietro).
6. /mi copy clona, /mi delete elimina (chiedi prima: i giocatori potrebbero averne copie).

### Statistiche utili

| Statistica | Significato |
|---|---|
| name, lore | Nome (con colori) e righe di descrizione. |
| material | L'oggetto Minecraft di base (IRON_SWORD, PAPER...). |
| model | Il modello 3D, per esempio mmoitems:sword/cutlass. |
| tier | Rarità (COMMON...). |
| required-level | Livello necessario per usarlo. |
| attack-damage / attack-speed | Danno e velocità. Usa base + spread per valori casuali. |
| armor, armor-toughness, max-health | Statistiche d'armatura. |
| critical-strike-chance / -power | Colpi critici. |
| element, ability | Danno elementale e abilità speciali. |
| gem-sockets | Quante gemme si possono incastonare (le gemme sono di tipo gem_stone). |
| restore-health / -food / -saturation | Cibo e pozioni (tipo consumable). |
| unbreakable, max-durability | Durabilità. |
| disable-crafting / -smelting / -smithing / -enchanting / -repairing | true = blocca la ricetta o l'uso vanilla. I materiali li usano tutti. |
| displayed-type | Etichetta sotto il nome. |

### Modificare il file invece del menu

```
CUTLASS:
  base:
    model: mmoitems:sword/cutlass
    material: IRON_SWORD
    attack-damage:
      base: 7.0
      spread: 0.04
      max-spread: 0.13
    attack-speed:
      base: 1.7
    lore:
      - '&7Un po'' meglio di una spada di ferro.'
    required-level: 3.0
    tier: COMMON
```

Modifica il file sul server, poi /mi reload. Un refuso può impedire di caricare l'intero file: fai una copia prima e controlla la console dopo il reload.

### Altri usi

- **Ricompensa di quest:** azione mi give SWORD CUTLASS %player%.
- **Negozi degli NPC:** una stazione MMOItems, per esempio mi stations open shop-weapon-1 %player%.
- **Negozio sul sito:** metti il comando give nel campo comando dell'articolo; più comandi si concatenano con ;; (per esempio un kit e una reliquia).
- **Crafting:** l'editor ha un menu per ricette personalizzate; i materiali fatti per le quest di solito hanno il crafting disattivato.

## 6. Regole che seguiamo

- Non rinominare né eliminare un oggetto che i giocatori già possiedono senza avvisare: crea un nuovo ID.
- Fai una copia prima delle modifiche grandi. Dopo una buona sessione chiedi a Claude di prelevare la cartella dal server e fare il commit, così esiste una copia in git.
- Mai nomi di giocatori o dati personali nella lore.

## 7. Problemi comuni

| Sintomo | Verifica |
|---|---|
| /mi give dice "unknown item" | Tipo o ID scritti male (gli ID sono in maiuscolo), oppure il file non si è caricato: guarda la console dopo /mi reload. |
| L'oggetto è carta semplice / aspetto sbagliato | Riga model mancante o errata; il pack non è caricato sul tuo client; oppure non è stato aggiornato. |
| Cubo viola/nero | Il modello esiste nel pack ma manca la texture o la definizione: dillo a Claude, l'audit del pack lo trova. |
| Il menu di modifica non fa nulla dopo il clic | Guarda la chat: aspetta un valore da te. Scrivilo o scrivi cancel. |
| Le modifiche non si vedono sulle copie vecchie | /mi reload e poi /mi give di nuovo. |
| Tutto il file smette di caricarsi | Errore YAML vicino alla tua modifica: ripristina la copia, ricarica e rifai con calma. |
| La quest non accetta l'oggetto | La quest deve chiedere lo stesso materiale e un nome che contenga il filtro (per gli IekItems usa item:id). |
| Il browser dei modelli è vuoto | Il pack non è nella cartella mixer, oppure usa "Rescan packs". Controlla pack-dirs. |
