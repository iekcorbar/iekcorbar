"""
Builds the Plesk deploy zip with spec-correct forward-slash entry paths.

Windows zip tools are unreliable for this job:
  - `tar -a -c -f x.zip ...` (bsdtar zip-emulation) produced a malformed,
    dual-stream archive when one input (IekcorbarSiteSync-1.0.0.jar) is
    itself already a ZIP-format file -- Linux `unzip` on Plesk only
    recovered the jar's own internal contents and reported
    "N extra bytes at beginning or within zipfile" for everything else.
  - PowerShell's Compress-Archive writes entry names with backslash path
    separators on Windows, which the ZIP spec forbids and Linux `unzip`
    does not reliably convert into subdirectories.

Python's zipfile module always normalizes to forward slashes, so it's the
reliable option here regardless of host OS.
"""
import os
import sys
import zipfile

# NEVER ship the dev machine's src/lib/db.json in a deploy zip. It's meant as
# a local-dev/emergency-fallback mirror, but MySQL write failures in
# production silently fall back to writing *there* -- if that ever happens,
# production's db.json can end up holding real, live-only data (chat
# messages, guide steps, brewing recipes have all been lost to this exact
# bug). Overwriting it with whatever's on the developer's machine on every
# deploy destroys that data. If db.json genuinely needs to be reset/seeded
# on the server, do it deliberately there, not as a side effect of deploying
# unrelated code changes.
EXCLUDE = {
    "src/lib/db.json",
}

# Source maps and the file-tracing manifests (.nft.json) are build-time
# debug/tracing artifacts: server.js never reads them. Per eigenaarsbesluit
# 30-10 ("zip hoeft alleen de build te hebben") gaan ze niet meer mee - ze
# waren goed voor ~9 MB van de 31 MB die de Plesk-filemanager niet pakte.
SKIP_SUFFIXES = (".map", ".nft.json")


def add_path(zf, base, rel):
    full = os.path.join(base, rel)
    if not os.path.exists(full):
        print(f"WARNING: skipping missing path: {rel}")
        return
    if os.path.isdir(full):
        for root, _dirs, files in os.walk(full):
            for f in files:
                if f.endswith(SKIP_SUFFIXES):
                    continue
                fp = os.path.join(root, f)
                arcname = os.path.relpath(fp, base).replace(os.sep, "/")
                if arcname in EXCLUDE:
                    print(f"Excluding {arcname} from the deploy zip (see EXCLUDE note).")
                    continue
                zf.write(fp, arcname)
    else:
        arcname = rel.replace(os.sep, "/")
        if arcname in EXCLUDE:
            print(f"Excluding {arcname} from the deploy zip (see EXCLUDE note).")
            return
        zf.write(full, arcname)


def main():
    out = sys.argv[1]
    items = sys.argv[2:]
    base = os.getcwd()

    if os.path.exists(out):
        os.remove(out)

    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        for item in items:
            add_path(zf, base, item)

    print(f"Wrote {out}")


if __name__ == "__main__":
    main()
