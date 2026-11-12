# CC0-mesh-bronnen (tools/downloads/mesh_*.json)

Elk `mesh_<sid>.json` is gegenereerd door `mesh2model.py` (of herbouwd door `mesh_rerig.py`,
gezien 11-11) uit een CC0-download-mesh. De bron-OBJ's zelf staan NIET in de repo (te groot).

| sid | bron-bestand | bron | licentie |
|-----|-------------|------|----------|
| elephant | elephant.obj | grab3d.com/models/animals/elephant/ (her-voxeld 11-12, --vox 1.4) | CC0 (AI-assist, Meshy → Blender-cleanup; Grab3D-publieke domeinverklaring) |
| giraffe | giraffe.obj | grab3d.com/models/animals/giraffe/ (her-voxeld 11-12) | CC0 (idem) |
| lion | lion.obj | grab3d.com/models/animals/lion/ (her-voxeld 11-12) | CC0 (idem) |
| macaw | parrot.obj | grab3d.com/models/animals/parrot/ (her-voxeld 11-12) | CC0 (idem) |
| orca | Whale.obj | Quaternius "Animated Fish Pack" via opengameart.org (`Animated%20Fish%20Pack%20by%20@Quaternius.zip`) | CC0 |
| koi | Fish2.obj | idem (fish pack) | CC0 |
| zebra | Zebra.obj | Quaternius "Farm Animal Pack" via opengameart.org | CC0 |
| deer, crab, penguin, piranha, boar†, macaque† | oud: Quaternius-dl 10-11 (OBJ niet bewaard); 11-11 her-voxelen kan niet → `mesh_rerig.py` her-rig op de bestaande boxen | — | CC0 (oorspronkelijk) |

† boar + macaque staan sinds 11-12 NIET meer in de MESH-lijst van `make_animals.py`: hun
mesh-blob is vervangen door de hand-built builder (grab3d heeft geen zwijn/aap; de originele
Quaternius-OBJ's zitten in de betaalde Ultimate-Animal-Pack). De `mesh_boar.json` /
`mesh_macaque.json` blijven liggen als referentie.

‡ 11-12 blob-ronde: **lion, penguin en macaw** zijn óók teruggezet op hun hand-built builder, om
kwaliteit - niet om licentie. De ara werd een staande maiskolf (21,7 px hoog tegen 7,6 px voor z'n
eigen blauwe/groene look: `species_builder()` kent een mesh alleen toe aan de SOORTNAAM, dus de
EXTRA-looks bleven hand-built en kregen een ander formaat), de pinguin een strandbal van 2,5 × 1,8 ×
1,8 m in plaats van 1,1 m op z'n poten, en de leeuw-rig gaf ONE voorpoot 48 boxen en de andere 4
(vandaar "hoofd 3 px scheef"). `mesh_lion.json` / `mesh_penguin.json` / `mesh_macaw.json` blijven
liggen als referentie; de statues/beeldjes en het Zoo-pack meebouwen doet dit werk pas ná één
herbouw aan het eind van de dierenronde.

Let op: de grab3d-licenses zijn CC0 maar vermelden "AI-generated (Meshy)". Voor een
Minecraft-server met eigen voxel-omzetting is dat juridisch schoon; de bron is wel
geen handmatig-modelleerder. Eigenaar is geïnformeeld via PROJECTBORD 11-12.
