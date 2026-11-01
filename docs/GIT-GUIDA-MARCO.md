# Guida Git per Marco - lavorare insieme sul repository iekcorbar

Questa guida spiega come lavoriamo in due sulla stessa repository (branch `main`) senza pestarci i piedi. Se segui sempre l'ordine **scarica, lavora, salva, invia** non perderai mai nulla.

## 1. Le parole da conoscere

| Parola | Significato |
|---|---|
| repository (repo) | La cartella del progetto con tutta la sua storia. |
| commit | Un "salvataggio" con un messaggio che dice cosa hai cambiato. |
| push | Invia i tuoi commit a GitHub. |
| fetch | Scarica da GitHub le novità, ma NON tocca i tuoi file. Sicuro, si può fare sempre. |
| pull | Fetch + unisce le novità ai tuoi file. |
| branch | Una linea di lavoro. Noi usiamo `main`. |
| origin | Il nome di GitHub come "telecomando" della repo. |
| working tree | I file come sono ora sul tuo computer. |

## 2. Prima volta (una sola volta)

1. Installa Git (git-scm.com) e accedi a GitHub (si apre il browser alla prima operazione).
2. Imposta il tuo nome, così i commit sono riconoscibili:

```
git config --global user.name "Marco Dibbo"
git config --global user.email "la-tua-email@esempio.it"
```

3. Scarica la repo (l'indirizzo lo trovi su GitHub con il pulsante verde "Code"):

```
git clone https://github.com/baaye16/iekcorbar-final.git
cd iekcorbar-final
```

Per i plugin Java servono anche JDK 25 e Maven; per il sito Node.js (vedi CLAUDE.md nella radice).

## 3. Il ciclo di tutti i giorni

### Passo 1 - Prima di iniziare: guarda se ci sono novità

```
git fetch
git status
```

Se `git status` dice "Your branch is behind 'origin/main' by N commits", ci sono commit nuovi. Per vedere chi ha fatto cosa:

```
git log HEAD..origin/main --format="%h %an %s"
```

### Passo 2 - Scarica le novità

```
git pull --rebase
```

`--rebase` mette i tuoi commit sopra quelli degli altri: la storia resta pulita, senza commit di "merge" inutili.

Se hai file modificati e non ancora salvati, prima fai un commit (passo 4) oppure usa `git stash` (sezione 6).

### Passo 3 - Lavora

Modifica i file come al solito. Controlla sempre cosa hai cambiato:

```
git status
git diff
```

### Passo 4 - Salva (commit)

Aggiungi solo i file che vuoi davvero (meglio di `git add .`):

```
git add percorso/del/file
git commit -m "Plugin X: cosa hai fatto, in una riga"
```

Un buon messaggio dice COSA e DOVE, per esempio: `IekQuests: corretto il blocco delle ricompense`. Un commit per lavoro finito, non uno enorme a fine settimana.

### Passo 5 - Invia (push)

Prima di inviare, controlla di nuovo le novità:

```
git fetch
git pull --rebase
git push
```

Se il push viene rifiutato ("rejected / non-fast-forward") significa che nel frattempo qualcuno ha inviato altro: fai `git pull --rebase` e poi `git push` di nuovo.

## 4. Cosa NON fare mai

- **Mai `git push --force`** (né `-f`): cancella il lavoro dell'altro. Se ti sembra necessario, fermati e chiedi.
- **Mai `git reset --hard`** o `git checkout .` senza aver capito cosa cancellano: buttano via le modifiche non salvate.
- **Mai committare segreti**: password, token, file `.env.local`, chiavi del database. Sono già esclusi da `.gitignore`; se ne compare uno in `git status`, NON aggiungerlo e avvisa subito.
- **Non committare file grandi o generati**: `*.jar`, `*.zip`, `node_modules`, `.next`, `target`, la cartella `deploy/` (sono nel `.gitignore`).
- Non cambiare lo stesso file nello stesso momento dell'altro senza dirselo (vedi sezione 5).

## 5. Conflitti: cosa sono e come si risolvono

Un conflitto succede quando voi due avete cambiato le stesse righe dello stesso file. Git si ferma durante `git pull --rebase` e te lo dice. Non è un disastro:

1. `git status` mostra i file "both modified".
2. Apri il file: troverai segni come questi:

```
<<<<<<< HEAD
la versione che c'era su GitHub
=======
la tua versione
>>>>>>> tuo commit
```

3. Decidi cosa tenere (spesso un po' di entrambe), cancella le righe con `<<<<<<<`, `=======`, `>>>>>>>`.
4. Poi:

```
git add percorso/del/file
git rebase --continue
```

5. Se ti perdi, annulla tutto e torna a prima: `git rebase --abort`. Poi chiedi aiuto.

Per evitare conflitti: scambiatevi due parole su chi tocca quale plugin o file, e fate pull spesso.

## 6. Comandi utili

| Cosa vuoi | Comando |
|---|---|
| Vedere gli ultimi commit | `git log --oneline -15` |
| Vedere cosa ha cambiato un commit | `git show ABC1234` |
| Vedere la storia di un file | `git log --oneline -- percorso/file` |
| Mettere da parte modifiche non salvate | `git stash` poi `git stash pop` per riprenderle |
| Annullare modifiche a UN file non salvato | `git restore percorso/file` (attenzione: perdi le modifiche) |
| Correggere l'ultimo messaggio di commit (non ancora inviato) | `git commit --amend -m "nuovo messaggio"` |
| Annullare un commit già inviato, in modo sicuro | `git revert ABC1234` (crea un nuovo commit che lo annulla) |
| Vedere la differenza con GitHub | `git diff origin/main` |

## 7. Regole del progetto (importanti)

- Si lavora su `main`; commit piccoli e chiari, push spesso.
- Prima di ogni push: `git fetch` e `git pull --rebase`.
- Le versioni dei plugin si alzano nel `pom.xml` e il jar finito va nella cartella `deploy/` (che non va su Git). Il caricamento sul server lo fa il proprietario o Claude, in un ordine fisso: prima carica i nuovi file, poi ferma il server, poi togli i vecchi, poi avvia.
- Non cambiare il database, Plesk o il server in produzione senza accordo con il proprietario.
- Il file `CLAUDE.md` nella radice descrive tutto il progetto: leggilo prima di toccare un plugin.
- Non scrivere mai nelle pagine pubbliche (sito, wiki, giochi) se qualcosa è "gratis" o "a pagamento".

## 8. Se qualcosa va storto

1. Non farti prendere dal panico e **non usare comandi con `--force` o `--hard`**.
2. Scrivi cosa hai fatto e copia il messaggio di errore.
3. Di solito `git status` dice già cosa fare (legge bene i suggerimenti tra parentesi).
4. Avvisa il proprietario o chiedi a Claude: un lavoro già inviato su GitHub si può quasi sempre recuperare.

### Riepilogo in cinque righe

```
git fetch                 # c'è qualcosa di nuovo?
git pull --rebase         # scaricalo
# ...lavori...
git add file ; git commit -m "messaggio chiaro"
git pull --rebase ; git push
```
