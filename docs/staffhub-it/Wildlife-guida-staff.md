# Wildlife - animali, zoo e il drago a vapore

Guida completa per lo staff: apparizione, riproduzione, lo zoo, il drago e risoluzione dei problemi.

## 1. Che cos'è Wildlife

Wildlife aggiunge 30 animali accanto a quelli normali di Minecraft (nulla è stato sostituito o rimosso), più un Field Journal (diario di campo), una scheda di advancement Wildlife, un set di costruzione per lo zoo (Zoo), un drago a vapore cavalcabile e una creatura d'atmosfera, il wraith. Si tratta di trovare, osservare e registrare, non di catturare o combattere. Gli animali sono rari di proposito. I modelli vengono da FreeMinecraftModels.

### Comandi e permessi

| Comando | Cosa fa | Permesso |
|---|---|---|
| /wildlife | Menu di tutte le specie e i tuoi totali. | wildlife.use |
| /wildlife journal / stats / list / find / tamed | Diario, statistiche, elenco delle specie, animale più vicino, i propri draghi. | wildlife.use |
| /wildlife spawn specie [rare] [baby] | Fa apparire un animale (anche il wraith). | wildlife.admin |
| /wildlife egg specie [giocatore] | Dà un uovo di spawn. | wildlife.admin |
| /wildlife give giocatore oggetto | Dà un oggetto (per esempio crab_claw, lizard_tail). | wildlife.admin |
| /wildlife giveall [giocatore] | Uno di ogni oggetto Wildlife. | wildlife.admin |
| /wildlife remove raggio | Rimuove gli animali in un raggio. | wildlife.admin |
| /wildlife info / reload | Informazioni sul server / ricarica la configurazione. | wildlife.admin |
| /zoo | Impostazioni dello zoo (vedi capitolo 4). | |

## 2. Come compaiono gli animali

Ogni 6 secondi ogni giocatore ha un tentativo. Gli animali sono legati al bioma e all'ora del giorno; alcuni solo di giorno, le lucciole solo di notte, le lumache sotto la pioggia. Le manopole principali stanno in plugins/Wildlife/config.yml:

| Chiave | Predefinito | Significato |
|---|---|---|
| spawning.enabled | true | Attiva o disattiva l'apparizione selvatica. |
| spawning.chance | 0.55 | Probabilità che un tentativo proceda. Tienila bassa: gli animali sono rari. |
| spawning.max-animals-near-player | 8 | Nessun nuovo animale se ce ne sono già tanti vicino. |
| spawning.rare-chance | 0.025 | Probabilità di una variante di colore rara. |
| species.specie.enabled / weight | true / 1-12 | Per specie acceso/spento e peso all'interno di un bioma. |
| ambience.enabled | false | Suoni di bioma. Spento perché abbiamo la nostra musica. |
| breeding.* | - | Riproduzione sì/no, tempo di crescita (20 min), pausa (5 min), limite di affollamento. |
| dragon.ride-speed | 0.55 | Velocità del drago cavalcato. |

Nota: spegnere una specie ferma solo i nuovi animali. Quelli già nel mondo restano. Le modifiche valgono dopo /wildlife reload.

Cosa fanno gli animali: cervi e lucertole fuggono (sneak aiuta), ricci e lumache si arrotolano, gli orsi dormono di notte e pescano di giorno, i cinghiali caricano se messi alle strette, le farfalle accelerano i raccolti, i capibara danno un passaggio alle anatre. Solo orsi e cinghiali adulti reagiscono davvero.

## 3. Riproduzione, cuccioli e custodia

Clic destro su un adulto con il suo cibo: 30 secondi di innamoramento. Due innamorati della stessa specie vicini fanno un cucciolo. Un cucciolo è a metà dimensione, innocuo e cresce in circa 20 minuti. Un dente di leone dorato tiene piccolo il cucciolo (un altro lo lascia crescere di nuovo). I genitori hanno bisogno di circa 5 minuti di riposo. Se ce ne sono già molti della stessa specie vicini, il gioco dice che è troppo affollato.

Gli animali selvatici spariscono quando nessuno è vicino. Un cartellino, un guinzaglio o un dente di leone dorato (per i cuccioli) tiene un animale per sempre. Gli animali tenuti in uno zoo possono morire di fame se non vengono nutriti: rallentano e non lasciano nulla.

## 4. Lo zoo (Zoo)

I giocatori costruiscono uno zoo funzionante con i props: chiosco dei biglietti, tornello, cancello, recinti, mangiatoia, panchine, bagni e molto altro (tutto è in /zoo list). Un chiosco vende biglietti (pagati tramite Vault al proprietario); tornelli e cancelli consumano un biglietto per ingresso.

| Comando | Chi | Cosa fa |
|---|---|---|
| /zoo set name / price / minutes / open valore | Proprietario del chiosco | Configura il chiosco che guardi. |
| /zoo info | Tutti | Mostra il chiosco che guardi. |
| /zoo ticket giocatore [minuti] | wildlife.zoo.staff | Biglietto gratuito dal chiosco che guardi (0 = un ingresso). |
| /zoo gate [close] | wildlife.zoo.staff | Apre o chiude il cancello/tornello che guardi (circa 20 s aperto). |
| /zoo give oggetto [giocatore] | wildlife.admin | Dà un prop dello zoo. |
| /zoo list | Tutti | Tutti i props. |

Nota: lo staff con wildlife.zoo.staff passa per ogni cancello e può cambiare qualsiasi chiosco. Rimuovi i chioschi altrui solo dopo averne parlato con i proprietari.

Gli animali con guinzaglio o cartellino hanno fame; una mangiatoia nutre ogni pochi secondi l'animale più affamato entro sei blocchi che vuole quel cibo.

## 5. Il drago a vapore e il wraith

Drago a vapore: vive in profondità nelle grotte ed è raro (appare solo sottoterra, probabilità tramite spawning.cave-chance). Si doma con il carbone (probabilità per ogni offerta), sneak + clic destro = siediti, clic destro = monta. Si guida con WASD, la direzione dello sguardo e spazio. Ci sono cinque aspetti e un raro Gilded. L'aspetto Ender è rarissimo (dragon.ender-chance).

Novità 0.9.6-0.9.8: fischietto, borse da sella, nido del drago, etichette col nome, sedile del pilota, permessi per azione (wildlife.dragon.tame/ride/pet/whistle/bag/paint/nest, tutti true di default). Vedi "Novità dal 1 novembre 2026" e la pagina del sito /dragons.

Nota: il lavoro sul drago (IA e modello) non è ancora testato in gioco. Se vedi cose strane, annota cosa e dove, e passalo a Claude. Puoi spegnere il drago con species.dragon.enabled: false.

Wraith: una creatura d'atmosfera, non un animale. Spento in natura; piazzalo tu con /wildlife spawn wraith. Vaga di notte, svanisce all'alba, non è nel diario e non si riproduce.

## 6. Oggetti, statue e materiali

Ogni animale ha una statua (adulto, raro, cucciolo) e un uovo di spawn. Le statue si fabbricano con pietra più l'oggetto lasciato dall'animale (3 pietre + oggetto; cucciolo 2 pietre; raro 3 pietre + oggetto + ametista).

Gli animali adulti non morti di fame lasciano materie prime (avorio, corna, pelli, piume, corno, scaglie di koi ...), che sono anche ingredienti in Kitchen e Sakagura. Il Field Journal si fabbrica (libro + piuma + semi di grano) o lo vende il mercante errante.

Trofeo del cacciatore: una specie che il giocatore ha prima registrato nel diario a volte lascia la propria statua quando viene uccisa. I cumuli di talpa compaiono dove c'è una talpa; gli artigli di granchio danno un blocco di portata in più.

## 7. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Non vedi animali | Vai nel bioma e nell'ora giusti; controlla che spawning.enabled sia attivo; sono rari di proposito. |
| Un animale sembra un pappagallo/mucca/pollo | Il modello manca o non è caricato: /fmm reload; altrimenti controlla la cartella dei modelli (Claude). |
| Il messaggio di morte nomina l'animale sbagliato | I vecchi animali non hanno il nome specie nascosto; i nuovi sì. Lasciali rigenerare. |
| Gli animali spariscono | Normale: gli animali selvatici se ne vanno quando non ci sono giocatori vicini. Un cartellino o un guinzaglio li tiene. |
| Troppi animali o lag | Abbassa max-animals-near-player o spegni una specie; /wildlife remove raggio ripulisce. |
