"""Builds Instructies-EliteMobs-NPC-update.pdf (Dutch helper document for the user, not site content).
Usage: python -I tools/make_instructions_pdf.py <out.pdf>"""
import sys

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import KeepTogether, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

out = sys.argv[1] if len(sys.argv) > 1 else "Instructies-EliteMobs-NPC-update.pdf"
ss = getSampleStyleSheet()
BR, DK, GR = colors.HexColor("#8a5a1c"), colors.HexColor("#2a2a30"), colors.HexColor("#f3ead8")
H1 = ParagraphStyle("H1", parent=ss["Title"], fontName="Helvetica-Bold", fontSize=22, textColor=BR, spaceAfter=6, alignment=0)
H2 = ParagraphStyle("H2", parent=ss["Heading2"], fontName="Helvetica-Bold", fontSize=14, textColor=BR, spaceBefore=12, spaceAfter=4)
P = ParagraphStyle("P", parent=ss["BodyText"], fontName="Helvetica", fontSize=10.5, leading=14.5, textColor=DK)
SM = ParagraphStyle("SM", parent=P, fontSize=9, leading=12, textColor=colors.HexColor("#555555"))
CODE = ParagraphStyle("CODE", parent=P, fontName="Courier", fontSize=9, leading=12, backColor=GR, borderPadding=4, spaceBefore=3, spaceAfter=5)
TIP = ParagraphStyle("TIP", parent=P, backColor=colors.HexColor("#fff3cd"), borderPadding=6, spaceBefore=4, spaceAfter=6)


def p(t, st=P):
    return Paragraph(t, st)


def steps(items):
    rows = [[p("[&nbsp;&nbsp;]"), p(t)] for t in items]
    t = Table(rows, colWidths=[12 * mm, 158 * mm])
    t.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("BOTTOMPADDING", (0, 0), (-1, -1), 3), ("TOPPADDING", (0, 0), (-1, -1), 2)]))
    return t


def table(data, widths, head=True):
    rows = [[p(c, SM if i or not head else ParagraphStyle("th", parent=SM, fontName="Helvetica-Bold", textColor=colors.white)) for c in r] for i, r in enumerate(data)]
    t = Table(rows, colWidths=widths, repeatRows=1 if head else 0)
    st = [("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#c9b98f")), ("VALIGN", (0, 0), (-1, -1), "TOP"),
          ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, GR])]
    if head:
        st.append(("BACKGROUND", (0, 0), (-1, 0), BR))
    t.setStyle(TableStyle(st))
    return t


def footer(c, d):
    c.saveState()
    c.setFont("Helvetica", 8)
    c.setFillColor(colors.HexColor("#777777"))
    c.drawString(20 * mm, 10 * mm, "iekcorbar - EliteMobs & NPC update - 25 oktober 2026")
    c.drawRightString(190 * mm, 10 * mm, "Pagina %d" % d.page)
    c.restoreState()


s = []
s += [p("EliteMobs- en NPC-update", H1), p("Stap voor stap: modellen, boss-bestanden, NPC-bestanden en NPC-skins", SM), Spacer(1, 6)]
s += [p("Dit is alles wat er nieuw is op het gebied van EliteMobs-modellen en NPC's. Je doet het in deze volgorde: "
        "<b>back-up, modellen, boss-bestanden, NPC-bestanden, herladen, skins, testen</b>. Niets hiervan is al op de server getest, "
        "dus de test-lijst achteraan is belangrijk.")]
s += [p("Wat krijg je?", H2), table([
    ["Onderdeel", "Aantal", "Waar staat het op je pc (map <font face='Courier'>iekcorbar-final</font>)"],
    ["Modellen (.bbmodel) voor FreeMinecraftModels", "71 (39 boss-soorten + 7 extra + 25 NPC)", "<font face='Courier'>upload-2026-10-24\\plugins\\FreeMinecraftModels\\models\\elitemobs\\</font>"],
    ["Boss-bestanden met <font face='Courier'>customModel</font> erin (kopieen)", "369", "<font face='Courier'>EliteModels\\server\\custombosses\\</font>"],
    ["NPC-bestanden van EliteMobs met <font face='Courier'>customModel</font> (kopieen)", "25", "<font face='Courier'>EliteModels\\server\\npcs\\</font>"],
    ["Skins voor je Citizens-NPC's (BetonQuest-set)", "49", "<font face='Courier'>NpcSkins\\skins\\</font> (overzicht: <font face='Courier'>NpcSkins\\preview_sheet.png</font>)"],
], [60 * mm, 40 * mm, 70 * mm])]
s += [Spacer(1, 4), p("<b>Wat is er niet gedaan?</b> 28 bosses hadden al een eigen model en 226 gebruiken een LibsDisguises-<font face='Courier'>disguise</font> "
                      "(klasse-proeven e.d.): die zijn bewust overgeslagen. Gewone elites (de <font face='Courier'>elite_*.yml</font> in <font face='Courier'>mobproperties</font>) "
                      "kunnen in jouw EliteMobs-versie (10.9.10) geen model krijgen, dat ondersteunt de config niet.", TIP)]

s += [p("Stap 0 - Back-up (niet overslaan)", H2),
      p("De nieuwe bestanden <b>overschrijven</b> bestaande bestanden met dezelfde naam. Maak daarom eerst een kopie van deze mappen op de server:"),
      p("plugins/EliteMobs/custombosses<br/>plugins/EliteMobs/npcs<br/>plugins/FreeMinecraftModels/models", CODE),
      p("Let op: mijn kopieen zijn gemaakt van het archief dat je op 24 oktober hebt gestuurd. Heb je sindsdien een boss of NPC op de server aangepast, "
        "dan raak je die wijziging kwijt als je het bestand overschrijft. Kijk dan eerst naar de lijst in <font face='Courier'>EliteModels\\server\\assigned.csv</font> "
        "en kopieer alleen de bestanden die je wilt.", TIP),
      steps(["Back-up van de drie mappen gemaakt."])]

s += [p("Stap 1 - Modellen uploaden", H2),
      p("Kopieer alle 71 <font face='Courier'>.bbmodel</font>-bestanden naar de modellenmap van FreeMinecraftModels. Maak de map <font face='Courier'>elitemobs</font> aan als die er nog niet is "
        "(de vier oude proefmodellen <font face='Courier'>em_steam_zombie</font> e.d. staan er mogelijk al; die worden vervangen door de nieuwe versie)."),
      p("van:  upload-2026-10-24\\plugins\\FreeMinecraftModels\\models\\elitemobs\\*.bbmodel<br/>naar: plugins/FreeMinecraftModels/models/elitemobs/", CODE),
      steps(["71 bestanden in <font face='Courier'>plugins/FreeMinecraftModels/models/elitemobs/</font>."])]

s += [p("Stap 2 - Boss-bestanden", H2),
      p("Kopieer de <b>inhoud</b> van <font face='Courier'>EliteModels\\server\\custombosses\\</font> (de 30 submappen plus losse bestanden) naar "
        "<font face='Courier'>plugins/EliteMobs/custombosses/</font>. Kies in je FTP-programma of Plesk-bestandsbeheer voor <b>samenvoegen en overschrijven</b>. "
        "De mapnamen zijn gelijk aan die van EliteMobs, dus de bestanden komen op de goede plek."),
      p("In elk bestand staat nu onderaan een regel zoals:", P), p("customModel: em_steam_zombie", CODE),
      p("De vier bestanden <font face='Courier'>em_*_test.yml</font> in die map zijn de oude proefbosses van de eerste testronde; je mag ze negeren of weglaten.", SM),
      steps(["Boss-bestanden gekopieerd naar <font face='Courier'>plugins/EliteMobs/custombosses/</font>."])]

s += [p("Stap 3 - NPC-bestanden", H2),
      p("Kopieer de 25 bestanden uit <font face='Courier'>EliteModels\\server\\npcs\\</font> naar <font face='Courier'>plugins/EliteMobs/npcs/</font>. "
        "In deze kopieen is de regel <font face='Courier'>disguise: custom:ag_...</font> vervangen door <font face='Courier'>customModel: em_npc_...</font>."),
      table([["NPC-bestand", "Model", "NPC-bestand", "Model"],
             ["back_teleporter", "Hermes (bode met vleugels)", "gambling_den_owner", "De Baron (hoge hoed, stok)"],
             ["barkeep", "Bartley (bierpul)", "guide_0 / guide_1", "Dux / Casus (kompas, rol)"],
             ["blackjack_dealer", "Vincent (kaarten, oogschild)", "guild_attendant", "Gillian (grootboek)"],
             ["blacksmith", "Greg (hamer, schort)", "quest_giver", "Qel'Thuzad (kap, gloeiende ogen)"],
             ["card_shark", "Ace Morgan (kaartwaaier)", "repairman_config", "Reggie (sleutel, bril)"],
             ["class_trainer_* (6x)", "avonturier, berserker, cleric, paladin, ranger, spellcaster", "santa", "Saint Nick (uitgeschakeld in config)"],
             ["coin_flipper", "Lucky Pete (grote munt)", "scrapper_config", "Kelly (lasmasker, zak)"],
             ["combat_instructor", "Charles (fluitje)", "scroll_applier_config", "Scotty (rollen, bril)"],
             ["enchanted", "Eden (spreukenboek)", "slot_machine_operator", "Spinner Sam (gokkast op rug)"],
             ["fletcher", "Robin (pijlenkoker)", "", ""]],
            [38 * mm, 47 * mm, 38 * mm, 47 * mm]),
      steps(["NPC-bestanden gekopieerd naar <font face='Courier'>plugins/EliteMobs/npcs/</font>."])]

s += [p("Stap 4 - Herladen", H2),
      p("Herstart de server (aanbevolen: FreeMinecraftModels laadt zijn modellen pas na het opstarten, en dat is de zekerste manier). "
        "Kijk in <font face='Courier'>logs/latest.log</font> of er regels over <i>FreeMinecraftModels</i> of <i>EliteMobs</i> met 'model' of 'error' staan. "
        "Sla die log op, want die heb ik straks nodig."),
      steps(["Server herstart.", "<font face='Courier'>logs/latest.log</font> bewaard."])]

s += [PageBreak(), p("Stap 5 - Skins voor je Citizens-NPC's", H1),
      p("Dit gaat over de 49 skins in <font face='Courier'>NpcSkins\\skins\\</font>. Je Citizens-NPC's zijn spelers met een skin, en Citizens heeft een "
        "ondertekende textuur nodig. Een PNG-bestand op de schijf is dus niet genoeg: het bestand moet eerst op een webadres staan."),
      p("A. Bestand op een webadres zetten", H2),
      p("Kies een van deze twee manieren:"),
      table([["Manier", "Wat je doet", "Let op"],
             ["Eigen website", "Zet de PNG's in een map op je eigen webserver, bijvoorbeeld <font face='Courier'>https://iekcorbar.com/npc-skins/greg.png</font>.", "Alleen jij beheert het. Dit vraagt een bestandsupload naar de site; ik doe dat niet zonder dat jij erom vraagt."],
             ["MineSkin", "Upload het PNG op mineskin.org en gebruik de link die je terugkrijgt.", "Je stuurt het bestand naar een externe dienst. Dat is jouw keuze."]],
            [32 * mm, 80 * mm, 58 * mm]),
      p("B. Skin op de NPC zetten", H2),
      p("Doe dit in het spel als OP, per NPC. Het id staat in <font face='Courier'>NpcSkins\\npc_skins.csv</font>:"),
      p("/npc select 62<br/>/npc skin --url https://jouw-adres/master_brewer.png", CODE),
      p("Alle commando's staan klaar in <font face='Courier'>NpcSkins\\apply_commands.txt</font>; vervang daarin <font face='Courier'>&lt;URL&gt;</font> door jouw adres. "
        "De skins zijn voor de <b>standaard (brede) armen</b>, kies dus geen 'slim'. De 24 NPC's die 'Guard' heten (ids 93 en 99 t/m 121) "
        "mogen guard_a t/m guard_d door elkaar gebruiken.", P),
      table([["Voorbeeld-id", "NPC", "Bestand"], ["50", "Master Farmer", "master_farmer.png"], ["79", "Vorkan Kross", "vorkan_kross.png"],
             ["82", "Aiko (theehuis)", "aiko.png"], ["126", "Capitan Usop", "capitan_usop.png"], ["93", "Guard", "guard_a.png"]],
            [30 * mm, 70 * mm, 70 * mm]),
      steps(["PNG's op een webadres gezet.", "Skin gezet op minstens 3 NPC's om te proberen.", "De rest gedaan (of mij laten weten wat ik moet aanpassen)."])]

s += [p("Stap 6 - Testen", H2),
      p("Loop de lijst door en schrijf op wat niet klopt. Het gaat om de dingen die ik niet vanaf mijn pc kon controleren."),
      steps(["Een boss met een nieuw model verschijnt (bijvoorbeeld een zombie-boss in de mijnen): zie je de steampunk-zombie, en blijft de oude vanilla-zombie weg?",
             "Lopen en slaan: draaien de armen en benen de goede kant op? (Ik kon de draairichting niet controleren.)",
             "De creeper-boss: zwelt hij op vlak voor de explosie? (Dat is een <i>scale</i>-animatie; FMM speelt die misschien niet af.)",
             "Een NPC met nieuw model, bijvoorbeeld Greg of Bartley: staat het model er, en is de oude LibsDisguises-skin weg?",
             "Klik op die NPC: werkt het gesprek of de winkel nog gewoon?",
             "Een Citizens-NPC met een nieuwe skin: zie je de hoed, jas of rugzak uitsteken (de 3D-laag)?",
             "Geen foutmeldingen in <font face='Courier'>logs/latest.log</font> over ontbrekende modellen."])]

s += [p("Terugdraaien als het misgaat", H2),
      p("1. Zet de back-up van <font face='Courier'>custombosses</font> en <font face='Courier'>npcs</font> terug.<br/>"
        "2. Of haal in één bestand de regel <font face='Courier'>customModel: ...</font> weg (en zet bij NPC's de oude <font face='Courier'>disguise:</font>-regel terug).<br/>"
        "3. Herstart de server. De modellen zelf in de map kunnen blijven staan; zonder verwijzing worden ze niet gebruikt."),
      p("Wat ik daarna van je wil weten", H2),
      p("Vertel me hoe het ging en stuur <font face='Courier'>logs/latest.log</font>. Dan kijk ik of FMM de animaties goed afspeelt, of de scale- en talk-animatie werken, "
        "en pas ik de modellen aan waar nodig. Als je skins wilt aanpassen, noem dan de NPC-naam en wat er anders moet.")]
s += [p("Andere nog niet geüploade onderdelen", H2),
      p("Deze instructies gaan alleen over EliteMobs-modellen, NPC's en skins. De rest van de update (plugins, resourcepack, website) staat in de oudere PDF's, "
        "<font face='Courier'>Upload-checklist-2026-10-24.pdf</font> en <font face='Courier'>Upload-simpel.pdf</font>. Die noemen nog oudere versienummers; "
        "zeg het als ik ze moet bijwerken.", SM)]

SimpleDocTemplate(out, pagesize=A4, leftMargin=20 * mm, rightMargin=20 * mm, topMargin=18 * mm, bottomMargin=18 * mm,
                  title="EliteMobs- en NPC-update", author="iekcorbar").build(s, onFirstPage=footer, onLaterPages=footer)
print("wrote", out)
