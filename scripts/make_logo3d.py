# make_logo3d.py - draait headless in Blender (5.2):
#   & 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' -b -P scripts\make_logo3d.py -- still <afemap>
#   ... -- anim <afemap>   -> PNG-frames, daarna montage met ffmpeg (buiten Blender)
#
# Het ECHTE iekcorbar-merk in 3D: de donkere badge met koperen rand, de boog
# "IEKCORBAR" in schreefletters, het tandwiel links en de sakura-tak rechts -
# als fysiek voorwerp: lak-kopje, geëxtrudeerd messing, bloemblaadjes met een
# zacht subsurface, stoom en gouden embers eromheen. Modus still = oordeelbeeld.
#
# Belangrijk (les 28-10): het anker-tegeltje is alleen het StaffHub-APP-icoon,
# niet het merk. Dit bestand bouwt het merk na aan de hand van de badge.
import math
import os
import sys

import bpy

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
MODE = args[0] if args else "still"
OUTDIR = args[1] if len(args) > 1 else os.getcwd()
os.makedirs(OUTDIR, exist_ok=True)

# ---------- huisstijl uit de badge ----------
def srgb(h):
    h = h.lstrip("#")
    return tuple((int(h[i:i + 2], 16) / 255.0) ** 2.2 for i in (0, 2, 4))

C_LAK = srgb("100d15")      # donker badge-navy (in proef v3 te grijs onder licht)
C_MESSING = srgb("b57a3c")   # koper/goud van rand, letters, tandwiel
C_BLOEM = srgb("f2b6c8")    # roze sakura (v3 waste wit)
C_HART = srgb("c46a86")     # dieper roze bloemhart
C_TAK = srgb("3a2417")      # bruine tak
C_WERELD = srgb("0a0a10")

# serif-lettertype van Windows (dichtst bij de koperen schreefletters van de badge)
FONTDATA = None
for fp in (r"C:\Windows\Fonts\georgiab.ttf", r"C:\Windows\Fonts\timesbd.ttf", r"C:\Windows\Fonts\arialbd.ttf"):
    if os.path.exists(fp):
        try:
            FONTDATA = bpy.data.fonts.load(fp)
            break
        except Exception:
            continue

# ---------- leeg beginnen ----------
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

def mate(naam, kleur, metallic=0.0, rough=0.5, sussurface=0.0):
    m = bpy.data.materials.new(naam)
    m.use_nodes = True
    b = m.node_tree.nodes.get("Principled BSDF")
    b.inputs["Base Color"].default_value = (*kleur, 1)
    b.inputs["Metallic"].default_value = metallic
    b.inputs["Roughness"].default_value = rough
    for mog in ("Subsurface Weight", "Subsurface"):
        if mog in b.inputs:
            b.inputs[mog].default_value = sussurface
            break
    return m

def geef(ob, m):
    ob.data.materials.clear()
    ob.data.materials.append(m)

m_lak = mate("Lak", C_LAK, 0.1, 0.28)
m_mes = mate("Messing", C_MESSING, 1.0, 0.26)
m_bloem = mate("Bloem", C_BLOEM, 0.0, 0.45, 0.25)
m_hart = mate("Hart", C_HART, 0.0, 0.5, 0.3)
m_tak = mate("Tak", C_TAK, 0.0, 0.6)

# ---------- de badge (opgebouwd plat in XY, dikte +Z; gaat later rechtop) ----------
badge = link(bpy.data.objects.new("BADGE", None))

def aan(ob):
    ob.parent = badge
    ob.matrix_parent_inverse = badge.matrix_world.inverted()

schijf = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=128, radius=1.62, depth=0.16, location=(0, 0, 0.08))
geef(schijf, m_lak); aan(schijf)
rand = ops(bpy.ops.mesh.primitive_torus_add, major_radius=1.62, minor_radius=0.055,
           major_segments=128, minor_segments=16, location=(0, 0, 0.16))
geef(rand, m_mes); aan(rand)
# De dunne messing-boog van de badge: een sliert die ONDER de letters door
# loopt (start halverwege links-omhoog, zwaait rechts naar beneden).
def boog(stap, straal, draai, midden, segmenten=42):
    cu = bpy.data.curves.new("boog", type="CURVE")
    cu.dimensions = "2D"
    sp = cu.splines.new("POLY")
    sp.points.add(segmenten)
    for p in range(segmenten + 1):
        a = math.radians(draai + stap * p)
        x, y = midden[0] + straal * math.cos(a), midden[1] + straal * math.sin(a)
        sp.points[p].co = (x, y, 0, 1)
    cu.bevel_depth = 0.011
    cu.bevel_resolution = 3
    cu.use_fill_caps = True
    ob = link(bpy.data.objects.new("boog", cu))
    ob.location = (0, 0, 0.168)
    geef(ob, m_mes); aan(ob)

boog(2.8, 1.44, 140, (0, 0))   # 140° langs de linkerrand omlaag naar ~257°

# ---------- letters langs de boog: één voor één (geen pad-volging, wel exact) ----------
woord = "IEKCORBAR"
for i, ch in enumerate(woord):
    hoek = math.radians(126 - i * 72.0 / (len(woord) - 1))
    fd = bpy.data.curves.new("letter", type="FONT")
    fd.body = ch
    fd.size = 0.30
    fd.align_x = "CENTER"
    fd.align_y = "CENTER"
    fd.extrude = 0.022
    if FONTDATA:
        fd.font = FONTDATA
    ob = link(bpy.data.objects.new("letter_" + ch, fd))
    ob.location = (1.40 * math.cos(hoek), 1.40 * math.sin(hoek), 0.175)
    ob.rotation_euler = (0, 0, hoek - math.pi / 2)
    geef(ob, m_mes)
    aan(ob)

# ---------- tandwiel: 6 brede platte tanden, donker gat met geel binnenwerk ----------
m_goud = mate("Goud", srgb("d9a038"), 1.0, 0.30)

def tandwiel(x, y, r):
    wiel = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=64, radius=r * 0.78, depth=0.12, location=(x, y, 0.20))
    geef(wiel, m_mes); aan(wiel)
    for k in range(6):
        a = k * math.pi / 3 + math.radians(12)
        t = ops(bpy.ops.mesh.primitive_cube_add, size=1, location=(x + math.cos(a) * r * 0.86, y + math.sin(a) * r * 0.86, 0.20))
        t.scale = (r * 0.42, r * 0.30, 0.12)
        t.rotation_euler = (0, 0, a)
        geef(t, m_mes); aan(t)
    rand_gat = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=48, radius=r * 0.42, depth=0.14, location=(x, y, 0.20))
    geef(rand_gat, m_lak); aan(rand_gat)      # donkere krans om het gele oog
    oog = ops(bpy.ops.mesh.primitive_cylinder_add, vertices=48, radius=r * 0.26, depth=0.16, location=(x, y, 0.20))
    geef(oog, m_goud); aan(oog)               # geel/oranje hartje als in de badge

tandwiel(-0.72, -0.28, 0.48)

# ---------- sakura: tak, bloemen, knoppen ----------
def bocht(punten, radius, mat, z=0.175):
    cu = bpy.data.curves.new("tak", type="CURVE")
    cu.dimensions = "2D"
    sp = cu.splines.new("POLY")
    sp.points.add(len(punten) - 1)
    for p, (x, y) in zip(sp.points, punten):
        p.co = (x, y, 0, 1)
    cu.bevel_depth = radius
    cu.bevel_resolution = 4
    cu.use_fill_caps = True
    ob = link(bpy.data.objects.new("tak", cu))
    ob.location = (0, 0, z)
    geef(ob, mat)
    aan(ob)
    return ob

bocht([(0.15, 1.48), (0.44, 0.96), (0.56, 0.34), (0.78, -0.26), (0.58, -0.92), (0.34, -1.38)], 0.032, m_tak)
bocht([(0.56, 0.34), (0.96, 0.58)], 0.020, m_tak)             # zijtakje naar rechtsboven
bocht([(0.78, -0.26), (1.12, -0.44)], 0.020, m_tak)           # zijtakje naar rechtsonder

def bloem(x, y, r, rot=0.0):
    """Sakura: vijf brede blaadjes die elkaar net raken rond een donker hart.
    v5b: blaadjes kleiner (0.50r) - de 0.66r-make-up was een moeras van wolken
    dat de tekst opslokte."""
    for k in range(5):
        a = rot + k * (2 * math.pi / 5)
        p = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=20, ring_count=12, radius=r * 0.50,
                location=(x + math.cos(a) * r * 0.58, y + math.sin(a) * r * 0.58, 0.165))
        p.scale = (0.82, 0.66, 0.16)
        p.rotation_euler = (0, 0, a + math.pi / 2)
        geef(p, m_bloem); aan(p)
    h = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=16, ring_count=10, radius=r * 0.20, location=(x, y, 0.175))
    h.scale = (1, 1, 0.6)
    geef(h, m_hart); aan(h)

# bloesems langs de tak, als in de badge: volle cluster rechts, niet bovenaan
bloem(0.30, 1.02, 0.36)
bloem(0.72, 0.72, 0.30)
bloem(0.58, 0.16, 0.38)
bloem(1.00, -0.12, 0.28)
bloem(0.70, -0.58, 0.34)
bloem(0.40, -1.05, 0.30)
# losse kleine bloempjes linksboven (zwevende accenten in de badge)
for bx, by, br in ((-0.98, 0.58, 0.14), (0.96, 1.06, 0.12)):
    for k in range(5):
        a = k * (2 * math.pi / 5) + 0.4
        p = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=12, ring_count=8, radius=br * 0.5,
                location=(bx + math.cos(a) * br * 0.7, by + math.sin(a) * br * 0.7, 0.17))
        p.scale = (0.9, 0.55, 0.12)
        p.rotation_euler = (0, 0, a)
        geef(p, m_bloem); aan(p)
for bx, by in ((0.44, 0.62), (0.66, -0.30)):                  # knoppen
    kn = ops(bpy.ops.mesh.primitive_uv_sphere_add, segments=12, ring_count=8, radius=0.075, location=(bx, by, 0.20))
    kn.scale = (0.7, 0.7, 1.1)
    geef(kn, m_hart); aan(kn)

# ---------- badge rechtop richting camera ----------
badge.rotation_euler = (math.pi / 2, 0, math.radians(-8))
badge.location = (0, 0, 0.05)

print("BADGE-OPBOUW:", len(bpy.data.objects), "objecten")

# ---------- (geen stoom/embers: het merk ZELF is de held - 29-10 besluit) ----------
stoom = []
embers = []

# ---------- vloer + wereld + lampen ----------
m_vloer = mate("Vloer", srgb("0d0f16"), 0.35, 0.30)
vloer = ops(bpy.ops.mesh.primitive_plane_add, size=26, location=(0, 0, -1.75))
geef(vloer, m_vloer)

wereld = bpy.data.worlds.get("World") or bpy.data.worlds.new("World")
bpy.context.scene.world = wereld
wereld.use_nodes = True
wb = wereld.node_tree.nodes.get("Background")
wb.inputs[0].default_value = (*C_WERELD, 1)
wb.inputs[1].default_value = 0.35

def lamp(naam, energie, kleur, maat, positie):
    ld = bpy.data.lights.new(naam, type="AREA")
    ld.energy = energie
    ld.color = kleur
    if hasattr(ld, "size"):
        ld.size = maat
    ob = link(bpy.data.objects.new(naam, ld))
    ob.location = positie
    import mathutils
    ob.rotation_euler = mathutils.Vector((-positie[0], -positie[1], 0.1 - positie[2])).to_track_quat("-Z", "Y").to_euler()

lamp("key", 480, (1.0, 0.95, 0.88), 3.0, (2.6, -3.0, 2.6))
lamp("rim", 620, (0.55, 0.70, 1.0), 2.2, (-2.8, 2.4, 1.6))
lamp("fill", 110, (0.85, 0.9, 1.0), 4.0, (0, -4.2, 0.2))

# ---------- camera ----------
cam = link(bpy.data.objects.new("Cam", bpy.data.cameras.new("Cam")))
cam.data.lens = 50
cam.location = (0, -9.9, 0.55)
target = link(bpy.data.objects.new("Target", None))
target.location = (0, 0, 0.0)
c = cam.constraints.new("TRACK_TO")
c.target = target
c.track_axis = "TRACK_NEGATIVE_Z"
c.up_axis = "UP_Y"
bpy.context.scene.camera = cam

# ---------- animatie ----------
sc = bpy.context.scene
sc.render.fps = 24
if MODE == "anim":
    sc.frame_start, sc.frame_end = 1, 96
    base_z = badge.rotation_euler.z
    voor = 0.0
    for fr, draai in ((1, -0.30), (48, 0.30), (96, -0.30)):
        badge.rotation_euler = (math.pi / 2, 0, base_z + draai)
        badge.keyframe_insert("rotation_euler", frame=fr)
    for fc in badge.animation_data.action.fcurves:
        for kp in fc.keyframe_points:
            kp.interpolation = "BEZIER"
            kp.easing = "EASE_IN_OUT"
    for i, ob in enumerate(stoom):
        voor = ob.location.z
        for fr, (s, z) in {1: (0.55, voor - 0.3), 72: (1.15, voor + 0.5), 97: (1.5, voor + 1.0)}.items():
            ob.scale = (s, s * 0.7, s * 1.3)
            ob.location.z = z
            ob.keyframe_insert("scale", frame=fr)
            ob.keyframe_insert("location", frame=fr)
    for i, ob in enumerate(embers):
        start = 8 + i * 11
        ob.keyframe_insert("location", frame=start)
        ob.location.z += 1.9
        ob.keyframe_insert("location", frame=start + 52)
        ob.scale = (0.01, 0.01, 0.01)
        ob.keyframe_insert("scale", frame=start + 52)
else:
    sc.frame_set(1)

# ---------- render ----------
sc.render.resolution_x = 1536
sc.render.resolution_y = 1536
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

# zachte sluier-gloed (Blender 5.x: NodeGroupOutput as einde, Glare.type lezen we
# defensively - lukt het niet, dan draait de render gewoon zonder gloed)
sc.use_nodes = True
try:
    ct = sc.node_tree
except Exception:
    ct = None
if ct is None:
    try:
        ct = bpy.data.node_groups.new("composiet", "CompositorNodeTree")
        sc.compositing_node_group = ct
    except Exception:
        ct = None
if ct:
    rl = ct.nodes.new("CompositorNodeRLayers")
    comp = ct.nodes.new("NodeGroupOutput")
    try:
        gl = ct.nodes.new("CompositorNodeGlare")
        for socket, waarde in (("Threshold", 0.85), ("Size", 8)):
            for inp in gl.inputs:
                if inp.name == socket:
                    try:
                        inp.default_value = waarde
                    except Exception:
                        pass
        ct.links.new(rl.outputs["Image"], gl.inputs["Image"])
        ct.links.new(gl.outputs["Image"], comp.inputs[0])
    except Exception as e:
        print("glare overgeslagen:", e)
        ct.links.new(rl.outputs["Image"], comp.inputs[0])

if MODE == "anim":
    sc.render.image_settings.file_format = "PNG"
    sc.render.filepath = os.path.join(OUTDIR, "frames-v5", "f_")
    bpy.ops.render.render(animation=True)
    print("FRAMES KLAAR naar", os.path.join(OUTDIR, "frames-v5"))
else:
    sc.render.image_settings.file_format = "PNG"
    sc.render.filepath = os.path.join(OUTDIR, "logo3d-v5-final.png")
    bpy.ops.render.render(write_still=True)
    print("STILL KLAAR:", sc.render.filepath)
