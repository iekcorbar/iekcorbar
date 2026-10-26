"""Adds the missing 'meta' block to Blockbench .bbmodel and FMM .fmmodel files.

FreeMinecraftModels logs "Missing 'meta' field in model: X. Defaulting to version 4."
for every model exported without it. This script writes exactly that default
into the file (format_version 4.0), so the model loads the same as now - just
without the log line. Files that already have a meta block are left alone.

It also reports (does not fix) models with a "hitbox" bone that has no cube in
it - FMM can't build a hitbox for those.

Usage (on a copy of plugins/FreeMinecraftModels/models, or on the server):
    python fix_bbmodel_meta.py <models folder>            # dry run: only lists
    python fix_bbmodel_meta.py <models folder> --apply    # writes, keeps <file>.bak
    python fix_bbmodel_meta.py <models folder> --apply --no-backup
"""
import json, os, sys


def has_cube(group, cubes):
    for child in group.get("children", []):
        if isinstance(child, dict):
            if has_cube(child, cubes):
                return True
        elif child in cubes:
            return True
    return False


def hitbox_problems(model):
    cubes = {e.get("uuid") for e in model.get("elements", [])}
    found = []

    def walk(groups):
        for g in groups:
            if not isinstance(g, dict):
                continue
            if str(g.get("name", "")).lower() == "hitbox" and not has_cube(g, cubes):
                found.append(g)
            walk(g.get("children", []))
    walk(model.get("outliner", []))
    return found


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    apply = "--apply" in sys.argv
    backup = "--no-backup" not in sys.argv
    if len(args) != 1 or not os.path.isdir(args[0]):
        print(__doc__)
        sys.exit(1)

    fixed = skipped = broken = 0
    for root, _, files in os.walk(args[0]):
        for name in sorted(files):
            if not name.lower().endswith((".bbmodel", ".fmmodel")):
                continue
            path = os.path.join(root, name)
            rel = os.path.relpath(path, args[0])
            try:
                with open(path, encoding="utf-8-sig") as f:
                    text = f.read()
                model = json.loads(text)
            except (OSError, ValueError) as e:
                print(f"  ERROR    {rel}: {e}")
                broken += 1
                continue
            if not isinstance(model, dict):
                print(f"  ERROR    {rel}: not a Blockbench model")
                broken += 1
                continue

            for _ in hitbox_problems(model):
                print(f"  HITBOX   {rel}: 'hitbox' bone without a cube (fix in Blockbench)")

            if isinstance(model.get("meta"), dict):
                skipped += 1
                continue
            box_uv = any(e.get("box_uv") for e in model.get("elements", []) if isinstance(e, dict))
            meta = {"format_version": "4.0", "model_format": "free", "box_uv": bool(box_uv)}
            new = {"meta": meta}
            new.update({k: v for k, v in model.items() if k != "meta"})
            print(f"  {'FIXED' if apply else 'WOULD FIX'} {rel}")
            fixed += 1
            if apply:
                if backup and not os.path.exists(path + ".bak"):
                    with open(path + ".bak", "w", encoding="utf-8") as f:
                        f.write(text)
                compact = "\n" not in text.strip()
                with open(path, "w", encoding="utf-8") as f:
                    if compact:
                        json.dump(new, f, ensure_ascii=False, separators=(",", ":"))
                    else:
                        json.dump(new, f, ensure_ascii=False, indent="\t")

    verb = "fixed" if apply else "to fix"
    print(f"\n{fixed} {verb}, {skipped} already fine, {broken} unreadable.")
    if fixed and not apply:
        print("Dry run - nothing changed. Run again with --apply to write the files.")


if __name__ == "__main__":
    main()
