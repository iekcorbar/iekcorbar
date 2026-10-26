"""Wildlife-sounds resource pack: the animal and biome sounds made by sounds/make_sounds.py.
(The animal models themselves are in FreeMinecraftModels' own pack.)
Usage: python make_sound_pack.py <out folder>  -> <out>/Wildlife-sounds.zip
"""
import json, os, shutil, sys, zipfile


def main():
    out = sys.argv[1]
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "sounds", "out", "wildlife")
    if not os.path.isdir(src):
        sys.exit("no sounds yet - run sounds/make_sounds.py first")
    root = os.path.join(out, "Wildlife-sounds")
    shutil.rmtree(root, ignore_errors=True)
    shutil.copytree(src, os.path.join(root, "assets", "wildlife"))
    with open(os.path.join(root, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"description": "Wildlife sounds", "pack_format": 64,
                            "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}}, f)
    zpath = os.path.join(out, "Wildlife-sounds.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(root):
            for name in sorted(files):
                full = os.path.join(dirpath, name)
                z.write(full, os.path.relpath(full, root).replace(os.sep, "/"))
    print("wrote", zpath)


if __name__ == "__main__":
    main()
