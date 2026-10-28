#!/bin/sh
# Wekelijkse back-up van de StaffHub-bak: sqlite + blobs in één tar.
# Cron-regel (eigenaar voegt toe): 15 3 * * 0 /opt/staffhub/backup.sh
set -e
DIR="$(cd "$(dirname "$0")" && pwd)/backups"
mkdir -p "$DIR"
STAMP=$(date +%F-%H%M)
tar czf "$DIR/staffhub-$STAMP.tgz" -C "$(dirname "$0")" data
# bewaar de laatste 8
ls -1t "$DIR"/staffhub-*.tgz | tail -n +9 | xargs -r rm -f
echo "backup klaar: $DIR/staffhub-$STAMP.tgz"
