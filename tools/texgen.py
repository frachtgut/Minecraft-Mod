"""
Starfallen texture generator: items, blocks, armor layers, particles, sky and effect textures.

    python3 tools/texgen.py
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageFilter  # noqa: E402

import item_art  # noqa: E402
from pixel import Canvas, Noise, ramp, mix, shade, clamp, from_art  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/starfallen")
TEX = os.path.join(ASSETS, "textures")


def save(img, *path):
    full = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    (img.img if isinstance(img, Canvas) else img).save(full)


def premultiply(cv):
    """Additive (eyes) render types ignore alpha, so fade the colour itself."""
    for y in range(cv.h):
        for x in range(cv.w):
            r, g, b, a = cv.get(x, y)
            f = a / 255.0
            cv.set(x, y, (clamp(r * f), clamp(g * f), clamp(b * f), a))
    return cv


def animate(frames, *path, frametime=4, interpolate=False):
    """Stack frames vertically and write the .mcmeta animation file."""
    w, h = frames[0].w, frames[0].h
    strip = Canvas(w, h * len(frames))
    for i, f in enumerate(frames):
        strip.paste(f, 0, i * h)
    save(strip, *path)
    meta = {"animation": {"frametime": frametime}}
    if interpolate:
        meta["animation"]["interpolate"] = True
    with open(os.path.join(TEX, *path) + ".mcmeta", "w") as fh:
        json.dump(meta, fh, indent=2)


# =============================================================================================
# Items
# =============================================================================================

def items():
    for name, (rows, palette, glow) in item_art.SPRITES.items():
        save(from_art(rows, palette), "item", f"{name}.png")
        if glow is not None:
            base = from_art(rows, palette)
            g = Canvas(16, 16)
            for y in range(16):
                for x in range(16):
                    if glow[y][x] != "." and base.get(x, y)[3] > 0:
                        g.set(x, y, base.get(x, y))
            save(g, "item", f"{name}_glow.png")
    item_variants()


def item_variants():
    """Model-override variants: the scythe with its blade in flight, and the hammer glowing while it charges."""
    rows, palette, glow = item_art.SPRITES["eclipse_scythe"]
    handle = ["." * 16 if y < 6 else row for y, row in enumerate(rows)]
    save(from_art(handle, palette), "item", "eclipse_scythe_thrown.png")
    g = Canvas(16, 16)
    base = from_art(handle, palette)
    for y in range(16):
        for x in range(16):
            if handle[y][x] in "yrk":
                g.set(x, y, base.get(x, y))
    save(g, "item", "eclipse_scythe_thrown_glow.png")

    rows, palette, glow = item_art.SPRITES["meteor_hammer"]
    hot = dict(palette)
    hot.update({"R": (170, 64, 26), "t": (226, 104, 40), "r": (120, 42, 22), "m": (255, 214, 110), "y": (255, 246, 200)})
    save(from_art(rows, hot), "item", "meteor_hammer_charging.png")
    base = from_art(rows, hot)
    g = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if rows[y][x] in "Rtrmy":
                g.set(x, y, base.get(x, y))
    save(g, "item", "meteor_hammer_charging_glow.png")


# =============================================================================================
# Blocks
# =============================================================================================

ROCK = [(22, 18, 17), (32, 26, 24), (44, 36, 32), (58, 47, 41), (72, 58, 49)]
VOIDB = [(10, 7, 20), (16, 11, 30), (23, 16, 42), (31, 22, 56), (40, 29, 70)]
ASTRALB = [(150, 170, 196), (172, 192, 216), (194, 212, 232), (214, 228, 244), (232, 242, 252)]


def noise_block(seed, colors, scale=0.35, jitter=0.12):
    rng = random.Random(seed)
    n = Noise(seed)
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            t = n.fbm(x * scale, y * scale, 3) * (1 - jitter) + rng.random() * jitter
            cv.set(x, y, ramp(colors, t))
    return cv


def meteorite(seed=1):
    cv = noise_block(seed, ROCK, 0.3)
    rng = random.Random(seed)
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        cv.set(x, y, (14, 11, 10))
        if rng.random() < 0.5:
            cv.set((x + 1) % 16, y, (18, 14, 13))
    for _ in range(4):
        cv.set(rng.randrange(16), rng.randrange(16), (120, 60, 30))
    for _ in range(3):
        cv.set(rng.randrange(16), rng.randrange(16), (98, 84, 72))
    return cv


def crack_paths(seed, count=4, length=9):
    rng = random.Random(seed)
    paths = []
    for _ in range(count):
        x, y = rng.randrange(16), rng.randrange(16)
        path = []
        for _ in range(length):
            path.append((x % 16, y % 16))
            r = rng.random()
            if r < 0.45:
                x += rng.choice((-1, 1))
            elif r < 0.9:
                y += rng.choice((-1, 1))
            else:
                x += rng.choice((-1, 1))
                y += rng.choice((-1, 1))
        paths.append(path)
    return paths


def molten_frames():
    frames = []
    paths = crack_paths(77, 6, 11)
    for f, heat in enumerate((0.75, 0.9, 1.0, 0.9)):
        cv = meteorite(5)
        for p in paths:
            for i, (x, y) in enumerate(p):
                c = mix((150, 45, 10), (255, 200, 90), heat * (0.6 + 0.4 * math.sin(i * 0.7 + f)))
                cv.set(x, y, c)
                # soft glow around the fissure
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = (x + dx) % 16, (y + dy) % 16
                    old = cv.get(nx, ny)
                    if old[0] < 120:
                        cv.set(nx, ny, mix(old, (200, 70, 20), 0.35 * heat))
        frames.append(cv)
    return frames


def ore():
    cv = meteorite(9)
    glow = Canvas(16, 16)
    rng = random.Random(90)
    metal = [(70, 80, 100), (120, 134, 160), (178, 192, 218), (230, 238, 250)]
    spots = [(3, 3), (10, 4), (5, 10), (12, 11), (8, 7)]
    for (sx, sy) in spots:
        cells = [(sx, sy), (sx + 1, sy), (sx, sy + 1)] + ([(sx + 1, sy + 1)] if rng.random() < 0.6 else [])
        for i, (x, y) in enumerate(cells):
            c = metal[3] if i == 0 else metal[2] if i == 1 else metal[1]
            cv.set(x, y, c)
            glow.set(x, y, (150, 200, 255) if i == 0 else (90, 130, 200))
        cv.set(sx - 1, sy + 1, metal[0])
    return cv, glow


def plates(colors, seam, rivet, seed, emblem=None, glow_seam=False):
    cv = noise_block(seed, colors, 0.5, 0.25)
    glow = Canvas(16, 16)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 8), (0, i), (8, i)):
            cv.set(x, y, seam)
            if glow_seam:
                glow.set(x, y, seam)
        for (x, y) in ((i, 7), (i, 15), (7, i), (15, i)):
            cv.set(x, y, shade(colors[0], 0.8))
    for (x, y) in ((2, 2), (5, 2), (2, 5), (5, 5), (10, 2), (13, 2), (10, 5), (13, 5), (2, 10), (5, 10), (2, 13), (5, 13),
                   (10, 10), (13, 10), (10, 13), (13, 13)):
        cv.set(x, y, rivet)
    if emblem:
        cx, cy = 7.5, 7.5
        for y in range(16):
            for x in range(16):
                dx, dy = x - cx, y - cy
                r = math.hypot(dx, dy)
                ang = math.atan2(dy, dx) + math.pi / 4
                frac = abs(((ang / (2 * math.pi / 4)) % 1.0) - 0.5) * 2
                if r <= 5.5 * (1 - frac) + 1.4 * frac:
                    cv.set(x, y, emblem)
                    glow.set(x, y, emblem)
    return cv, glow


def bricks(colors, mortar, seed, specks=None, cracked=False):
    rng = random.Random(seed)
    n = Noise(seed)
    cv = Canvas(16, 16)
    for y in range(16):
        row = y // 4
        offset = 4 if row % 2 else 0
        for x in range(16):
            if y % 4 == 3 or (x + offset) % 8 == 7:
                cv.set(x, y, mortar)
                continue
            brick = ((x + offset) // 8, row)
            base = 0.35 + 0.4 * ((hash(brick) % 100) / 100.0)
            t = base * 0.6 + n.fbm(x * 0.5, y * 0.5) * 0.3 + rng.random() * 0.1
            c = ramp(colors, t)
            if y % 4 == 0:
                c = shade(c, 1.08)
            cv.set(x, y, c)
    glow = Canvas(16, 16)
    if specks:
        for _ in range(5):
            x, y = rng.randrange(16), rng.randrange(16)
            if cv.get(x, y) != tuple(mortar) + (255,):
                cv.set(x, y, specks)
                glow.set(x, y, specks)
    if cracked:
        for path in crack_paths(seed + 3, 3, 8):
            for (x, y) in path:
                cv.set(x, y, shade(mortar, 0.7))
    return cv, glow


def pillar(colors, band, seed):
    side = Canvas(16, 16)
    n = Noise(seed)
    for y in range(16):
        for x in range(16):
            flute = 0.55 + 0.35 * math.cos((x % 4) / 4 * 2 * math.pi)
            t = flute * 0.7 + n.fbm(x * 0.4, y * 0.2) * 0.3
            side.set(x, y, ramp(colors, t))
    for x in range(16):
        side.set(x, 0, band)
        side.set(x, 15, band)
        side.set(x, 1, shade(band, 0.8))
    top = noise_block(seed + 1, colors, 0.4, 0.2)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if 5.5 < r < 6.6 or r < 1.5:
                top.set(x, y, band)
            if x in (0, 15) or y in (0, 15):
                top.set(x, y, shade(colors[0], 0.8))
    return side, top


def chiseled(colors, frame, emblem_fn, seed):
    cv = noise_block(seed, colors, 0.4, 0.2)
    glow = Canvas(16, 16)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i), (i, 1), (i, 14), (1, i), (14, i)):
            cv.set(x, y, frame if (x in (0, 15) or y in (0, 15)) else shade(frame, 0.75))
    emblem_fn(cv, glow)
    return cv, glow


def star_emblem(color, glow_color=None, radius=5.2):
    def fn(cv, glow):
        cx, cy = 7.5, 7.5
        for y in range(16):
            for x in range(16):
                dx, dy = x - cx, y - cy
                r = math.hypot(dx, dy)
                ang = math.atan2(dy, dx) + math.pi / 4
                frac = abs(((ang / (2 * math.pi / 4)) % 1.0) - 0.5) * 2
                if r <= radius * (1 - frac) + 1.3 * frac:
                    cv.set(x, y, color)
                    if glow_color:
                        glow.set(x, y, glow_color)
    return fn


def eye_emblem(cv, glow):
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.0, (y - 7.5) / 3.0
            if dx * dx + dy * dy <= 1.0:
                c = (40, 20, 80)
                r = math.hypot(x - 7.5, y - 7.5)
                if r < 2.6:
                    c = (200, 110, 255)
                if r < 1.2:
                    c = (255, 240, 255)
                cv.set(x, y, c)
                if r < 2.6:
                    glow.set(x, y, c)


def starfield(seed):
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    glow = Canvas(16, 16)
    n = Noise(seed)
    for y in range(16):
        for x in range(16):
            t = n.fbm(x * 0.3, y * 0.3)
            c = mix((6, 8, 22), (26, 16, 52), t)
            if x % 8 == 0 or y % 8 == 0:
                c = (30, 26, 58)
            cv.set(x, y, c)
    stars = [(3, 2), (11, 5), (6, 12), (13, 13), (2, 9), (9, 9)]
    for i, (x, y) in enumerate(stars):
        c = (255, 245, 200) if i % 3 == 0 else (170, 230, 255) if i % 3 == 1 else (230, 180, 255)
        cv.set(x, y, c)
        glow.set(x, y, c)
        if i % 3 == 0:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                cv.set(x + dx, y + dy, shade(c, 0.55))
                glow.set(x + dx, y + dy, shade(c, 0.55))
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        cv.set(x, y, (120, 120, 160))
        glow.set(x, y, (90, 90, 130))
    return cv, glow


def lamp_frames():
    frames = []
    for f in range(4):
        cv = Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                edge = x in (0, 15) or y in (0, 15)
                inner = x in (1, 14) or y in (1, 14)
                if edge:
                    c = (60, 70, 110)
                elif inner:
                    c = (150, 170, 220)
                else:
                    r = math.hypot(x - 7.5, y - 7.5)
                    c = mix((255, 255, 240), (140, 220, 255), min(1, r / 7))
                cv.set(x, y, c)
        # twinkling stars inside
        rng = random.Random(f * 13 + 5)
        for _ in range(4):
            x, y = 3 + rng.randrange(10), 3 + rng.randrange(10)
            cv.set(x, y, (255, 255, 255))
        for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
            cv.set(x, y, (255, 214, 110))
        frames.append(cv)
    return frames


def seal_frames():
    frames = []
    glyphs = ["x.x.xx", ".xx.x.", "x..xx.", "xx.x.x"]
    for f in range(8):
        cv = Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                t = 0.5 + 0.5 * math.sin((y + f * 2) * 0.8 + x * 0.3)
                cv.set(x, y, (90 + int(60 * t), 200 + int(40 * t), 255, 90 + int(60 * t)))
        for gy in range(0, 16, 5):
            yy = (gy - f * 2) % 16
            g = glyphs[(gy // 5 + f) % 4]
            for i, ch in enumerate(g):
                if ch == "x":
                    cv.set(5 + i, yy, (230, 255, 255, 230))
                    cv.set(5 + i, (yy + 1) % 16, (200, 245, 255, 200))
        for y in range(16):
            cv.set(0, y, (170, 240, 255, 200))
            cv.set(15, y, (170, 240, 255, 200))
        frames.append(cv)
    return frames


def crystal_cross():
    cv = Canvas(16, 16)
    spikes = [(4, 6, 3), (8, 1, 4), (12, 5, 3)]
    for (cx, top, w) in spikes:
        for y in range(top, 16):
            half = max(0, int((y - top) * 0.45))
            half = min(half, w // 2 + 1)
            for x in range(cx - half, cx + half + 1):
                t = (y - top) / (16 - top)
                c = mix((235, 255, 255), (40, 130, 200), t)
                if x == cx - half:
                    c = shade(c, 1.15)
                if x == cx + half and half > 0:
                    c = shade(c, 0.75)
                cv.set(x, y, c)
    return cv


def egg_stage(stage):
    cv = Canvas(16, 16)
    glow = Canvas(16, 16)
    rng = random.Random(40 + stage)
    n = Noise(41)
    for y in range(16):
        for x in range(16):
            c = mix((20, 30, 80), (50, 80, 150), n.fbm(x * 0.3, y * 0.3))
            cv.set(x, y, c)
    for _ in range(8):
        x, y = rng.randrange(16), rng.randrange(16)
        cv.set(x, y, (255, 245, 200))
        glow.set(x, y, (255, 245, 200))
    for i in range(stage * 2):
        for (x, y) in crack_paths(100 + i, 1, 10)[0]:
            cv.set(x, y, (140, 240, 255))
            glow.set(x, y, (140, 240, 255))
    return cv, glow


def telescope_textures():
    brass = noise_block(61, [(120, 80, 30), (170, 120, 50), (214, 166, 80), (240, 206, 120)], 0.6, 0.2)
    for y in range(0, 16, 5):
        for x in range(16):
            brass.set(x, y, (250, 225, 150))
    wood = noise_block(62, [(52, 32, 20), (72, 46, 28), (92, 60, 36)], 0.3, 0.2)
    for x in range(0, 16, 4):
        for y in range(16):
            wood.set(x, y, (44, 28, 18))
    lens = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            lens.set(x, y, mix((230, 255, 255), (40, 120, 190), min(1, r / 8)))
    return brass, wood, lens


def brazier_textures():
    metal = noise_block(71, [(24, 20, 30), (38, 32, 46), (54, 46, 64)], 0.5, 0.2)
    for x in range(16):
        metal.set(x, 0, (214, 170, 80))
        metal.set(x, 1, (160, 120, 50))
    ash = noise_block(72, [(40, 36, 40), (62, 58, 64), (84, 80, 88)], 0.6, 0.3)
    coals = Canvas(16, 16)
    glow = Canvas(16, 16)
    rng = random.Random(73)
    for y in range(16):
        for x in range(16):
            c = mix((60, 20, 70), (120, 220, 255), rng.random() ** 2)
            coals.set(x, y, c)
            glow.set(x, y, c)
    return metal, ash, coals, glow


def altar_textures():
    side, glow = bricks(VOIDB, (6, 4, 12), 81, specks=(255, 214, 110))
    for x in range(16):
        side.set(x, 0, (214, 170, 80))
        side.set(x, 15, (214, 170, 80))
    for (x, y) in ((4, 6), (5, 7), (4, 8), (11, 6), (10, 7), (11, 8), (7, 5), (8, 5), (7, 10), (8, 10)):
        side.set(x, y, (200, 120, 255))
        glow.set(x, y, (200, 120, 255))
    top = Canvas(16, 16)
    tglow = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            c = (22, 15, 40)
            if 5.5 < r < 6.8:
                c = (214, 170, 80)
            if r < 3.2:
                c = mix((255, 250, 230), (190, 110, 255), r / 3.2)
                tglow.set(x, y, c)
            if x in (0, 15) or y in (0, 15):
                c = (214, 170, 80)
            top.set(x, y, c)
    return side, glow, top, tglow


def spike_textures():
    top, _ = bricks(VOIDB, (6, 4, 12), 91)
    for (hx, hy) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        for dx in (-1, 0):
            for dy in (-1, 0):
                top.set(hx + dx, hy + dy, (4, 2, 8))
    spike = Canvas(16, 16)
    for y in range(16):
        half = 0.6 + y * 0.5
        for x in range(16):
            d = abs(x - 7.5)
            if d <= half:
                c = mix((240, 244, 252), (80, 86, 112), y / 15)
                if x > 7.5:
                    c = shade(c, 0.72)
                spike.set(x, y, c)
    return top, spike


def flame_jet_front():
    cv, _ = bricks(VOIDB, (6, 4, 12), 95)
    glow = Canvas(16, 16)
    for y in range(3, 13):
        for x in range(3, 13):
            if (x + y) % 3 == 0 or x in (3, 12) or y in (3, 12):
                cv.set(x, y, (40, 36, 50))
            else:
                c = mix((255, 220, 120), (200, 60, 20), math.hypot(x - 7.5, y - 7.5) / 6)
                cv.set(x, y, c)
                glow.set(x, y, c)
    return cv, glow


def cage():
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                cv.set(x, y, (60, 44, 90))
            elif x in (5, 10) or y in (5, 10):
                cv.set(x, y, (120, 96, 170))
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        cv.set(x, y, (214, 170, 80))
    return cv


def blocks():
    save(meteorite(3), "block", "meteorite.png")
    animate(molten_frames(), "block", "molten_meteorite.png", frametime=10, interpolate=True)
    o, og = ore()
    save(o, "block", "meteoric_iron_ore.png")
    save(og, "block", "meteoric_iron_ore_glow.png")
    b, _ = plates([(40, 44, 56), (60, 66, 82), (82, 90, 110), (104, 114, 138)], (24, 26, 34), (150, 160, 184), 11)
    save(b, "block", "meteoric_iron_block.png")
    b, g = plates([(30, 110, 160), (50, 150, 200), (90, 200, 235), (150, 235, 255)], (20, 70, 110), (220, 250, 255), 12, emblem=(255, 214, 110))
    save(b, "block", "astral_alloy_block.png")
    save(g, "block", "astral_alloy_block_glow.png")
    b, g = plates([(22, 12, 48), (34, 20, 70), (48, 30, 96), (62, 40, 120)], (190, 90, 255), (120, 90, 180), 13, glow_seam=True)
    save(b, "block", "voidsteel_block.png")
    save(g, "block", "voidsteel_block_glow.png")
    save(crystal_cross(), "block", "starlit_crystal.png")
    fs = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            c = mix((255, 255, 240), (255, 190, 80), min(1, r / 9))
            if (x * 7 + y * 3) % 11 == 0:
                c = (255, 255, 255)
            fs.set(x, y, c)
    save(fs, "block", "fallen_star.png")

    a, _ = bricks(ASTRALB, (120, 132, 158), 21, specks=(230, 190, 90))
    save(a, "block", "astral_bricks.png")
    a, _ = bricks(ASTRALB, (120, 132, 158), 21, specks=(230, 190, 90), cracked=True)
    save(a, "block", "cracked_astral_bricks.png")
    c, g = chiseled(ASTRALB, (190, 150, 70), star_emblem((255, 214, 110), (255, 230, 150)), 23)
    save(c, "block", "chiseled_astral_bricks.png")
    save(g, "block", "chiseled_astral_bricks_glow.png")
    s, t = pillar(ASTRALB, (200, 160, 80), 24)
    save(s, "block", "astral_pillar.png")
    save(t, "block", "astral_pillar_top.png")

    v, vg = bricks(VOIDB, (6, 4, 12), 31, specks=(210, 190, 255))
    save(v, "block", "void_bricks.png")
    save(vg, "block", "void_bricks_glow.png")
    v, _ = bricks(VOIDB, (6, 4, 12), 31, specks=(210, 190, 255), cracked=True)
    save(v, "block", "cracked_void_bricks.png")
    v2, _ = bricks(VOIDB, (6, 4, 12), 31, specks=(210, 190, 255), cracked=True)
    for (x, y) in crack_paths(333, 2, 6)[0]:
        v2.set(x, y, (4, 2, 8))
    save(v2, "block", "crumbling_void_bricks.png")
    c, g = chiseled(VOIDB, (90, 60, 150), eye_emblem, 33)
    save(c, "block", "chiseled_void_bricks.png")
    save(g, "block", "chiseled_void_bricks_glow.png")
    s, t = pillar(VOIDB, (120, 80, 190), 34)
    save(s, "block", "void_pillar.png")
    save(t, "block", "void_pillar_top.png")
    sf, sg = starfield(41)
    save(sf, "block", "starfield_tiles.png")
    save(sg, "block", "starfield_tiles_glow.png")
    animate(lamp_frames(), "block", "starlight_lamp.png", frametime=12, interpolate=True)
    animate(seal_frames(), "block", "sanctum_seal.png", frametime=3)
    k, kg = chiseled(VOIDB, (140, 220, 255), star_emblem((150, 235, 255), (190, 245, 255), 6.0), 44)
    save(k, "block", "seal_keystone.png")
    save(kg, "block", "seal_keystone_glow.png")
    ki, _ = chiseled(VOIDB, (60, 50, 90), star_emblem((50, 60, 90), None, 6.0), 44)
    save(ki, "block", "seal_keystone_inactive.png")
    side, sg, top, tg = altar_textures()
    save(side, "block", "star_altar_side.png")
    save(sg, "block", "star_altar_side_glow.png")
    save(top, "block", "star_altar_top.png")
    save(tg, "block", "star_altar_top_glow.png")
    st, sp = spike_textures()
    save(st, "block", "spike_trap_top.png")
    save(sp, "block", "spike.png")
    ff, fg = flame_jet_front()
    save(ff, "block", "flame_jet_front.png")
    save(fg, "block", "flame_jet_front_glow.png")
    save(cage(), "block", "sentinel_cage.png")
    metal, ash, coals, cglow = brazier_textures()
    save(metal, "block", "brazier_metal.png")
    save(ash, "block", "brazier_ash.png")
    save(coals, "block", "brazier_coals.png")
    save(cglow, "block", "brazier_coals_glow.png")
    brass, wood, lens = telescope_textures()
    save(brass, "block", "telescope_brass.png")
    save(wood, "block", "telescope_wood.png")
    save(lens, "block", "telescope_lens.png")
    for stage in range(3):
        e, eg = egg_stage(stage)
        save(e, "block", f"stellar_egg_{stage}.png")
        save(eg, "block", f"stellar_egg_{stage}_glow.png")


# =============================================================================================
# Armor layers (64x32)
# =============================================================================================

def armor_layers():
    sets = {
        "meteoric": ([(40, 44, 56), (60, 66, 82), (82, 90, 110), (110, 120, 146)], (255, 120, 34), (26, 22, 30)),
        "starforged": ([(24, 90, 140), (40, 140, 190), (86, 206, 240), (170, 240, 255)], (255, 201, 74), (12, 40, 70)),
        "voidwalker": ([(18, 10, 42), (32, 20, 72), (52, 34, 110), (80, 54, 160)], (200, 90, 255), (8, 4, 20)),
    }
    for name, (cols, accent, dark) in sets.items():
        n = Noise(hash(name) & 0xFFFF)
        l1 = Canvas(64, 32)
        l2 = Canvas(64, 32)

        def fill(cv, x0, y0, x1, y1, trim_rows=()):
            for y in range(y0, y1):
                for x in range(x0, x1):
                    t = n.fbm(x * 0.3, y * 0.3)
                    c = ramp(cols, t)
                    if y in trim_rows:
                        c = accent
                    cv.set(x, y, c)

        # helmet (head box at 0,0)
        fill(l1, 0, 0, 32, 16)
        for x in range(8, 16):            # visor slit on the front face
            l1.set(x, 12, dark)
            l1.set(x, 13, dark)
        for x in range(8, 16):
            l1.set(x, 8, accent)
        l1.set(11, 10, accent)
        l1.set(12, 10, accent)
        # chestplate: body (16,16)-(40,32), arms (40,16)-(56,32)
        fill(l1, 16, 16, 40, 32)
        fill(l1, 40, 16, 56, 32, trim_rows=(31,))
        for y in range(22, 30):           # emblem down the chest
            l1.set(23 + (y % 2), y, accent)
            l1.set(24 - (y % 2), y, shade(accent, 0.8))
        for x in range(20, 28):
            l1.set(x, 20, shade(cols[3], 1.1))
        # boots: lower part of the leg box (0,16)-(16,32)
        fill(l1, 0, 26, 16, 32, trim_rows=(26,))
        fill(l1, 4, 16, 12, 20)
        # leggings (layer 2): belt on the body box + full legs
        fill(l2, 16, 16, 40, 32)
        for y in range(16, 26):
            for x in range(16, 40):
                l2.set(x, y, (0, 0, 0, 0))
        for x in range(20, 28):
            l2.set(x, 26, accent)
        fill(l2, 0, 16, 16, 32)
        for y in range(20, 32, 4):
            l2.set(5, y, accent)
            l2.set(6, y, accent)
        save(l1, "models", "armor", f"{name}_layer_1.png")
        save(l2, "models", "armor", f"{name}_layer_2.png")
    # The halo is drawn by its own render layer: its armor texture is fully transparent.
    save(Canvas(64, 32), "models", "armor", "celestial_layer_1.png")
    save(Canvas(64, 32), "models", "armor", "celestial_layer_2.png")


# =============================================================================================
# Particles, sky and effects
# =============================================================================================

def star_shape(size, radius, inner, color=(255, 255, 255), points=4):
    cv = Canvas(size, size)
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx) + math.pi / points + math.pi / 2
            frac = abs(((ang / (2 * math.pi / points)) % 1.0) - 0.5) * 2
            limit = radius * (1 - frac) + inner * frac
            if r <= limit:
                a = 255 if r < limit - 0.8 else 160
                cv.set(x, y, color + (a,))
    return cv


def particles():
    for i, (r, inner) in enumerate(((3.9, 1.1), (3.3, 1.0), (2.4, 0.9), (1.5, 0.7))):
        save(star_shape(8, r, inner), "particle", f"star_spark_{i}.png")
    for i in range(2):
        cv = Canvas(8, 8)
        for y in range(8):
            for x in range(8):
                d = math.hypot(x - 3.5, y - 3.5)
                if d < 2.2 - i * 0.5:
                    cv.set(x, y, (255, 255, 255, 255 if d < 1.2 else 170))
        save(cv, "particle", f"ember_{i}.png")
    for i in range(8):
        cv = Canvas(16, 16)
        rng = random.Random(500 + i)
        n = Noise(600 + i)
        rad = 3.5 + i * 0.5
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5) + (n.fbm(x * 0.4, y * 0.4) - 0.5) * 3
                if d < rad:
                    v = clamp(200 + 55 * n.fbm(x * 0.5, y * 0.5))
                    a = clamp(230 * (1 - d / rad) + 25)
                    cv.set(x, y, (v, v, v, a))
        save(cv, "particle", f"meteor_smoke_{i}.png")
    ring = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if 12 < d < 15.8:
                a = clamp(255 * (1 - abs(d - 14.2) / 2.2))
                ring.set(x, y, (255, 255, 255, a))
    save(ring, "particle", "shockwave.png")
    flash = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 16
            if d < 1:
                flash.set(x, y, (255, 255, 255, clamp(255 * (1 - d) ** 1.6)))
    save(flash, "particle", "nova_flash.png")
    glyphs = [
        ["..x.....", ".xxx....", "..x.x...", "..x..x..", "..xxxx..", ".....x..", "....x...", "........"],
        ["x.....x.", ".x...x..", "..x.x...", "...x....", "..x.x...", ".x...x..", "........", "........"],
        ["..xxxx..", ".x....x.", ".x.xx.x.", ".x.xx.x.", ".x....x.", "..xxxx..", "........", "........"],
        ["...x....", "...x....", ".xxxxx..", "...x....", "..x.x...", ".x...x..", "........", "........"],
    ]
    for i, g in enumerate(glyphs):
        cv = Canvas(8, 8)
        for y, row in enumerate(g):
            for x, ch in enumerate(row):
                if ch == "x":
                    cv.set(x, y, (255, 255, 255, 255))
        save(cv, "particle", f"rune_{i}.png")


def environment():
    streak = Canvas(64, 16)
    for y in range(16):
        for x in range(64):
            t = x / 63
            dv = abs(y - 7.5) / 7.5
            a = t ** 1.8 * max(0, 1 - dv * (1.6 - t * 0.6))
            c = mix((255, 170, 90), (255, 255, 240), t)
            if a > 0.01:
                streak.set(x, y, c + (clamp(255 * a),))
    save(premultiply(streak), "environment", "shooting_star.png")

    neb = Canvas(256, 64)
    n = Noise(777)
    n2 = Noise(778)
    for y in range(64):
        for x in range(256):
            u = x / 256.0
            v = y / 64.0
            base = n.fbm(u * 8, v * 3, 4)
            wisps = n2.fbm(u * 16 + base * 2, v * 6, 3)
            fade = math.sin(v * math.pi) ** 1.5
            a = max(0.0, (base * 0.7 + wisps * 0.5) - 0.55) * 2.2 * fade
            c = ramp([(80, 40, 160), (190, 80, 220), (255, 140, 200), (120, 200, 255)], min(1, base * 0.8 + wisps * 0.4))
            if a > 0.01:
                neb.set(x, y, c + (clamp(255 * min(1, a)),))
    rng = random.Random(3)
    for _ in range(90):
        x, y = rng.randrange(256), rng.randrange(64)
        neb.set(x, y, (255, 255, 255, 200))
    save(neb, "environment", "nebula.png")

    ecl = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            dx, dy = x - 31.5, y - 31.5
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            rays = 0.5 + 0.5 * math.sin(ang * 9) * math.sin(ang * 5 + 1)
            if d < 10:
                continue
            if d < 32:
                inten = max(0.0, 1 - (d - 10) / (6 + 16 * rays))
                if inten > 0:
                    c = mix((255, 250, 235), (200, 100, 255), min(1, (d - 10) / 12))
                    ecl.set(x, y, c + (clamp(255 * inten),))
    save(ecl, "environment", "eclipse.png")


def effects():
    flare = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 16
            if d < 1:
                a = (1 - d) ** 2.2
                flare.set(x, y, (255, 255, 255, clamp(255 * a)))
    save(premultiply(flare), "entity", "flare.png")
    save(premultiply(star_shape(16, 7.8, 2.0)), "entity", "star_bolt.png")

    arrow = Canvas(32, 32)
    for x in range(16):
        c = mix((120, 220, 255), (255, 255, 255), x / 15)
        arrow.set(x, 2, c)
        if x > 11:
            arrow.set(x, 1, (220, 250, 255))
            arrow.set(x, 3, (220, 250, 255))
        if x < 4:
            arrow.set(x, 1, (170, 120, 255))
            arrow.set(x, 3, (170, 120, 255))
    for y in range(5, 10):
        for x in range(5):
            if x == 2 or y == 7:
                arrow.set(x, y, (200, 240, 255))
    save(arrow, "entity", "star_arrow.png")

    band = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            a = (1 - y / 15) ** 1.4
            streaks = 0.75 + 0.25 * math.sin(x * 1.7)
            band.set(x, y, (255, 255, 255, clamp(255 * a * streaks)))
    save(premultiply(band), "entity", "shockwave.png")

    beam = Canvas(16, 16)
    n = Noise(88)
    for y in range(16):
        for x in range(16):
            v = clamp(170 + 85 * n.fbm(x * 0.8, y * 0.15))
            beam.set(x, y, (v, v, v, 255))
    save(beam, "entity", "void_beam.png")

    disk = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            dx, dy = x - 31.5, y - 31.5
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            if 9 < d < 31:
                swirl = 0.5 + 0.5 * math.sin(ang * 3 + d * 0.5)
                a = math.sin((d - 9) / 22 * math.pi) * (0.55 + 0.45 * swirl)
                c = mix((255, 240, 220), (160, 70, 255), (d - 9) / 22)
                disk.set(x, y, c + (clamp(255 * a),))
    save(premultiply(disk), "entity", "accretion_disk.png")


def logo():
    font = {
        "S": ["xxxx", "x...", "xxxx", "...x", "xxxx"], "T": ["xxxxx", "..x..", "..x..", "..x..", "..x.."],
        "A": [".xx.", "x..x", "xxxx", "x..x", "x..x"], "R": ["xxx.", "x..x", "xxx.", "x.x.", "x..x"],
        "F": ["xxxx", "x...", "xxx.", "x...", "x..."], "L": ["x...", "x...", "x...", "x...", "xxxx"],
        "E": ["xxxx", "x...", "xxx.", "x...", "xxxx"], "N": ["x..x", "xx.x", "x.xx", "x..x", "x..x"],
    }
    text = "STARFALLEN"
    scale = 7
    width = sum(len(font[c][0]) + 1 for c in text) * scale
    img = Canvas(width + 40, 5 * scale + 60)
    n = Noise(9)
    for y in range(img.h):
        for x in range(img.w):
            t = n.fbm(x * 0.02, y * 0.05)
            img.set(x, y, mix((8, 6, 24), (40, 20, 70), t))
    rng = random.Random(4)
    for _ in range(120):
        img.set(rng.randrange(img.w), rng.randrange(img.h), (255, 255, 255))
    x0 = 20
    for c in text:
        g = font[c]
        for gy, row in enumerate(g):
            for gx, ch in enumerate(row):
                if ch == "x":
                    for yy in range(scale):
                        for xx in range(scale):
                            col = mix((255, 246, 214), (255, 170, 60), (gy * scale + yy) / (5 * scale))
                            img.set(x0 + gx * scale + xx, 30 + gy * scale + yy, col)
        x0 += (len(g[0]) + 1) * scale
    out = os.path.join(ROOT, "src/main/resources/starfallen_logo.png")
    img.img.save(out)


def mob_effects():
    icon = Canvas(18, 18)
    star = star_shape(18, 8.6, 2.6, (255, 227, 138))
    icon.paste(star, 0, 0)
    for (x, y) in ((4, 3), (14, 4), (3, 14), (13, 13)):
        icon.set(x, y, (255, 255, 255))
    for (x, y) in ((8, 8), (9, 8), (8, 9), (9, 9)):
        icon.set(x, y, (255, 255, 255))
    save(icon, "mob_effect", "starstruck.png")


def main():
    mob_effects()
    items()
    blocks()
    armor_layers()
    particles()
    environment()
    effects()
    logo()
    print("textures written to", TEX)


if __name__ == "__main__":
    main()
