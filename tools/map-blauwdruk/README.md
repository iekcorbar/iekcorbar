# Map-blauwdruk (A4, zwartwit)

Printklaar ontwerp voor de gele map met offline documentatie: voorblad, 3 rugetiketten, 24 tabbladen.

    node tools/map-blauwdruk/build-a4.js deploy/map-blauwdruk-a4
    msedge --headless --no-pdf-header-footer --virtual-time-budget=15000 --print-to-pdf=deploy/map-blauwdruk-a4/iekcorbar-map-blauwdruk-a4.pdf file:///<pad>/print.html

`designs.js` bevat de vier zwartwit-covers en het stickervel (browser en Node); `build-a4.js` bouwt de drie A4-pagina's in millimeters.
Lettertypen (Cinzel, IBM Plex Sans) komen van Google Fonts, dus bouw met internet.
