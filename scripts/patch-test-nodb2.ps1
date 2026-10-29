# patch-test-nodb2.ps1 — ronde 2: alle productie-DB-adressen naar localhost
# (niets KAN de echte DB meer raken), + lokale storage-keuzes waar mogelijk.
$P = "E:\mctest26_3\plugins"
$bestanden = Get-ChildItem $P -Recurse -Include *.yml,*.json,*.properties,*.conf -ErrorAction SilentlyContinue
$aangepast = 0
foreach ($f in $bestanden) {
  $t = Get-Content $f.FullName -Raw -ErrorAction SilentlyContinue
  if (-not $t) { continue }
  $n = $t
  # 1) productie-DB-adressen -> localhost (credentials blijven staan maar zijn nutteloos lokaal)
  $n = $n -replace '172\.18\.0\.1', '127.0.0.1'
  $n = $n -replace 'sql2\.revivenode\.com', '127.0.0.1'
  # 2) plugins met een storage-method-knop: lokaal zetten
  if ($f.FullName -match "LuckPerms") { $n = $n -replace 'storage-method:\s*mysql', 'storage-method: h2' }
  if ($f.FullName -match "Jobs") { $n = $n -replace 'type:\s*SQL\b', 'type: YAML' }
  if ($f.FullName -match "EliteMobs") { $n = $n -replace '(?i)(use[-_]?mysql[a-z]*:\s*)true', '$1false' }
  if ($f.FullName -match "CMI") { $n = $n -replace '(?i)(type:\s*)MYSQL', '$1SQLite' }
  if ($f.FullName -match "ValhallaMMO") { $n = $n -replace "(?i)db_type:\s*'?mysql'?", "db_type: 'sqlite'" }
  if ($f.FullName -match "MMOCore|MMOItems|MythicLib|MMOBridge") {
    $n = $n -replace "(?i)storage(-type)?:\s*'?MYSQL'?", 'storage$1: SQLITE'
  }
  if ($n -ne $t) {
    Set-Content $f.FullName $n -NoNewline -Encoding UTF8
    $aangepast++
  }
}
"bestanden aangepast: $aangepast"
# eindscan: mag niets meer vinden van de productie-DB
$rest = 0
foreach ($f in (Get-ChildItem $P -Recurse -Include *.yml,*.json,*.properties,*.conf -ErrorAction SilentlyContinue)) {
  $t = Get-Content $f.FullName -Raw -ErrorAction SilentlyContinue
  if ($t -and ($t -match '172\.18\.0\.1|revivenode')) { $rest++; "  NOG TER PLAATSE: $($f.FullName)" }
}
"productie-DB-verwijzingen over: $rest"
