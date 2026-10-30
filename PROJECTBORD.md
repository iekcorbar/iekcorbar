# PROJECTBORD — iekcorbar

Eén overzicht van wat er speelt. De agent werkt dit bij na elke afgeronde werkblok;
jij leest hier alleen wat er doet. (Detailstatus per serverpunt: `deploy/TE-DEPLOYEN.txt`.)

**Peildatum: 29-10-2026 avond**

## 🟢 Live op de server
| Blok | Sinds | Wat |
|---|---|---|
| AfkCinema 0.2.5 | 29-10 16:56 | AFK-bioscoop, waypoints, zwevende schermen |
| Easter-egg-golf | 29-10 16:56 | 16 eieren over 8 plugins + meta-ei SiteSync |
| StaffHub 0.4.9 | 29-10 19:2x | bulk-beheer, prullenbak-undo, .bbmodel-3D-weergave |
| Relics-config + ghosts-disc | 28-10 | discs staan weer goed |

## 🟡 Wacht op JOU
| Wat | Waar | Actie |
|---|---|---|
| RAM-plafond + nachtelijke herstart (2 paneel-klikken) | TE-DEPLOYEN punten 47-48 | BluMap is al door de agent afgezet (punt 46); Fenneswereld = van nichtje, blijft staan |
| StaffHub quest-gidsen importeren | deploy/staffhub-quests/ (8 ENGELSE md) | map slepen in de StaffHub-webapp (team-rechten); NL-versie is verouderd - niet meer gebruiken |
| StaffHub 0.4.11 🟢 LIVE (30-10 05:20, agent) | TE-DEPLOYEN punt 49 (af) | DELETION-BUG REPARERD: prullenbak-lazy-create had onjuiste SQL (5 waardes/6 kolommen) → 'verwijderen' deed niets; nu fix + rebuild + bewezen op DB-kopie. Tevens 0.4.10 (vertaalbalk mobiel) mee live |
| IekQuests 0.4.0 🟢 LIVE sinds 30-10 05:40 (18 quests: +Mad Doctor/Usop/Techcore/Cleric) | docs/quest-wizard.md | teleport=staff-only (spelers: kompas), money-condities, pay N, name-filter; 10 BQ-pakketten offline; BQ houdt: Rod, tutorial, jobs, Backpack, guards |
| Halloween-quest plaatsen (morgen is het zo ver!) | TE-DEPLOYEN punt 52 | /npc create Igor Nightgrave → mapje uploaden → /bq reload |
| Steamworks 0.3.26 🟢 LIVE sinds 30-10 05:05 (treinlijnen + windmolen-woosh + receptboek-fix) | TE-DEPLOYEN punt 42 (afgevinkt) | oude 0.3.25 van de server af |
| Tutorial: analyse + slotplan af | docs/tutorial-analyse.md | grijs-bericht VERSTUURD door eigenaar (30-10, DM) → wacht op antwoord; daarna ja/nee lockplan + klasse-keus |
| iekcorbar-pack-build19.zip 🟢 LIVE sinds 30-10 05:05 (woosh + blips + Milo/Igor-stem + questboek-model) | TE-DEPLOYEN punt 43 (afgevinkt) | build14 uit de mixer; clients: pack opnieuw halen + herstart |
| steamworks-seed (59 wiki-pagina's) | staff-site | opnieuw importeren |
| AfkCinema 0.3.0 (klikbare zwevende menu's) | TE-DEPLOYEN punt 44 | bij dezelfde herstart-batch; jar staat ook al in de testbank (E:) |
| NDA ondertekenen | docs/nda/signed/ (3 pdf's) | zelf tekenen → per DM naar kooyer, iekjebr36, greyarts91; getekend terug in dezelfde map. (afriendlystomach resigned - PDF ingetrokken) |
| Kerstdorp-delen klaar | deploy/kerst-town-schems/ (3 .schem) | in de server-WorldEdit-map droppen, dan net als de trailer: //schem load kerstdorp-01 + //paste -a (01=kerstboom, 02=rood huisje, 03=lantaarn/toren) |

## 🔵 In uitvoering (agent) → afgerond, wacht op deploy
| Blok | Status |
|---|---|
| Tank- + plateauwagen | 🟢 LIVE 30-10 05:05: jar + 2 sw-modellen + pack build19 allemaal op de server |

## ⚪ Wachtrij
- **Cinematic Interaction** (Fabric-mod `cinematic` v0.18.30, MC 26.3): REA-analyse
  eerste-passage KLAAR (docs/cinematic-onderzoek-rea.md: server-model, 17 payloads,
  actie-grammatica, feasibility = 100% server-side + server-getrouw paneel).
  Volgende stap: bouw-sessie werkbank-prototype (TABLE + FURNACE) op 26.2/26.3.
  Jar staat in archief/cinematic-interaction/.
- Wissels + rangeerkoppeling (treinen, als volgende batch)
- Sakagura: fruitbomen
- Website: profielscherm, AI-vertalingen
- MMO: feature-keuze uit Valhalla-onderzoek (gesprek met eigenaar)
- Paper 26.3: upgrade-beslisboom (testbank groen, blockers in docs/263-testrapport.md)

## ❓ Open vragen (kort antwoord genoeg)
1. `deploy/iekcorbar_iekje_barend.zip` (site-build van 28-10) — staat op geen enkele lijst: nog nodig of weg?
2. StaffHub-desktop 0.1.3 (TE-DEPLOYEN punt 25): al bij staff uitgedeeld? Zo ja, dan vliegt die er ook af.
3. De oude punten ≤32 op de deploy-lijst: zal ik ze straks één voor één met de server vergelijken en de lijst naar 0 terugbrengen?

## 📌 Hoe dit bord werkt
- Eén regel per werkblok, status: 🟢 live · 🟡 wachten-op-eigenaar · 🔵 agent-bezig · ⚪ wachtrij.
- Afgerond → meteen bijwerken + opruimen volgens skill `cleanup` (artefact uit deploy/, punt uit de lijst).
- Niets hier = geen persoonlijke namen/gegevens: die blijven in gitignore-mappen.
