# PROJECTBORD — iekcorbar

Eén overzicht van wat er speelt. De agent werkt dit bij na elke afgeronde werkblok;
jij leest hier alleen wat er doet. (Detailstatus per serverpunt: `deploy/TE-DEPLOYEN.txt`.)

**Peildatum: 30-10-2026 avond**

## 🟢 Live op de server
| Blok | Sinds | Wat |
|---|---|---|
| AfkCinema 0.2.5 | 29-10 16:56 | AFK-bioscoop, waypoints, zwevende schermen |
| Easter-egg-golf | 29-10 16:56 | 16 eieren over 8 plugins + meta-ei SiteSync |
| StaffHub 0.4.9 | 29-10 19:2x | bulk-beheer, prullenbak-undo, .bbmodel-3D-weergave |
| Relics-config + ghosts-disc | 28-10 | discs staan weer goed |
| Sakagura 0.5.0 | 30-10 11:23 | 12 nieuwe gewassen + orchard-trees (5 soorten, sapling→groei→plukken→vellen) |
| Relics 0.5.0 | 30-10 12:25 | shop-crates `/relics crate` (kits uit crates.yml) + shadow-set voortaan niet-craftbaar |
| **Wildlife 0.8.0 stoomdraken** | 30-10 18:51 | berijden/temmen/grotten + 149 modellen + pack build21; clients: pack herhalen + herstart |
| **SiteSync 1.16.3 + 4 Darkmatter86-boeken** | 30-10 18:51 | story_bundle-model + 11 books geladen; /ibook give <speler> the_well enz. |
| **Relics 0.6.0 (Blood Token + Water Bottle)** | 30-10 18:38 | token-relic + Blessed via blood-moon (crates/config herschreven, .bak-060), fles drinkbaar; pack build22 |
| **IekQuests 0.5.4 + Igor conversatie-quest** | 30-10 18:05 | stage-dialoog in chat + paneel sluit voor leespauze; Igor: 4 verduidelijkte stages + receptuur in de praat |
| Tank- + plateauwagen | 30-10 05:05 | jar + 2 sw-modellen + pack-build (build19; intussen opgevolgd door 20/21) |

## 🟡 Wacht op JOU
| Wat | Waar | Actie |
|---|---|---|
| site-zip + **Shop Settings-commando's** | TE-DEPLOYEN punt 63 (nieuw: sha1 ecf1d85c) | zip uitpakken + NPM + restart; dan Staff Panel → Shop Settings: money-take `cmi money take {player} {amount}` en coins-take `em money remove {player} {amount}` (de oude 'elitecoins take'/'em money take' bestaan niet op de server; balance-koop lukt alleen als koper ONLINE is - CMI pakt doelen uitsluitend online) |
| 2 site-SQL's importeren | deploy/site-30-10-kits.sql + site-30-10-nieuws.sql | phpMyAdmin → iekcorbar-DB; kits zijn nu veilig (Relics 0.5.0 draait), nieuws kan direct |
| 2 announcement-posts publiceren | Discord #announcements | per post nog op 'Publish' klikken - anders zien abonnees de Halloween- en Blood Moon-post niet |
| RAM-plafond + nachtelijke herstart (2 paneel-klikken) | TE-DEPLOYEN punten 47-48 | BluMap is al door de agent afgezet (punt 46); Fenneswereld = van nichtje, blijft staan |
| StaffHub quest-gidsen importeren | deploy/staffhub-quests/ (8 ENGELSE md) | map slepen in de StaffHub-webapp (team-rechten); NL-versie is verouderd - niet meer gebruiken |
| StaffHub 0.4.11 🟢 LIVE (30-10 05:20, agent) | TE-DEPLOYEN punt 49 (af) | DELETION-BUG REPARERD: prullenbak-lazy-create had onjuiste SQL (5 waardes/6 kolommen) → 'verwijderen' deed niets; nu fix + rebuild + bewezen op DB-kopie. Tevens 0.4.10 (vertaalbalk mobiel) mee live |
| StaffHub 0.5.0 🟢 LIVE (30-10 ~13:00, agent) | — | Grote UX-ronde op eigenaarswish: permissies per lid (caps-scherm in Accounts), zijbalk WEG → mappen-tegels + broodkruimel, documenten als popup, AI als volle rechterkolom, nieuwe bbmodel-kijker (textuur/klei/draad + textuurpaneel + fullscreen) |
| IekQuests 0.5.2 🟢 LIVE sinds 30-10 11:23 (51 quests: alles gemigreerd behalve tutorial) | TE-DEPLOYEN punten 55-58 | 0.5.0 = golf 3b+4; 0.5.1 = stemmen-fix (pack-events op naam afspelen); 0.5.2 = /iekrestart met titel + afteller. BQ jar van de server af |
| IekQuests 0.5.3 🟢 LIVE sinds 30-10 ~14:20 (56 quests) | TE-DEPLOYEN punt 61 | Steamworks-quests: Engineer-cursus II-V (cogwerk→boiler→olie→spoor) + hervatbare Engineer's Contracts; keuzes in lijstvorm werden door de engine genegeerd (o.a. Vokans stallknoppen) - parser hersteld, alles vertakt nu wél |
| IekQuests 0.5.3 + 12 cursuskets 🟢 LIVE (quests.yml geladen bij herstart 18:51, /questbook-reload damit overbodig) | TE-DEPLOYEN punt 62 | Cook's course (Daichi 48-51), Cellar course (Goro 52-55), Keeper's course (Vorkan 56-59) - 68 quests |
| Halloween-quest: Igor NPC 137 + quest live (met stem!) | TE-DEPLOYEN punten 52 + 56 | /questbook reload niet eens nodig - herstart 09:46 laadt alles. Klik Igor: "Heeeeey, little pumpkin..." hoorbaar; quest-complete klinkt nu echt |
| Steamworks 0.3.26 🟢 LIVE sinds 30-10 05:05 (treinlijnen + windmolen-woosh + receptboek-fix) | TE-DEPLOYEN punt 42 (afgevinkt) | oude 0.3.25 van de server af |
| Steamworks 0.3.27 (placement-rotatie: machines kijken kant op van speler + wrench draait voorkant) | TE-DEPLOYEN punt 54 | 🟢 LIVE 30-10 06:54 (sha1 6df44ece gemeten, log groen); KineticsTest 70/70; wiki-seed herbouwd |
| Tutorial: analyse + slotplan af | docs/tutorial-analyse.md | grijs-bericht VERSTUURD door eigenaar (30-10, DM) → wacht op antwoord; daarna ja/nee lockplan + klasse-keus |
| iekcorbar-pack-build20.zip 🟢 LIVE sinds 30-10 11:23 (nieuwe gewassen + bomen + alles wat build19 had) | TE-DEPLOYEN punt 58 | build19 uit de mixer; clients: pack opnieuw halen + herstart |
| steamworks-seed (59 wiki-pagina's) | staff-site | opnieuw importeren |
| AfkCinema 0.3.0 (klikbare zwevende menu's) | TE-DEPLOYEN punt 44 | bij dezelfde herstart-batch; jar staat ook al in de testbank (E:) |
| NDA ondertekenen | docs/nda/signed/ (3 pdf's) | zelf tekenen → per DM naar kooyer, iekjebr36, greyarts91; getekend terug in dezelfde map. (afriendlystomach resigned - PDF ingetrokken) |
| Kerstdorp-delen klaar | deploy/kerst-town-schems/ (3 .schem) | in de server-WorldEdit-map droppen, dan net als de trailer: //schem load kerstdorp-01 + //paste -a (01=kerstboom, 02=rood huisje, 03=lantaarn/toren) |

## 🔵 In uitvoering (agent) → afgerond, wacht op deploy
| Blok | Status |
|---|---|
| **IekQuests 0.5.4 + Igor conversatie-quest** | 30-10 18:05 | stage-dialoog in chat + paneel sluit voor leespauze; Igor: 4 verduidelijkte stages + receptuur in de praat |

## ⚪ Wachtrij
- **Pluginstrategie eigen beheer** → docs/plugin-strategie.md (30-10): routekaart
  BQ→IekQuests af, next: IekShops (vervangt MMOItems-stations), IekElites
  (vervangt EliteMobs+MMOBridge-koppeling, bevat custom elite-config), TechCore→
  Steamworks, CMI-inventory. MythicLib blijft tot die tijd (hard op MMOItems).
- **REA-wachtrij (eigenaar):** ItemJoin (jar trek in archief/rea/) → TAB goed
  instellen → DecentHolograms. MythicLib-check is GAAN (deps + configs
  uitgelezen; schade-koppeling zit in MMOBridge-integrations, niet MythicLib zelf).
- **Cinematic Interaction** (Fabric-mod `cinematic` v0.18.30, MC 26.3): REA-analyse
  eerste-passage KLAAR (docs/cinematic-onderzoek-rea.md: server-model, 17 payloads,
  actie-grammatica, feasibility = 100% server-side + server-getrouw paneel).
  Volgende stap: bouw-sessie werkbank-prototype (TABLE + FURNACE) op 26.2/26.3.
  Jar staat in archief/cinematic-interaction/.
- Wissels + rangeerkoppeling (treinen, als volgende batch)
- Sakagura: fruitbomen
- Website: profielscherm, AI-vertalingen
- Website: shop volledig verwijderd (page + API + nav + db-methods) - gaat naar apart subdomain (eigenaar bouwt dat zelf); DB-tabellen bewaard voor queued deliveries
- MMO: feature-keuze uit Valhalla-onderzoek (gesprek met eigenaar)
- Paper 26.3: upgrade-beslisboom (testbank groen, blockers in docs/263-testrapport.md)

## ❓ Open vragen (kort antwoord genoeg)
1. ~~site-zip zonder lijst~~ — staat nu als TE-DEPLOYEN punt 63 (herbouwd, sha1 ecf1d85c).
2. StaffHub-desktop 0.1.3 (TE-DEPLOYEN punt 25): al bij staff uitgedeeld? Zo ja, dan vliegt die er ook af.
3. De oude punten ≤32 op de deploy-lijst: zal ik ze straks één voor één met de server vergelijken en de lijst naar 0 terugbrengen?

## 📌 Hoe dit bord werkt
- Eén regel per werkblok, status: 🟢 live · 🟡 wachten-op-eigenaar · 🔵 agent-bezig · ⚪ wachtrij.
- Afgerond → meteen bijwerken + opruimen volgens skill `cleanup` (artefact uit deploy/, punt uit de lijst).
- Niets hier = geen persoonlijke namen/gegevens: die blijven in gitignore-mappen.
| IekQuests 0.5.0 - golf 3b+4: Rod, 24 guards, Backpack x3, JobsRank1-5 gemigreerd | ✅ LIVE | 🟢 30-10 uitgerold door agent; 51 quests, log groen; alleen tutorial (golf 5) nog niet - wacht op greyarts |
