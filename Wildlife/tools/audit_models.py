#!/usr/bin/env python3
"""Audit all wildlife bbmodels for animation and structure issues."""
import json, os, sys, glob

MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "fmm-models", "wildlife")

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

    # Check animations
    anims = data.get("animations", [])
    anim_names = [a.get("name", "") for a in anims]

    # Required animations for animals
    required = ["idle", "walk", "death"]
    for req in required:
        if req not in anim_names:
            issues.append(f"missing animation '{req}'")

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
            for channel, keyframes in channels.items() if isinstance(channels, dict) else []:
                pass
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

    # Check element bounds
    for el in elements:
        from_v = el.get("from", [0, 0, 0])
        to_v = el.get("to", [0, 0, 0])
        for i in range(3):
            if from_v[i] < -16 or from_v[i] > 32 or to_v[i] < -16 or to_v[i] > 32:
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
    print()
    print("Issue categories:")
    for cat, count in sorted(issue_summary.items(), key=lambda x: -x[1]):
        print(f"  {count:4d}  {cat}")

if __name__ == "__main__":
    main()
