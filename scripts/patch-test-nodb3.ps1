# patch-test-nodb3.ps1 — ronde 3: echte DB-credentials uit de test-copy halen
# en alle mysql-switches op het gevonden toetsnaam neerleggen.
$P = "E:\mctest26_3\plugins"

function Patch($rel, $pat, $rep) {
  $f = Join-Path $P $rel
  if (-not (Test-Path $f)) { "  (ontbreekt: $rel)"; return }
  $t = Get-Content $f -Raw
  $n = [regex]::Replace($t, $pat, $rep)
  if ($n -ne $t) { Set-Content $f $n -NoNewline -Encoding UTF8; "  GEPATCHT: $rel" }
  else { "  (patroon niet gevonden: $rel)" }
}

# 0) CREDENTIALEN SCRUB: WorldGuard-blok met jdbc-mysql URL -> leeg
Patch "WorldGuard\config.yml" '(?m)^(\s*dsn:) jdbc:mysql://\S+$' '$1 ""'

# 1) MMOCore / MMOItems: mysql-blok enabled true -> false
Patch "MMOCore\config.yml"  '(?s)(mysql:\s*\r?\n\s*enabled: )true' '$1false'
Patch "MMOItems\config.yml" '(?s)(mysql:\s*\r?\n\s*enabled: )true' '$1false'

# 2) TAB: idem
Patch "TAB\config.yml"      '(?s)(mysql:\s*\r?\n\s*enabled: )true' '$1false'

# 3) Jobs: method MySQL -> YAML (storage: section)
Patch "Jobs\generalConfig.yml" '(?m)^(\s*)method: MySQL$' '$1method: YAML'

# 4) CMI: method MySQL -> sqlite
Patch "CMI\Settings\DataBaseInfo.yml" '(?m)^(\s*)method: MySQL$' '$1method: sqlite'

# 5) ItemJoin: MySQL: true -> false
Patch "ItemJoin\config.yml" '(?m)^(\s*)MySQL: true$' '$1MySQL: false'

# 6) BetonQuest: alleen binnen de database-/mysql-sectie uitzetten (handmatig bekeken)
#    (wordt hieronder dynamisch ingelezen; zie de regels vóór het patchen)
"=== BETONQUEST context rond enabled/type/host ==="
$bq = Get-Content (Join-Path $P "BetonQuest\config.yml")
for ($i = 0; $i -lt $bq.Count; $i++) {
  if ($bq[$i] -match 'enabled:|type:|host:|mysql|database') { "  {0}: {1}" -f ($i+1), $bq[$i].Trim() }
}

# 7) MythicLib opslag (MMOItems-backend): kijk of er een aparte storage-config is
Get-ChildItem "$P\MythicLib" -ErrorAction SilentlyContinue | ForEach-Object { "  MythicLib-bestand: $($_.Name)" }

# EINDSCAN: nog ergens een actieve mysql-poging (host AND enabled true binnenzelfde blok)?
$tell = 0
Get-ChildItem $P -Recurse -Include *.yml,*.conf -ErrorAction SilentlyContinue | ForEach-Object {
  $t = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
  if ($t -and $t -match 'jdbc:mysql://\w+:[^@\s]+@') { $tell++; "  NOG CREDENTIALEN: $($_.FullName)" }
}
"actieve jdbc-mysql-URLs met wachtwoord: $tell"
"PATCH-RONDE-3-KLAAR"
