Own music discs for the Relics plugin
=====================================
Drop audio files here (ogg, mp3, wav, flac, m4a, opus) - for example songs made with an AI music tool, as long as you
have the rights to use them. Then run, in the Relics folder:

    python -I tools/make_discs.py <path-to-ffmpeg>
    python -I tools/make_pack.py resourcepack          (and the usual: python -I scripts/merge-packs.py at the repo root)
    mvn -o clean package

Every file becomes one disc item. The list of discs lives in the `discs:` section of the plugin config
(Relics/src/main/resources/config.yml on the dev side, plugins/Relics/config.yml on the server) - tools/make_discs.py
fills the generated entries between the `# BEGIN:generated` / `# END:generated` markers from the audio files above,
and you can add or remove your own entries by hand outside those markers. Fields per disc:

    id:        the disc id (sound event relics:disc.<id>, item relics:disc_<id>)
    title:     title shown on the disc / in the jukebox
    seconds:   length of the song in seconds
    event:     sound event to play (relics:disc.<id> unless you renamed it)
    recipe:    extra crafting item for the shapeless recipe (a Material name, empty = staff only / /zukan)
    colour:    jukebox disc colour (0-5)

Example entry:

    discs:
      winter_waltz:
        title: Winter Waltz
        seconds: 95
        event: disc.winter_waltz
        recipe: SNOWBALL
        colour: 0

A disc is crafted shapeless from a note block + paper + black dye + the extra item. Without an extra item it can only be
handed out by staff (/relics disc give <player> <id>) or found through /zukan. Songs are played from a jukebox like any
vanilla disc. Mono is used on purpose: stereo files do not fade with distance. Built-in discs (Jingle Bells, Deck the
Halls, We Wish You a Merry Christmas, Silent Night) are synthesised by sounds/make_sounds.py; their entries are in the
generated block too.
