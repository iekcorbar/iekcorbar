#!/bin/bash
# iekcorbar serverbackup - automatisch (cron 's nachts) of handmatig: iekcorbar-backup
#
# Wat het doet, in deze volgorde:
#   1. RCON "save-all flush"  -> de wereld wordt naar schijf weggeschreven,
#      zodat de tar geen half-beschreven region-files pakt.
#   2. tar + pigz van de VOLLEDIGE server-volume (~22 GB). Alles mee: wereld,
#      plugins, serversoftware, bluemap, logs - er is 300+ GB vrij, dus er
#      wordt niets weggelaten uit plaatsgebrek. Alleen ./backups (dat zijn de
#      panel-eigen backups; die wil je niet dubbel) en ./cache (herbouwbaar)
#      blijven buiten boord.
#   3. Opruimen: de laatste 7 backups bewaard, de rest weg (~100 GB piek).
#   4. Discord: het backup-kanaal krijgt zelf bericht - naam, grootte, duur en
#      vrije schijfruimte. Lukt de backup niet, dan gaat er een alarmbericht
#      heen; stilzwijgend falen bestaat niet.
#
# Webhook-URL: /root/.config/iekcorbar/backup-webhook (root 600) - staat in
# geen repo, log of chatbericht.

set -o pipefail
VOLUME=/var/lib/pterodactyl/volumes/06b962a3-cf1d-4606-a254-a62027f0983d
RUIMTE=/var/backups/iekcorbar
BEHOU=7
ts=$(date +%F_%H%M)
BESTAND="$RUIMTE/iekcorbar-$ts.tar.gz"
LOG="$RUIMTE/iekcorbar-$ts.log"
START=$(date +%s)
mkdir -p "$RUIMTE"

meld() {  # $1 ok|fout   $2 extra tekst
  if [ "$1" = ok ]; then
    duur=$(( $(date +%s) - START ))
    grootte=$(du -h "$BESTAND" 2>/dev/null | cut -f1)
    vrij=$(df -h "$RUIMTE" | awk 'NR==2 {print $4}')
    tekst="💾 **Server-backup $ts finished** · size ${grootte:-?} · took ${duur}s · ${vrij} free on /var · full volume copy (bluemap and logs included), last $BEHOU kept. $2"
  else
    tekst="🚨 **Server-backup $ts FAILED** · $2"
  fi
  [ -f /root/.config/iekcorbar/backup-webhook ] || { echo "geen webhook-config" >&2; return; }
  json=$(python3 -c 'import json,sys; print(json.dumps({"content": sys.argv[1]}))' "$tekst")
  curl -s -m 15 -H 'content-type: application/json' -d "$json" \
    "$(cat /root/.config/iekcorbar/backup-webhook)" >/dev/null 2>&1
}

# --- 1. flush ---
RCONUIT=""
if command -v iekcorbar-rcon >/dev/null 2>&1; then
  RCONUIT=$(iekcorbar-rcon "save-all flush" 2>&1 | head -c 200) || true
fi
sleep 6

# --- 2. tar (pigz = multi-core gzip) ---
tar -I pigz -cf "$BESTAND" \
  --exclude='./backups' --exclude='./cache' \
  --warning=no-file-changed \
  -C "$VOLUME" . >"$LOG" 2>&1
code=$?
# code 1 = "sommige bestanden veranderden tijdens het lezen" - bij een
# draaiende server verwacht en goedaardig; alles erger is een echte fout.
[ $code -eq 1 ] && code=0
if [ $code -ne 0 ]; then
  meld fout "tar code $code: $(tail -c 300 "$LOG" 2>/dev/null)"
  rm -f "$BESTAND"
  exit 1
fi

# --- 3. opruimen ---
ls -1t "$RUIMTE"/iekcorbar-*.tar.gz 2>/dev/null | tail -n +$((BEHOU + 1)) | xargs -r rm -f

# --- 4. melden ---
flush=$(printf %s "$RCONUIT" | grep -io 'flushed\|saved the game' | head -1)
meld ok "World flush: ${flush:-not confirmed (continued)}."
exit 0
