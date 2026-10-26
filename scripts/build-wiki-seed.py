"""Turns docs/wiki-<name>/*.md into src/lib/wiki-seeds/<name>.json, the pages the
staff "Import <Name> pages" button in the wiki writes. Run it after editing the
markdown, from the repo root: python -I scripts/build-wiki-seed.py [steamworks|kitchen]
(no argument = every docs/wiki-* folder)."""
import glob, json, os, re, sys

NL = chr(10)


def slugify(t):
    return re.sub(r"[^a-z0-9]+", "-", t.lower()).strip("-")


def short(title):
    out = ""
    for w in slugify(title).split("-"):
        if out and len(out) + len(w) + 1 > 26:
            break
        out = out + "-" + w if out else w
    return out


TABLE_HEADS = {"part", "crop", "animal", "prop", "meal"}


def cells(l):
    return [c.strip() for c in l.strip().strip("|").split("|")]


def split_table(pg):
    """A page built around a Part/Crop/Animal/Prop/Meal table: every row becomes a page."""
    lines = pg["content"].splitlines()
    for i in range(len(lines) - 2):
        if lines[i].startswith("|") and lines[i + 1].startswith("|--") and cells(lines[i])[0].lower() in TABLE_HEADS:
            break
    else:
        return [pg]
    head = cells(lines[i])
    j = i + 2
    rows = []
    while j < len(lines) and lines[j].startswith("|"):
        rows.append(cells(lines[j]))
        j += 1
    if len(rows) < 3:
        return [pg]
    children, links = [], []
    for r in rows:
        title = r[0].replace("**", "").strip()
        slug = pg["slug"] + "-" + short(title)
        body = [f"**{h}:** {v}" if k else v for k, (h, v) in enumerate(zip(head[1:], r[1:]), 0)]
        if len(r) == 2:
            body = [r[1]]
        text = NL.join(["* " + b if len(r) > 2 else b for b in body]) + NL + NL + f"Back to [{pg['title']}](/wiki?page={pg['slug']})"
        children.append({"slug": slug, "title": title, "category": pg["category"], "parent": pg["slug"], "content": text + NL})
        first = re.split(r"(?<=[.!?]) ", r[1])[0] if len(r) == 2 else ""
        links.append(f"* [{title}](/wiki?page={slug})" + (f" - {first}" if first else ""))
    idx = lines[:i] + links + lines[j:]
    parent = dict(pg, content=NL.join(idx).strip() + NL)
    return [parent] + children


def split_page(pg):
    """A page with two or more ### sections becomes an index page (intro + a link list
    per ## group) plus one child page per ### section, so the wiki sidebar can open
    a category and list its parts one by one."""
    lines = pg["content"].splitlines()
    mark = "### "
    if sum(1 for l in lines if l.startswith("### ")) < 2:
        if sum(1 for l in lines if l.startswith("## ")) >= 2:
            mark = "## "
        else:
            return split_table(pg)
    intro, children, groups = [], [], []   # groups: (heading line or None, [child index])
    cur = None
    group = None
    for l in lines:
        if l.startswith(mark):
            title = l[len(mark):].strip()
            slug = pg["slug"] + "-" + short(title)
            cur = {"slug": slug, "title": title, "category": pg["category"], "parent": pg["slug"], "body": []}
            children.append(cur)
            if group is None:
                group = [None, []]
                groups.append(group)
            group[1].append(cur)
        elif mark == "### " and l.startswith("## "):
            cur = None
            group = [l, []]
            groups.append(group)
        elif cur is not None:
            cur["body"].append(l)
        elif group is None:
            intro.append(l)
        else:
            group.append(l)  # text under a ## heading before its first ###
    out = []
    idx = list(intro)
    for g in groups:
        if g[0]:
            idx += ["", g[0]]
        idx += [x for x in g[2:]]
        idx += [""] + [f"* [{c['title']}](/wiki?page={c['slug']})" for c in g[1]]
    parent = dict(pg, content=NL.join(idx).strip() + NL)
    out.append(parent)
    for c in children:
        body = NL.join(c["body"]).strip()
        body += f"{NL}{NL}Back to [{pg['title']}](/wiki?page={pg['slug']})"
        out.append({"slug": c["slug"], "title": c["title"], "category": c["category"], "parent": c["parent"], "content": body + NL})
    return out


def build(name):
    pages = []
    for f in sorted(glob.glob(f"docs/wiki-{name}/*.md")):
        raw = open(f, encoding="utf-8").read()
        m = re.match(r"^---\r?\n(.*?)\r?\n---\r?\n(.*)$", raw, re.S)
        meta = {}
        for line in m.group(1).splitlines():
            k, _, v = line.partition(":")
            meta[k.strip()] = v.strip()
        pages.append({"slug": meta["slug"], "title": meta["title"], "category": meta["category"],
                      "parent": meta.get("parent") or None, "content": m.group(2).strip() + "\n"})
    root = next((p["slug"] for p in pages if p["slug"] == name), None) or next((p["slug"] for p in pages if not p["parent"]), None)
    flat = []
    for p in pages:
        if p["parent"] == root:
            p["parent"] = None   # chapters are top-level entries; their parts are the sub-list
        flat += [p] if p["slug"] == root or p["slug"].endswith("-intro") else split_page(p)
    pages = flat
    ids = [("%s-%s" % (name, p["slug"]))[:50] for p in pages]
    assert len(set(ids)) == len(ids), "page ids collide when cut to 50 characters"
    json.dump({"source": f"docs/wiki-{name} (regenerate with scripts/build-wiki-seed.py)", "pages": pages},
              open(f"src/lib/wiki-seeds/{name}.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(name + ":", len(pages), "pages written")


names = sys.argv[1:] or sorted(d[len("docs/wiki-"):] for d in glob.glob("docs/wiki-*") if os.path.isdir(d))
for n in names:
    build(n.replace("\\", "/").split("/")[-1])
