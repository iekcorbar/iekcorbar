#!/usr/bin/env python3
"""Audit all wildlife bbmodels for animation and structure issues."""
import json, os, sys, glob

MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "fmm-models", "wildlife")
STATIC_MODELS = []  # models with no animation tracks at all (item/prop looks) - fine, listed for review


def audit_model(path):
    issues = []
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    name = os.path.basename(path)

    # Check resolution
    res = data.get("resolution", {})
    if res.get("width") != res.get("height"):
        issues.append(f"non-square resolution {res.get('width')}x{res.get('height')}")

    # Check elements exist
    elements = data.get("elements", [])
    if not elements:
        issues.append("no elements")

    # Check bones
    # Collect all bone uuids recursively (bones are dicts with uuid, children can be dicts or str refs)
    all_bones = set()
    def collect_bones(nodes):
        for n in nodes:
            if isinstance(n, dict) and "uuid" in n and "children" in n:
                all_bones.add(n["uuid"])
                collect_bones(n.get("children", []))
            elif isinstance(n, dict) and "uuid" in n:
                all_bones.add(n["uuid"])
    collect_bones(data.get("outliner", []))

    # Required animations for animals. Static prop/item models (crab claw, lizard tail drop,
    # bottled wisps, field journal) legitimately have no animations at all - Blockbench exports
    # them with an empty animation list. Only require idle/walk/death when the model HAS
    # animations (so a real animal that lost one clip still fails), and report pure-static
    # models as a summary instead of issues.
    anims = data.get("animations", [])
    if isinstance(anims, dict):
        anims = [dict(v, name=k) for k, v in anims.items()]
    anim_names = [a.get("name", "") for a in anims]
    if anims:
        for req in ["idle", "walk", "death"]:
            if req not in anim_names:
                issues.append(f"missing animation '{req}'")
    else:
        STATIC_MODELS.append(name)  # static look (crab claw, bottled wisps, ...) - not an issue

    for anim in anims:
        aname = anim.get("name", "?")
        length = anim.get("length", 0)
        if length <= 0:
            issues.append(f"animation '{aname}' has zero/negative length")
            continue

        # Check keyframes
        for bone_uuid, channels in anim.get("animators", {}).items():
            if bone_uuid not in all_bones and bone_uuid != "00000000-0000-0000-0000-000000000000":
                issues.append(f"animation '{aname}': keyframe on unknown bone {bone_uuid[:8]}")
            # Check keyframe timing
            if isinstance(channels, dict):
                for channel_name, kf_list in channels.items():
                    if not isinstance(kf_list, list):
                        continue
                    for kf in kf_list:
                        if not isinstance(kf, dict):
                            continue
                        time = kf.get("time", 0)
                        if time > length + 0.001:
                            issues.append(f"animation '{aname}': keyframe at {time}s exceeds clip length {length}s")
                        if time < 0:
                            issues.append(f"animation '{aname}': negative keyframe time {time}")

    # Check element bounds. make_animals._fit: the Blockbench export shifts x/z by +8 (P = c + 8),
    # so the vanilla item-model limit (-16..32 in exported space) means -24..24 in raw bbmodel
    # coordinates on x/z, while y keeps the direct -16..32 window. Checking the raw -16..32 on x/z
    # was the pre-2026-11-01 convention and flagged 283 working models (bear fur tips, dragon
    # wing ends, ...).
    for el in elements:
        from_v = el.get("from", [0, 0, 0])
        to_v = el.get("to", [0, 0, 0])
        for i in range(3):
            lim_lo, lim_hi = (-16.0, 32.0) if i == 1 else (-24.0, 24.0)
            if from_v[i] < lim_lo or from_v[i] > lim_hi or to_v[i] < lim_lo or to_v[i] > lim_hi:
                issues.append(f"element '{el.get('name', '?')}' out of bounds on axis {i}: from={from_v[i]} to={to_v[i]}")
                break

    # Check for missing textures
    textures = data.get("textures", [])
    tex_names = {t.get("name", "") for t in textures}
    for el in elements:
        faces = el.get("faces", {})
        for face_name, face_data in faces.items():
            tex = face_data.get("texture", "")
            if tex and tex.startswith("#"):
                tex_ref = tex[1:]
                if tex_ref not in tex_names:
                    issues.append(f"element '{el.get('name', '?')}' references unknown texture '{tex_ref}'")
                    break

    return issues

def main():
    models = sorted(glob.glob(os.path.join(MODELS_DIR, "*.bbmodel")))
    print(f"Auditing {len(models)} models in {MODELS_DIR}")
    print()

    total_issues = 0
    models_with_issues = 0
    issue_summary = {}

    for path in models:
        issues = audit_model(path)
        if issues:
            models_with_issues += 1
            total_issues += len(issues)
            name = os.path.basename(path)
            print(f"  {name}: {len(issues)} issue(s)")
            for issue in issues:
                print(f"    - {issue}")
                issue_summary[issue.split(':')[0] if ':' in issue else issue] = issue_summary.get(issue.split(':')[0] if ':' in issue else issue, 0) + 1
        else:
            pass  # clean

    print()
    print(f"Summary: {len(models)} models, {models_with_issues} with issues, {total_issues} total issues")
    print(f"Static looks (no animation tracks - props/items, OK): {len(STATIC_MODELS)}")
    if STATIC_MODELS:
        print("  " + ", ".join(m.replace(".bbmodel", "") for m in STATIC_MODELS))
    print()
    print("Issue categories:")
    for cat, count in sorted(issue_summary.items(), key=lambda x: -x[1]):
        print(f"  {count:4d}  {cat}")
    sys.exit(1 if total_issues else 0)

if __name__ == "__main__":
    main()
