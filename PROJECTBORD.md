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
| Steamworks 0.3.26 (treinlijnen + windmolen-woosh) | TE-DEPLOYEN punt 42 | bij volgende herstart-batch |
| iekcorbar-pack-build15.zip | TE-DEPLOYEN punt 43 | samen met punt 42; build14 uit mixer |
| steamworks-seed (59 wiki-pagina's) | staff-site | opnieuw importeren |
| NDA ondertekenen | docs/nda/signed/ (3 pdf's) | zelf tekenen → per DM naar kooyer, iekjebr36, greyarts91; getekend terug in dezelfde map. (afriendlystomach resigned - PDF ingetrokken) |

## 🔵 In uitvoering (agent) → afgerond, wacht op deploy
| Blok | Status |
|---|---|
| Tankwagon (stoomgoederen per spoor) | af: code + model + audit + site + wiki; jar (punt 42) + sw_tank_wagon.json + pack (punt 43) wachten op jouw batch |

## ⚪ Wachtrij
- **Cinematic Interaction** (Fabric-mod `cinematic` v0.18.30, MC 26.3): REA-analyse
  eerste-passage KLAAR (docs/cinematic-onderzoek-rea.md: server-model, 17 payloads,
  actie-grammatica, feasibility = 100% server-side + server-getrouw paneel).
  Volgende stap: bouw-sessie werkbank-prototype (TABLE + FURNACE) op 26.2/26.3.
  Jar staat in archief/cinematic-interaction/.
- Plateau-wagon, wissels + rangeerkoppeling (treinen, daarna)
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
