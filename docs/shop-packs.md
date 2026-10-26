# Shop-packs — recepten (2026-10-26)

Layout van de geluk-spelen (lucky-dip) packs. Alles hier is **in-game valuta**;
echte geld is er nergens (zie `/donate`: vrijwillige giften, geen tegenprestatie).

Prijzen zijn suggesties — pas ze aan waar je wilt, de site leest ze live uit de
shop-items-tabel (geen deploy nodig).

## Voorbereiding 1× per pack (in-game, als admin)

1. Sta met het gewenste item in je hand en maak de kit:
   - `/kit create iek_copper` (nieuw; daarna `add` voor elk volgend item,
     `add` met /kit add iek_copper in de hand)
   - CMI-opdrachten heten soms `kitcreate`/`kitadd` — gebruik wat je tab-completion laat zien.
2. Test de bezorging vanaf de console, want de argumentvolgorde is CMI-versie-afhankelijk:
   - `kit iek_copper <jouwnaam>` **en** omgekeerd, tot één van beide werkt.
   - Noteer de werkende volgorde; die gebruik je in de giveCommand hieronder.
3. Zet de Relics willekeur-pools in de config (Relics 0.3.7): `random.common`,
   `random.rare`, `random.epic` — al gevuld met voorbeelden in de config.yml, uit te breiden naar smaak.

## De vier packs (aan te maken via Shop-pagina > staff panel > Add Item)

| # | Naam (slug) | Valuta | Prijs | giveCommand | Inhoud kit |
|---|---|---|---|---|---|
| 1 | Copper Pouch (`copper-pouch`) | balance | 250 | `kit iek_copper {player};;relics random {player} common` | brood, 8× eikenplanken, 4× kool, wat experience bottles, 1× willekeurige common-relic uit de pool |
| 2 | Bronze Crate (`bronze-crate`) | balance | 750 | `kit iek_bronze {player};;relics random {player} common` | 16× gebeiteld eiken, 4× koper- equivalents (raw copper), 2× fireworks, 3× golden carrot, 1× common-relic |
| 3 | Steel Case (`steel-case`) | balance | 2000 | `kit iek_steel {player};;relics random {player} rare` | 8× iron blocks, ender pearl ×2, saddle ×1, 6× glow berries, 1× rare-relic uit de pool |
| 4 | Gold Vault (`gold-vault`) | coins | 15 | `kit iek_gold {player};;relics random {player} epic` | 4× gold blocks, netherite scrap ×1, elytra piece (of: 1× shulker shell), 2× bundle, 1× epic-relic uit de pool |

Optioneel 5de item, puur geluk-spel zonder kit:

| 5 | Lucky Dip (`lucky-dip`) | coins | 3 | `relics random {player} rare` | — |

## Spelregels (ook in je eigen shop-teksten)

- Coins-items vragen de speler **online** te zijn (Elite coins leven in EliteMobs
  z'n metadata — alleen online uitleesbaar).
- Offline balance-purchases worden automatisch bezorgd bij de volgende login
  (SiteSync ≥ 1.15.1: ook de hele `;;`-keten, kit én relic).
- Kits onbeperkt haalbaar? CMI heeft een kit-cooldown: zet per kit bijv.
  `cooldown: 60` seconden, of houd herhalingen in de gaten via de bestaande
  `purchased`-records (staff-inzicht: Moderation > Shop purchases).

## Wat er nog NIET in zit (bewust)

- Echte-geld-relics (cosmetics) — apart blok, samen uit te werken, EULA-proof
  opzet: puur visueel, geen playervoordeel.
- Petities van pack-inhoud — kit-inhoud wijzigen = de kit opnieuw samenstellen
  met `/kit add`/`/kit remove`, de site raakt er niet door.
