# Steamworks — kinetisch systeem (ontwerp)

Eigen steampunk/Japans machinesysteem voor iekcorbar, gebaseerd op hoe Create
werkt (gedecompileerd uit `create-1.21.1-6.0.10.jar`, code MIT), maar met eigen
modellen, eigen namen, eigen getallen en een paar bewust andere regels.
**Niets uit Create's assets** (modellen, textures, geluiden, recepten-JSON,
taalbestanden) wordt gebruikt — die zijn All Rights Reserved.

---

## 1. Wat Create doet (bevindingen) → wat wij doen

| Onderdeel | Create (gemeten in de code) | Steamworks (ons ontwerp) |
|---|---|---|
| Eenheid kracht | Stress Units (SU) | **Torque (Tq)** |
| Max snelheid | 256 RPM, daarboven breekt het blok | **192 RPM**; daarboven springt een *breekpen* (blok valt als item, netwerk stopt) |
| Snelheidsniveaus | medium 30, fast 100 | **langzaam < 24, gemiddeld 24–80, snel > 80** |
| Belasting | impact × \|RPM\| | zelfde formule (natuurkundig logisch, geen creatief werk) |
| Capaciteit | capacity × \|RPM\| van de bron | zelfde formule |
| Overbelast | hele netwerk staat stil | **netwerk "loopt vast"**: draait niet, stoomwolkjes + kreun-geluid, meter in het rood |
| Tegengestelde richtingen | blok wordt vernietigd | **blok blokkeert** (stopt, geen item-verlies) — vriendelijker |
| Flikker-bescherming | score > 128 → kapot | update-limiet per netwerk per seconde, daarna 2 s pauze |

### Overbrengingen (snelheidsfactor tussen twee buren)

| Verbinding | Create | Steamworks |
|---|---|---|
| As → as (zelfde as) | 1 | 1 |
| Klein tandwiel ↔ klein (naast elkaar) | −1 | −1 |
| Groot ↔ klein (diagonaal) | −2 / −0.5 | **−2 / −0.5** (zelfde verhouding; afwijken voelt fout bij tandwielen) |
| Groot ↔ groot (haaks) | ±1 | ±1 |
| Kettingaandrijving | 1, of gearshift-factor | **kettingwiel klein/groot: 1 of 2** (zichtbaar aan het model) |
| Versnellingsbak (gearbox) | richting omkeren per zijde | zelfde |
| Koppeling (clutch) | redstone = ontkoppeld | zelfde |
| Omkeerbak (gearshift) | redstone = draairichting om | zelfde |
| Riem (belt) | 1 tussen twee assen | **touw/riem-katrol**, max 16 blokken, ook transport (fase 3) |

### Krachtbronnen

| Bron | Create | Steamworks |
|---|---|---|
| Handslinger | 8 SU-cap, 32 RPM, 10 ticks per klik, kost honger | **karakuri-slinger**: 16 RPM, 8 Tq, 20 ticks per klik, kost honger |
| Waterrad | flowScore (−1..1) × 8 / grootte RPM, cap 32 | **mizuguruma** (Japans waterrad): stroming langs de schoepen; klein 8 RPM / 32 Tq, groot 4 RPM / 128 Tq |
| Windmolen | zeilen/8 RPM (1–16), min 8 zeilen, cap 512 | **papieren zeilen (washi)**: min 8, RPM = zeilen/10 (1–12), hoogte-bonus boven Y 100 |
| Stoommachine | boiler + blaze burner, cap 1024 | **ketel + vuurhaard op kolen/houtskool**: druk bouwt op (ketel-meter), grotere ketel = meer Tq; ons pronkstuk |
| Creatieve motor | 16384 | **staff-only "Aether-motor"**, instelbaar |

### Machines (verbruikers)

| Machine | Create-gedrag | Steamworks |
|---|---|---|
| Molensteen | timer −clamp(\|RPM\|/16, 1, 512) per tick | **ishi-usu** (stenen handmolen-look), zelfde soort formule, eigen recepten |
| Pers | cyclus 240, sneller met RPM | **stoomhamer**: zelfde idee (cyclus, sneller bij hogere RPM), platen/geplette items |
| Breekwielen | snelheid × 4 / log2(stapel) | **steenbreker-walsen**, eigen opbrengsten |
| Ventilator | 20 blokken; lava=smelten, vuur=roken, zielvuur=haunting, water=wassen | **blaasbalg-fan**: 12 blokken, zelfde vier processen (vanilla-logica, generiek) |
| Boor / zaag | breeksnelheid = \|RPM\|/100 vs. hardheid | zelfde soort formule, beschermingscheck via synthetische BlockBreakEvent |
| Menger + bak | 40-ticks cyclus, recepttijd ~ log2(512/RPM) | **roerwerk + koperen ketel**, eigen recepten |
| Deployer | gebruikt item als speler | **karakuri-arm** (fase 3), fake-player-acties met protectiecheck |

---

## 2. Techniek op Paper

- **Draaiende modellen:** `ItemDisplay`-entiteiten met `item_model` naar onze
  eigen FMM-gegenereerde modellen. Rotatie via `setTransformation` +
  `setInterpolationDuration` → de client interpoleert vloeiend, de server stuurt
  maar ~1 update per seconde per onderdeel. Geen armor stands, geen animatie
  per tick.
- **Netwerk:** graaf van kinetische knopen per blokpositie. Bij plaatsen/breken:
  BFS vanaf dat blok, snelste bron wint (Create's regel), factoren zoals hierboven.
  Netwerken tikken alleen als hun chunks geladen zijn; max 512 knopen per netwerk.
- **Opslag:** chunk-PersistentDataContainer (geen database nodig).
- **Bescherming:** elke plaatsing/actie langs een synthetisch Bukkit-event, zodat
  Lands/claims/WorldGuard automatisch meedoen.
- **Modellen:** uit ons eigen Python-generator-patroon (`make_props.py`), thema's
  koper/messing/ijzer/verdigris + Japans hout/lak/papier.
- **Configureerbaar:** alle getallen in `kinetics.yml` (Tq, RPM, cyclustijden),
  recepten in `machines.yml`.

## 3. Fasering

1. **Fase 1 — fundament:** as, klein/groot tandwiel, versnellingsbak,
   handslinger, waterrad, snelheids-/belastingsmeter, molensteen.
2. **Fase 2 — verwerking:** stoomhamer, roerwerk + ketel, blaasbalg-fan,
   koppeling, omkeerbak. *(0.2.0: stoomhamer + ijzeren/koperen/gouden platen
   gebouwd; impact 8 = precies één handslinger per hamer.)*
3. **Fase 3 — automatisering:** boor, zaag, karakuri-arm, riem/katrol, ketting.
4. **Fase 4 — grote bronnen:** stoomketel, windmolen, breekwielen.

## 4. Beslissingen (2026-10-23)

- Aparte plugin: `Steamworks/` → `Steamworks-0.1.0.jar`.
- Overbelasting/tegenrichting/vastgelopen tandwielen: **vastlopen**.
- Fase 1 gebouwd (0.1.0), offline wiskunde-test geslaagd, nog niet live getest.
  Afwijkingen van dit document: waterrad 4 RPM per watervoerende zijde (max 8).
