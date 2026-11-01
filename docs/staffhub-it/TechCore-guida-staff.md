# TechCore - tecnologia e macchine

Guida per lo staff: macchine, blueprint, file e risoluzione dei problemi.

## 1. Che cos'è TechCore

TechCore è il plugin di tecnologia del server (versione 0.9.35), un plugin a parte di un autore esterno, non nostro. I giocatori costruiscono con esso macchine in due ere: Era 1 (TechnoCore, generatore a combustibile, breaker, compattatore, lavaminerali, placer, estrattore, miscelatore di fluidi, pompa, serbatoio) ed Era 2 (generatore rotativo, power injector, batteria, trasformatore, motore a cinghia, avvolgitore di bobine, magnetizzatore, compressore di polvere, pressa di potenziamento). Ci sono anche multiblocchi (pressa meccanica, serbatoio grande), tubi (rame e acciaio), ruote idrauliche, mulini a vento e pannelli solari, gadget per l'Evolution Belt e materiali propri (acciaio, nuclei, polveri, cristalli).

Le macchine funzionano con energia TCU (batterie, power injector, connettori) e usano fluidi tramite tubi. Ogni macchina ha la propria GUI.

### Comandi e permessi

| Comando | Cosa fa |
|---|---|
| /techcore (/tech) | Comando admin con sottocomandi (permesso techcore.admin). |
| /techcore blueprint grant giocatore ERA_x:id | Dà un blueprint, cioè il diritto di fabbricare una ricetta. IekQuests lo usa come ricompensa. |
| /techcore gadget validate | Controlla gadgets.yml senza applicare nulla. |
| /techcore advancement ... | Elenco, info, concessione, revoca, reset degli advancement di TechCore. |
| /techbrowser | Tech browser: sfoglia tutte le macchine e le ricette (uso dei giocatori). |
| /techgui | Test della GUI del generatore a combustibile (admin). |

| Permesso | Significato |
|---|---|
| techcore.admin | Tutto l'admin (predefinito op). |
| techcore.admin.blueprint | Dare blueprint. |
| techcore.admin.resourcepack | Gestione del resource pack. |
| techcore.gadget.admin / duration | Gestire i gadget / impostare la durata. |
| techcore.minimap.admin | Gestire la minimappa. |
| techcore.cartography.control | Attivo di default: i giocatori usano il proprio modulo mappa. |

Nota: l'elenco esatto dei sottocomandi non è nelle nostre fonti; scrivi /techcore in gioco per l'elenco attuale. Questo capitolo è stato composto dai file del server, non dal manuale dell'autore.

## 2. Blueprint e progressione

Le ricette dipendono dai blueprint: un giocatore può fabbricare una macchina solo se ha il blueprint (ora 41 ricette). I blueprint arrivano dal gioco stesso (piazzare e fabbricare una macchina, Engineer's Hammer, Wrench) oppure come ricompensa di quest; il corso Engineer in IekQuests li dà per capitolo. Lo staff può consegnarne uno a mano quando qualcosa va storto.

| File (plugins/TechCore/) | Cosa contiene |
|---|---|
| blueprints/ | era1_crafting, era2_crafting, static_machines, copper_pipe_craft: quale azione concede quale blueprint. |
| recipes/ | Ricette: leghe, breaker, equipaggiamento, gadget, era 1 e 2. |
| macchine/ (era 1, era 2, multiblocco) | Proprietà per macchina. |
| items/ | Tutti gli oggetti: macchine, materiali, tubi, gadget, strumenti. |
| machine-capabilities.yml | Slot, serbatoi, batterie e porte per macchina (capacità TCU). |
| gadgets.yml, equipment*.yml | Evolution Belt, gadget (diagnostica, cartografia), equipaggiamento. |
| config.yml | Resource pack, profili dei blocchi, HUD/action bar, velocità di rotazione di ruote e mulini. |
| techcore.db | Tutte le macchine, i progressi e il tempo dei gadget (SQLite). Non manometterlo mai, fanne il backup. |
| backups/ | Backup automatici fatti da TechCore stesso. |

## 3. Insieme ai nostri plugin

TechCore e Steamworks sono due sistemi vapore/tecnologia separati; non condividono nulla. Steamworks usa rotazione e velocità, TechCore usa energia TCU e fluidi. Un dettaglio importante: a ogni avvio del server TechCore scrive il proprio codice di caricamento in tutti gli altri jar dei plugin (quindi i nostri jar crescono di circa 230 KB). È normale e innocuo per noi; significa solo che lo sha1 di un jar sul server non è mai uguale alla nostra build e che si deve confrontare per classi. Caricare un jar fresco funziona bene: al prossimo avvio viene "patchato" di nuovo.

TechCore crea un proprio resource pack (generated/techcore-resourcepack.zip); sta accanto al nostro pack nel mixer del ResourcePackManager.

## 4. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Il giocatore non riesce a fabbricare una macchina | Ha il blueprint? Dallo con /techcore blueprint grant. |
| La macchina non funziona | C'è energia (TCU) o fluido? Guarda la GUI e le porte in machine-capabilities.yml. |
| Modello o GUI viola/vuoti | Pack di TechCore non caricato: /resourcepacks reload, riavvia il client. |
| Avviso "ActionBar vs PacketEvents" nel log | Noto e non nostro; il compositor funziona (PacketEvents 2.14). |
| Nuova versione di TechCore | Non aggiornare mai alla cieca: prima fai il backup di plugins/TechCore, poi sostituisci il jar nell'ordine consueto. |
