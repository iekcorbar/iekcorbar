# make_trailer.py - headless Blender (5.2) voor de "Sakura and Steam"-trailer.
#   & blender.exe -b -P scripts\make_trailer.py -- <shot> <uit.png>
# shots: sakura | stoom | kerker | lucht
#
# Elke shot is een diorama van EIGEN assets. BELANGRIJK (les 29-10): bb2obj.py
# levert .obj in PIXELS (16 px = 1 blok) - import_obj rekent dat om naar
# meters; anders staat de camera in een 16-meter-blok en render je zwart.
# Stills krijgen in ffmpeg Ken Burns-beweging (make_trailer_animatic.ps1).
import math
import os
import sys

import bpy

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SHOT = args[0] if args else "sakura"
OUT = args[1] if len(args) > 1 else os.path.join(os.getcwd(), f"trailer-{SHOT}.png")

OBJ = os.path.join(os.environ.get("TEMP", "/tmp"), "opencode", "trailer", "obj")


def srgb(h):
    h = h.lstrip("#")
    return tuple((int(h[i:i + 2], 16) / 255.0) ** 2.2 for i in (0, 2, 4))


# ---------- schone doeken ----------
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete()
for blok in (bpy.data.meshes, bpy.data.materials, bpy.data.curves, bpy.data.lights, bpy.data.cameras):
    for b in list(blok):
        if b.users == 0:
            blok.remove(b)

COL = bpy.context.collection


def link(ob):
    COL.objects.link(ob)
    return ob


def ops(fn, **k):
    fn(**k)
    return bpy.context.active_object


def mate(naam, kleur, metallic=0.0, rough=0.5, emissie=None, sterkte=0.0):
    m = bpy.data.materials.new(naam)
    m.use_nodes = True
    b = m.node_tree.nodes.get("Principled BSDF")
    b.inputs["Base Color"].default_value = (*kleur, 1)
    b.inputs["Metallic"].default_value = metallic
    b.inputs["Roughness"].default_value = rough
    if emissie:
        b.inputs["Emission Color"].default_value = (*emissie, 1)
        b.inputs["Emission Strength"].default_value = sterkte
    return m


def geef(ob, m):
    ob.data.materials.clear()
    ob.data.materials.append(m)


PX = 1.0 / 16.0  # bb2obj-pixels -> meters


def import_obj(naam, loc, schaal=1.0, rot=(0, 0, 0), doel_r=None):
    """Obj importeren; bb2obj-pixels worden meters (16 px = 1 blok).

    schaal: veelvoud davon (1.0 = model-grootte in MC-blokken).
    doel_r: als gegeven, forceer langste bbox-rand op zoveel METERS.
    """
    pad = os.path.join(OBJ, naam + ".obj")
    if not os.path.exists(pad):
        raise FileNotFoundError(pad)
    bpy.ops.wm.obj_import(filepath=pad)
    ob = bpy.context.active_object
    # maat in pixels meten via de bounding box (world-hoekpunten)
    hoeken = [ob.matrix_world @ __import__("mathutils").Vector(c) for c in ob.bound_box]
    breed = max(h.x for h in hoeken) - min(h.x for h in hoeken)
    hoog = max(h.z for h in hoeken) - min(h.z for h in hoeken)
    diep = max(h.y for h in hoeken) - min(h.y for h in hoeken)
    langste_px = max(breed, hoog, diep, 1.0)
    if doel_r:
        faktor = doel_r / langste_px
    else:
        faktor = schaal * PX   # 16 px (1 MC-blok) -> 1 m bij schaal 1.0
    ob.scale = (faktor, faktor, faktor)
    ob.rotation_euler = rot
    ob.location = loc
    for m in ob.data.materials:
        if m and m.use_nodes:
            for n in m.node_tree.nodes:
                if n.type == "TEX_IMAGE":
                    try:
                        n.interpolation = "Closest"
                    except Exception:
                        pass
    return ob


def wereld(kleur, sterkte=0.25):
    w = bpy.data.worlds.get("World") or bpy.data.worlds.new("World")
    bpy.context.scene.world = w
    w.use_nodes = True
    bg = w.node_tree.nodes.get("Background")
    bg.inputs[0].default_value = (*kleur, 1)
    bg.inputs[1].default_value = sterkte


def lamp(energie, kleur, maat, positie, richting=None):
    ld = bpy.data.lights.new("lamp", type="AREA")
    ld.energy = energie
    ld.color = kleur
    if hasattr(ld, "size"):
        ld.size = maat
    ob = link(bpy.data.objects.new("lamp", ld))
    ob.location = positie
    import mathutils
    doel = richting or (0, 0, 0)
    d = mathutils.Vector((doel[0] - positie[0], doel[1] - positie[1], doel[2] - positie[2]))
    ob.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    return ob


def camera(loc, doel, lens=50):
    cam = link(bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")))
    cam.data.lens = lens
    cam.location = loc
    import mathutils
    d = mathutils.Vector((doel[0] - loc[0], doel[1] - loc[1], doel[2] - loc[2]))
    cam.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    bpy.context.scene.camera = cam


def vloer(kleur, z=0.0, metallic=0.2, rough=0.5):
    ob = ops(bpy.ops.mesh.primitive_plane_add, size=60, location=(0, 0, z))
    geef(ob, mate("Vloer", srgb(kleur), metallic, rough))
    return ob


# ---------- procedures: takken, bloemen, bloesems ----------
def build_tak(punten, radius, mat):
    """punten: (x, y, z) in meters - de tak mag in 3D door het beeld lopen."""
    cu = bpy.data.curves.new("tak", type="CURVE")
    cu.dimensions = "3D"
    sp = cu.splines.new("POLY")
    sp.points.add(len(punten) - 1)
    for p, (px, py, pz) in zip(sp.points, punten):
        p.co = (px, py, pz, 1)
    cu.bevel_depth = radius
    cu.bevel_resolution = 4
    cu.use_fill_caps = True
    ob = link(bpy.data.objects.new("tak", cu))
    geef(ob, mat)
    return ob


def build_bloem(x, y, z, r, m_bloem, m_hart, hoek=0.0, kant=1.05):
    """Bloem die met het gezicht iets naar de camera (voren/omhoog) kijkt."""
    for k in range(5):
        a = hoek + k * (2 * math.pi / 5)
        p = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=16, ring_count=10,
                radius=r * 0.5, location=(x + math.cos(a) * r * 0.55,
                                          y + math.sin(a) * r * 0.55 * math.cos(kant),
                                          z + math.sin(a) * r * 0.55 * math.sin(kant)))
        p.scale = (0.85, 0.6, 0.16)
        p.rotation_euler = (kant, 0, a + math.pi / 2)
        geef(p, m_bloem)
    h = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=12, ring_count=8, radius=r * 0.16, location=(x, y, z))
    h.scale = (1, 1, 0.6)
    geef(h, m_hart)


def petals_verstrooid(aantal, m_bloem, x0, x1, y0, y1, z0, z1, rmin=0.03, rmax=0.06, zaa=7):
    import random
    rng = random.Random(zaa)
    for _ in range(aantal):
        p = ops(bpy.ops.mesh.primitive_circle_add, vertices=6, radius=rng.uniform(rmin, rmax),
                fill_type="NGON",
                location=(rng.uniform(x0, x1), rng.uniform(y0, y1), rng.uniform(z0, z1)))
        p.rotation_euler = (rng.uniform(0, 3), rng.uniform(0, 3), rng.uniform(0, 3))
        p.scale = (1.0, 0.7, 1.0)
        geef(p, m_bloem)


# ================= SHOTS =================
if SHOT == "sakura":
    # Act 1: donkere lucht, sakura-tak dwars door beeld, vallende bloesems, lantaarn.
    wereld(srgb("120a18"), 0.3)
    vloer("141218", 0.0, 0.3, 0.35)          # nat-assefalt-look
    m_bloem = mate("Bloem", srgb("f2b6c8"), 0.0, 0.4)
    m_hart = mate("Hart", srgb("c46a86"), 0.0, 0.5)
    m_tak = mate("Tak", srgb("3a2417"), 0.0, 0.6)
    build_tak([(-3.0, -1.4, 0.9), (-0.8, -0.3, 1.5), (1.4, 0.4, 2.0), (3.4, 1.3, 2.5), (5.6, 2.6, 3.2)], 0.09, m_tak)
    build_tak([(1.4, 0.4, 2.0), (2.2, -0.2, 2.7), (3.2, -0.5, 3.2)], 0.05, m_tak)
    build_tak([(3.4, 1.3, 2.5), (4.6, 0.9, 3.1)], 0.05, m_tak)
    for (bx, by, bz, br) in ((-0.8, -0.3, 1.6, 0.45), (1.4, 0.4, 2.1, 0.55), (2.2, -0.2, 2.8, 0.42),
                             (3.4, 1.3, 2.6, 0.5), (4.6, 0.9, 3.2, 0.42), (5.6, 2.6, 3.3, 0.5),
                             (0.4, 0.1, 1.85, 0.38), (2.8, 0.9, 2.35, 0.4)):
        build_bloem(bx, by, bz, br, m_bloem, m_hart, hoek=br * 5.0, kant=1.15)
        # kleine zusterbloem naast elke hoofdbloem: voller cluster zoals de badge
        build_bloem(bx + 0.22, by + 0.18, bz + 0.16, br * 0.68, m_bloem, m_hart, hoek=br * 2.2, kant=1.35)
    petals_verstrooid(70, m_bloem, -2.5, 6.0, -2.8, 2.2, 0.4, 4.2)
    # koperen lantaarn rechts-op-achtergrond met warm hart
    lan = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=24, radius=0.22, depth=0.5, location=(5.0, -1.6, 0.7))
    geef(lan, mate("Lantern", srgb("7a4a1e"), 0.8, 0.4))
    gloei = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=24, radius=0.16, depth=0.36, location=(5.0, -1.6, 0.72))
    geef(gloei, mate("Gloed", srgb("ffce7a"), 0.0, 0.4, emissie=(1.0, 0.72, 0.32), sterkte=30))
    staap = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=12, radius=0.03, depth=0.7, location=(5.0, -1.6, 0.32))
    geef(staap, m_tak)
    lamp(750, (1.0, 0.87, 0.68), 3.0, (1.5, -4.5, 5.0), (2.5, 0.6, 2.0))
    lamp(950, (0.55, 0.7, 1.0), 2.4, (-4.0, 4.5, 3.5), (2.5, 0.6, 2.2))
    lamp(140, (1.0, 0.66, 0.3), 0.6, (5.0, -1.6, 1.3), (4.0, 0.0, 1.6))
    camera((-3.6, -4.8, 1.7), (3.2, 0.6, 2.2), lens=45)

elif SHOT == "stoom":
    # Act 2: hoekje stoomfabriek - andesieten wand, messing paneel, balgventilator, ketel.
    wereld(srgb("0a0a0e"), 0.3)
    vloer("101014", 0.0, 0.5, 0.35)
    import random
    rng = random.Random(3)
    m_staal = mate("Staal", srgb("5a5f68"), 0.9, 0.4)
    # alle kasings-blocken op exact 1,05 m genormaliseerd (doel_r), dan pas gelegd
    # wand ACHTER de machines (camera kijkt richting +y): muur op y=+2.2
    for i in range(8):
        for j in range(3):
            import_obj("sw_andesite_casing", (i * 1.08 - 3.8, 2.2, j * 1.08 + 0.54),
                       doel_r=1.05, rot=(0, 0, rng.choice((0, math.pi / 2, math.pi, -math.pi / 2))))
    for i in range(3):
        import_obj("sw_brass_casing", (i * 1.08 + 5.0, 2.2, 0.54), doel_r=1.05)
    # ventilator-front naar de camera (y=-2.2)
    import_obj("sw_bellows_fan_casing", (0.2, -2.2, 1.6), doel_r=2.3, rot=(0, 0, -math.pi / 2))
    import_obj("sw_bellows_fan_rotor", (0.2, -1.2, 1.6), doel_r=2.2, rot=(0, 0, -math.pi / 2))
    ketel = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=48, radius=0.85, depth=1.7, location=(3.0, -0.6, 0.85))
    geef(ketel, mate("Koper", srgb("b57a3c"), 1.0, 0.35))
    deksel = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=48, radius=0.5, depth=0.18, location=(3.0, -0.6, 1.78))
    geef(deksel, m_staal)
    pijp = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=24, radius=0.1, depth=1.3, location=(3.6, -0.6, 2.3))
    geef(pijp, mate("Pijp", srgb("6b6e73"), 1.0, 0.4))
    m_stoom = mate("Stoom", srgb("cfd6de"), 0.0, 0.9)
    for i in range(6):
        s = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=14, ring_count=10, radius=0.16 + i * 0.05,
                location=(3.62 + rng.uniform(-0.1, 0.15), -0.6 + rng.uniform(-0.15, 0.1), 3.0 + i * 0.34))
        geef(s, m_stoom)
        s.scale = (1.0, 0.9, 0.8)
    lamp(900, (1.0, 0.9, 0.72), 3.0, (0.5, -7.5, 4.6), (0.5, 0.4, 1.2))
    lamp(1500, (0.5, 0.66, 1.0), 2.6, (-7.0, 4.5, 3.6), (0.4, 1.0, 1.4))
    lamp(260, (1.0, 0.72, 0.38), 1.2, (3.2, -2.4, 2.8), (3.0, -0.6, 1.4))
    camera((-1.4, -9.0, 2.1), (1.2, 0.8, 1.3), lens=40)

elif SHOT == "kerker":
    # Act 3: donkere dungeon - lantarentuik + wraith tussen puin, zwevende gloeibollen.
    wereld(srgb("050408"), 0.15)
    vloer("0c0a10", 0.0, 0.1, 0.8)
    import random
    rng = random.Random(11)
    for i in range(6):
        import_obj("sw_andesite_casing", (rng.uniform(-4.0, 4.5), rng.uniform(-1.0, 3.2), 0.53),
                   doel_r=1.06, rot=(0, 0, rng.uniform(0, 3)))
    # rotswand achtergrond zodat de rim-light iets vangt
    m_rots = mate("Rots", srgb("2e2a35"), 0.05, 0.9)
    for i in range(6):
        r = ops(bpy.ops.mesh.primitive_ico_sphere_add, subdivisions=1, radius=rng.uniform(1.4, 2.6),
                location=(rng.uniform(-5, 6), rng.uniform(5.5, 7.5), rng.uniform(0.5, 3)))
        r.scale = (1.2, 0.7, 1.1)
        geef(r, m_rots)
    # entity-modellen lezen slecht in-close (zie 29-10 proef): sfeer-monsters i.p.v.
    # imports - lang wraith-silhouet met ogen + lage mounds (kopjes) met oogparen.
    m_nul = mate("Nul", srgb("07060a"), 0.0, 0.95)
    m_ogen = mate("Ogen", srgb("ffb347"), 0.0, 0.4, emissie=(1.0, 0.62, 0.18), sterkte=16)
    wraith = ops(bpy.ops.mesh.primitive_cone_add, vertices=24, radius1=0.85, radius2=0.25, depth=3.4,
                 location=(-2.4, 2.2, 1.6))
    wraith.scale = (0.8, 0.55, 1.0)
    geef(wraith, m_nul)
    w_kop = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=16, ring_count=10, radius=0.34, location=(-2.4, 2.2, 3.2))
    w_kop.scale = (0.9, 0.7, 1.0)
    geef(w_kop, m_nul)
    for off in (-0.12, 0.12):
        oog = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=10, ring_count=8, radius=0.055,
                  location=(-2.4 + off, 1.92, 3.25))
        geef(oog, m_ogen)
    for (mx, my, mr) in ((1.9, 0.8, 1.1), (3.1, -0.2, 0.75)):
        bult = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=16, ring_count=10, radius=mr, location=(mx, my, mr * 0.45))
        bult.scale = (1.25, 0.9, 0.55)
        geef(bult, m_nul)
        for off in (-mr * 0.22, mr * 0.22):
            oog = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=10, ring_count=8, radius=0.05,
                      location=(mx + off, my - mr * 0.78, mr * 0.62))
            geef(oog, m_ogen)
    m_gloed = mate("Gloed", srgb("ffd27a"), 0.0, 0.4, emissie=(1.0, 0.74, 0.3), sterkte=9)
    for (x, y, z) in ((1.0, -0.6, 2.0), (2.6, 0.0, 2.5), (1.8, 1.1, 1.7), (-2.2, 1.4, 2.8)):
        l = ops(bpy.ops.mesh.primitive_ico_sphere_add, subdivisions=2, radius=0.15, location=(x, y, z))
        geef(l, m_gloed)
    lamp(240, (1.0, 0.8, 0.55), 1.6, (1.6, -2.8, 3.2), (1.6, 0.4, 0.8))
    lamp(620, (0.45, 0.6, 1.0), 2.2, (-5.0, -1.5, 4.0), (-2.8, 2.6, 1.4))
    camera((0.6, -7.2, 2.3), (1.0, 0.8, 0.9), lens=46)

elif SHOT == "lucht":
    # Act 4: zepelin-draak + luchtschip boven zwevend eiland, rozé-gouden lucht.
    wereld(srgb("2a1826"), 0.6)
    import random
    rng = random.Random(5)
    m_rots = mate("Rots", srgb("4a4453"), 0.1, 0.8)
    for i in range(14):
        a = rng.uniform(0, 6.28)
        r = rng.uniform(0.3, 3.0)
        b = ops(bpy.ops.mesh.primitive_ico_sphere_add, subdivisions=1, radius=rng.uniform(0.7, 1.5),
                location=(math.cos(a) * r * 0.8, math.sin(a) * r * 0.5, -rng.uniform(0.3, 2.2) - abs(math.sin(a)) * 0.6))
        b.scale = (1.0, 1.0, 0.7)
        geef(b, m_rots)
    gras = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=48, radius=3.2, depth=0.4, location=(0, 0, 0.05))
    geef(gras, mate("Gras", srgb("3f5d3a"), 0.0, 0.8))
    import_obj("em_zeppelin_dragon", (4.4, 2.6, 5.4), doel_r=6.8,
               rot=(math.radians(-8), math.radians(12), math.radians(38)))
    import_obj("em_airship_ghast", (-4.6, 5.2, 7.6), doel_r=5.5, rot=(0, 0, math.radians(-125)))
    lamp(2600, (1.0, 0.7, 0.5), 7.0, (-10.0, -18.0, 10.0), (0, 0, 3.0))
    lamp(900, (1.0, 0.75, 0.55), 4.0, (-7.0, -5.0, 6.5), (3.0, 1.4, 4.4))
    lamp(700, (0.85, 0.6, 0.9), 5.0, (14.0, 8.0, 12.0), (3.0, 1.0, 4.5))
    camera((-4.6, -13.0, 1.6), (0.9, 1.6, 3.2), lens=35)

else:
    raise SystemExit(f"onbekende shot: {SHOT}")

# ---------- render ----------
sc = bpy.context.scene
sc.render.resolution_x = 1600
sc.render.resolution_y = 900
for engine in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
    try:
        sc.render.engine = engine
        break
    except Exception:
        continue
try:
    sc.eevee.taa_render_samples = 48
except Exception:
    pass
for probe in ("use_raytracing", "use_gtao"):
    try:
        setattr(sc.eevee, probe, True)
    except Exception:
        pass
try:
    sc.view_settings.view_transform = "Filmic"
except Exception:
    pass
sc.render.image_settings.file_format = "PNG"
sc.render.filepath = OUT
bpy.ops.render.render(write_still=True)
print("SHOT KLAAR:", SHOT, "->", OUT)
