#!/usr/bin/env python3
"""Post a message to a Discord webhook.

Usage:
  python -I scripts/discord-post.py --webhook URL --file message.md
  python -I scripts/discord-post.py --webhook URL --text "Hello world"
  echo "msg" | python -I scripts/discord-post.py --webhook URL

Environment:
  DISCORD_WEBHOOK_URL / DISCORD_WEBHOOK_STAFF / DISCORD_WEBHOOK_PLAYER can be used instead of --webhook.
"""
import argparse, json, os, sys, urllib.request

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--webhook", "-w", help="Discord webhook URL")
    ap.add_argument("--file", "-f", help="Read message from file")
    ap.add_argument("--text", "-t", help="Message text")
    ap.add_argument("--name", help="Override webhook username")
    args = ap.parse_args()

    url = args.webhook or os.environ.get("DISCORD_WEBHOOK_URL")
    if not url:
        # try named envs
        for k in ("DISCORD_WEBHOOK_PLAYER", "DISCORD_WEBHOOK_STAFF"):
            v = os.environ.get(k)
            if v:
                url = v
                break
    if not url:
        print("ERROR: no webhook URL given (--webhook or DISCORD_WEBHOOK_* env)", file=sys.stderr)
        sys.exit(1)

    if args.file:
        with open(args.file, encoding="utf-8") as fh:
            text = fh.read()
    elif args.text:
        text = args.text
    else:
        text = sys.stdin.read()

    # Discord limit: 2000 chars per message. Split if needed.
    chunks = []
    while len(text) > 1900:
        idx = text.rfind("\n", 0, 1900)
        if idx == -1:
            idx = 1900
        chunks.append(text[:idx])
        text = text[idx:].lstrip("\n")
    if text:
        chunks.append(text)

    for chunk in chunks:
        payload = {"content": chunk}
        if args.name:
            payload["username"] = args.name
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json", "User-Agent": "Mozilla/5.0 (compatible; iekcorbar-deploy/1.0)"})
        try:
            with urllib.request.urlopen(req) as resp:
                print(f"OK {resp.status} ({len(chunk)} chars)")
        except urllib.error.HTTPError as e:
            print(f"FAIL {e.code}: {e.read().decode('utf-8', 'replace')}", file=sys.stderr)
            sys.exit(1)

if __name__ == "__main__":
    main()