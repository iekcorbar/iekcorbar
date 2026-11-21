#!/bin/bash
# iekcorbar serverbackup - automatisch (cron 's nachts) of handmatig: iekcorbar-backup
#
# Wat het doet, in deze volgorde:
#   1. "save-all flush" via de site_command_queue  -> de wereld wordt naar
#      schijf weggeschreven, zodat de tar geen half-beschreven region-files
#      pakt. (Sinds 15-11: RCON staat UIT op de server - deze wachtrij is de
#      RCON-vervanging; de plugin voert de commando's uit en het antwoord
#      komt in de tabel terug, de console blijft stil.)
#   2. tar + pigz van de VOLLEDIGE server-volume (~22 GB). Alles mee: wereld,
#      plugins, serversoftware, bluemap, logs - er is 300+ GB vrij, dus er
#      wordt niets weggelaten uit plaatsgebrek. Alleen ./backups (dat zijn de
#      panel-eigen backups; die wil je niet dubbel) en ./cache (herbouwbaar)
#      blijven buiten boord.
#   3. Opruimen: de laatste 4 backups bewaard, de rest weg (~68 GB piek).
#   4. Discord: het backup-kanaal krijgt zelf bericht - naam, grootte, duur en
#      vrije schijfruimte. Lukt de backup niet, dan gaat er een alarmbericht
#      heen; stilzwijgend falen bestaat niet.
#
# Webhook-URL: /root/.config/iekcorbar/backup-webhook (root 600) - staat in
# geen repo, log of chatbericht.

set -o pipefail
VOLUME=/var/lib/pterodactyl/volumes/06b962a3-cf1d-4606-a254-a62027f0983d
RUIMTE=/var/backups/iekcorbar
BEHOU=4
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

# --- 1. flush via de command-queue (RCON is sinds 15-11 UIT) ---
# Credentials: dezelfde database als de SiteSync-plugin zelf gebruikt, uit
# haar live config.yml in de server-volume. MYSQL_PWD houdt het wachtwoord
# uit de ps-lijst.
RCONUIT=""
CFG="$VOLUME/plugins/IekcorbarSiteSync/config.yml"
if [ -f "$CFG" ] && command -v mysql >/dev/null 2>&1; then
  eval "$(python3 - "$CFG" <<'PYEOF'
import re, sys, shlex
t = open(sys.argv[1], encoding='utf-8').read()
db = t.split('database:', 1)[1] if 'database:' in t else t
def g(key, default=None):
    m = re.search(r'^\s*%s:\s*(.+?)\s*$' % key, db, re.M)
    if not m:
        return default
    v = m.group(1)
    q = re.match(r'^"([^"]*)"', v) or re.match(r"^'([^']*)'", v)
    if q:
        return q.group(1)  # quoted: anything after the closing quote is a comment
    return re.split(r'\s+#', v)[0].strip()  # unquoted: strip inline comment
print('QHOST=%s QPORT=%s QNAME=%s QUSER=%s' % tuple(shlex.quote(g(k, d)) for k, d in
      [('host', '172.18.0.1'), ('port', '3306'), ('name', 's30266_iekcorbar_player_data'), ('username', '')]))
import os
pw = g('password', '')
os.environ['MYSQL_PWD'] = pw
print('MYSQL_PWD=%s' % shlex.quote(pw))
PYEOF
)" 2>/dev/null
fi
if [ -n "${QUSER:-}" ] && [ -n "${MYSQL_PWD:-}" ] && command -v mysql >/dev/null 2>&1; then
  QID=$(mysql -h "$QHOST" -P "$QPORT" -u "$QUSER" -N -s "$QNAME" \
    -e "INSERT INTO site_command_queue (command, status) VALUES ('save-all flush', 'queued'); SELECT LAST_INSERT_ID();" 2>/dev/null)
  if [ -n "$QID" ]; then
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
      sleep 1
      QROW=$(mysql -h "$QHOST" -P "$QPORT" -u "$QUSER" -N -s "$QNAME" \
        -e "SELECT status, IFNULL(response,'') FROM site_command_queue WHERE id=$QID" 2>/dev/null)
      case "$QROW" in
        done*|failed*) RCONUIT="${QROW#*$'\t'}"; mysql -h "$QHOST" -P "$QPORT" -u "$QUSER" "$QNAME" -e "DELETE FROM site_command_queue WHERE id=$QID" 2>/dev/null; break ;;
      esac
    done
  fi
  # Backwards-val: plugin jar <1.18 of DB onbereikbaar = de wereld-updater van
  # vanilla zelf blijft draaien; tar pakt dan REGION-consistente files zoals
  # voorheen bij een gemiste flush. Vandaar "continue" in de Discordmelding.
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
