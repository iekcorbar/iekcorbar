# Greyarts-bericht (30-10) — Italiaams, in Delen (Geen Nitro: max 2000 Teken per DM-bericht)

Deel 1: inleiding + tutorial inkorten/bugs
Deel 2: Tutorial-Blockade
Deel 3: neue Quests + Abschluss

---

## DEEL 1/3

Ciao greyarts! Abbiamo analizzato la tutorial a fondo — prima cosa: è costruita davvero bene (flusso NPC, hook ItemJoin, router di login, pulizia dei tag… lavoro di alto livello). Abbiamo letto il package.yml riga per riga e abbiamo tre punti per te.

**1) Accorciare + un bug nascosto**
- I giocatori vogliono giocare, non leggere. I capitoli IV–VII sono un muro di testo in chat. Idea: tenere i capitoli pratici (tour menu E, crafting, razza/classe) e spostare tutta la spiegazione dei "sistemi" (rarità pesca, colture, Zeta, Oracoli…) in un libro **Adventurer's Guide** dato alla fine (+ link alla wiki). Una riga in chat per sistema: "leggi dopo nel tuo libro."
- Bug delle classi: ValhallaRaces tiene tutte le 9 classi in UN solo slot, e la tutorial esegue `races set class` tre volte — quindi resta solo l'ultimo giro; la classe da combattimento e quella di produzione scelte vengono sovrascritte in silenzio e i loro bonus non si applicano mai. Due uscite: (a) una scelta onesta tra 9 classi, (b) vero multi-classe se VR lo permette. Decidi tu — prepariamo noi qualunque cosa.
- Doppia domanda: `pick_race`/`pick_class`/`on_join` di VR sono tutti veri, quindi i nuovi giocatori vedono la GUI della razza del plugin al login E gli Assignor nella tutorial. Meglio forse spegnere la GUI on_join e lasciare che la tutorial gestisca quel momento?
- Cose piccole: "Iekobar" → "Iek Corbar" nell'introduzione del dialogo, un `<space>` letterale, le parentesi 「⚔」 (rischio font), e i nomi dei tag `Choosed_*`.

---

## DEEL 2/3

**2) Blocco della tutorial (il proprietario lo vuole sistemato per primo)**
Adesso i nuovi giocatori possono uscire dalla tutorial con i comandi. Il server non ha Essentials — /home, /sethome, /back, /warp sono tutti **CMI**, e `cmi.command.*` è aperto a tutti di default. Peggio: la scala ranghi CMI (Adventure → Kōsei a 1 h di gioco → …) esegue `lp user X parent set rank_1`, che sostituisce qualunque parent un giocatore abbia — quindi qualsiasi gruppo "tutorial" aggiunto verrebbe rimosso in silenzio dopo un'ora passata semplicemente a non fare nulla.

Correzione proposta, tutta config, niente codice:
- Gruppo LuckPerms `tutorial` = default meno `cmi.command.home/sethome/delhome/back/warp/tpa/tpahere` (+ i nodi `essentials.*` morti, come rete di sicurezza).
- Azione BQ `tutorial_start` → `lp user %player% parent set tutorial`; `tutorial_complete` → `lp user %player% parent set default` + concedi il nodo `iekcorbar.tutorial_done`.
- Il rango Kōsei riceve `PermissionRequirement: iekcorbar.tutorial_done`, così la scala aspetta la tutorial e poi funziona esattamente come adesso.

Sembra sensato? C'è qualcos'altro con cui i giocatori possono scappare che dovremmo bloccare? E possiamo tenere una copia nel repo del package.yml della tutorial + CMI Ranks.yml (in `pluginset-aangepast/`) così niente si perde o si modifica al buio?

---

## DEEL 3/3

**3) Nuove quest**
Dopo, il proprietario vuole creare nuove quest. BetonQuest è puro YAML, quindi possiamo scrivere pacchetti quest completi (conversazioni, obiettivi, journal, azioni) + una lista pronta di comandi `/npc create` per gli NPC che voi o il proprietario piazzate una volta. Hai idee per le quest? Abbozzane una (per chi è, come funziona la ricompensa) e noi prepariamo il pacchetto.

Grazie amico — la tutorial è già la parte meglio costruita del server; vogliamo solo che i giocatori arrivino prima al divertimento. 🙏

---

(De Nederlandse spiegeltekst staat in de chat van 29/30-10; commando- en configwaarden zijn bewust Engels gelaten.)
