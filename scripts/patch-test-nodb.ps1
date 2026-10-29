# patch-test-nodb.ps1 — draait PAS na het uitpakken, VOOR de eerste start.
# MAAKT DE TESTSERVER DICHT: niets raakt de productie-MySQL, Discord of vote-services.
$ErrorActionPreference = 'Stop'
$P = "E:\mctest26_3\plugins"

# 1) jars die naar buiten praten: helemaal weghalen uit de test
$weg = @(
  "IekcorbarSiteSync-1.16.0.jar",   # duwt playerdata + RCON naar de site-DB
  "DiscordSRV-Build-1.30.5.jar",    # MySQL + Discord-bot
  "nuvotifier.jar",                 # vote-callback-listener
  "tally-plugin-0.1.86.jar",        # vote-sync
  "VotingPlugin.jar"                # vote-site commotie
)
foreach ($j in $weg) {
  if (Test-Path "$P\$j") { Remove-Item "$P\$j" -Force; "VERWIJDERD: $j" }
}

# 2) MySQL-verwijzingen in bijgeleverde configs: vinden + op sqlite/none zetten
$pat = '172\.18\.0\.1|jdbc:mysql|db_type:\s*.?.?mysql|storage(-type)?:\s*.?.?mysql'
$cfgs = Get-ChildItem $P -Recurse -Include *.yml,*.json,*.properties,*.conf -ErrorAction SilentlyContinue
$hits = @()
foreach ($f in $cfgs) {
  $t = Get-Content $f.FullName -Raw -ErrorAction SilentlyContinue
  if ($t -and ($t -match $pat)) {
    $hits += $f.FullName
  }
}
"MYSQL-VERWIJZINGEN GEVONDEN: $($hits.Count)"
$hits | ForEach-Object { "  $_" }

# 3) gerichte patches (config-knoppen per plugin)
function PatchFile($pad, $paren, $verv) {
  if (-not (Test-Path $pad)) { "  (ontbreekt: $pad)"; return }
  $t = Get-Content $pad -Raw
  $n = $t -replace $paren, $verv
  if ($n -ne $t) { Set-Content $pad $n -NoNewline -Encoding UTF8; "  gepatcht: $(Split-Path $pad -Leaf) [$paren -> $verv]" }
}
if (Test-Path "$P\ValhallaMMO\config.yml") {
  PatchFile "$P\ValhallaMMO\config.yml" "db_type: ?'?mysql'?" "db_type: 'sqlite'"
  PatchFile "$P\ValhallaMMO\config.yml" "db_host: ?'[\d\.]+'?" "db_host: '127.0.0.1'"
}
# MythicLib (MMOItems/MMOCore-backend): storage naar sqlite/yaml
Get-ChildItem "$P" -Recurse -Filter "config.yml" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match "MythicLib|MMOCore|MMOItems|MMOBridge" } | ForEach-Object {
  PatchFile $_.FullName "storage-type: ?'?(MYSQL|MySQL|mysql)'?" "storage-type: SQLITE"
  PatchFile $_.FullName "type: ?'?(MYSQL|MySQL|mysql)'?" "type: SQLITE"
  PatchFile $_.FullName "host: ?'[\d\.]+'?" "host: '127.0.0.1'"
}
# Jobs: database.sql -> yaml
if (Test-Path "$P\Jobs\config.yml") {
  PatchFile "$P\Jobs\config.yml" "type: ?SQL" "type: YAML"
}
# BlueMap: staat standaard op sqlite-file, maar check voor de zekerheid
Get-ChildItem "$P\bluemap" -Recurse -Filter "storage.conf" -ErrorAction SilentlyContinue | ForEach-Object {
  PatchFile $_.FullName 'type = "SQL_MYSQL"' 'type = "JSON_FLAT"'
}

# 4) eindcontrole: mag geen enkelejdbc/172.18 meer vinden in ACTIEVE pads (rest = meldpunt)
$rest = @()
foreach ($f in (Get-ChildItem $P -Recurse -Include *.yml,*.json,*.properties,*.conf -ErrorAction SilentlyContinue)) {
  $t = Get-Content $f.FullName -Raw -ErrorAction SilentlyContinue
  if ($t -and $t -match "172\.18\.0\.1|jdbc:mysql") { $rest += $f.FullName }
}
"RESTEERDE MySQL-VERWIJZINGEN (handmatig checken vóór start): $($rest.Count)"
$rest | ForEach-Object { "  $_" }
"PATCH-KLAAR"
