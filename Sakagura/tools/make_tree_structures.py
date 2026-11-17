"""Sakagura orchard trees (0.8.0): the REAL-tree shapes.

Every species gets three voxel structures - sprout, young tree, full tree - made of actual
log and leaf blocks, plus a list of fruit slots (leaf cells where a ripe fruit hangs as an
ItemDisplay). The plugin pastes these straight into the world (see Orchard.java), so what
you walk through is a real trunk of real leaves, chopped with a real axe like vanilla.

Names must match Tree.java. Shapes are deterministic (seeded per species), so rebuilding
this file twice gives the same trees.yml.

Usage (Sakagura folder): python -I tools/make_tree_structures.py
Writes: src/main/resources/trees.yml
"""
import math, os, random, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "trees.yml")

# (id, trunk material, leaf material, trunk height, canopy layers [(y, radius)...] top-down
#  order = lowest layer first, arms (cherry-style side branches) or None, fruit slot count)
# Petals/leafFall stay in Tree.java; fruit ids stay in Fruit.java - here it is only shape.
SPECIES = [
    ("sakura",  "CHERRY_LOG", "CHERRY_LEAVES",          5, [(5, 3), (6, 2)], "arms",  5),
    ("ume",     "BIRCH_LOG",  "FLOWERING_AZALEA_LEAVES", 4, [(4, 2), (5, 2)], "gnarl", 4),
    ("apple",   "OAK_LOG",    "OAK_LEAVES",              4, [(4, 2), (5, 2), (6, 1)], "cross", 5),
    ("orange",  "JUNGLE_LOG", "JUNGLE_LEAVES",           6, [(5, 2), (6, 2), (7, 1)], None, 4),
    ("maple",   "DARK_OAK_LOG", "DARK_OAK_LEAVES",       6, [(6, 3), (7, 2), (8, 1)], None, 0),
    ("cherry",  "CHERRY_LOG", "CHERRY_LEAVES",           6, [(6, 3), (7, 2)], "arms",  6),
    ("peach",   "OAK_LOG",    "FLOWERING_AZALEA_LEAVES", 4, [(4, 2), (5, 1)], "gnarl", 4),
    ("lemon",   "BIRCH_LOG",  "AZALEA_LEAVES",           4, [(4, 2), (5, 1)], "cross", 4),
    ("fig",     "DARK_OAK_LOG", "JUNGLE_LEAVES",         5, [(5, 3), (6, 1)], None,    4),
    ("walnut",  "DARK_OAK_LOG", "BIRCH_LEAVES",          7, [(6, 3), (7, 3), (8, 2)], None, 5),
]


def circle_cells(r):
    """A rounded blob: Chebyshev-square minus the pointy corners (vanilla-ish foliage)."""
    out = []
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = math.hypot(dx, dz)
            if d <= r + 0.35:
                out.append((dx, dz))
    return out


def full_tree(trunk, leaf, h, layers, arms, rnd):
    """Return {(x,y,z): material} for the mature tree with the trunk base at (0,0,0)."""
    blocks = {}
    for y in range(0, h + 1):
        blocks[(0, y, 0)] = trunk
    for (y, r) in layers:
        for (dx, dz) in circle_cells(r):
            if dx == 0 and dz == 0:
                continue  # the trunk holds the centre
            blocks[(dx, y, dz)] = leaf
    # organic edge: drop a few outer leaves (never a whole column)
    bottom = min(y for (y, _r) in layers)
    edge = [c for c, m in blocks.items() if m == leaf and abs(c[0]) + abs(c[2]) >= 2]
    for c in edge:
        if rnd.random() < 0.14:
            neighbours = sum(1 for d in ((1, 0), (-1, 0), (0, 1), (0, -1))
                             if (c[0] + d[0], c[1], c[2] + d[1]) in blocks)
            if neighbours <= 2 and rnd.random() < 0.5:
                del blocks[c]
    if arms == "arms":  # cherry: two short horizontal arms, leaves hanging under them
        for d in ((1, 0), (0, 1)) if rnd.random() < 0.5 else ((-1, 0), (0, -1)):
            blocks[(d[0], h - 1, d[1])] = trunk
            blocks[(2 * d[0], h - 1, 2 * d[1])] = trunk
            for dx in (d[0], 2 * d[0]):
                for dz in (d[1], 2 * d[1]):
                    blocks[(dx, h, dz)] = leaf
                    blocks[(dx, h - 2, dz)] = leaf
    if arms == "cross":  # oak: one stub branch on two sides at canopy level
        for d in ((1, 0), (0, -1)):
            blocks[(d[0], h - 1, d[1])] = trunk
            blocks[(2 * d[0], h - 1, 2 * d[1])] = leaf
    if arms == "gnarl":  # plum/peach: one crooked side branch
        d = (1, 1) if rnd.random() < 0.5 else (-1, -1)
        blocks[(d[0], h - 1, d[1])] = trunk
        blocks[(2 * d[0], h, 2 * d[1])] = leaf
    # a treetop knot of leaves above the highest layer
    top = max(y for (y, _r) in layers)
    for c in circle_cells(1):
        blocks.setdefault((c[0], top + 1, c[1]), leaf)
    return blocks


def fruit_slots(blocks, leaf, count, rnd):
    """Pick leaf cells on the lowest two canopy levels, spaced >= 2 apart (fruit hangs there)."""
    leafs = [c for c, m in blocks.items() if m == leaf]
    min_y = min(c[1] for c in leafs)
    cand = sorted([c for c in leafs if c[1] <= min_y + 1 and abs(c[0]) + abs(c[2]) >= 1],
                  key=lambda c: (c[1], math.hypot(c[0], c[2]), c[0], c[2]))
    rnd.shuffle(cand)
    out = []
    for c in cand:
        if all(math.dist(c, o) >= 1.9 for o in out):
            out.append(c)
        if len(out) >= count:
            break
    # when the blob is small, relax the spacing rather than hand out fewer fruits
    if len(out) < count:
        for c in cand:
            if c not in out:
                out.append(c)
            if len(out) >= count:
                break
    return out[:count]


def young_tree(trunk, leaf, h, rnd):
    blocks = {}
    for y in range(0, h - 1):
        blocks[(0, y, 0)] = trunk
    for (dx, dz) in circle_cells(1):
        blocks[(dx, h - 1, dz)] = leaf
    blocks[(0, h, 0)] = leaf
    return blocks


def sprout(trunk, leaf, rnd):
    return {(0, 0, 0): trunk, (0, 1, 0): leaf,
            (1 if rnd.random() < 0.5 else -1, 1, 0): leaf,
            (0, 1, 1 if rnd.random() < 0.5 else -1): leaf}


def write_yml(trees):
    out = ["# Generated by tools/make_tree_structures.py - do not edit by hand.",
           "# Real-block orchard trees (0.8.0): relative cells per growth stage, trunk base (0,0,0).",
           "# fruit = leaf cells where a ripe fruit hangs (Orchard.java). Order = species ids of Tree.java.", ""]
    for tid, stages, slots in trees:
        out.append(f"{tid}:")
        for i, blocks in enumerate(stages):
            out.append(f"  stage{i}:")
            for c, m in sorted(blocks.items()):
                out.append(f"    - [{c[0]}, {c[1]}, {c[2]}, {m}]")
        out.append("  fruit:")
        for c in slots:
            out.append(f"    - [{c[0]}, {c[1]}, {c[2]}]")
        out.append("")
    with open(OUT, "w", newline="\n") as f:
        f.write("\n".join(out))


def main():
    trees = []
    for tid, trunk, leaf, h, layers, arms, nfruit in SPECIES:
        rnd = random.Random(sum(map(ord, tid)) * 7 + len(tid))
        big = full_tree(trunk, leaf, h, layers, arms, rnd)
        young = young_tree(trunk, leaf, max(3, h // 2 + 1), rnd)
        seed = sprout(trunk, leaf, rnd)
        slots = fruit_slots(big, leaf, nfruit, rnd) if nfruit else []
        # sanity: every fruit slot is a leaf cell of the mature tree, heights stay buildable
        for c in slots:
            assert big.get(c) == leaf, f"{tid}: fruit slot {c} is not a leaf"
        assert max(c[1] for c in big) <= 10, f"{tid}: too tall"
        assert all(abs(c[0]) <= 4 and abs(c[2]) <= 4 for c in big), f"{tid}: too wide"
        assert (0, 0, 0) in big and big[(0, 0, 0)] == trunk, f"{tid}: trunk base missing"
        assert (0, 0, 0) in young and (0, 0, 0) in seed, f"{tid}: stage bases"
        trees.append((tid, [seed, young, big], slots))
    write_yml(trees)
    print(f"wrote {os.path.relpath(OUT, os.path.join(HERE, '..'))}: " +
          ", ".join(f"{t[0]} ({len(t[1][2])} blocks/{len(t[2])} fruit)" for t in trees))


if __name__ == "__main__":
    sys.exit(main())
