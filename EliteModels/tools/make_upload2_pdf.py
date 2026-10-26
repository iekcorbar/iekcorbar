"""Builds Upload-deel-2-2026-10-25.pdf: only what changed after the first upload checklist (Dutch helper document).
Run WITHOUT -I (reportlab lives in the user site):  python tools/make_upload2_pdf.py <out.pdf>"""
import sys

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

out = sys.argv[1] if len(sys.argv) > 1 else "Upload-deel-2-2026-10-25.pdf"
ss = getSampleStyleSheet()
BR, DK, GR = colors.HexColor("#8a5a1c"), colors.HexColor("#2a2a30"), colors.HexColor("#f3ead8")
H1 = ParagraphStyle("H1", parent=ss["Title"], fontName="Helvetica-Bold", fontSize=22, textColor=BR, spaceAfter=6, alignment=0)
H2 = ParagraphStyle("H2", parent=ss["Heading2"], fontName="Helvetica-Bold", fontSize=14, textColor=BR, spaceBefore=12, spaceAfter=4)
P = ParagraphStyle("P", parent=ss["BodyText"], fontName="Helvetica", fontSize=10.5, leading=14.5, textColor=DK)
SM = ParagraphStyle("SM", parent=P, fontSize=9, leading=12, textColor=colors.HexColor("#555555"))
TH = ParagraphStyle("TH", parent=SM, fontName="Helvetica-Bold", textColor=colors.white)
CODE = ParagraphStyle("CODE", parent=P, fontName="Courier", fontSize=9, leading=12, backColor=GR, borderPadding=4, spaceBefore=3, spaceAfter=5)
TIP = ParagraphStyle("TIP", parent=P, backColor=colors.HexColor("#fff3cd"), borderPadding=6, spaceBefore=4, spaceAfter=6)


def p(t, st=P):
    return Paragraph(t, st)


def c(t):
    return "<font face='Courier'>%s</font>" % t


def steps(items):
    t = Table([[p("[&nbsp;&nbsp;]"), p(x)] for x in items], colWidths=[12 * mm, 158 * mm])
    t.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("BOTTOMPADDING", (0, 0), (-1, -1), 3), ("TOPPADDING", (0, 0), (-1, -1), 2)]))
    return t


def table(data, widths):
    rows = [[p(x, TH if i == 0 else SM) for x in r] for i, r in enumerate(data)]
    t = Table(rows, colWidths=widths, repeatRows=1)
    t.setStyle(TableStyle([("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#c9b98f")), ("VALIGN", (0, 0), (-1, -1), "TOP"),
                           ("BACKGROUND", (0, 0), (-1, 0), BR), ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, GR])]))
    return t


def footer(cv, d):
    cv.saveState()
    cv.setFont("Helvetica", 8)
    cv.setFillColor(colors.HexColor("#777777"))
    cv.drawString(20 * mm, 10 * mm, "iekcorbar - upload deel 2 - 25 oktober 2026")
    cv.drawRightString(190 * mm, 10 * mm, "Pagina %d" % d.page)
    cv.restoreState()


s = [p("Upload, deel 2", H1), p("Alleen wat er is bijgekomen na de eerste checklist (24 oktober)", SM), Spacer(1, 6)]
s += [p("Je hebt de stappen uit de eerste PDF gedaan. Dit is alles wat daarna nog is veranderd. Alles staat in de map " + c("upload-2026-10-24") +
        " op je pc. Er is niets bij dat een database-wijziging (SQL) of een website-upload nodig heeft."),
      p("De simpele versie", H2),
      steps(["<b>1.</b> Zet de 9 nieuwe jars in " + c("plugins/") + " (vervang de oude).",
             "<b>2.</b> Vervang " + c("Iekcorbar-pack.zip") + " in " + c("plugins/ResourcePackManager/mixer/") + ".",
             "<b>3.</b> Zet " + c("satchel.yml") + " en " + c("satchel_rare.yml") + " in " + c("plugins/WanderingTrades/trades/") + ".",
             "<b>4.</b> Herstart de server en bewaar " + c("logs/latest.log") + ".",
             "<b>5.</b> Daarna de EliteMobs/NPC-update: zie " + c("Instructies-EliteMobs-NPC-update.pdf") + "."]),
      p("Heb je een jar al op de nieuwste versie staan, dan kun je hem gerust opnieuw plaatsen: een gelijke versie vervangen kost niets.", TIP)]

s += [p("1. De 9 jars", H2),
      p("Zet ze in de map " + c("plugins/") + " op de server en haal de oude versie weg (zet die ergens anders neer, niet in " + c("plugins/") + ", anders laadt de server er twee). "
        "De kolom 'was' is mijn schatting van wat je na de eerste PDF had, afgeleid uit mijn archiefmap; klopt het niet, dan maakt dat niet uit: vervang gewoon alles."),
      table([["Jar", "was", "wordt", "Wat is er nieuw"],
             ["IekcorbarSiteSync", "1.14.2", "1.14.3", "Een grapboek (" + c("not_supposed_to.txt") + ") dat alleen in begraven schatten en ancient cities zit."],
             ["Steamworks", "0.3.9", "0.3.11", "Eigen advancement-tab (alleen toasts), kleine easter eggs, nette stop van de server."],
             ["Kitchen", "0.2.2", "0.2.6", "Gewassen naast water blijven staan, geen geflikker meer bij lopen over gewassen, advancement-tab, easter eggs."],
             ["Sakagura", "0.2.1", "0.2.6", "Zelfde gewassen-fixes, advancement-tab, easter eggs bij drinken en brouwen."],
             ["Wildlife", "0.3.2", "0.3.5", "Grotere klimboomstam, advancement-tab onder de Wildlife-tab, easter eggs (capibara, olifant)."],
             ["Relics", "0.3.1", "0.3.4", "Advancement-tab (alleen in het spel), easter eggs bij grammofoon, maneki-neko en wagasa-standaard."],
             ["Kura", "0.1.5", "0.1.7", "Advancement-tab, easter eggs (zoek 'cookie' in een terminal, de koerier-golem Dobby)."],
             ["Zukan", "0.2.4", "0.2.6", "Advancement-tab, easter eggs (50 bladwijzers, zoek 'recipe for disaster')."],
             ["Satchel", "0.1.0", "0.2.3", "Rugzakken tegelijk opslaan en terugzetten uit kisten, auto-aanvullen van de hotbar, rugzakken alleen te koop bij wandelende handelaren, advancement-tab, easter eggs."]],
            [30 * mm, 15 * mm, 15 * mm, 110 * mm]),
      steps(["Alle 9 jars vervangen."])]

s += [p("2. Resourcepack", H2),
      p("Het is één samengevoegd bestand: " + c("Iekcorbar-pack.zip") + " (ongeveer 3,9 MB, 11 packs erin, met een eigen pack-icoon). Het vervangt het oude bestand met dezelfde naam in "
        + c("plugins/ResourcePackManager/mixer/") + ". Er horen daar geen losse pack-zips van onze plugins naast te staan (Steamworks-blocks, Kitchen-pack, enzovoort); haal die weg als ze er nog staan, anders komen dezelfde bestanden dubbel in de pack."),
      p("Nieuw in deze pack: schaduw-kaarten voor de ertsen (labPBR, voor shaders zoals Complementary Unbound), de Wildlife-statuemodellen, de aangepaste Zukan-schermen en de Satchel-modellen.", SM),
      steps(["Oude pack vervangen en losse pack-zips verwijderd."])]

s += [p("3. Handelaren (WanderingTrades)", H2),
      p("Nieuw: " + c("satchel.yml") + " en " + c("satchel_rare.yml") + " in " + c("plugins/WanderingTrades/trades/") + ". Hiermee verkopen wandelende handelaren de rugzakken: canvas, koper en ijzer in het gewone bestand, goud, diamant en netherite in het zeldzame bestand. "
        "De overige trade-bestanden (Engineer's Manual, Wildlife, Zukan) staan er al van de eerste ronde."),
      p("De rugzak is eerst een gewoon papier met een naam en wordt bij het eerste rechtsklikken veranderd in de echte rugzak. Dat is normaal.", SM),
      steps(["Twee trade-bestanden geplaatst."])]

s += [PageBreak(), p("4. Herstarten en controleren", H1),
      p("Herstart de server (geen /reload). Kijk daarna in " + c("logs/latest.log") + ":"),
      steps(["Alle negen plugins staan als 'Enabled' in de log.",
             "Geen rode regels over ontbrekende advancements, modellen of recepten.",
             "Je komt zonder foutmelding binnen; de resourcepack laadt (let op het pack-icoon in de lijst).",
             "Bewaar het hele logbestand om aan mij door te geven."])]

s += [p("5. Wat je kunt proberen (testlijst)", H2),
      p("Doe er een paar, je hoeft niet alles. Schrijf op wat niet klopt."),
      table([["Wat", "Hoe", "Verwacht"],
             ["Advancement-tabs", "Open het advancement-scherm (L)", "Per plugin een eigen tab; verschijnen als toast, niet in de chat."],
             ["Gewassen naast water", "Zet rijst of soja op bouwgrond naast water", "Het gewas blijft staan; het water vloeit er niet in."],
             ["Gewassen aanlopen", "Loop over een rij gewassen", "Geen flikkerende of verdwijnende planten."],
             ["Rugzak kopen", "Zoek een wandelende handelaar", "Hij verkoopt een rugzak; bij rechtsklikken wordt het de echte."],
             ["Rugzak vullen", "Sneak + rechtsklik op een kist met een rugzak in je hand", "Spullen worden gedeeld tussen kist en rugzak (alleen soorten die in beide zitten)."],
             ["Ertstexturen", "Zink, tin of zwavel in de grond", "Eigen textuur; met shaders glans en diepte."],
             ["Klimboomstam", "Plaats de Climbing Log (zoo)", "Ongeveer drie blokken lang."],
             ["Zukan-scherm", "Open het Zukan-boek", "Steampunk-scherm; schuift het 1 pixel scheef, dan stel ik het bij."],
             ["Easter eggs", "Zie de lijst in mijn notities; bijvoorbeeld zoek 'cookie' in een Kura-terminal", "Een grapje en een verborgen advancement."]],
            [36 * mm, 64 * mm, 70 * mm])]

s += [p("6. Nog niet gedaan", H2),
      p("<b>Website:</b> de nieuwe wiki-pagina's (Wildlife, Kura-koeriers, Satchel) en de bijbehorende knoppen voor medewerkers komen pas online als er een nieuwe website-zip wordt gemaakt en geüpload. "
        "Ik maak die alleen op jouw verzoek. Zeg het als je hem wilt."),
      p("<b>EliteMobs en NPC's:</b> dat staat in een aparte PDF, " + c("Instructies-EliteMobs-NPC-update.pdf") + ". Doe die na deze."),
      p("<b>Terugdraaien:</b> zet de oude jar of het oude pack terug uit je back-up en herstart. De plugin-gegevens in de wereld (placed props, gewassen, rugzak-inhoud) blijven bewaard.", SM)]

SimpleDocTemplate(out, pagesize=A4, leftMargin=20 * mm, rightMargin=20 * mm, topMargin=18 * mm, bottomMargin=18 * mm,
                  title="Upload deel 2", author="iekcorbar").build(s, onFirstPage=footer, onLaterPages=footer)
print("wrote", out)
