# Novità per lo staff - dal 1 novembre 2026

Riepilogo di tutto ciò che è cambiato di recente e che lo staff deve sapere. Dettagli nelle guide dei singoli plugin.

## Wildlife 0.9.6 - 0.9.8: il drago come compagno

- **Fischietto del drago**: richiama tutti i draghi del giocatore. Borse da sella, tinture e menu animale (/wildlife pet: siediti/alzati, vieni, rinomina, libera) sono descritti nella nuova pagina del sito /dragons.
- **Nido del drago (Dragon Nest)**: nuovo prop fabbricabile (Relics 0.7.3). Il drago lo reclama e vi riposa quando il giocatore sta fermo; le uova vengono deposte nel suo nido. Lo staff con wildlife.admin può liberare qualsiasi nido.
- **Etichette**: i draghi domati mostrano il nome sopra la testa; guardandoli compare nella action bar fame e nido. Anche il carbone ora cura un drago ferito.
- **Cavalcata**: il pilota siede su un sedile proprio all'altezza della sella (dragon.seat-height regolabile). Il drago aggira gli ostacoli con sensori invece di scivolare di lato.
- **Permessi per azione** (tutti true di default): wildlife.dragon.tame, ride, pet, whistle, bag, paint, nest; il gruppo wildlife.dragon.* li contiene tutti. Per vietare una singola azione a un rango usa LuckPerms con false su quel nodo.

## Gare di cucina e di sakè

- /kitchen comp e /brew comp: start, stop, status, submit, leaderboard. Il tab-complete e l'help ora li mostrano.
- Il sottocomando **area** compare nell'help (Kitchen 0.3.17 e Sakagura 0.7.12) per limitare l'invio a una regione WorldGuard, ma nel codice di Kitchen non è ancora collegato a un comando funzionante. Per ora l'area si imposta solo se il file di dati della gara ha già una regione. Non promettere ai giocatori che funzioni; segnalalo a Claude.
- /brew season status, list, crops, set: guarda o cambia la stagione del giardino (set solo admin). Il sito (/calendar) mostra la stagione attuale con il conto alla rovescia.

## Gouw 0.3.0

- Il limite di chunk dipende dal rango: dai al gruppo il permesso gouw.chunks.<numero> (vince il più alto); senza, vale max-chunks (16).
- /gouw admin addchunks giocatore n e /gouw admin buychunks giocatore n costo: chunk bonus oltre il limite del rango (massimo bonus-max, 100). Lo usa l'NPC dei contratti.

## Musica per regione (SiteSync 1.17.0)

La musica può essere impostata per regione, bioma o mondo. La traccia dello spawn gira in loop continuo; il Nether resta silenzioso. Richiede il pack più recente nel mixer del Resource Pack Manager.

## Sito web

Nuove pagine pubbliche: /dragons (guida al drago), /gouw (guida ai claim), /start (le prime ore), /search (ricerca su guide, wiki, news, ricette), /calendar (eventi, feste, stagione del giardino), /whats-new (novità per i giocatori), /kitchen, /relics, /tools e un modulo per segnalare un bug senza account (/report-bug). Dopo ogni aggiornamento del sito, lo staff deve premere i pulsanti Import nella wiki (/wiki) per aggiornare le pagine nel database.

## StaffHub

- **Gen-san**: l'assistente IA può ora proporre azioni (una scheda da confermare). Usa i tuoi permessi e il cestino (Trashbin) invece di cancellare per davvero.
- **Download con il tasto destro**: una pagina come Markdown, HTML o PDF; una cartella o una selezione come zip.

## EliteMobs: livelli degli elite troppo alti (2 novembre 2026)

> **Nota per greyarts:** questa modifica l'ha fatta antimatter, perché i mob vanilla nell'overworld erano diventati elite di livello troppo alto. Sul server ho cambiato tre valori in `plugins/EliteMobs/MobCombatSettings.yml`. Il file di prima è salvato come `MobCombatSettings.yml.bak-20261102-antimatter` nella stessa cartella. Il server non è stato riavviato. Il file sul server è aggiornato e il reload di EliteMobs (`em reload`) è stato eseguito. Se il comportamento non va bene, ripristina il backup e ricarica di nuovo EliteMobs. Nel repo la stessa configurazione è in `EliteMobs-config/MobCombatSettings.yml` (commit "changed by antimatter").

| Impostazione | Prima | Ora | Effetto |
|---|---|---|---|
| `naturalEliteMobsLevelCap` | 100 | 60 | Gli elite naturali non superano più il livello 60. |
| `useScaledCombatForNaturalElites` | false | true | L'elite viene simulato al livello del giocatore: conta l'equipaggiamento, non il livello. |
| `eliteMobsSpawnPercentage` | 1.0 | 0.5 | Metà degli spawn naturali diventa elite rispetto a prima. |

Cosa cambia per lo staff:
- Negli spawn naturali nell'overworld vedrete meno elite e nessuno sopra il livello 60.
- Dungeon, boss personalizzati ed eventi non cambiano: questi valori riguardano solo i mob che spawnano da soli.
- Se un giocatore chiede perché un mob ora è più debole o più raro, la risposta è questa modifica. Non promettere una data per un eventuale ritorno ai valori precedenti.
- Il reload si fa con il comando del plugin, `em reload`, non con il `/reload` generale (vedi la regola d'oro in 00-Indice).

## Aggiornamenti del 2 novembre 2026

- **Sakagura 0.7.13 (online):** la Steam Still e le botti di sakè non si girano più di 180 gradi al momento del posizionamento. Se vedete ancora una botte storta, segnalatelo: potrebbe dipendere dal modello e non dal plugin.
- **IekcorbarSiteSync 1.17.2 (online):** le advancement doppie al riavvio sono sparite. Ci sono tre nuove schede (Iekcorbar, Delvers and Hunters, Homesteaders) con 61 advancement personalizzate. Attenzione: al primo `/iadv check` molti giocatori ricevono di colpo una serie di advancement, perché le statistiche già raccolte vengono contate. È normale.
- **Sito - menu e ricettario:** il menu Play è stato diviso in Play e Guides. Il ricettario (Play > Recipes) ha ora una ricerca e un pannello con render, "Made by" e "Used in". Il pacchetto del sito è pronto ma non ancora online: lo carica il proprietario su Plesk.
- **Whistle (Steamworks):** il recipe sul sito ora corrisponde al gioco: rame in lingotto, nugget di ferro e carbone, sulla riga in alto della griglia. Nel gioco il cambio non è ancora attivo: serve la nuova jar di Steamworks, che non è ancora stata caricata.
- **Pipe (Steamworks):** il recipe sul sito è aggiornato a 2 lingotti di rame in colonna = 4 pipe.
- **Steamworks sul server:** la jar sul server non è identica alla versione di questo repository. Non la sostituiamo finché non è chiaro quale versione è la più recente.
