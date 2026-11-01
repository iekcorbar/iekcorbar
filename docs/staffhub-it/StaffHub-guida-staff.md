# StaffHub - la bacheca di conoscenza dello staff

Manuale completo: accesso, permessi, uso quotidiano, traduzione, drive e amministrazione.

## 1. Che cos'è StaffHub

StaffHub è la bacheca di conoscenza e il drive condiviso del team staff, raggiungibile su staff.iekcorbar.com. È separato dal sito e dal server Minecraft: una password StaffHub rubata non tocca nulla su iekcorbar.com. Qui trovi manuali, file, modelli e appunti; tutto è ricercabile, versionato e traducibile.

| Parte | Cos'è |
|---|---|
| Cartelle e file | Tessere per cartella con il numero di elementi, briciole di pane in alto, pulsante home. |
| Documenti (pagine) | Manuali in rich text con titoli, elenchi, codice, link e immagini; si aprono in un popup. |
| Ricerca | Su nomi, contenuto dei documenti e commenti. |
| Versioni | Caricare lo stesso nome file = nuova versione. Nulla viene sovrascritto; ripristinare è sempre possibile. |
| Cestino | Cancellare manda prima nel Cestino, con una barra di annullamento di 15 secondi. |
| Drive WebDAV | Lo stesso albero come unità di rete in Esplora risorse/Finder. |
| Assistente (Board AI) | Fai domande sui manuali; risposte nella tua lingua con le fonti. |
| Traduzione | Documenti, file di testo e PDF in NL, EN, IT o FI. |
| App desktop | Finestra Windows attorno a StaffHub con icona propria e aiuto WebDAV nel menu. |

## 2. Accesso e il tuo account

L'account lo ricevi dal proprietario. L'accesso è limitato: troppi tentativi sbagliati ti bloccano per un po', e il messaggio è volutamente generico. Nel tuo profilo (il tuo nome in alto a destra) imposti la lingua (NL/EN/IT/FI) e il testo leggibile (carattere extra chiaro); la preferenza ti segue su ogni dispositivo. Lì vedi anche i tuoi dispositivi e puoi revocarne uno se perdi un telefono o un PC.

### Permessi (cosa puoi fare)

| Permesso | Significato | Staff predefinito |
|---|---|---|
| upload | Caricare file, creare cartelle | sì |
| pages | Scrivere e modificare documenti | sì |
| trash | Spostare cose nel cestino | sì |
| move | Rinominare e spostare | sì |
| tokens | Creare token WebDAV | sì |
| ownervis | Aprire e marcare i luoghi riservati al proprietario | no |
| purge | Cancellare definitivamente, svuotare il cestino | no |
| users | Gestire gli account dello staff | no |
| admin | Panoramica admin: sessioni e registro di audit | no |

Solo il proprietario può assegnare i quattro permessi "pesanti" (ownervis, purge, users, admin). Se il proprietario non ha impostato nulla di specifico per te, hai i cinque normali. Se manca un pulsante, probabilmente ti manca quel permesso: chiedilo al proprietario.

## 3. Uso quotidiano

| Vuoi... | Fai così |
|---|---|
| Trovare qualcosa | Barra di ricerca in alto; oppure naviga tra le cartelle con le briciole di pane. |
| Leggere un documento | Cliccalo; si apre in un popup. Esc o un clic fuori lo chiude. |
| Scrivere un documento | Nuovo documento nella cartella giusta; si salva quando hai finito. Puoi trascinare o incollare immagini. |
| Caricare file | Trascina file o intere cartelle nella finestra. Stesso nome = nuova versione. |
| Decomprimere uno zip | Carica lo zip; appare prima un piano di estrazione. Controllalo e conferma. |
| Più cose insieme | Spunta le caselle (o quella in alto per l'intera cartella): sposta o cestina tutta la selezione. |
| Spostare | Trascina su una tessera di cartella, oppure usa "sposta" e naviga tra le cartelle. |
| Ripristinare qualcosa | Subito dopo la cancellazione: barra di annullamento. Più tardi: cartella Cestino, poi ripristina. Una versione più vecchia: cronologia versioni del file. |
| Condividere un link | Il pulsante link nell'editor copia un link diretto; chi è connesso e può vederlo apre subito il documento. |
| Commentare | Sotto file e documenti ci sono i commenti; usali per domande, non per decisioni. |
| Vedere media | Audio e video si riproducono nel popup con una playlist; .glb e .bbmodel si vedono in 3D (ruota, zoom; vista texture, argilla o fil di ferro). |

## 4. Tradurre (documenti, file di testo, PDF)

Sotto un documento o un file c'è la barra di traduzione con NL, EN, IT e FI. Clicca una lingua e la traduzione appare nella finestra; viene salvata, quindi la seconda volta è istantanea (il pulsante con la freccia traduce di nuovo). Funziona per: documenti, .md, .txt, .html, .log e PDF con livello di testo.

- PDF: il testo viene estratto dal PDF, tradotto e mostrato. Una scansione senza livello di testo non funziona; StaffHub lo dice. Immagini e impaginazione esatta del PDF non vengono portate con sé.
- Salvare o stampare come PDF: il pulsante "stampa / PDF" sotto la traduzione apre una finestra pulita; scegli Stampa o Salva come PDF.
- Comandi, nomi di file e nomi di plugin restano non tradotti. Poi StaffHub controlla se titoli ed elenchi corrispondono e avvisa in caso di dubbio.
- I documenti lunghi vanno a pezzi (massimo 12). Se un documento è troppo lungo, StaffHub lo dice; dividilo.

Nota: una traduzione è un aiuto, non la verità: controlla sempre comandi e numeri sull'originale.

### I manuali in StaffHub

Tutti i manuali staff (PDF) stanno nella cartella Manuals, con una sottocartella per lingua: NL, EN e IT. Nella cartella IT trovi questi manuali già scritti in italiano come documenti.

## 5. Assistente e drive

Assistente: il pulsante apre una colonna a destra. Fai domande in linguaggio semplice; la risposta arriva nella tua lingua con fonti cliccabili. Su telefono è un pulsante fluttuante. Legge solo ciò che c'è in StaffHub e non può cambiare nulla.

Drive WebDAV: crea un token nel tuo profilo. In Esplora risorse di Windows: Connetti unità di rete a https://staff.iekcorbar.com/dav, con il tuo nome utente e il token come password. Stessi permessi del sito; un token si revoca con un clic.

## 6. Per il proprietario

- Finestra Account: creare staff, reimpostare password, scegliere i team e spuntare i permessi per ogni membro; "torna ai permessi predefiniti" rimuove le scelte.
- Pannello admin (pulsante a scudo, solo proprietari): conteggi, tutte le sessioni revocabili per account, e il registro di chi ha fatto cosa.
- Luoghi privati: una cartella può essere "owner"; la vede solo chi ha il permesso ownervis.
- Backup: il database e i file stanno nella cartella dati sulla VPS; deploy/backup.sh conserva tar settimanali. Fai una copia del database prima di ogni aggiornamento.
- Aggiornamento: nuovo sorgente in /opt/staffhub, poi docker compose up -d --build. La memoria è limitata a 512 MB così il server Minecraft non ne risente mai.

## 7. Quando qualcosa non va

| Sintomo | Cosa fare |
|---|---|
| Non riesco ad accedere | Aspetta un momento (limite di tentativi) e riprova; se persiste: chiedi al proprietario di reimpostare la password. |
| Manca un pulsante | Ti manca quel permesso, vedi il capitolo 2. |
| La traduzione fallisce | PDF senza testo, oppure documento troppo lungo. Riprova o dividi il documento. |
| La traduzione si discosta dall'originale | Avviso: controlla titoli ed elenchi e traduci di nuovo. |
| Cancellato per errore | Barra di annullamento, oppure Cestino e ripristina. |
| WebDAV chiede continuamente la password | Usa il token come password, non la password dell'account; usa https. |
