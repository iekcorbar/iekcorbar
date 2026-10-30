"""Own sound effects for Wildlife, Steamworks and Kitchen - synthesised from scratch (no samples,
no copied assets): oscillators, noise, envelopes and filters in plain Python.

Writes WAV files, converts them with ffmpeg to Ogg Vorbis (what Minecraft needs) and writes a
sounds.json per namespace:
    sounds/out/<namespace>/sounds.json
    sounds/out/<namespace>/sounds/<group>/<name>_<n>.ogg
The pack builders copy sounds/out/<namespace> into their pack; the plugins play the keys
"<namespace>:<event>" (e.g. "wildlife:duck.ambient").

Usage (repo root): python -I sounds/make_sounds.py [path-to-ffmpeg]
"""
import json, math, os, random, shutil, struct, subprocess, sys, wave

SR = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
TAU = 2 * math.pi

# ------------------------------------------------------------------ building blocks


def n(sec):
    return int(sec * SR)


def silence(sec):
    return [0.0] * n(sec)


def noise(sec, rnd):
    return [rnd.uniform(-1, 1) for _ in range(n(sec))]


def osc(freqs, kind="sine", phase=0.0):
    """freqs: list of per-sample frequencies (Hz)."""
    out, p = [], phase
    for f in freqs:
        p += f / SR
        x = p % 1.0
        if kind == "sine":
            out.append(math.sin(TAU * p))
        elif kind == "saw":
            out.append(2 * x - 1)
        elif kind == "square":
            out.append(1.0 if x < 0.5 else -1.0)
        else:  # triangle
            out.append(4 * abs(x - 0.5) - 1)
    return out


def glide(f0, f1, sec, curve=1.0):
    m = n(sec)
    return [f0 + (f1 - f0) * ((i / max(1, m - 1)) ** curve) for i in range(m)]


def const(f, sec):
    return [f] * n(sec)


def env(sig, a=0.01, d=0.1, s=0.7, r=0.1):
    """Attack/decay/sustain/release envelope over the whole signal length."""
    m = len(sig)
    na, nd, nr = n(a), n(d), n(r)
    out = []
    for i, x in enumerate(sig):
        if i < na:
            g = i / max(1, na)
        elif i < na + nd:
            g = 1 - (1 - s) * (i - na) / max(1, nd)
        elif i >= m - nr:
            g = s * (m - i) / max(1, nr)
        else:
            g = s
        out.append(x * g)
    return out


def decay(sig, t):
    """Exponential decay with time constant t (s)."""
    return [x * math.exp(-i / (t * SR)) for i, x in enumerate(sig)]


def mul(a, b):
    return [x * y for x, y in zip(a, b)]


def gain(sig, g):
    return [x * g for x in sig]


def mix(*sigs):
    m = max(len(s) for s in sigs)
    out = [0.0] * m
    for s in sigs:
        for i, x in enumerate(s):
            out[i] += x
    return out


def place(base, sig, at):
    """Adds sig into base starting at `at` seconds (base grows if needed)."""
    start = n(at)
    if len(base) < start + len(sig):
        base.extend([0.0] * (start + len(sig) - len(base)))
    for i, x in enumerate(sig):
        base[start + i] += x
    return base


def lowpass(sig, cutoff):
    """One-pole low-pass; cutoff may be a number or a per-sample list (a sweep)."""
    cl = cutoff if isinstance(cutoff, list) else None
    a = 1 - math.exp(-TAU * cutoff / SR) if cl is None else 0.0
    y, out = 0.0, []
    for i, x in enumerate(sig):
        if cl is not None:
            a = 1 - math.exp(-TAU * cl[min(i, len(cl) - 1)] / SR)
        y += a * (x - y)
        out.append(y)
    return out


def highpass(sig, cutoff):
    lp = lowpass(sig, cutoff)
    return [x - y for x, y in zip(sig, lp)]


def bandpass(sig, f, q=4.0):
    """RBJ biquad band-pass (constant peak gain); f may be a number or per-sample list."""
    out = []
    x1 = x2 = y1 = y2 = 0.0
    fl = f if isinstance(f, list) else None
    for i, x in enumerate(sig):
        fc = fl[min(i, len(fl) - 1)] if fl else f
        w = TAU * min(fc, SR * 0.45) / SR
        al = math.sin(w) / (2 * q)
        b0, b2 = al, -al
        a0, a1, a2 = 1 + al, -2 * math.cos(w), 1 - al
        y = (b0 * x + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, x, y1, y
        out.append(y)
    return out


def am(sig, rate, depth=0.5):
    return [x * (1 - depth + depth * (0.5 + 0.5 * math.sin(TAU * rate * i / SR))) for i, x in enumerate(sig)]


def vibrato(freqs, rate, cents):
    return [f * 2 ** (cents / 1200 * math.sin(TAU * rate * i / SR)) for i, f in enumerate(freqs)]


def normalize(sig, peak=0.85):
    m = max(1e-9, max(abs(x) for x in sig))
    return [x / m * peak for x in sig]


def fade(sig, fin=0.005, fout=0.02):
    m = len(sig)
    a, b = n(fin), n(fout)
    return [x * min(1, i / max(1, a), (m - i) / max(1, b)) for i, x in enumerate(sig)]


def ring(freqs_amps, sec, t):
    """Inharmonic metallic ring: sum of decaying sines."""
    out = [0.0] * n(sec)
    for f, amp in freqs_amps:
        s = decay(osc(const(f, sec)), t * (1 if f < 1500 else 0.5))
        for i, x in enumerate(s):
            out[i] += x * amp
    return out

# ------------------------------------------------------------------ wildlife


def duck_quack(rnd):
    out = []
    for k in range(rnd.randint(2, 3)):
        f0 = rnd.uniform(380, 460)
        src = osc(vibrato(glide(f0, f0 * 0.82, 0.16), 30, 40), "saw")
        q = mix(gain(bandpass(src, 1000, 3), 1.0), gain(bandpass(src, 2200, 4), 0.6), gain(noise(0.16, rnd), 0.05))
        place(out, env(q, 0.01, 0.05, 0.6, 0.06), k * 0.22)
    return out


def deer_call(rnd):
    f = vibrato(glide(rnd.uniform(560, 640), rnd.uniform(430, 480), 0.55, 0.7), 6, 30)
    tone = mix(osc(f), gain(osc([x * 2 for x in f]), 0.3))
    breath = gain(bandpass(noise(0.55, rnd), 1800, 1.5), 0.25)
    return env(mix(tone, breath), 0.05, 0.1, 0.8, 0.2)


def snort(rnd):
    return env(lowpass(noise(0.3, rnd), 900), 0.005, 0.08, 0.4, 0.15)


def bear_growl(rnd, sec=1.3):
    f = vibrato(glide(rnd.uniform(75, 90), rnd.uniform(60, 70), sec), 7, 60)
    body = lowpass(osc(f, "saw"), 600)
    rough = am(lowpass(noise(sec, rnd), 500), 22, 0.8)
    return env(mix(body, gain(rough, 0.8)), 0.15, 0.2, 0.8, 0.4)


def bear_roar(rnd):
    f = glide(110, 170, 0.4) + glide(170, 90, 0.7)
    body = lowpass(osc(vibrato(f, 9, 80), "saw"), 900)
    return env(mix(body, gain(lowpass(noise(1.1, rnd), 1200), 0.7)), 0.05, 0.2, 0.9, 0.4)


def boar_grunt(rnd):
    out = []
    for k in range(rnd.randint(2, 4)):
        g = lowpass(osc(glide(rnd.uniform(110, 140), 95, 0.12), "square"), 700)
        place(out, env(mix(g, gain(lowpass(noise(0.12, rnd), 800), 0.6)), 0.005, 0.04, 0.5, 0.05), k * 0.16)
    return out


def squeal(rnd):
    f = vibrato(glide(1100, 1500, 0.15) + glide(1500, 900, 0.35), 18, 120)
    return env(mix(osc(f, "saw"), gain(noise(0.5, rnd), 0.1)), 0.01, 0.05, 0.7, 0.15)


def bird_song(rnd, style=0):
    out = []
    t = 0.0
    base = [3200, 2600, 4100, 2200][style % 4]
    for k in range(rnd.randint(4, 8)):
        d = rnd.uniform(0.05, 0.13)
        f0 = base * rnd.uniform(0.8, 1.25)
        f1 = f0 * rnd.choice((0.7, 1.3, 1.5, 0.85))
        ch = osc(vibrato(glide(f0, f1, d, rnd.choice((0.5, 1, 2))), rnd.uniform(30, 70), 60))
        place(out, env(ch, 0.005, d * 0.3, 0.7, d * 0.3), t)
        t += d + rnd.uniform(0.02, 0.09)
    return out


def chirp(rnd):
    return env(osc(glide(rnd.uniform(3000, 4200), rnd.uniform(2200, 2800), 0.08)), 0.003, 0.03, 0.5, 0.03)


def clicks(rnd, count=3, spacing=0.07):
    out = []
    for k in range(count):
        c = decay(highpass(noise(0.03, rnd), 2500), 0.004)
        place(out, c, k * spacing * rnd.uniform(0.7, 1.3))
    return out


def capy_purr(rnd):
    return env(am(osc(vibrato(const(rnd.uniform(800, 950), 0.6), 5, 20)), 18, 0.9), 0.05, 0.1, 0.6, 0.2)


def hiss(rnd, sec=0.6, cut=3000):
    return env(highpass(noise(sec, rnd), cut), 0.02, 0.1, 0.7, sec * 0.4)


def snuffle(rnd):
    out = []
    for k in range(4):
        place(out, env(lowpass(noise(0.07, rnd), 1500), 0.005, 0.02, 0.5, 0.03), k * 0.11)
    return out


def flutter(rnd):
    return env(am(lowpass(noise(0.6, rnd), 700), 16, 0.95), 0.05, 0.1, 0.5, 0.2)


def buzz(rnd, f=180, sec=0.8):
    src = mix(osc(vibrato(const(f, sec), 6, 30), "saw"), gain(noise(sec, rnd), 0.2))
    return env(am(bandpass(src, f * 3, 2), 40, 0.4), 0.05, 0.1, 0.6, 0.2)


def squish(rnd):
    return env(bandpass(noise(0.18, rnd), glide(600, 250, 0.18), 3), 0.005, 0.05, 0.5, 0.08)

# ------------------------------------------------------------------ ambience


def woodpecker(rnd):
    out = []
    for k in range(rnd.randint(8, 14)):
        knock = mix(decay(osc(const(rnd.uniform(1400, 1700), 0.04)), 0.008), decay(gain(noise(0.04, rnd), 0.4), 0.004))
        place(out, knock, k * 0.055)
    return out


def crickets(rnd, sec=2.0):
    out = [0.0] * n(sec)
    f = rnd.uniform(4300, 4900)
    t = 0.0
    while t < sec - 0.2:
        trill = am(osc(const(f, 0.12)), 60, 1.0)
        place(out, env(trill, 0.005, 0.02, 0.8, 0.02), t)
        t += rnd.uniform(0.25, 0.45)
    return out[:n(sec)]


def owl(rnd):
    out = []
    for k, (d, f) in enumerate(((0.35, 420), (0.25, 400), (0.5, 380))):
        h = env(mix(osc(vibrato(const(f, d), 5, 15)), gain(bandpass(noise(d, rnd), f, 3), 0.3)), 0.06, 0.05, 0.8, 0.12)
        place(out, h, [0, 0.55, 0.9][k])
    return out


def frog(rnd):
    # 27-10: was a raw 130-170 Hz square wave full-normalised - a LOUD low buzz that
    # carried through walls and drove players mad. Now a warm croak: sine with a soft
    # beating octave, quick pitch drop for the "krok", tremolo kept mild. The rnd call
    # order is unchanged (uniform, randint, uniform per croak) so every sound after it
    # in the seeded stream stays byte-identical.
    out = []
    f = rnd.uniform(130, 170)
    for k in range(rnd.randint(2, 4)):
        base = const(f, 0.16)
        c = osc([x * (1 + 0.6 * math.exp(-i / max(1, n(0.035)))) for i, x in enumerate(base)])
        c = mix(c, gain(osc([x * 2.01 for x in base]), 0.3))
        c = am(c, rnd.uniform(35, 50), 0.45)
        place(out, env(bandpass(lowpass(c, 1400), f * 4, 2.0), 0.008, 0.05, 0.7, 0.05), k * 0.26)
    return out


def gull(rnd):
    f = glide(rnd.uniform(1400, 1700), 900, 0.45, 0.6)
    src = mix(osc(vibrato(f, 25, 50), "saw"), gain(noise(0.45, rnd), 0.15))
    return env(bandpass(src, 2000, 1.5), 0.02, 0.1, 0.8, 0.15)


def waves(rnd, sec=4.0):
    src = lowpass(noise(sec, rnd), 1200)
    m = len(src)
    return [x * math.sin(math.pi * i / m) ** 2 for i, x in enumerate(src)]


def cicada(rnd, sec=2.5):
    src = mix(osc(const(rnd.uniform(3800, 4400), sec), "saw"), gain(noise(sec, rnd), 0.3))
    return env(am(bandpass(src, 4200, 3), rnd.uniform(70, 110), 0.9), 0.4, 0.2, 0.8, 0.8)


def wind(rnd, sec=4.0):
    src = bandpass(noise(sec, rnd), glide(300, 700, sec / 2) + glide(700, 300, sec / 2), 1.2)
    m = len(src)
    return [x * math.sin(math.pi * i / m) for i, x in enumerate(src)]


def jungle_bird(rnd):
    # 27-10: the old one was a muffled 900-1600 Hz whoop. Now a bright two-note
    # tropical whistle with a little air. Uses its OWN Random - the shared seeded
    # stream stays untouched, so no other sound changes.
    local = random.Random(1971)
    out = []
    for k in range(2):
        f = vibrato(glide(1500, 2300, 0.12, 1.4) + glide(2300, 1900, 0.09), 9, 30)
        s = mix(osc(f), gain(bandpass(noise(0.21, local), 2800, 1.5), 0.1))
        place(out, env(s, 0.01, 0.04, 0.8, 0.05), k * 0.42)
    return out

# ------------------------------------------------------------------ steamworks


def chuff(rnd, sec=0.35):
    burst = env(bandpass(noise(sec, rnd), glide(1500, 500, sec), 1.2), 0.005, 0.08, 0.5, sec * 0.5)
    thump = decay(osc(glide(90, 50, 0.12)), 0.05)
    return mix(burst, gain(thump, 0.6))


def boiler_hiss(rnd):
    return env(am(highpass(noise(1.6, rnd), 2500), 3, 0.2), 0.2, 0.1, 0.8, 0.4)


def gear_clatter(rnd):
    out = []
    for k in range(rnd.randint(5, 8)):
        place(out, gain(ring([(rnd.uniform(1800, 2600), 1)], 0.05, 0.01), 0.6), k * 0.07)
    return out


def grind(rnd, sec=1.2):
    return env(am(lowpass(noise(sec, rnd), 350), 4, 0.4), 0.1, 0.1, 0.8, 0.3)


def hammer(rnd):
    thump = decay(osc(glide(110, 45, 0.2)), 0.08)
    metal = ring([(820, 0.6), (1370, 0.4), (2110, 0.3), (3020, 0.15)], 0.8, 0.25)
    steam = env(highpass(noise(0.6, rnd), 2000), 0.1, 0.1, 0.5, 0.3)
    out = mix(gain(thump, 1.0), gain(metal, 0.5))
    return place(out, gain(steam, 0.35), 0.15)


def whoosh(rnd, sec=1.5):
    return env(bandpass(noise(sec, rnd), 600, 0.8), 0.3, 0.2, 0.8, 0.4)


def drill(rnd, sec=1.0):
    return env(mix(lowpass(osc(vibrato(const(140, sec), 9, 40), "saw"), 1500), gain(bandpass(noise(sec, rnd), 2500, 1.5), 0.5)), 0.05, 0.1, 0.8, 0.2)


def saw_buzz(rnd, sec=1.0):
    return env(mix(osc(const(220, sec), "saw"), gain(osc(const(447, sec), "saw"), 0.5), gain(highpass(noise(sec, rnd), 3000), 0.4)), 0.05, 0.1, 0.7, 0.2)


def creak(rnd, f0=300, f1=340, sec=0.8):
    src = osc(vibrato(glide(f0, f1, sec), 40, 25), "saw")
    return env(bandpass(am(src, 30, 0.8), 1100, 2), 0.05, 0.1, 0.7, 0.2)


def windmill_woosh(rnd):
    # 29-10: eigenaar vond de creak (gezaag op 140 Hz) erg irritant; de windmILL
    # moet klinken als wind door bladeren. Geen toon, alleen zacht gefilterd
    # ruis-gezwollen: een bandpass die traag 500-1100 Hz op en neer vaart met
    # een sine-envelope, plus een diepe adem-laag. Eigen Random: de GEDEELDE
    # seeded stream verbruikt hier niets (de oude creak ook niet), dus alle
    # andere clips blijven byte-identisch.
    local = random.Random(7301)
    sec = 2.8
    sweep = glide(500, 1100, sec / 2) + glide(1100, 500, sec / 2)
    leaves = bandpass(noise(sec, local), sweep, 1.3)
    breath = lowpass(noise(sec, local), 350)
    sig = mix(leaves, gain(breath, 0.35))
    m = len(sig)
    return [x * math.sin(math.pi * i / m) for i, x in enumerate(sig)]


# Variantenteller (30-10): de CATALOGUE-bouwer roept elke generator een keer per
# variant aan met dezelfde GEGEVENDE rnd. Eigen fixed-seed funcs (voice_milo,
# voice_machine) zouden anders per variant IDENTIEKE bytes produceren - de MC-
# randompick tussen varianten doet dan niets. Een teller geeft elke variant z'n
# eigen seed, ZONDER de Gedeelde seeded stream te verbruiken (audio-craft regel).
_VCOUNT = {}


def _vseed(base):
    _VCOUNT[base] = _VCOUNT.get(base, 0) + 1
    return random.Random(base + _VCOUNT[base])


def voice_milo(rnd):
    # 30-10: NPC-stem voor questconversaties (Milo de handelaar). Dierentuin-achtig
    # gekwebbel: een rijtje korte lettergrepen die elk opgliden, daarna zacht
    # afgezwakt. Eigen lokale Random (les van windmill_woosh): de GEDEELDE seeded
    # stream verbruikt hier niets, alle bestaande clips blijven byte-identisch.
    local = _vseed(8417)
    out = []
    for _ in range(local.randint(4, 6)):
        f0 = local.choice((380.0, 430.0, 480.0, 530.0)) * local.uniform(0.92, 1.08)
        syl = osc(glide(f0, f0 * 1.35, 0.09))
        out += env(syl, 0.008, 0.03, 0.8, 0.03)
        out += silence(local.uniform(0.02, 0.06))
    return lowpass(out, 1400)


def voice_machine(rnd):
    # 30-10: machine-stem (Mad-Docter-achtige NPCs): vierkante tik-tik-piepjes met
    # ruis-klak tussen de lettergrepen. Lokale Random, zie voice_milo.
    local = _vseed(8418)
    out = []
    for _ in range(local.randint(3, 5)):
        f0 = local.choice((900.0, 1100.0, 1300.0))
        syl = osc(const(f0, 0.05), "square")
        out += env(syl, 0.005, 0.02, 0.9, 0.02)
        out += gain(noise(local.uniform(0.02, 0.05), local), 0.12)
    return out


def quest_sting(rnd):
    # 30-10: quest-complete motiefje: C-E-G-C opgewaaierd plus nazaag.
    # Volledig deterministisch - geen rnd-verbruik.
    out = []
    for f in (523.0, 659.0, 784.0, 1046.0):
        out += env(osc(const(f, 0.14)), 0.006, 0.04, 0.7, 0.05)
    out += gain(decay(osc(const(1046.0, 0.5)), 0.18), 0.4)
    return out


def quest_click(rnd):
    # 30-10: tiny pluck wanneer een speler een gespreksoptie kiest. Deterministisch.
    syl = osc(glide(800.0, 1250.0, 0.06))
    return env(syl, 0.004, 0.03, 0.6, 0.02)


def servo(rnd):
    return env(mix(osc(glide(600, 950, 0.5), "square"), gain(noise(0.5, rnd), 0.05)), 0.02, 0.05, 0.4, 0.1)


def whistle(rnd):
    sec = 1.3
    tones = mix(*[gain(osc(vibrato(const(f, sec), 5, 8)), 0.3) for f in (523, 659, 784)])
    return env(mix(tones, gain(bandpass(noise(sec, rnd), 1500, 1), 0.3)), 0.08, 0.1, 0.9, 0.3)


def brake(rnd):
    src = mix(osc(vibrato(const(3100, 1.0), 13, 60)), gain(highpass(noise(1.0, rnd), 3000), 0.5))
    return env(src, 0.05, 0.1, 0.6, 0.4)


def rail_clack(rnd):
    out = []
    for at in (0, 0.18):
        place(out, mix(decay(osc(const(320, 0.06)), 0.02), decay(gain(noise(0.06, rnd), 0.5), 0.01)), at)
    return out


def bell(rnd):
    return ring([(880, 0.6), (1760, 0.3), (2380, 0.25), (3150, 0.1)], 2.0, 0.8)


def hum(rnd, sec=2.0):
    s = mix(osc(const(50, sec)), gain(osc(const(100, sec)), 0.5), gain(lowpass(noise(sec, rnd), 300), 0.3))
    return am(s, 1.5, 0.3)

# ------------------------------------------------------------------ kitchen


def chop(rnd):
    thock = decay(osc(glide(rnd.uniform(240, 300), 160, 0.08)), 0.03)
    click = decay(highpass(noise(0.03, rnd), 3000), 0.004)
    return mix(thock, gain(click, 0.7))


def simmer(rnd, sec=2.0):
    out = gain(lowpass(noise(sec, rnd), 400), 0.15)
    t = 0.0
    while t < sec - 0.1:
        f = rnd.uniform(300, 900)
        place(out, gain(decay(osc(glide(f, f * 1.6, 0.05)), 0.02), rnd.uniform(0.3, 0.8)), t)
        t += rnd.uniform(0.04, 0.18)
    return out[:n(sec)]


def sizzle(rnd, sec=2.0):
    out = gain(am(highpass(noise(sec, rnd), 3500), 9, 0.4), 0.6)
    t = 0.0
    while t < sec - 0.05:
        place(out, gain(decay(noise(0.02, rnd), 0.003), 0.9), t)
        t += rnd.uniform(0.03, 0.2)
    return out[:n(sec)]


def pour(rnd):
    return env(bandpass(noise(0.9, rnd), glide(500, 1400, 0.9), 3), 0.05, 0.1, 0.8, 0.2)


def chopsticks(rnd):
    out = []
    for at in (0, 0.09):
        place(out, decay(bandpass(noise(0.03, rnd), 2800, 4), 0.006), at)
    return out


def slurp(rnd):
    return env(bandpass(noise(0.45, rnd), glide(400, 1800, 0.45, 0.7), 5), 0.05, 0.1, 0.8, 0.1)


def ignite(rnd):
    return env(lowpass(noise(0.7, rnd), glide(200, 2500, 0.7)), 0.02, 0.1, 0.7, 0.3)

# ------------------------------------------------------------------ relics (scary lore props)


def whisper(rnd):
    """Breathy, wordless whispering: noise through shifting vowel formants, in syllables."""
    sec = rnd.uniform(1.4, 2.2)
    src = noise(sec, rnd)
    f1 = []
    t = 0.0
    while len(f1) < n(sec):
        f1 += glide(rnd.uniform(500, 900), rnd.uniform(500, 900), rnd.uniform(0.12, 0.25))
    f2 = [f * rnd.uniform(2.2, 2.6) for f in f1]
    v = mix(bandpass(src, f1[:n(sec)], 6), gain(bandpass(src, f2[:n(sec)], 8), 0.7))
    syll = []
    while len(syll) < n(sec):
        syll += env([1.0] * n(rnd.uniform(0.12, 0.3)), 0.03, 0.05, 0.7, 0.06) + [0.0] * n(rnd.uniform(0.02, 0.12))
    return env(mul(v, syll[:len(v)]), 0.2, 0.1, 0.9, 0.4)


def heartbeat(rnd):
    out = []
    for k in range(2):
        for j, (at, g) in enumerate(((0.0, 1.0), (0.22, 0.7))):
            place(out, gain(decay(osc(glide(65, 40, 0.15)), 0.06), g), k * 0.9 + at)
    return lowpass(out, 400)


def crackle(rnd, sec=1.5):
    out = gain(lowpass(noise(sec, rnd), 900), 0.08)
    t = 0.0
    while t < sec - 0.02:
        place(out, gain(decay(highpass(noise(0.015, rnd), 1500), 0.002), rnd.uniform(0.3, 1.0)), t)
        t += rnd.uniform(0.03, 0.25)
    return out[:n(sec)]


def portal_hum(rnd, sec=2.4):
    drone = mix(osc(vibrato(const(70, sec), 0.5, 20)), gain(osc(const(140.5, sec)), 0.5), gain(osc(const(211, sec), "triangle"), 0.25))
    shimmer = am(mix(*[gain(osc(const(f, sec)), 0.12) for f in (1320, 1760, 2093)]), 6, 0.9)
    return env(mix(drone, shimmer), 0.5, 0.2, 0.9, 0.6)


def totem_alarm(rnd):
    tone = am(mix(osc(const(196, 1.2), "triangle"), gain(osc(const(207.6, 1.2), "triangle"), 0.8)), 3, 0.7)
    return env(mix(tone, gain(ring([(1170, 0.4), (1650, 0.3)], 1.2, 0.4), 0.4)), 0.05, 0.2, 0.8, 0.4)


def wet_squelch(rnd):
    return env(lowpass(bandpass(noise(0.35, rnd), glide(500, 180, 0.35), 4), 1200), 0.01, 0.08, 0.6, 0.15)


def growl_distant(rnd):
    return lowpass(gain(bear_growl(rnd, 1.6), 0.8), 300)


# ------------------------------------------------------------------ relics: Xmas Town


NOTE = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def note_freq(name):
    """'E5' -> Hz (A4 = 440)."""
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTE[pitch] + 12 * (octave - 4) - 9) / 12)


def tine(f, sec=0.9):
    """Music-box tine: a pure tone plus an inharmonic overtone, decaying."""
    body = mix(osc(const(f, sec)), gain(osc(const(f * 2.76, sec)), 0.25), gain(osc(const(f * 5.4, sec)), 0.08))
    return decay(body, 0.35)


def melody(notes, beat=0.28):
    """notes: [(name or None, beats)] - public-domain carol melodies only."""
    out, t = [], 0.0
    for name, beats in notes:
        if name:
            place(out, tine(note_freq(name)), t)
        t += beats * beat
    return out


JINGLE_BELLS = [("E5", 1), ("E5", 1), ("E5", 2), ("E5", 1), ("E5", 1), ("E5", 2), ("E5", 1), ("G5", 1), ("C5", 1.5), ("D5", 0.5), ("E5", 4),
                ("F5", 1), ("F5", 1), ("F5", 1.5), ("F5", 0.5), ("F5", 1), ("E5", 1), ("E5", 1), ("E5", 0.5), ("E5", 0.5),
                ("E5", 1), ("D5", 1), ("D5", 1), ("E5", 1), ("D5", 2), ("G5", 2),
                ("E5", 1), ("E5", 1), ("E5", 2), ("E5", 1), ("E5", 1), ("E5", 2), ("E5", 1), ("G5", 1), ("C5", 1.5), ("D5", 0.5), ("E5", 4),
                ("F5", 1), ("F5", 1), ("F5", 1), ("F5", 1), ("F5", 1), ("E5", 1), ("E5", 1), ("E5", 0.5), ("E5", 0.5),
                ("G5", 1), ("G5", 1), ("F5", 1), ("D5", 1), ("C5", 4)]
DECK_THE_HALLS = [("G5", 1.5), ("F5", 0.5), ("E5", 1), ("D5", 1), ("C5", 1), ("D5", 1), ("E5", 1), ("C5", 1),
                  ("D5", 0.5), ("E5", 0.5), ("F5", 0.5), ("D5", 0.5), ("E5", 1.5), ("D5", 0.5), ("C5", 1), ("B4", 1), ("C5", 2),
                  ("G5", 1.5), ("F5", 0.5), ("E5", 1), ("D5", 1), ("C5", 1), ("D5", 1), ("E5", 1), ("C5", 1),
                  ("D5", 0.5), ("E5", 0.5), ("F5", 0.5), ("D5", 0.5), ("E5", 1.5), ("D5", 0.5), ("C5", 1), ("B4", 1), ("C5", 2)]
WE_WISH = [("D5", 1), ("G5", 1), ("G5", 0.5), ("A5", 0.5), ("G5", 0.5), ("F#5", 0.5), ("E5", 1), ("E5", 1),
           ("E5", 1), ("A5", 1), ("A5", 0.5), ("B5", 0.5), ("A5", 0.5), ("G5", 0.5), ("F#5", 1), ("D5", 1),
           ("D5", 1), ("B5", 1), ("B5", 0.5), ("C6", 0.5), ("B5", 0.5), ("A5", 0.5), ("G5", 1), ("E5", 1),
           ("D5", 0.5), ("D5", 0.5), ("E5", 1), ("A5", 1), ("F#5", 1), ("G5", 2)]


def sleigh_bells(rnd, sec=1.6):
    out = [0.0] * n(sec)
    t = 0.0
    while t < sec - 0.2:
        f = rnd.uniform(2800, 4200)
        place(out, gain(ring([(f, 0.6), (f * 1.47, 0.3), (f * 2.2, 0.15)], 0.25, 0.08), rnd.uniform(0.2, 0.6)), t)
        t += rnd.uniform(0.03, 0.12)
    return out[:n(sec)]


def gift_open(rnd):
    rustle = env(bandpass(noise(0.5, rnd), 3000, 1.2), 0.02, 0.1, 0.6, 0.2)
    out = gain(am(rustle, 25, 0.8), 0.6)
    for k, f in enumerate((1047, 1319, 1568, 2093)):
        place(out, gain(tine(f, 0.6), 0.5), 0.35 + k * 0.08)
    return out


def glitter_chime(rnd):
    out = []
    for k in range(6):
        place(out, gain(tine(rnd.choice((1568, 1760, 2093, 2349, 2637)), 0.7), 0.5), k * 0.09)
    return out


def advent_door(rnd):
    out = creak(rnd, 420, 480, 0.35)
    place(out, gain(tine(1568, 0.6), 0.5), 0.3)
    return out


# ------------------------------------------------------------------ relics: New Year


def temple_toll(rnd):
    """Deep bronze temple bell: low inharmonic partials with a long hum."""
    strike = gain(decay(lowpass(noise(0.1, rnd), 600), 0.02), 0.6)
    body = ring([(64, 0.8), (128.5, 0.6), (171, 0.35), (243, 0.25), (402, 0.12)], 5.0, 2.2)
    return place(body, strike, 0)


def clock_strike(rnd):
    return ring([(523, 0.6), (1046, 0.3), (1410, 0.2), (2094, 0.1)], 1.6, 0.6)


def clock_tick(rnd):
    return gain(decay(bandpass(noise(0.03, rnd), 3200, 5), 0.004), 0.8)


def confetti_pop(rnd):
    pop = decay(lowpass(noise(0.08, rnd), 1500), 0.015)
    paper = gain(am(env(bandpass(noise(0.7, rnd), 4000, 1.2), 0.02, 0.1, 0.5, 0.4), 30, 0.8), 0.4)
    return place(pop, paper, 0.03)


def fizz(rnd, sec=2.0):
    out = gain(highpass(noise(sec, rnd), 5000), 0.4)
    t = 0.0
    while t < sec - 0.02:
        place(out, gain(decay(highpass(noise(0.01, rnd), 3000), 0.002), rnd.uniform(0.2, 0.7)), t)
        t += rnd.uniform(0.01, 0.06)
    return out[:n(sec)]


# ------------------------------------------------------------------ sakagura (brewing)


def splash(rnd):
    body = env(bandpass(noise(0.45, rnd), glide(1800, 600, 0.45), 1.5), 0.005, 0.08, 0.5, 0.25)
    out = gain(body, 0.8)
    for k in range(3):
        f = rnd.uniform(500, 900)
        place(out, gain(decay(osc(glide(f, f * 1.8, 0.06)), 0.03), 0.5), 0.05 + k * 0.07)
    return out


def drips(rnd):
    out = []
    for k in range(rnd.randint(2, 4)):
        f = rnd.uniform(900, 1500)
        place(out, gain(decay(osc(glide(f * 0.7, f, 0.04)), 0.05), 0.7), k * rnd.uniform(0.25, 0.45))
    return out


def hiccup(rnd):
    f = rnd.uniform(220, 300)
    voice = osc(vibrato(glide(f, f * 1.6, 0.12), 12, 40), "saw")
    return env(bandpass(mix(voice, gain(noise(0.12, rnd), 0.3)), 900, 2), 0.005, 0.03, 0.6, 0.05)


def puke(rnd):
    gush = env(lowpass(am(noise(1.1, rnd), 9, 0.6), glide(700, 300, 1.1)), 0.05, 0.2, 0.8, 0.4)
    gag = env(bandpass(osc(glide(140, 90, 0.3), "saw"), 500, 2), 0.01, 0.05, 0.6, 0.1)
    return place(gain(gag, 0.6), gush, 0.15)


# ------------------------------------------------------------------ relics: Easter, St. Patrick's Day, Curiosities

SAKURA = [("A5", 1), ("A5", 1), ("B5", 2), ("A5", 1), ("A5", 1), ("B5", 2),
          ("A5", 1), ("B5", 1), ("C6", 1), ("B5", 1), ("A5", 1), ("B5", 0.5), ("A5", 0.5), ("F5", 2),
          ("E5", 1), ("C5", 1), ("E5", 1), ("F5", 1), ("E5", 1), ("E5", 0.5), ("C5", 0.5), ("B4", 2),
          ("A5", 1), ("B5", 1), ("C6", 1), ("B5", 1), ("A5", 1), ("B5", 0.5), ("A5", 0.5), ("F5", 2),
          ("E5", 1), ("C5", 1), ("E5", 1), ("F5", 1), ("E5", 1), ("E5", 0.5), ("C5", 0.5), ("B4", 2),
          ("A5", 1), ("A5", 1), ("B5", 2), ("A5", 1), ("A5", 1), ("B5", 2),
          ("E5", 1), ("F5", 1), ("B5", 0.5), ("A5", 0.5), ("F5", 1), ("E5", 4)]


def koto(f, sec=1.2):
    """Plucked string (koto/harp): bright attack, quick decay."""
    body = mix(osc(const(f, sec), "triangle"), gain(osc(const(f * 2, sec)), 0.35), gain(osc(const(f * 3.01, sec)), 0.15))
    return decay(body, 0.4)


def gramophone_sakura(rnd):
    """Sakura Sakura (traditional Japanese melody) on an old record: plucked notes, lowpassed, with crackle."""
    out, t = [], 0.0
    for name, beats in SAKURA:
        place(out, gain(koto(note_freq(name)), 0.8), t)
        t += beats * 0.42
    out = lowpass(out, 2600)
    hiss = gain(highpass(noise(len(out) / SR, rnd), 3000), 0.03)
    clicks = crackle(rnd, len(out) / SR)
    return mix(out, hiss, gain(clicks, 0.25))


def harp_arpeggios(rnd):
    chords = [("G4", "B4", "D5", "G5"), ("C4", "E4", "G4", "C5"), ("D4", "F#4", "A4", "D5"), ("G4", "B4", "D5", "G5")]
    out, t = [], 0.0
    for ch in chords:
        for k, nme in enumerate(ch + ch[-2::-1]):
            place(out, gain(koto(note_freq(nme), 1.6), 0.6), t)
            t += 0.16
        t += 0.2
    return out


def coins(rnd):
    out = []
    for k in range(rnd.randint(5, 8)):
        f = rnd.uniform(2500, 4000)
        place(out, gain(ring([(f, 0.6), (f * 1.52, 0.3)], 0.3, 0.08), rnd.uniform(0.3, 0.7)), k * rnd.uniform(0.04, 0.09))
    return out


def egg_found(rnd):
    out = []
    for k, n_ in enumerate(("C6", "E6", "G6", "C7")):
        place(out, gain(tine(note_freq(n_), 0.7), 0.6), k * 0.07)
    return out


def zap(rnd):
    sec = rnd.uniform(0.3, 0.6)
    src = mix(gain(osc(vibrato(const(120, sec), 60, 300), "square"), 0.4), gain(highpass(noise(sec, rnd), 2000), 0.6))
    return env(am(src, rnd.uniform(25, 45), 0.9), 0.005, 0.05, 0.7, 0.1)


def bamboo_clack(rnd):
    knock = mix(decay(bandpass(noise(0.08, rnd), 900, 6), 0.03), decay(osc(const(620, 0.12)), 0.03))
    return place(gain(knock, 1.0), gain(splash(rnd), 0.25), 0.05)


# ------------------------------------------------------------------ catalogue: namespace -> event -> (fn, variants, subtitle)

# ------------------------------------------------------------------ relics: Celtic harp tunes (traditional, public domain)


def harp_pluck(f, sec=2.2):
    """Harp string: a plucked triangle with soft overtones and a long, warm decay."""
    body = mix(osc(const(f, sec), "triangle"), gain(osc(const(f * 2, sec)), 0.28), gain(osc(const(f * 3.0, sec)), 0.1))
    return decay(body, 0.8)


def harp_tune(notes, beat=0.42, bass=None):
    """notes: [(name or None, beats)]; every melody note is plucked, with an optional soft bass note an octave below on strong beats."""
    out, t = [], 0.0
    for name, beats in notes:
        if name:
            place(out, gain(harp_pluck(note_freq(name)), 0.85), t)
            if bass and beats >= 1.5:
                pitch, octave = name[:-1], int(name[-1])
                place(out, gain(harp_pluck(note_freq(pitch + str(octave - 1)), 2.6), 0.35), t)
        t += beats * beat
    return lowpass(out, 5200)


MINSTREL_BOY = [("D4", 1), ("G4", 1.5), ("G4", 0.5), ("G4", 1), ("A4", 1), ("B4", 1.5), ("B4", 0.5), ("B4", 1), ("A4", 1),
                ("B4", 1), ("D5", 1), ("B4", 1), ("G4", 1), ("A4", 3), ("D4", 1),
                ("G4", 1.5), ("G4", 0.5), ("G4", 1), ("A4", 1), ("B4", 1.5), ("B4", 0.5), ("B4", 1), ("A4", 1),
                ("B4", 1), ("D5", 1), ("E5", 1), ("D5", 1), ("G5", 4)]
LONDONDERRY = [("G4", 1), ("C5", 2), ("C5", 1), ("D5", 1), ("E5", 2), ("E5", 1), ("G5", 1), ("E5", 2), ("E5", 1), ("D5", 1),
               ("C5", 3), ("C5", 1), ("D5", 2), ("D5", 1), ("C5", 1), ("A4", 2), ("A4", 1), ("G4", 1), ("A4", 4),
               ("G4", 1), ("C5", 2), ("C5", 1), ("D5", 1), ("E5", 2), ("E5", 1), ("G5", 1), ("A5", 2), ("A5", 1), ("G5", 1),
               ("E5", 3), ("E5", 1), ("D5", 2), ("D5", 1), ("C5", 1), ("D5", 2), ("E5", 1), ("D5", 1), ("C5", 4)]
MOLLY_MALONE = [("C5", 1), ("C5", 1), ("C5", 1), ("E5", 1), ("G5", 1), ("G5", 2), ("G5", 1), ("E5", 1), ("E5", 1), ("D5", 1), ("C5", 3),
                ("C5", 1), ("C5", 1), ("C5", 1), ("E5", 1), ("G5", 1), ("G5", 2), ("G5", 1), ("A5", 1), ("G5", 1), ("E5", 1), ("D5", 3),
                ("E5", 1), ("E5", 1), ("E5", 1), ("G5", 1), ("C6", 1), ("C6", 2), ("B5", 1), ("A5", 1), ("G5", 1), ("E5", 1),
                ("D5", 1), ("C5", 1), ("D5", 1), ("E5", 1), ("C5", 3)]
COUNTY_DOWN = [("E5", 1), ("G5", 1.5), ("G5", 0.5), ("G5", 1), ("A5", 1), ("G5", 1), ("E5", 1), ("D5", 1.5), ("E5", 0.5), ("G5", 2),
               ("E5", 1), ("G5", 1.5), ("G5", 0.5), ("G5", 1), ("A5", 1), ("G5", 1), ("E5", 1), ("D5", 1), ("C5", 1), ("D5", 2),
               ("E5", 1), ("G5", 1.5), ("G5", 0.5), ("A5", 1), ("C6", 1), ("A5", 1), ("G5", 1), ("E5", 1.5), ("D5", 0.5), ("E5", 1), ("G5", 1),
               ("A5", 1), ("G5", 1), ("E5", 1), ("D5", 1), ("C5", 4)]
FAIR_MOVED = [("D5", 1), ("D5", 1.5), ("E5", 0.5), ("G5", 1), ("A5", 1), ("G5", 2), ("E5", 1), ("D5", 1), ("E5", 1.5), ("D5", 0.5), ("C5", 1), ("A4", 1),
              ("A4", 3), ("D5", 1), ("D5", 1.5), ("E5", 0.5), ("G5", 1), ("A5", 1), ("C6", 2), ("A5", 1), ("G5", 1), ("A5", 1.5), ("G5", 0.5), ("E5", 1), ("D5", 1),
              ("E5", 1), ("D5", 1), ("C5", 1), ("A4", 1), ("G4", 4)]


def tune_fn(notes, beat=0.42):
    return lambda r: harp_tune(notes, beat, bass=True)


# ------------------------------------------------------------------ relics: bone xylophone (own spooky tunes + Grieg, public domain)


def bone_note(f, sec=0.7):
    """Hollow bone bar struck with a mallet: a short wooden ping with a dry overtone."""
    body = mix(osc(const(f, sec)), gain(osc(const(f * 3.9, sec)), 0.3), gain(osc(const(f * 9.2, sec)), 0.06))
    return decay(body, 0.2)


def bone_tune(notes, beat=0.3):
    out, t = [], 0.0
    for name, beats in notes:
        if name:
            place(out, gain(bone_note(note_freq(name)), 0.9), t)
        t += beats * beat
    return out


# Original tunes (not any existing song): a bouncy minor shuffle, a creaky waltz and a slow lullaby.
BONE_SHUFFLE = [("E5", 0.5), ("E5", 0.5), ("G5", 0.5), ("E5", 0.5), ("B4", 1), (None, 1), ("E5", 0.5), ("E5", 0.5), ("G5", 0.5), ("A5", 0.5), ("G5", 2),
                ("D5", 0.5), ("D5", 0.5), ("F5", 0.5), ("D5", 0.5), ("A4", 1), (None, 1), ("D5", 0.5), ("D5", 0.5), ("F5", 0.5), ("G5", 0.5), ("F5", 2),
                ("E5", 0.5), ("G5", 0.5), ("B5", 1), ("A5", 0.5), ("G5", 0.5), ("E5", 1), ("D5", 0.5), ("F5", 0.5), ("A5", 1), ("G5", 0.5), ("F5", 0.5), ("D5", 1),
                ("E5", 0.5), ("D#5", 0.5), ("E5", 0.5), ("B4", 0.5), ("E4", 3)]
BONE_WALTZ = [("A4", 1), ("C5", 1), ("E5", 1), ("A5", 2), ("G#5", 1), ("E5", 1), ("C5", 1), ("E5", 2), (None, 1),
              ("A4", 1), ("C5", 1), ("E5", 1), ("A5", 2), ("B5", 1), ("C6", 1), ("B5", 1), ("A5", 2), (None, 1),
              ("D5", 1), ("F5", 1), ("A5", 1), ("D6", 2), ("C#6", 1), ("A5", 1), ("F5", 1), ("A5", 2), (None, 1),
              ("E5", 1), ("G#5", 1), ("B5", 1), ("A5", 1), ("E5", 1), ("C5", 1), ("A4", 3)]
BONE_LULLABY = [("E5", 2), ("G5", 1), ("B5", 3), ("A5", 2), ("G5", 1), ("F#5", 3), ("E5", 2), ("G5", 1), ("B5", 2), ("D6", 1), ("C6", 3),
                ("B5", 2), ("A5", 1), ("G5", 2), ("F#5", 1), ("E5", 4), (None, 2)]
# In the Hall of the Mountain King (Grieg, 1875, public domain) from memory: three rounds, each faster and higher
MOUNTAIN_KING = [("B4", 1), ("C#5", 1), ("D5", 1), ("E5", 1), ("F#5", 1), ("D5", 1), ("F#5", 2), ("F5", 1), ("C#5", 1), ("F5", 2),
                 ("E5", 1), ("C5", 1), ("E5", 2), ("B4", 1), ("C#5", 1), ("D5", 1), ("E5", 1), ("F#5", 1), ("D5", 1), ("F#5", 1), ("B5", 1), ("A5", 1), ("F#5", 1), ("D5", 1), ("F#5", 1), ("A5", 4)]


def mountain_king(rnd):
    out, t = [], 0.0
    for beat, shift in ((0.34, 0), (0.25, 0), (0.17, 12)):
        for name, beats in MOUNTAIN_KING:
            if name:
                pitch, octave = name[:-1], int(name[-1])
                place(out, gain(bone_note(note_freq(pitch + str(octave + shift // 12))), 0.9), t)
            t += beats * beat
        t += 0.2
    return out


# ------------------------------------------------------------------ wildlife: elephant + zebra (generated after everything else, see EXTRA)


def elephant_rumble(rnd):
    """Low contact rumble: a throaty saw an octave under a voice, slowly pulsing."""
    sec = 1.5
    f = vibrato(glide(rnd.uniform(72, 84), rnd.uniform(52, 60), sec), 5, 60)
    body = lowpass(osc(f, "saw"), 300)
    over = gain(osc([x * 2 for x in f]), 0.4)
    return env(am(mix(body, over, gain(lowpass(noise(sec, rnd), 200), 0.35)), 9, 0.5), 0.2, 0.2, 0.8, 0.5)


def elephant_trumpet(rnd):
    """Rising, cracking trumpet blast: harsh saw with two formants and a breath of noise."""
    f = glide(320, 780, 0.35, 0.6) + glide(780, 650, 0.5) + glide(650, 380, 0.45)
    src = osc(vibrato(f, 14, 45), "saw")
    horn = mix(bandpass(src, 1100, 2.5), gain(bandpass(src, 2400, 3), 0.6), gain(bandpass(noise(len(f) / SR, rnd), 1800, 1.2), 0.18))
    return env(horn, 0.04, 0.1, 0.85, 0.3)


def zebra_bark(rnd):
    """The zebra's 'kwa-ha': short barking yips that drop in pitch."""
    out = []
    for k in range(rnd.randint(2, 4)):
        f0 = rnd.uniform(620, 760)
        src = osc(vibrato(glide(f0, f0 * 0.62, 0.17), 25, 60), "saw")
        yip = mix(bandpass(src, 900, 3), gain(bandpass(src, 1900, 4), 0.5), gain(noise(0.17, rnd), 0.04))
        place(out, env(yip, 0.01, 0.05, 0.6, 0.06), k * 0.24)
    return out


def zebra_whinny(rnd):
    f = vibrato(glide(900, 1500, 0.2) + glide(1500, 700, 0.5), 20, 80)
    return env(mix(osc(f, "saw"), gain(bandpass(noise(len(f) / SR, rnd), 2200, 1.5), 0.2)), 0.02, 0.06, 0.7, 0.2)


def wraith_moan(rnd):
    """Wraith: a breathy low moan sliding down - wind through a crack in the dark. Warm sine
    + soft octave, slow vibrato and a ribbon of filtered noise; deliberately NOT a drone buzz."""
    sec = rnd.uniform(1.6, 2.1)
    f0 = rnd.uniform(165, 230)
    f = vibrato(glide(f0, f0 * rnd.uniform(0.55, 0.68), sec, 1.3), 4.2, 45)
    voice = mix(osc(f), gain(osc([x * 2 for x in f]), 0.28), gain(lowpass(osc(f, "triangle"), 900), 0.35))
    breath = gain(bandpass(noise(sec, rnd), 800, 1.2), 0.16)
    return env(am(mix(voice, breath), 3.5, 0.35), 0.35, 0.3, 0.6, 0.6)


def wraith_shriek(rnd):
    """Wraith hurt: a thin rising wail through two formants, with a tremble - startling but short."""
    sec = rnd.uniform(0.7, 0.95)
    f = vibrato(glide(rnd.uniform(420, 520), 1150, sec * 0.35, 0.7) + glide(1150, 700, sec * 0.65), 11, 90)
    src = osc(f, "saw")
    wail = mix(gain(bandpass(src, 1400, 2.2), 1.0), gain(bandpass(src, 2600, 3), 0.5), gain(lowpass(osc([x * 0.5 for x in f]), 500), 0.3))
    return env(am(wail, 7, 0.5), 0.03, 0.08, 0.75, 0.35)


def wraith_fade(rnd):
    """Wraith death: the voice sinks out of hearing under a sigh of cold air (long release)."""
    sec = 1.8
    f = vibrato(glide(rnd.uniform(280, 330), 85, sec, 1.5), 3, 60)
    voice = lowpass(osc(f, "triangle"), 700)
    air = gain(bandpass(noise(sec, rnd), glide(1800, 300, sec), 1.4), 0.3)
    return env(mix(voice, air), 0.1, 0.3, 0.5, 0.9)


CATALOGUE = {
    "wildlife": {
        "duck.ambient": (duck_quack, 3, "Duck quacks"), "duck.hurt": (lambda r: gain(duck_quack(r), 1.0), 2, "Duck hurts"),
        "deer.ambient": (deer_call, 2, "Deer calls"), "deer.snort": (snort, 2, "Deer snorts"),
        "bear.ambient": (bear_growl, 2, "Bear growls"), "bear.hurt": (bear_roar, 2, "Bear roars"),
        "boar.ambient": (boar_grunt, 3, "Boar grunts"), "boar.hurt": (squeal, 2, "Boar squeals"),
        "songbird.ambient": (lambda r: bird_song(r, r.randint(0, 3)), 4, "Bird sings"), "songbird.hurt": (chirp, 2, "Bird chirps"),
        "crab.ambient": (clicks, 2, "Crab clicks"), "crab.wave": (lambda r: clicks(r, 5, 0.09), 1, "Crab waves"),
        "capybara.ambient": (capy_purr, 2, "Capybara purrs"), "tortoise.hide": (hiss, 1, "Tortoise hisses"),
        "hedgehog.ambient": (snuffle, 2, "Hedgehog snuffles"), "lizard.hurt": (lambda r: hiss(r, 0.3, 4000), 1, "Lizard hisses"),
        "snail.hurt": (squish, 2, "Snail squishes"), "insect.flutter": (flutter, 2, "Wings flutter"),
        "dragonfly.buzz": (lambda r: buzz(r, 150, 0.6), 2, "Dragonfly buzzes"),
        "ambient.forest_birds": (lambda r: bird_song(r, r.randint(0, 3)), 6, "Birds sing"),
        "ambient.woodpecker": (woodpecker, 2, "Woodpecker knocks"), "ambient.crickets": (crickets, 3, "Crickets chirp"),
        "ambient.owl": (owl, 2, "Owl hoots"), "ambient.frogs": (frog, 4, "Frogs croak"), "ambient.gull": (gull, 3, "Gull cries"),
        "ambient.waves": (waves, 2, "Waves wash ashore"), "ambient.cicadas": (cicada, 2, "Cicadas buzz"),
        "ambient.wind": (wind, 2, "Wind blows"), "ambient.jungle_bird": (jungle_bird, 2, "Jungle bird calls"),
    },
    "steamworks": {
        "engine.chuff": (chuff, 3, "Steam engine chuffs"), "boiler.hiss": (boiler_hiss, 2, "Boiler hisses"),
        "gear.clatter": (gear_clatter, 3, "Gears clatter"), "millstone.grind": (grind, 2, "Millstone grinds"),
        "hammer.strike": (hammer, 3, "Steam hammer strikes"), "fan.whoosh": (whoosh, 2, "Fan blows"),
        "drill.grind": (drill, 2, "Drill grinds"), "saw.buzz": (saw_buzz, 2, "Saw buzzes"),
        "belt.rumble": (lambda r: grind(r, 1.0), 2, "Belt rumbles"), "pumpjack.creak": (creak, 2, "Pumpjack creaks"),
        "windmill.woosh": (windmill_woosh, 2, "Wind blows past the windmill"), "arm.servo": (servo, 3, "Robot arm whirs"),
        "train.chuff": (lambda r: chuff(r, 0.25), 3, "Locomotive chuffs"), "train.whistle": (whistle, 1, "Train whistles"),
        "train.brake": (brake, 1, "Train brakes"), "train.clack": (rail_clack, 2, "Rails clack"),
        "station.bell": (bell, 1, "Station bell rings"), "machine.hum": (hum, 2, "Machines hum"),
        # Quest-stemmen (30-10): conversation-blips, te spelen via BetonQuest
        # "notify io:sound sound:steamworks:voice.milo". Laatst in het blok, dus
        # alle bestaande events blijven byte-identisch.
        "voice.milo": (voice_milo, 3, "Milo chatters"), "voice.machine": (voice_machine, 3, "A machine chatters"),
        "quest.complete": (quest_sting, 1, "Quest complete"), "quest.click": (quest_click, 1, "A choice is made"),
    },
    "kitchen": {
        "knife.chop": (chop, 4, "Knife chops"), "pot.simmer": (simmer, 2, "Pot simmers"), "grill.sizzle": (sizzle, 2, "Food sizzles"),
        "pour": (pour, 2, "Liquid pours"), "chopsticks.clack": (chopsticks, 3, "Chopsticks clack"),
        "eat.slurp": (slurp, 2, "Someone slurps"), "stove.ignite": (ignite, 1, "Stove lights"),
    },
    "relics": {
        "grave.whisper": (whisper, 4, "Something whispers"), "egg_sac.heartbeat": (heartbeat, 2, "Something throbs"),
        "egg_sac.squelch": (wet_squelch, 2, "Something squelches"), "flame.crackle": (crackle, 2, "Flame crackles"),
        "portal.hum": (portal_hum, 2, "Portal shard hums"), "totem.alarm": (totem_alarm, 1, "Warding totem warns"),
        "cage.creak": (lambda r: creak(r, 200, 235, 1.1), 2, "Cage creaks"), "skull.growl": (growl_distant, 2, "Distant growl"),
        "sleigh.bells": (sleigh_bells, 2, "Sleigh bells jingle"), "gift.open": (gift_open, 1, "Present opens"),
        "snow_globe.chime": (glitter_chime, 2, "Snow globe chimes"), "advent.open": (advent_door, 1, "Calendar door opens"),
        "toy_train.chuff": (lambda r: gain(chuff(r, 0.15), 0.6), 2, "Toy train puffs"), "hearth.crackle": (lambda r: crackle(r, 2.5), 2, "Fire crackles"),
        "music_box.jingle_bells": (lambda r: melody(JINGLE_BELLS), 1, "Music box plays"),
        "music_box.deck_the_halls": (lambda r: melody(DECK_THE_HALLS), 1, "Music box plays"),
        "music_box.we_wish": (lambda r: melody(WE_WISH), 1, "Music box plays"),
        "temple_bell.toll": (temple_toll, 1, "Temple bell tolls"), "clock.strike": (clock_strike, 1, "Clock strikes"),
        "clock.tick": (clock_tick, 2, "Clock ticks"), "confetti.pop": (confetti_pop, 2, "Confetti pops"), "sparkler.fizz": (fizz, 2, "Sparkler fizzes"),
        "egg.found": (egg_found, 1, "Egg found"), "gold.jingle": (coins, 2, "Gold jingles"), "harp.play": (harp_arpeggios, 1, "Harp plays"),
        "tesla.zap": (zap, 3, "Tesla coil zaps"), "gramophone.sakura": (gramophone_sakura, 1, "Gramophone plays"),
        "shishi_odoshi.clack": (bamboo_clack, 2, "Bamboo clacks"),
        "harp.minstrel_boy": (tune_fn(MINSTREL_BOY), 1, "Harp plays"), "harp.londonderry_air": (tune_fn(LONDONDERRY, 0.5), 1, "Harp plays"),
        "harp.molly_malone": (tune_fn(MOLLY_MALONE, 0.36), 1, "Harp plays"), "harp.county_down": (tune_fn(COUNTY_DOWN, 0.34), 1, "Harp plays"),
        "harp.fair_moved": (tune_fn(FAIR_MOVED, 0.46), 1, "Harp plays"),
        "bones.shuffle": (lambda r: bone_tune(BONE_SHUFFLE, 0.3), 1, "Bones clatter a tune"), "bones.waltz": (lambda r: bone_tune(BONE_WALTZ, 0.36), 1, "Bones clatter a tune"),
        "bones.lullaby": (lambda r: bone_tune(BONE_LULLABY, 0.4), 1, "Bones clatter a tune"), "bones.mountain_king": (mountain_king, 1, "Bones clatter a tune"),
    },
    "sakagura": {
        "cauldron.add": (splash, 3, "Ingredient splashes in"), "cauldron.bubble": (lambda r: simmer(r, 1.2), 3, "Cauldron bubbles"),
        "distill.drip": (drips, 2, "Still drips"), "cask.open": (lambda r: creak(r, 180, 210, 0.6), 2, "Cask creaks open"),
        "drunk.hiccup": (hiccup, 3, "Hic!"), "drunk.puke": (puke, 2, "Someone throws up"),
    },
}

# Wraith (Wildlife 0.7.0): appended AFTER both CATALOGUE and EXTRA. The RNG stream must not
# shift for anything already in the pack, so new events never go into EXTRA's wildlife block
# (that block is drawn BEFORE the relics EXTRA events, which would all change byte-wise).
LATE = {
    "wildlife": {
        "wraith.ambient": (wraith_moan, 3, "Wraith moans"), "wraith.hurt": (wraith_shriek, 2, "Wraith shrieks"),
        "wraith.death": (wraith_fade, 2, "Wraith fades away"),
    },
}


# Generated after every other namespace so the seeded random stream of all existing sounds stays identical.
# ------------------------------------------------------------------ relics: music disc "Silent Night" (Gruber, public domain)
SILENT_NIGHT = [("G4", 1.5), ("A4", .5), ("G4", 1), ("E4", 3), ("G4", 1.5), ("A4", .5), ("G4", 1), ("E4", 3),
                ("D5", 2), ("D5", 1), ("B4", 3), ("C5", 2), ("C5", 1), ("G4", 3),
                ("A4", 2), ("A4", 1), ("C5", 1.5), ("B4", .5), ("A4", 1), ("G4", 1.5), ("A4", .5), ("G4", 1), ("E4", 3),
                ("A4", 2), ("A4", 1), ("C5", 1.5), ("B4", .5), ("A4", 1), ("G4", 1.5), ("A4", .5), ("G4", 1), ("E4", 3),
                ("D5", 2), ("D5", 1), ("F5", 1.5), ("D5", .5), ("B4", 1), ("C5", 3), ("E5", 3),
                ("C5", 1), ("G4", 1), ("E4", 1), ("G4", 1.5), ("F4", .5), ("D4", 1), ("C4", 3)]
SILENT_BASS = ["C3", "C3", "C3", "C3", "G3", "G3", "C3", "C3", "F3", "F3", "C3", "C3", "F3", "F3", "C3", "C3", "G3", "G3", "C3", "C3", "C3", "G3", "C3"]


def carol_disc(notes, beat, repeats):
    """A short carol melody repeated, the second half an octave up, with a pause between the rounds."""
    out, t = [], 0.0
    for k in range(repeats):
        for name, beats in notes:
            nm = name if k % 2 == 0 else name[:-1] + str(int(name[-1]) + 1) if name[-1] in "45" else name
            place(out, tine(note_freq(nm), 1.4), t)
            t += beats * beat
        t += 1.5 * beat
    return out


def silent_night_disc(rnd):
    """Two verses on a music box with a soft bass: about a minute."""
    beat = 0.62
    out, t = [], 0.0
    for verse in range(2):
        for name, beats in SILENT_NIGHT:
            place(out, tine(note_freq(name), 1.8), t)
            t += beats * beat
        t += 0.8 * beat
    # bass: the root of every bar, an octave below, under both verses
    bars = int(t / (3 * beat))
    for b in range(bars):
        place(out, gain(tine(note_freq(SILENT_BASS[b % len(SILENT_BASS)]), 2.2), 0.55), b * 3 * beat)
    return out


def songbird_sings(rnd):
    """Love Songbird: a few bright rising chirps with tiny gaps."""
    out, t = [], 0.0
    for k in range(rnd.randint(3, 5)):
        place(out, gain(chirp(rnd), 0.85), t)
        t += rnd.uniform(0.09, 0.18)
    place(out, gain(chirp(rnd), 0.55), t)
    return out


def wish_coin(rnd):
    """Wishing Well: coin plops into dark water, then one soft high bell for the wish."""
    splash = env(bandpass(noise(0.35, rnd), glide(1500, 500, 0.35), 1.5), 0.005, 0.06, 0.3, 0.2)
    out = gain(splash, 0.7)
    place(out, ring([(1046, 0.5), (1568, 0.35), (2093, 0.25)], 1.2, 0.5), 0.3)
    return out[:n(1.8)]


EXTRA = {
    "wildlife": {
        "elephant.ambient": (elephant_rumble, 2, "Elephant rumbles"), "elephant.hurt": (elephant_trumpet, 2, "Elephant trumpets"),
        "zebra.ambient": (zebra_bark, 3, "Zebra barks"), "zebra.hurt": (zebra_whinny, 2, "Zebra whinnies"),
    },
    "relics": {
        "disc.silent_night": (silent_night_disc, 1, "Music disc plays"),
        "disc.jingle_bells": (lambda r: carol_disc(JINGLE_BELLS, 0.3, 2), 1, "Music disc plays"),
        "disc.deck_the_halls": (lambda r: carol_disc(DECK_THE_HALLS, 0.3, 4), 1, "Music disc plays"),
        "disc.we_wish": (lambda r: carol_disc(WE_WISH, 0.3, 4), 1, "Music disc plays"),
        # Valentine's (27-10): appended last so every sound before it stays byte-identical.
        "valentine.chirp": (songbird_sings, 2, "Songbird sings"), "valentine.wish": (wish_coin, 2, "Wish lands in the well"),
    },
}


def write_wav(path, sig):
    sig = fade(normalize(sig))
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x)) * 32767)) for x in sig))


def main():
    ffmpeg = sys.argv[1] if len(sys.argv) > 1 else shutil.which("ffmpeg")
    if not ffmpeg:
        sys.exit("ffmpeg not found - install it (winget install --id Gyan.FFmpeg -e) or pass its path")
    rnd = random.Random(2026)
    shutil.rmtree(OUT, ignore_errors=True)
    tmp = os.path.join(OUT, "_wav")
    os.makedirs(tmp)
    total = 0
    for ns, events in CATALOGUE.items():
        sounds_json = {}
        for event, (fn, variants, subtitle) in events.items():
            group, _, name = event.rpartition(".")
            folder = (group or "misc").replace(".", "/")
            entries = []
            for v in range(variants):
                rel = f"{folder}/{name}_{v + 1}"
                wav = os.path.join(tmp, f"{ns}_{rel.replace('/', '_')}.wav")
                write_wav(wav, fn(rnd))
                ogg = os.path.join(OUT, ns, "sounds", rel + ".ogg")
                os.makedirs(os.path.dirname(ogg), exist_ok=True)
                subprocess.run([ffmpeg, "-loglevel", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-ac", "1", "-ar", "32000", "-b:a", "40k", ogg], check=True)
                entries.append(f"{ns}:{rel}")
                total += 1
            sounds_json[event] = {"sounds": entries, "subtitle": f"subtitles.{ns}.{event}"}
        with open(os.path.join(OUT, ns, "sounds.json"), "w", encoding="utf-8") as f:
            json.dump(sounds_json, f, indent=1)
        # English subtitles for the "Subtitles" accessibility option
        lang = {f"subtitles.{ns}.{e}": sub for e, (_, _, sub) in events.items()}
        os.makedirs(os.path.join(OUT, ns, "lang"), exist_ok=True)
        with open(os.path.join(OUT, ns, "lang", "en_us.json"), "w", encoding="utf-8") as f:
            json.dump(lang, f, indent=1)
    for extra in (EXTRA, LATE):   # LATE is drawn last: the seeded stream of every earlier sound stays byte-identical
        for ns, events in extra.items():
            jpath = os.path.join(OUT, ns, "sounds.json")
            lpath = os.path.join(OUT, ns, "lang", "en_us.json")
            sounds_json = json.load(open(jpath, encoding="utf-8"))
            lang = json.load(open(lpath, encoding="utf-8"))
            for event, (fn, variants, subtitle) in events.items():
                group, _, name = event.rpartition(".")
                folder = (group or "misc").replace(".", "/")
                entries = []
                for v in range(variants):
                    rel = f"{folder}/{name}_{v + 1}"
                    wav = os.path.join(tmp, f"{ns}_{rel.replace('/', '_')}.wav")
                    write_wav(wav, fn(rnd))
                    ogg = os.path.join(OUT, ns, "sounds", rel + ".ogg")
                    os.makedirs(os.path.dirname(ogg), exist_ok=True)
                    subprocess.run([ffmpeg, "-loglevel", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-ac", "1", "-ar", "32000", "-b:a", "40k", ogg], check=True)
                    entries.append(f"{ns}:{rel}")
                    total += 1
                sounds_json[event] = {"sounds": entries, "subtitle": f"subtitles.{ns}.{event}"}
                lang[f"subtitles.{ns}.{event}"] = subtitle
            json.dump(sounds_json, open(jpath, "w", encoding="utf-8"), indent=1)
            json.dump(lang, open(lpath, "w", encoding="utf-8"), indent=1)
    shutil.rmtree(tmp)
    print("wrote", total, "sounds to", OUT)


if __name__ == "__main__":
    main()
