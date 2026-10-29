# make_trailer_animatic.ps1 - plakt de trailer-animatic in elkaar (3:50).
#   powershell -File scripts\make_trailer_animatic.ps1
#
# Bronnen (allen lokaal, gitignore-werk in deploy\):
#   - vier heldenbeelden  1600x900: deploy\trailer\shots\shot-{sakura,stoom,kerker,lucht}.png
#   - logo-animatie 4s 1080x1080:  deploy\logo3d\iekcorbar-logo3d-anim.mp4 (naadloos)
#   - vertelling (229,9s):          C:\Users\baaye\Downloads\trailer versie 2- ... .mp3
#   - tekstkaart 1280x720 doorzichtig: deploy\trailer\tekst-uitkaart.png
#
# Timings volgens het script-PDF: ACT1 0:00-0:45 · ACT2 0:45-1:45 · ACT3 1:45-2:45
# · ACT4 2:45-3:35 · UIT 3:35-3:50 (15s). Ken-Burns-pan/zoom per beeld, 1s
# overvloeien (xfade), logo-lus onder de slotklok, tekst erover, fade-to-black.

$ErrorActionPreference = "Stop"
$root  = "C:\Users\baaye\Desktop\iekcorbar-final"
$shots = Join-Path $root "deploy\trailer\shots"
$uit   = Join-Path $root "deploy\trailer"
$mp3   = "C:\Users\baaye\Downloads\trailer versie 2- narrated intro 6 with play link female only.mp3"
$logo  = Join-Path $root "deploy\logo3d\iekcorbar-logo3d-anim.mp4"
$tekst = Join-Path $uit "tekst-uitkaart.png"
$ff    = "C:\Users\baaye\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.2-full_build\bin\ffmpeg.exe"
$bestand = Join-Path $uit "iekcorbar-trailer-animatic-v1.mp4"

Set-Location $root

# ---- tussenstap 1: vier Ken-Burns-clips van de stills (1280x720, 24 fps) ----
# zoompan: zichtvenster = input/zoom. Pannen = upscale x2, fixed zoom 2, x van
# 0 naar iw-iw/2. Inzoomen = center-pan met groeiende z.
function NieuweClip($naam, $duur, $uitdrukking) {
    $in  = Join-Path $shots "shot-$naam.png"
    $tmp = Join-Path $uit "clip-$naam.mp4"
    & $ff -y -v error -loop 1 -framerate 24 -t $duur -i $in -vf $uitdrukking -r 24 -c:v libx264 -crf 18 -preset fast -pix_fmt yuv420p $tmp
    if ($LASTEXITCODE -ne 0) { throw "clip $naam mislukt" }
    return $tmp
}

$P = "on/(24*{D})"   # voortgangsvariabele 0..1 (24 fps)
# sakura: links-> rechts pan · stoom: rechts->links pan
$c1 = NieuweClip "sakura" 46 ("scale=3200:1800:flags=lanczos,zoompan=z='2':x='(iw-iw/zoom)*" + ($P -replace '\{D\}','46') + "':y='ih/2-(ih/zoom)/2':d=1:s=1280x720:fps=24,format=yuv420p")
$c2 = NieuweClip "stoom"  61 ("scale=3200:1800:flags=lanczos,zoompan=z='2':x='(iw-iw/zoom)*(1-" + ($P -replace '\{D\}','61') + ")':y='ih/2-(ih/zoom)/2':d=1:s=1280x720:fps=24,format=yuv420p")
# kerker: rustig inzoomen op het midden (1.0 -> 1.25)
$c3 = NieuweClip "kerker" 61 ("scale=1600:900,zoompan=z='1+0.25*" + ($P -replace '\{D\}','61') + "':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=1280x720:fps=24,format=yuv420p")
# lucht: diagonaal pan + licht zoom-out (2.0 -> 1.5)
$c4 = NieuweClip "lucht"  51 ("scale=3200:1800:flags=lanczos,zoompan=z='2-0.5*" + ($P -replace '\{D\}','51') + "':x='(iw-iw/zoom)*" + ($P -replace '\{D\}','51') + "':y='(ih-ih/zoom)*(1-" + ($P -replace '\{D\}','51') + ")/2':d=1:s=1280x720:fps=24,format=yuv420p")

# ---- tussenstap 2: outro = logo-lus 15s + tekstkaart erover ----
$outro = Join-Path $uit "clip-outro.mp4"
& $ff -y -v error -stream_loop 4 -i $logo -i $tekst -t 15 `
    -filter_complex "[0:v]scale=520:520:flags=lanczos[p];color=c=black:s=1280x720:r=24[b];[b][p]overlay=(W-w)/2:70:shortest=1[bp];[bp][1:v]overlay=0:0,format=yuv420p[out]" `
    -map "[out]" -r 24 -c:v libx264 -crf 18 -preset fast $outro
if ($LASTEXITCODE -ne 0) { throw "outro mislukt" }

# ---- tussenstap 3: alles aan elkaar met 1s xfades (4 overgangen = -4s) ----
# total: 46+61+61+51+15 - 4 = 230s ~ == mp3 (229,9)
& $ff -y -v error -i $c1 -i $c2 -i $c3 -i $c4 -i $outro -i $mp3 -filter_complex `
"[0][1]xfade=transition=fade:duration=1:offset=45[a];[a][2]xfade=transition=fade:duration=1:offset=105[b];[b][3]xfade=transition=fade:duration=1:offset=165[c];[c][4]xfade=transition=fade:duration=1:offset=215[v];[v]fade=t=in:st=0:d=1.2,fade=t=out:st=227.5:d=2.4,format=yuv420p" `
-map "[v]v" -map 5:a -c:v libx264 -crf 19 -preset medium -c:a aac -b:a 192k -shortest $bestand
if ($LASTEXITCODE -ne 0) { throw "montage mislukt" }

"ANIMATIC KLAAR: $bestand"
