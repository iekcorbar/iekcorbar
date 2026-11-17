# Kura - rete di magazzino

Guida per lo staff: componenti, impostazioni, collaborazione e risoluzione dei problemi.

## 1. Che cos'è Kura

Kura è la nostra rete di magazzino: un Inventory Cable unisce bauli, barili e shulker box in un'unica rete. Un Storage Terminal mostra poi tutto con ricerca e ordinamento, un Crafting Terminal fabbrica direttamente dal magazzino e i terminali wireless aprono la rete a distanza. Funziona tutto nel gioco normale, senza mod.

### Comandi e permessi

| Comando | Cosa fa |
|---|---|
| /kura (/storage) | Breve aiuto e link alla wiki. |
| /kura list | Tutti gli oggetti di Kura. |
| /kura give giocatore oggetto [quantità] | Dà un componente o uno strumento. |
| /kura giveall [giocatore] | Uno di ogni oggetto Kura (entra in shulker box con nome). |
| /kura reload | Rilegge la configurazione. |

| Permesso | Significato |
|---|---|
| kura.admin | Dare oggetti, ricaricare la configurazione. |

## 2. Come funziona la rete

Dal 0.1.11 vale una sola regola: **un cavo unisce ciò che tocca**. Ogni baule, barile o shulker box a contatto con un cavo entra nella rete, e così i contenitori che toccano quelli; un cavo qualsiasi è già una rete (non serve più un blocco centrale). Inventory Connector, Inventory Trim e Cable Connector non si creano più ma restano funzionanti come cavi, quindi le reti già costruite sul server reggono. La rete viene ricostruita solo quando qualcosa cambia (piazzare, rompere, esplosione, caricamento chunk); nessun timer scansiona il mondo.

| Componente | Ruolo |
|---|---|
| Storage Terminal | Mostra e gestisce tutto; ricerca per parole, #gruppo o @plugin. |
| Crafting Terminal | Una griglia che si riempie da sola dalla rete; shift+clic sul risultato per una pila. |
| Inventory Hopper | Sposta 8 oggetti per volta da dietro a davanti; filtro con clic destro. |
| Inventory Interface | Barile che importa (tutto va nella rete) o esporta (tiene pronta una pila di ogni oggetto filtro). |
| Level Emitter | Emette un segnale redstone sopra o sotto una certa quantità di un oggetto. |
| Open Crate / Filing Cabinet | Butta fuori tutto davanti / conserva fino a 256 oggetti di un tipo. |
| Terminali wireless | 16 blocchi (normale) o 64 blocchi; con un beacon ovunque nel mondo o in qualsiasi mondo (avanzato). |

Nota: fornaci, tramogge, banchi di pozioni e botti di Sakagura non contano mai come magazzino. Una rete smette di crescere a 4096 blocchi; il terminale te lo dice.

## 3. Impostazioni (config.yml)

| Chiave | Predefinito | Significato |
|---|---|---|
| storage-blocks | chest, barrel, shulker, copper chest | Quali blocchi contano come magazzino. |
| excluded-container-keys | sakagura:cask | Contenitori che non sono mai magazzino. |
| network.max-blocks | 4096 | Dimensione massima della rete. |
| parts.break-hits | 3 | Colpi per rompere un componente (1 in creativa). |
| parts.break-window-ticks | 60 | Ticks in cui i colpi contano (3 s); ogni componente si toglie anche con shift + clic destro a mano vuota. |
| inventory-hopper.items-per-transfer | 8 | Oggetti per trasferimento. |
| wireless.range / advanced-range | 16 / 64 | Portata del terminale wireless. |
| wireless.max-loaded-chunks | 25 | Chunk che una rete remota tiene caricati. |
| filing-cabinet.capacity | 256 | Oggetti in un Filing Cabinet. |
| couriers.enabled / radius | true / 12 | Corrieri golem di rame; raggio di ricerca 4-24 blocchi. |

Corrieri: fai clic destro su un golem di rame (entro 12 blocchi da un'Interface) con l'Inventory Configurator; da quel momento va a prendere oggetti dai bauli di rame o dai bauli dove le macchine Steamworks consegnano. Un altro clic destro lo libera.

## 4. Collaborazione con altri plugin

- Zukan: un Crafting Terminal ha un pulsante Recipes; in una ricetta "Move ingredients" riempie la griglia dalla rete.
- Steamworks: tramogge, nastri e bracci funzionano con l'Interface e l'Open Crate, perché sono barili veri.
- Protezione (Gouw): l'Interface è un barile, quindi i contenitori protetti restano protetti.

## 5. Quando qualcosa non va

| Sintomo | Cosa fa |
|---|---|
| Un baule non si unisce | Tocca davvero un connector/trim/cavo? È stato escluso con il Configurator? È una botte di Sakagura? |
| Il terminale non mostra nulla | Manca il connector? Rete troppo grande? Clic destro sul connector per illuminare la rete. |
| Il terminale wireless non funziona | È nel raggio? Avanzato: collegato con sneak + clic destro, beacon entro 8 blocchi dal terminale? |
| Manca un oggetto | Cerca con /zukan o nel terminale; controlla filtri e priorità del baule (Configurator). |
