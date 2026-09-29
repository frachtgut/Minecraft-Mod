"""
Starfallen data generator: blockstates, block/item models, particles, sounds are elsewhere; this
writes models, recipes, loot tables, tags, damage types, advancements, worldgen and the language file.

    python3 tools/datagen.py
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import lang_en  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/starfallen")
DATA = os.path.join(RES, "data")
NS = "starfallen"


def sf(path):
    return f"{NS}:{path}"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def asset(*p):
    return os.path.join(ASSETS, *p)


def data(ns, *p):
    return os.path.join(DATA, ns, *p)


# =============================================================================================
# Block models & blockstates
# =============================================================================================

DIRS = ("down", "up", "north", "south", "west", "east")
FULLBRIGHT = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def block_model(name, obj):
    write_json(asset("models", "block", f"{name}.json"), obj)


def blockstate(name, obj):
    write_json(asset("blockstates", f"{name}.json"), obj)


def simple_state(name, model=None):
    blockstate(name, {"variants": {"": {"model": sf(f"block/{model or name}")}}})


def cube_all(name, texture=None):
    block_model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": sf(f"block/{texture or name}")}})


def cube_faces(tex, glow=None, cull=True):
    faces = {d: {"texture": tex, **({"cullface": d} if cull else {})} for d in DIRS}
    return faces


def glow_cube(name, texture, glow):
    """A full cube with a second, full-bright overlay cube for the glowing details."""
    block_model(name, {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": sf(f"block/{texture}"), "all": sf(f"block/{texture}"), "glow": sf(f"block/{glow}")},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": cube_faces("#all")},
            {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "forge_data": FULLBRIGHT, "faces": cube_faces("#glow")},
        ],
    })


def item_block(name, model=None):
    write_json(asset("models", "item", f"{name}.json"), {"parent": sf(f"block/{model or name}")})


def stairs_slab_wall(base, tex, prefix):
    """Generates stairs, slab and wall models + states named <prefix>_stairs etc."""
    t = sf(f"block/{tex}")
    stairs, slab, wall = f"{prefix}_stairs", f"{prefix}_slab", f"{prefix}_wall"
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        block_model(stairs + suffix, {"parent": f"minecraft:block/{parent}", "textures": {"bottom": t, "side": t, "top": t}})
    block_model(slab, {"parent": "minecraft:block/slab", "textures": {"bottom": t, "side": t, "top": t}})
    block_model(slab + "_top", {"parent": "minecraft:block/slab_top", "textures": {"bottom": t, "side": t, "top": t}})
    for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                           ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")):
        block_model(wall + suffix, {"parent": f"minecraft:block/{parent}", "textures": {"wall": t}})

    # stairs state, transformed from the vanilla layout
    variants = {}
    facings = {"east": 0, "south": 90, "west": 180, "north": 270}
    for facing, fy in facings.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = stairs + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                y = fy
                if shape in ("inner_left", "outer_left"):
                    y = (fy + 270) % 360
                x = 0
                if half == "top":
                    x = 180
                    if shape in ("inner_left", "outer_left"):
                        y = fy
                    elif shape in ("inner_right", "outer_right"):
                        y = (fy + 90) % 360
                v = {"model": sf(f"block/{model}")}
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                if x or y:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    blockstate(stairs, {"variants": variants})
    blockstate(slab, {"variants": {
        "type=bottom": {"model": sf(f"block/{slab}")},
        "type=top": {"model": sf(f"block/{slab}_top")},
        "type=double": {"model": sf(f"block/{base}")},
    }})
    parts = [{"when": {"up": "true"}, "apply": {"model": sf(f"block/{wall}_post")}}]
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for height, suffix in (("low", "_side"), ("tall", "_side_tall")):
            apply = {"model": sf(f"block/{wall}{suffix}"), "uvlock": True}
            if y:
                apply["y"] = y
            parts.append({"when": {side: height}, "apply": apply})
    blockstate(wall, {"multipart": parts})
    item_block(stairs)
    item_block(slab)
    item_block(wall, f"{wall}_inventory")


def pillar(name):
    block_model(name, {"parent": "minecraft:block/cube_column",
                       "textures": {"end": sf(f"block/{name}_top"), "side": sf(f"block/{name}")}})
    block_model(name + "_horizontal", {"parent": "minecraft:block/cube_column_horizontal",
                                       "textures": {"end": sf(f"block/{name}_top"), "side": sf(f"block/{name}")}})
    blockstate(name, {"variants": {
        "axis=y": {"model": sf(f"block/{name}")},
        "axis=z": {"model": sf(f"block/{name}_horizontal"), "x": 90},
        "axis=x": {"model": sf(f"block/{name}_horizontal"), "x": 90, "y": 90},
    }})
    item_block(name)


def box(frm, to, tex, cull=None, faces=DIRS, uv=None, extra=None, rotation=None):
    e = {"from": frm, "to": to, "faces": {}}
    for d in faces:
        f = {"texture": tex}
        if uv:
            f["uv"] = uv(d, frm, to) if callable(uv) else uv
        if cull and d in cull:
            f["cullface"] = d
        e["faces"][d] = f
    if extra:
        e.update(extra)
    if rotation:
        e["rotation"] = rotation
    return e


def auto_uv(d, frm, to):
    """UVs matching the element's own footprint, like vanilla's default."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    if d in ("up", "down"):
        return [x0, z0, x1, z1]
    if d in ("north", "south"):
        return [x0, 16 - y1, x1, 16 - y0]
    return [z0, 16 - y1, z1, 16 - y0]


def clamp_uv(d, frm, to):
    u = auto_uv(d, frm, to)
    return [max(0, min(16, v)) for v in u]


def blocks():
    # ---- plain cubes
    for name in ("meteorite", "meteoric_iron_block", "astral_bricks", "cracked_astral_bricks", "cracked_void_bricks",
                 "crumbling_void_bricks", "molten_meteorite"):
        cube_all(name)
        item_block(name)
    for name in ("meteorite", "meteoric_iron_block", "astral_bricks", "cracked_astral_bricks", "cracked_void_bricks"):
        simple_state(name)
    blockstate("crumbling_void_bricks", {"variants": {"triggered=false": {"model": sf("block/crumbling_void_bricks")},
                                                      "triggered=true": {"model": sf("block/crumbling_void_bricks")}}})
    blockstate("molten_meteorite", {"variants": {"permanent=false": {"model": sf("block/molten_meteorite")},
                                                 "permanent=true": {"model": sf("block/molten_meteorite")}}})

    # ---- glowing cubes
    for name, tex, glow in (("meteoric_iron_ore", "meteoric_iron_ore", "meteoric_iron_ore_glow"),
                            ("astral_alloy_block", "astral_alloy_block", "astral_alloy_block_glow"),
                            ("voidsteel_block", "voidsteel_block", "voidsteel_block_glow"),
                            ("chiseled_astral_bricks", "chiseled_astral_bricks", "chiseled_astral_bricks_glow"),
                            ("void_bricks", "void_bricks", "void_bricks_glow"),
                            ("chiseled_void_bricks", "chiseled_void_bricks", "chiseled_void_bricks_glow"),
                            ("starfield_tiles", "starfield_tiles", "starfield_tiles_glow")):
        glow_cube(name, tex, glow)
        simple_state(name)
        item_block(name)

    # ---- full-bright cubes
    block_model("starlight_lamp", {
        "parent": "minecraft:block/block",
        "textures": {"particle": sf("block/starlight_lamp"), "all": sf("block/starlight_lamp")},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "forge_data": FULLBRIGHT, "faces": cube_faces("#all")}],
    })
    simple_state("starlight_lamp")
    item_block("starlight_lamp")

    block_model("sanctum_seal", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "textures": {"particle": sf("block/sanctum_seal"), "all": sf("block/sanctum_seal")},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "forge_data": FULLBRIGHT, "faces": cube_faces("#all")}],
    })
    simple_state("sanctum_seal")
    item_block("sanctum_seal")

    glow_cube("seal_keystone", "seal_keystone", "seal_keystone_glow")
    cube_all("seal_keystone_inactive")
    blockstate("seal_keystone", {"variants": {"active=true": {"model": sf("block/seal_keystone")},
                                              "active=false": {"model": sf("block/seal_keystone_inactive")}}})
    item_block("seal_keystone")

    # ---- stairs, slabs, walls, pillars
    stairs_slab_wall("astral_bricks", "astral_bricks", "astral_brick")
    stairs_slab_wall("void_bricks", "void_bricks", "void_brick")
    pillar("astral_pillar")
    pillar("void_pillar")

    # ---- starlit crystal (amethyst-style cluster)
    block_model("starlit_crystal", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                    "textures": {"cross": sf("block/starlit_crystal")}})
    variants = {}
    for facing, rot in (("up", {}), ("down", {"x": 180}), ("north", {"x": 90}), ("south", {"x": 90, "y": 180}),
                        ("east", {"x": 90, "y": 90}), ("west", {"x": 90, "y": 270})):
        for wl in ("true", "false"):
            variants[f"facing={facing},waterlogged={wl}"] = {"model": sf("block/starlit_crystal"), **rot}
    blockstate("starlit_crystal", {"variants": variants})
    write_json(asset("models", "item", "starlit_crystal.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": sf("block/starlit_crystal")}})

    # ---- fallen star: a jagged chunk of solid starlight
    t = "#star"
    block_model("fallen_star", {
        "parent": "minecraft:block/block",
        "textures": {"particle": sf("block/fallen_star"), "star": sf("block/fallen_star")},
        "elements": [
            box([3, 0, 3], [13, 6, 13], t, uv=auto_uv, extra={"forge_data": FULLBRIGHT}),
            box([4, 6, 4], [12, 10, 12], t, uv=auto_uv, extra={"forge_data": FULLBRIGHT}),
            box([6, 10, 6], [10, 13, 10], t, uv=auto_uv, extra={"forge_data": FULLBRIGHT}),
            box([6, 1, 1], [10, 7, 15], t, uv=clamp_uv, extra={"forge_data": FULLBRIGHT},
                rotation={"origin": [8, 4, 8], "axis": "y", "angle": 45}),
            box([1, 1, 6], [15, 7, 10], t, uv=clamp_uv, extra={"forge_data": FULLBRIGHT},
                rotation={"origin": [8, 4, 8], "axis": "y", "angle": 45}),
            box([7, 4, 2], [9, 12, 4], t, uv=clamp_uv, extra={"forge_data": FULLBRIGHT},
                rotation={"origin": [8, 8, 3], "axis": "x", "angle": -22.5}),
            box([7, 4, 12], [9, 11, 14], t, uv=clamp_uv, extra={"forge_data": FULLBRIGHT},
                rotation={"origin": [8, 8, 13], "axis": "x", "angle": 22.5}),
        ],
    })
    simple_state("fallen_star")
    item_block("fallen_star")

    # ---- spike trap
    def spike_model(name, height):
        elements = [
            box([0, 0, 0], [16, 16, 16], "#side", cull=DIRS, faces=("down", "north", "south", "west", "east")),
            box([0, 0, 0], [16, 16, 16], "#top", cull=("up",), faces=("up",)),
        ]
        if height > 0:
            for (cx, cz) in ((4, 4), (12, 4), (4, 12), (12, 12)):
                for ang in (45, -45):
                    elements.append({
                        "from": [cx - 1.5, 16, cz], "to": [cx + 1.5, 16 + height, cz],
                        "rotation": {"origin": [cx, 16, cz], "axis": "y", "angle": ang},
                        "shade": False,
                        "faces": {"north": {"texture": "#spike", "uv": [0, 0, 16, 16]},
                                  "south": {"texture": "#spike", "uv": [0, 0, 16, 16]}},
                    })
        block_model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                           "textures": {"particle": sf("block/void_bricks"), "side": sf("block/void_bricks"),
                                        "top": sf("block/spike_trap_top"), "spike": sf("block/spike")},
                           "elements": elements})
    spike_model("spike_trap", 0)
    spike_model("spike_trap_armed", 2)
    spike_model("spike_trap_extended", 11)
    variants = {}
    for ext in ("true", "false"):
        for armed in ("true", "false"):
            m = "spike_trap_extended" if ext == "true" else "spike_trap_armed" if armed == "true" else "spike_trap"
            variants[f"armed={armed},extended={ext}"] = {"model": sf(f"block/{m}")}
    blockstate("spike_trap", {"variants": variants})
    item_block("spike_trap", "spike_trap_extended")

    # ---- flame jet (six-way, like an observer)
    block_model("flame_jet", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": sf("block/void_bricks"), "side": sf("block/void_bricks"),
                     "front": sf("block/flame_jet_front"), "glow": sf("block/flame_jet_front_glow")},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "down": {"texture": "#side", "cullface": "down"}, "up": {"texture": "#side", "cullface": "up"},
                "north": {"texture": "#front", "cullface": "north"}, "south": {"texture": "#side", "cullface": "south"},
                "west": {"texture": "#side", "cullface": "west"}, "east": {"texture": "#side", "cullface": "east"}}},
            {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "forge_data": FULLBRIGHT,
             "faces": {"north": {"texture": "#glow", "cullface": "north"}}},
        ],
    })
    blockstate("flame_jet", {"variants": {
        "facing=north": {"model": sf("block/flame_jet")},
        "facing=east": {"model": sf("block/flame_jet"), "y": 90},
        "facing=south": {"model": sf("block/flame_jet"), "y": 180},
        "facing=west": {"model": sf("block/flame_jet"), "y": 270},
        "facing=up": {"model": sf("block/flame_jet"), "x": 270},
        "facing=down": {"model": sf("block/flame_jet"), "x": 90},
    }})
    item_block("flame_jet")

    # ---- sentinel eye cage (the eye itself is a block entity renderer)
    block_model("sentinel_eye", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": sf("block/sentinel_cage"), "cage": sf("block/sentinel_cage")},
        "elements": [box([1, 1, 1], [15, 15, 15], "#cage", uv=[0, 0, 16, 16])],
    })
    simple_state("sentinel_eye")
    item_block("sentinel_eye")

    # ---- astral brazier
    def brazier_model(name, lit):
        els = [
            box([4, 0, 4], [12, 3, 12], "#metal", uv=auto_uv, cull=("down",)),
            box([6, 3, 6], [10, 9, 10], "#metal", uv=auto_uv, faces=("north", "south", "west", "east")),
            box([2, 9, 2], [14, 14, 14], "#metal", uv=auto_uv, faces=("down", "north", "south", "west", "east")),
            box([2, 14, 2], [14, 14, 14], "#top", uv=auto_uv, faces=("up",)),
        ]
        if lit:
            els.append(box([2, 14.02, 2], [14, 14.02, 14], "#glow", uv=auto_uv, faces=("up",),
                           extra={"shade": False, "forge_data": FULLBRIGHT}))
        textures = {"particle": sf("block/brazier_metal"), "metal": sf("block/brazier_metal"),
                    "top": sf("block/brazier_coals" if lit else "block/brazier_ash")}
        if lit:
            textures["glow"] = sf("block/brazier_coals_glow")
        block_model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": els})
    brazier_model("astral_brazier", False)
    brazier_model("astral_brazier_lit", True)
    blockstate("astral_brazier", {"variants": {"lit=false": {"model": sf("block/astral_brazier")},
                                               "lit=true": {"model": sf("block/astral_brazier_lit")}}})
    item_block("astral_brazier", "astral_brazier_lit")

    # ---- star altar
    def altar_els(tex_side, tex_top, extra=None):
        return [
            box([0, 0, 0], [16, 4, 16], tex_side, uv=auto_uv, cull=("down",), faces=("down", "north", "south", "west", "east"), extra=extra),
            box([0, 4, 0], [16, 4, 16], tex_top, uv=auto_uv, faces=("up",), extra=extra),
            box([3, 4, 3], [13, 12, 13], tex_side, uv=auto_uv, faces=("north", "south", "west", "east"), extra=extra),
            box([1, 12, 1], [15, 16, 15], tex_side, uv=auto_uv, faces=("down", "north", "south", "west", "east"), extra=extra),
            box([1, 16, 1], [15, 16, 15], tex_top, uv=[0, 0, 16, 16], faces=("up",), cull=("up",), extra=extra),
        ]
    block_model("star_altar", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"particle": sf("block/star_altar_side"), "side": sf("block/star_altar_side"), "top": sf("block/star_altar_top"),
                     "side_glow": sf("block/star_altar_side_glow"), "top_glow": sf("block/star_altar_top_glow")},
        "elements": altar_els("#side", "#top") + altar_els("#side_glow", "#top_glow", {"shade": False, "forge_data": FULLBRIGHT}),
    })
    simple_state("star_altar")
    item_block("star_altar")

    # ---- astral telescope
    tele = [
        box([2, 0, 2], [14, 2, 14], "#wood", uv=auto_uv, cull=("down",)),
        box([7, 2, 7], [9, 11, 9], "#brass", uv=auto_uv),
        box([6, 9, 6], [10, 11, 10], "#brass", uv=auto_uv),
        # tube
        box([6, 11, -3], [10, 15, 12], "#brass", uv=clamp_uv, faces=("up", "down", "west", "east", "south"),
            rotation={"origin": [8, 12, 8], "axis": "x", "angle": 22.5}),
        box([6, 11, -3], [10, 15, -3], "#lens", uv=[4, 4, 12, 12], faces=("north",),
            rotation={"origin": [8, 12, 8], "axis": "x", "angle": 22.5}),
        box([5.5, 10.5, -3.5], [10.5, 15.5, -1.5], "#brass", uv=clamp_uv, faces=("up", "down", "west", "east"),
            rotation={"origin": [8, 12, 8], "axis": "x", "angle": 22.5}),
        box([7, 12, 12], [9, 14, 15], "#wood", uv=clamp_uv,
            rotation={"origin": [8, 12, 8], "axis": "x", "angle": 22.5}),
    ]
    block_model("astral_telescope", {"parent": "minecraft:block/block",
                                     "textures": {"particle": sf("block/telescope_brass"), "brass": sf("block/telescope_brass"),
                                                  "wood": sf("block/telescope_wood"), "lens": sf("block/telescope_lens")},
                                     "elements": tele})
    blockstate("astral_telescope", {"variants": {
        "facing=north": {"model": sf("block/astral_telescope")},
        "facing=east": {"model": sf("block/astral_telescope"), "y": 90},
        "facing=south": {"model": sf("block/astral_telescope"), "y": 180},
        "facing=west": {"model": sf("block/astral_telescope"), "y": 270},
    }})
    item_block("astral_telescope")

    # ---- stellar egg
    egg_shape = [([4, 0, 4], [12, 2, 12]), ([3, 2, 3], [13, 9, 13]), ([4, 9, 4], [12, 12, 12]), ([5, 12, 5], [11, 14, 11])]
    for stage in range(3):
        els = [box(f, t, "#egg", uv=auto_uv) for f, t in egg_shape]
        els += [box(f, t, "#glow", uv=auto_uv, extra={"shade": False, "forge_data": FULLBRIGHT}) for f, t in egg_shape]
        block_model(f"stellar_egg_{stage}", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                              "textures": {"particle": sf(f"block/stellar_egg_{stage}"), "egg": sf(f"block/stellar_egg_{stage}"),
                                                           "glow": sf(f"block/stellar_egg_{stage}_glow")},
                                              "elements": els})
    blockstate("stellar_egg", {"variants": {f"hatch={i}": {"model": sf(f"block/stellar_egg_{i}")} for i in range(3)}})
    item_block("stellar_egg", "stellar_egg_0")


# =============================================================================================
# Item models
# =============================================================================================

GLOW_ITEMS = set()


def item_model(name, parent="minecraft:item/generated", texture=None, extra=None):
    tex = sf(f"item/{texture or name}")
    glow_path = os.path.join(ASSETS, "textures", "item", f"{texture or name}_glow.png")
    obj = {"parent": parent, "textures": {"layer0": tex}}
    if os.path.exists(glow_path):
        obj["loader"] = "forge:item_layers"
        obj["textures"]["layer1"] = tex + "_glow"
        obj["forge_data"] = {"layers": {"1": {"block_light": 15, "sky_light": 15}}}
        GLOW_ITEMS.add(name)
    if extra:
        obj.update(extra)
    write_json(asset("models", "item", f"{name}.json"), obj)


BIG_HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 5.0, 0.75], "scale": [1.45, 1.45, 0.9]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 5.0, 0.75], "scale": [1.45, 1.45, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 4.2, 1.13], "scale": [0.95, 0.95, 0.95]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 4.2, 1.13], "scale": [0.95, 0.95, 0.95]},
}

GENERATED = ["raw_meteoric_iron", "meteoric_iron_ingot", "meteoric_iron_nugget", "stardust", "star_fragment",
             "astral_alloy_ingot", "molten_core", "nebula_gel", "void_essence", "eclipse_shard", "voidsteel_ingot",
             "heart_of_astraeon", "sigil_of_the_fallen_star", "starfall_beacon", "totem_of_the_fallen_star",
             "starseer_journal", "halo_of_the_fallen_star"]
ARMOR = [f"{s}_{p}" for s in ("meteoric", "starforged", "voidwalker") for p in ("helmet", "chestplate", "leggings", "boots")]
HANDHELD = ["meteoric_sword", "meteoric_pickaxe", "meteoric_axe", "meteoric_shovel", "meteoric_hoe", "starbreaker",
            "nebula_blade", "riftblade", "star_tether", "singularity_gauntlet"]
BIG = ["starcaller_staff"]
EGGS = ["star_wisp", "meteor_golem", "void_stalker", "nebula_jelly", "starseer", "astral_mimic", "comet_ray", "astraeon"]


def items():
    for n in GENERATED + ARMOR:
        item_model(n)
    for n in HANDHELD:
        item_model(n, "minecraft:item/handheld")
    for n in BIG:
        item_model(n, "minecraft:item/handheld", extra={"display": BIG_HANDHELD})
    # hammer: glows white-hot while charging
    item_model("meteor_hammer_charging", "minecraft:item/handheld", extra={"display": BIG_HANDHELD})
    item_model("meteor_hammer", "minecraft:item/handheld", extra={
        "display": BIG_HANDHELD,
        "overrides": [{"predicate": {"minecraft:charging": 1}, "model": sf("item/meteor_hammer_charging")}]})
    # scythe: the blade leaves your hand when thrown
    item_model("eclipse_scythe_thrown", "minecraft:item/handheld", extra={"display": BIG_HANDHELD})
    item_model("eclipse_scythe", "minecraft:item/handheld", extra={
        "display": BIG_HANDHELD,
        "overrides": [{"predicate": {"minecraft:thrown": 1}, "model": sf("item/eclipse_scythe_thrown")}]})
    # bow
    bow_display = json.load(open("/home/user/mcassets/assets/minecraft/models/item/bow.json"))["display"] \
        if os.path.exists("/home/user/mcassets/assets/minecraft/models/item/bow.json") else None
    for i in range(3):
        item_model(f"constellation_bow_pulling_{i}", sf("item/constellation_bow"))
        # the pulling models inherit display from the base bow model, but must not inherit its overrides
    item_model("constellation_bow", extra={
        "display": bow_display,
        "overrides": [
            {"predicate": {"minecraft:pulling": 1}, "model": sf("item/constellation_bow_pulling_0")},
            {"predicate": {"minecraft:pulling": 1, "minecraft:pull": 0.65}, "model": sf("item/constellation_bow_pulling_1")},
            {"predicate": {"minecraft:pulling": 1, "minecraft:pull": 0.9}, "model": sf("item/constellation_bow_pulling_2")},
        ]})
    for e in EGGS:
        write_json(asset("models", "item", f"{e}_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def particles():
    p = {
        "star_spark": [f"star_spark_{i}" for i in range(4)],
        "astral_spark": [f"star_spark_{i}" for i in range(4)],
        "void_spark": [f"star_spark_{i}" for i in range(4)],
        "ember": ["ember_0", "ember_1"],
        "meteor_smoke": [f"meteor_smoke_{i}" for i in range(8)],
        "shockwave": ["shockwave"],
        "void_shockwave": ["shockwave"],
        "nova_flash": ["nova_flash"],
        "rune": [f"rune_{i}" for i in range(4)],
    }
    for name, tex in p.items():
        write_json(asset("particles", f"{name}.json"), {"textures": [sf(t) for t in tex]})


# =============================================================================================
# Loot tables
# =============================================================================================

def item_entry(item, count=None, weight=None, functions=None, conditions=None):
    e = {"type": "minecraft:item", "name": item}
    fns = []
    if count is not None:
        if isinstance(count, tuple):
            fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}, "add": False})
        elif count != 1:
            fns.append({"function": "minecraft:set_count", "count": count, "add": False})
    if functions:
        fns += functions
    if fns:
        e["functions"] = fns
    if weight is not None:
        e["weight"] = weight
    if conditions:
        e["conditions"] = conditions
    return e


def empty(weight):
    return {"type": "minecraft:empty", "weight": weight}


SURVIVES = [{"condition": "minecraft:survives_explosion"}]
SILK = {"condition": "minecraft:match_tool", "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}


def block_loot(name, pools):
    write_json(data(NS, "loot_tables", "blocks", f"{name}.json"),
               {"type": "minecraft:block", "pools": pools, "random_sequence": sf(f"blocks/{name}")})


def self_drop(name):
    block_loot(name, [{"rolls": 1, "bonus_rolls": 0, "entries": [item_entry(sf(name))], "conditions": SURVIVES}])


def silk_or(name, drop, count, fortune="uniform"):
    fns = []
    if isinstance(count, tuple):
        fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}, "add": False})
    if fortune == "ore":
        fns.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"})
    elif fortune == "uniform":
        fns.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
                    "parameters": {"bonusMultiplier": 1}})
    fns.append({"function": "minecraft:explosion_decay"})
    block_loot(name, [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": sf(name), "conditions": [SILK]},
        {"type": "minecraft:item", "name": drop, "functions": fns},
    ]}]}])


def slab_drop(name):
    block_loot(name, [{"rolls": 1, "bonus_rolls": 0, "entries": [{
        "type": "minecraft:item", "name": sf(name),
        "functions": [{"function": "minecraft:set_count", "count": 2, "add": False,
                       "conditions": [{"condition": "minecraft:block_state_property", "block": sf(name), "properties": {"type": "double"}}]},
                      {"function": "minecraft:explosion_decay"}]}]}])


def block_loot_tables():
    for name in ("meteorite", "meteoric_iron_block", "astral_alloy_block", "voidsteel_block", "astral_bricks",
                 "cracked_astral_bricks", "chiseled_astral_bricks", "astral_brick_stairs", "astral_brick_wall", "astral_pillar",
                 "void_bricks", "cracked_void_bricks", "chiseled_void_bricks", "void_brick_stairs", "void_brick_wall",
                 "void_pillar", "starfield_tiles", "starlight_lamp", "spike_trap", "flame_jet", "sentinel_eye",
                 "crumbling_void_bricks", "astral_brazier", "astral_telescope", "stellar_egg", "star_altar"):
        self_drop(name)
    slab_drop("astral_brick_slab")
    slab_drop("void_brick_slab")
    silk_or("meteoric_iron_ore", sf("raw_meteoric_iron"), None, "ore")
    silk_or("molten_meteorite", sf("meteorite"), None, None)
    silk_or("starlit_crystal", sf("stardust"), (2, 4), "uniform")
    # the fallen star shatters into fragments and dust
    block_loot("fallen_star", [
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sf("fallen_star"), "conditions": [SILK]},
            item_entry(sf("star_fragment"), (2, 4), functions=[
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
                 "parameters": {"bonusMultiplier": 1}}]),
        ]}]},
        {"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:inverted", "term": SILK}],
         "entries": [item_entry(sf("stardust"), (3, 6))]},
    ])


def looting(lo=0, hi=1):
    return {"function": "minecraft:looting_enchant", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def entity_loot(name, pools):
    write_json(data(NS, "loot_tables", "entities", f"{name}.json"),
               {"type": "minecraft:entity", "pools": pools, "random_sequence": sf(f"entities/{name}")})


def pool(entries, rolls=1, conditions=None):
    p = {"rolls": rolls, "bonus_rolls": 0, "entries": entries}
    if conditions:
        p["conditions"] = conditions
    return p


def chance(p, per_looting=0.0):
    return {"condition": "minecraft:random_chance_with_looting", "chance": p, "looting_multiplier": per_looting}


KILLED_BY_PLAYER = {"condition": "minecraft:killed_by_player"}
ELITE = {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"nbt": "{Elite:1b}"}}
NOT_ELITE = {"condition": "minecraft:inverted", "term": ELITE}


def entity_loot_tables():
    entity_loot("star_wisp", [
        pool([item_entry(sf("stardust"), (1, 2), functions=[looting()])]),
        pool([item_entry(sf("star_fragment"))], conditions=[KILLED_BY_PLAYER, chance(0.05, 0.02)]),
    ])
    entity_loot("meteor_golem", [
        pool([item_entry(sf("raw_meteoric_iron"), (2, 5), functions=[looting(0, 2)])]),
        pool([item_entry(sf("molten_core"))], conditions=[chance(0.6, 0.1)]),
        pool([item_entry(sf("meteorite"), (1, 3))]),
    ])
    entity_loot("void_stalker", [
        pool([item_entry(sf("void_essence"), (1, 3), functions=[looting()])]),
        pool([item_entry(sf("eclipse_shard"))], conditions=[KILLED_BY_PLAYER, chance(0.06, 0.02)]),
    ])
    entity_loot("nebula_jelly", [
        pool([item_entry(sf("nebula_gel"), (1, 3), functions=[looting()])]),
        pool([item_entry(sf("stardust"), (0, 2))]),
    ])
    entity_loot("starseer", [
        pool([item_entry(sf("stardust"), (2, 4), functions=[looting()])], conditions=[NOT_ELITE]),
        pool([item_entry(sf("star_fragment"))], conditions=[NOT_ELITE, KILLED_BY_PLAYER, chance(0.35, 0.1)]),
        pool([item_entry(sf("eclipse_shard"))], conditions=[NOT_ELITE, KILLED_BY_PLAYER, chance(0.04, 0.02)]),
        # the High Starseer, master of the Observatory
        pool([item_entry(sf("starcaller_staff"))], conditions=[ELITE]),
        pool([item_entry(sf("star_fragment"), (3, 5), functions=[looting()])], conditions=[ELITE]),
        pool([item_entry(sf("astral_alloy_ingot"), (2, 4))], conditions=[ELITE]),
        pool([item_entry(sf("stardust"), (8, 16))], conditions=[ELITE]),
    ])
    entity_loot("astral_mimic", [
        pool([item_entry("minecraft:gold_ingot", (3, 7))]),
        pool([item_entry(sf("astral_alloy_ingot"), (1, 3), weight=3), item_entry("minecraft:diamond", (1, 3), weight=2),
              item_entry("minecraft:emerald", (2, 5), weight=2)], rolls=2),
        pool([item_entry(sf("star_fragment"), (1, 2))]),
        pool([item_entry("minecraft:enchanted_book", functions=[{"function": "minecraft:enchant_randomly"}], weight=3),
              item_entry(sf("star_tether"), weight=2), item_entry(sf("nebula_blade"), weight=1),
              item_entry(sf("starfall_beacon"), weight=2), empty(4)]),
    ])
    entity_loot("comet_ray", [
        pool([item_entry(sf("stardust"), (1, 3), functions=[looting()])]),
        pool([item_entry(sf("nebula_gel"), (0, 2))]),
    ])
    entity_loot("astraeon", [
        pool([item_entry(sf("heart_of_astraeon"), (2, 3))]),
        pool([item_entry(sf("eclipse_shard"), (4, 8), functions=[looting(0, 2)])]),
        pool([item_entry(sf("star_fragment"), (6, 12))]),
        pool([item_entry(sf("voidsteel_ingot"), (2, 4))]),
        pool([item_entry(sf("stardust"), (16, 32))]),
        pool([item_entry("minecraft:nether_star")], conditions=[chance(0.5, 0.0)]),
    ])


def chest(name, pools):
    write_json(data(NS, "loot_tables", "chests", f"{name}.json"),
               {"type": "minecraft:chest", "pools": pools, "random_sequence": sf(f"chests/{name}")})


def rolls(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


BOOK = [{"function": "minecraft:enchant_randomly"}]
TREASURE_BOOK = [{"function": "minecraft:enchant_with_levels", "levels": rolls(20, 39), "treasure": True}]
ENCHANTED = [{"function": "minecraft:enchant_with_levels", "levels": rolls(15, 30)}]
DAMAGED = [{"function": "minecraft:set_damage", "damage": rolls(0.4, 0.9)}]


def potion(pid):
    return [{"function": "minecraft:set_potion", "id": pid}]


def chest_loot_tables():
    I = item_entry
    chest("sanctum_common", [
        pool([I("minecraft:arrow", (4, 12), 10), I("minecraft:bone", (2, 6), 10), I("minecraft:gold_nugget", (3, 9), 8),
              I(sf("stardust"), (2, 6), 12), I(sf("meteoric_iron_ingot"), (1, 4), 8), I(sf("void_essence"), (1, 2), 6),
              I("minecraft:bread", (1, 3), 6), I("minecraft:torch", (4, 10), 8), I(sf("meteoric_iron_nugget"), (4, 10), 8),
              I("minecraft:candle", (1, 3), 4), I(sf("starlight_lamp"), (1, 2), 3), I("minecraft:golden_apple", 1, 1)], rolls(4, 7)),
        pool([I(sf("star_fragment"), 1, 1), I("minecraft:enchanted_book", 1, 2, BOOK), I(sf("star_tether"), 1, 1, DAMAGED), empty(8)]),
    ])
    chest("sanctum_library", [
        pool([I("minecraft:book", (1, 4), 10), I("minecraft:paper", (2, 8), 10), I("minecraft:ink_sac", (1, 3), 6),
              I("minecraft:glow_ink_sac", (1, 3), 6), I("minecraft:experience_bottle", (2, 6), 8), I("minecraft:lapis_lazuli", (3, 9), 8),
              I(sf("stardust"), (2, 5), 8), I("minecraft:candle", (1, 3), 5)], rolls(3, 6)),
        pool([I("minecraft:enchanted_book", 1, 10, BOOK), I("minecraft:enchanted_book", 1, 3, TREASURE_BOOK),
              I(sf("starseer_journal"), 1, 2)], rolls(1, 2)),
    ])
    chest("sanctum_trap_reward", [
        pool([I(sf("astral_alloy_ingot"), (2, 5), 10), I("minecraft:diamond", (1, 3), 8), I(sf("star_fragment"), (1, 2), 8),
              I("minecraft:golden_apple", (1, 2), 6), I(sf("stardust"), (4, 10), 10), I("minecraft:experience_bottle", (3, 8), 6)], rolls(3, 5)),
        pool([I(sf("star_tether"), 1, 4), I(sf("nebula_blade"), 1, 2), I(sf("starforged_helmet"), 1, 2),
              I(sf("starforged_boots"), 1, 2), I(sf("constellation_bow"), 1, 1), I(sf("starfall_beacon"), 1, 3)]),
    ])
    chest("sanctum_cavern", [
        pool([I(sf("raw_meteoric_iron"), (3, 8), 10), I(sf("stardust"), (3, 8), 10), I(sf("nebula_gel"), (1, 4), 8),
              I("minecraft:amethyst_shard", (2, 6), 8), I(sf("molten_core"), 1, 5), I(sf("star_fragment"), 1, 4),
              I("minecraft:glow_berries", (2, 6), 6), I(sf("meteorite"), (4, 12), 6)], rolls(4, 7)),
        pool([I(sf("meteor_hammer"), 1, 1), I(sf("starbreaker"), 1, 1), empty(6)]),
    ])
    chest("sanctum_vault", [
        pool([I("minecraft:gold_ingot", (4, 10), 10), I("minecraft:diamond", (2, 5), 8), I("minecraft:emerald", (3, 8), 8),
              I(sf("astral_alloy_ingot"), (2, 6), 10), I(sf("voidsteel_ingot"), (1, 2), 4), I(sf("eclipse_shard"), 1, 3),
              I(sf("star_fragment"), (2, 4), 8), I("minecraft:enchanted_golden_apple", 1, 1),
              I("minecraft:totem_of_undying", 1, 2)], rolls(5, 8)),
        pool([I(sf("riftblade"), 1, 1), I(sf("constellation_bow"), 1, 2), I(sf("singularity_gauntlet"), 1, 1),
              I("minecraft:diamond_sword", 1, 3, ENCHANTED), I("minecraft:enchanted_book", 1, 4, TREASURE_BOOK), empty(3)]),
    ])
    chest("sanctum_vault_sigil", [
        pool([I(sf("sigil_of_the_fallen_star"))]),
        pool([I(sf("star_fragment"), (2, 4))]),
        pool([I("minecraft:golden_apple", (2, 4))]),
        pool([I(sf("totem_of_the_fallen_star"), 1, 1), I("minecraft:totem_of_undying", 1, 3), empty(4)]),
    ])
    chest("observatory_common", [
        pool([I("minecraft:bread", (2, 5), 10), I("minecraft:apple", (1, 4), 8), I(sf("stardust"), (2, 6), 12),
              I(sf("meteoric_iron_ingot"), (1, 3), 8), I("minecraft:candle", (1, 4), 6), I("minecraft:clock", 1, 3),
              I("minecraft:compass", 1, 3), I("minecraft:spyglass", 1, 3), I("minecraft:map", 1, 4), I("minecraft:paper", (2, 6), 6),
              I(sf("raw_meteoric_iron"), (2, 5), 8)], rolls(3, 6)),
        pool([I(sf("star_fragment"), 1, 2), I(sf("starfall_beacon"), 1, 1), empty(7)]),
    ])
    chest("observatory_library", [
        pool([I("minecraft:book", (2, 5), 10), I("minecraft:paper", (3, 9), 10), I("minecraft:ink_sac", (1, 4), 6),
              I("minecraft:feather", (1, 4), 6), I("minecraft:experience_bottle", (1, 4), 6), I(sf("stardust"), (2, 5), 8)], rolls(3, 6)),
        pool([I("minecraft:enchanted_book", 1, 6, BOOK), I(sf("starseer_journal"), 1, 4), empty(2)], rolls(1, 2)),
    ])
    chest("observatory_lab", [
        pool([I("minecraft:glowstone_dust", (3, 9), 10), I("minecraft:redstone", (3, 9), 8), I("minecraft:blaze_powder", (1, 4), 6),
              I("minecraft:glass_bottle", (2, 5), 8), I(sf("nebula_gel"), (1, 4), 8), I(sf("stardust"), (3, 8), 10),
              I("minecraft:nether_wart", (2, 6), 6), I("minecraft:potion", 1, 4, potion("minecraft:strong_healing")),
              I("minecraft:potion", 1, 4, potion("minecraft:night_vision")), I("minecraft:potion", 1, 3, potion("minecraft:slow_falling"))], rolls(4, 7)),
    ])
    chest("observatory_top", [
        pool([I(sf("star_fragment"), (1, 3), 10), I(sf("astral_alloy_ingot"), (1, 3), 10), I(sf("stardust"), (4, 10), 10),
              I("minecraft:diamond", (1, 2), 5), I("minecraft:experience_bottle", (3, 7), 6)], rolls(3, 5)),
        pool([I(sf("constellation_bow"), 1, 2), I(sf("star_tether"), 1, 3), I(sf("starfall_beacon"), 1, 3), I(sf("nebula_blade"), 1, 1),
              empty(2)]),
    ])
    chest("observatory_secret", [
        pool([I("minecraft:diamond", (2, 5), 8), I("minecraft:gold_ingot", (3, 8), 10), I(sf("astral_alloy_ingot"), (2, 5), 10),
              I(sf("eclipse_shard"), 1, 3), I(sf("star_fragment"), (2, 3), 8), I("minecraft:emerald", (2, 6), 6)], rolls(4, 6)),
        pool([I(sf("stellar_egg"), 1, 2), I(sf("starcaller_staff"), 1, 1), I(sf("nebula_blade"), 1, 3),
              I("minecraft:enchanted_book", 1, 4, TREASURE_BOOK), I("minecraft:music_disc_otherside", 1, 1), empty(2)]),
    ])
    write_json(data(NS, "loot_tables", "dispensers", "sanctum_arrows.json"), {
        "type": "minecraft:chest",
        "pools": [pool([item_entry("minecraft:arrow", (24, 48), 10),
                        item_entry("minecraft:tipped_arrow", (6, 12), 3, functions=potion("minecraft:poison"))], rolls(2, 3))],
    })
    # extra loot seeded into vanilla chests through the global loot modifier
    write_json(data(NS, "loot_tables", "inject", "common.json"), {
        "type": "minecraft:chest",
        "pools": [pool([empty(40), item_entry(sf("stardust"), (2, 5), 20), item_entry(sf("meteoric_iron_ingot"), (1, 3), 14),
                        item_entry(sf("star_fragment"), 1, 5), item_entry(sf("starseer_journal"), 1, 4), item_entry(sf("star_tether"), 1, 2),
                        item_entry(sf("starfall_beacon"), 1, 2)])],
    })
    write_json(data(NS, "loot_tables", "inject", "rare.json"), {
        "type": "minecraft:chest",
        "pools": [pool([empty(30), item_entry(sf("astral_alloy_ingot"), (1, 3), 15), item_entry(sf("star_fragment"), (1, 2), 10),
                        item_entry(sf("eclipse_shard"), 1, 3), item_entry(sf("stellar_egg"), 1, 1),
                        item_entry(sf("constellation_bow"), 1, 2), item_entry(sf("starfall_beacon"), 1, 3)])],
    })


def loot_modifiers():
    targets = {
        "common": ["simple_dungeon", "abandoned_mineshaft", "desert_pyramid", "jungle_temple", "shipwreck_treasure",
                   "ruined_portal", "pillager_outpost", "igloo_chest"],
        "rare": ["stronghold_library", "stronghold_corridor", "ancient_city", "woodland_mansion", "end_city_treasure",
                 "bastion_treasure", "buried_treasure"],
    }
    entries = []
    for table, chests in targets.items():
        for c in chests:
            name = f"inject_{c}"
            write_json(data(NS, "loot_modifiers", f"{name}.json"), {
                "type": sf("add_table"),
                "conditions": [{"condition": "forge:loot_table_id", "loot_table_id": f"minecraft:chests/{c}"}],
                "table": sf(f"inject/{table}"),
            })
            entries.append(sf(name))
    write_json(data("forge", "loot_modifiers", "global_loot_modifiers.json"), {"replace": False, "entries": entries})


# =============================================================================================
# Recipes
# =============================================================================================

def recipe(name, obj):
    write_json(data(NS, "recipes", f"{name}.json"), obj)


def ing(x):
    return {"tag": x[1:]} if x.startswith("#") else {"item": x}


def shaped(name, pattern, key, result, count=1, category="misc"):
    recipe(name, {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                  "key": {k: ing(v) for k, v in key.items()}, "result": {"item": result, "count": count}})


def shapeless(name, ingredients, result, count=1, category="misc"):
    recipe(name, {"type": "minecraft:crafting_shapeless", "category": category,
                  "ingredients": [ing(i) for i in ingredients], "result": {"item": result, "count": count}})


def cook(name, ingredient, result, xp, smelt=200):
    recipe(name, {"type": "minecraft:smelting", "category": "misc", "ingredient": ing(ingredient), "result": result,
                  "experience": xp, "cookingtime": smelt})


def blast(name, ingredient, result, xp):
    recipe(name, {"type": "minecraft:blasting", "category": "misc", "ingredient": ing(ingredient), "result": result,
                  "experience": xp, "cookingtime": 100})


def cut(name, ingredient, result, count=1):
    recipe(name, {"type": "minecraft:stonecutting", "ingredient": ing(ingredient), "result": result, "count": count})


def recipes():
    S = sf
    # ---- smelting
    cook("meteoric_iron_ingot_from_smelting_raw", S("raw_meteoric_iron"), S("meteoric_iron_ingot"), 0.8)
    blast("meteoric_iron_ingot_from_blasting_raw", S("raw_meteoric_iron"), S("meteoric_iron_ingot"), 0.8)
    cook("meteoric_iron_ingot_from_smelting_ore", S("meteoric_iron_ore"), S("meteoric_iron_ingot"), 1.0)
    blast("meteoric_iron_ingot_from_blasting_ore", S("meteoric_iron_ore"), S("meteoric_iron_ingot"), 1.0)
    cook("meteoric_iron_nugget_from_smelting_meteorite", S("meteorite"), S("meteoric_iron_nugget"), 0.2)
    cook("cracked_astral_bricks", S("astral_bricks"), S("cracked_astral_bricks"), 0.1)
    cook("cracked_void_bricks", S("void_bricks"), S("cracked_void_bricks"), 0.1)

    # ---- storage
    for small, big, block in (("meteoric_iron_nugget", "meteoric_iron_ingot", None),
                              ("meteoric_iron_ingot", None, "meteoric_iron_block"),
                              ("astral_alloy_ingot", None, "astral_alloy_block"),
                              ("voidsteel_ingot", None, "voidsteel_block")):
        target = big or block
        shaped(f"{target}_from_{small}", ["###", "###", "###"], {"#": S(small)}, S(target), category="building" if block else "misc")
        shapeless(f"{small}_from_{target}", [S(target)], S(small), 9)

    # ---- alloys
    shapeless("astral_alloy_ingot", [S("meteoric_iron_ingot"), S("meteoric_iron_ingot"), S("stardust"), S("stardust"),
                                     S("stardust"), S("stardust"), S("star_fragment")], S("astral_alloy_ingot"), 2)
    shapeless("voidsteel_ingot", [S("astral_alloy_ingot"), S("astral_alloy_ingot"), S("void_essence"), S("void_essence"),
                                  S("void_essence"), S("void_essence")], S("voidsteel_ingot"), 2)
    shaped("fallen_star", ["###", "###", "###"], {"#": S("star_fragment")}, S("fallen_star"), category="building")

    # ---- building blocks
    shaped("astral_bricks", ["BBB", "BSB", "BBB"], {"B": "minecraft:stone_bricks", "S": S("stardust")}, S("astral_bricks"), 8, "building")
    shaped("astral_bricks_from_calcite", ["BBB", "BSB", "BBB"], {"B": "minecraft:calcite", "S": S("stardust")}, S("astral_bricks"), 8, "building")
    shaped("void_bricks", ["BBB", "BEB", "BBB"], {"B": "minecraft:deepslate_bricks", "E": S("void_essence")}, S("void_bricks"), 8, "building")
    shaped("void_bricks_from_blackstone", ["BBB", "BEB", "BBB"], {"B": "minecraft:polished_blackstone_bricks", "E": S("void_essence")},
           S("void_bricks"), 8, "building")
    for base, prefix in (("astral_bricks", "astral_brick"), ("void_bricks", "void_brick")):
        b = S(base)
        shaped(f"{prefix}_stairs", ["#  ", "## ", "###"], {"#": b}, S(f"{prefix}_stairs"), 4, "building")
        shaped(f"{prefix}_slab", ["###"], {"#": b}, S(f"{prefix}_slab"), 6, "building")
        shaped(f"{prefix}_wall", ["###", "###"], {"#": b}, S(f"{prefix}_wall"), 6, "misc")
        cut(f"{prefix}_stairs_from_stonecutting", b, S(f"{prefix}_stairs"))
        cut(f"{prefix}_slab_from_stonecutting", b, S(f"{prefix}_slab"), 2)
        cut(f"{prefix}_wall_from_stonecutting", b, S(f"{prefix}_wall"))
    shaped("chiseled_astral_bricks", ["#", "#"], {"#": S("astral_brick_slab")}, S("chiseled_astral_bricks"), category="building")
    shaped("chiseled_void_bricks", ["#", "#"], {"#": S("void_brick_slab")}, S("chiseled_void_bricks"), category="building")
    shaped("astral_pillar", ["#", "#"], {"#": S("astral_bricks")}, S("astral_pillar"), 2, "building")
    shaped("void_pillar", ["#", "#"], {"#": S("void_bricks")}, S("void_pillar"), 2, "building")
    for base in ("astral_bricks", "void_bricks"):
        prefix = base[:-1]
        chis = "chiseled_" + base
        pil = base.split("_")[0] + "_pillar"
        cut(f"{chis}_from_stonecutting", S(base), S(chis))
        cut(f"{pil}_from_stonecutting", S(base), S(pil))
    shapeless("starfield_tiles", [S("void_bricks"), S("void_bricks"), S("void_bricks"), S("void_bricks"), S("stardust")],
              S("starfield_tiles"), 4, "building")
    shaped("starlight_lamp", [" D ", "DGD", " D "], {"D": S("stardust"), "G": "minecraft:glowstone"}, S("starlight_lamp"), 1, "building")
    shapeless("crumbling_void_bricks", [S("void_bricks"), "minecraft:gravel"], S("crumbling_void_bricks"), 1, "building")

    # ---- dungeon kit
    shaped("spike_trap", ["NNN", "VPV"], {"N": S("meteoric_iron_nugget"), "V": S("void_bricks"), "P": "minecraft:stone_pressure_plate"},
           S("spike_trap"), 2, "redstone")
    shaped("flame_jet", ["VVV", "VFV", "VRV"], {"V": S("void_bricks"), "F": "minecraft:fire_charge", "R": "minecraft:redstone"},
           S("flame_jet"), 1, "redstone")
    shaped("sentinel_eye", ["VEV", "EYE", "VEV"], {"V": S("void_bricks"), "E": S("void_essence"), "Y": "minecraft:ender_eye"},
           S("sentinel_eye"), 1, "redstone")
    shaped("astral_brazier", ["I I", "IDI", " I "], {"I": S("meteoric_iron_ingot"), "D": S("stardust")}, S("astral_brazier"), 1, "decorations")
    shaped("astral_telescope", ["GSG", " A ", "I I"], {"G": "minecraft:gold_ingot", "S": "minecraft:spyglass",
                                                        "A": S("astral_alloy_ingot"), "I": S("meteoric_iron_ingot")},
           S("astral_telescope"), 1, "decorations")

    # ---- meteoric tools
    M = S("meteoric_iron_ingot")
    stick = "minecraft:stick"
    shaped("meteoric_sword", ["M", "M", "S"], {"M": M, "S": stick}, S("meteoric_sword"), category="equipment")
    shaped("meteoric_pickaxe", ["MMM", " S ", " S "], {"M": M, "S": stick}, S("meteoric_pickaxe"), category="equipment")
    shaped("meteoric_axe", ["MM", "MS", " S"], {"M": M, "S": stick}, S("meteoric_axe"), category="equipment")
    shaped("meteoric_shovel", ["M", "S", "S"], {"M": M, "S": stick}, S("meteoric_shovel"), category="equipment")
    shaped("meteoric_hoe", ["MM", " S", " S"], {"M": M, "S": stick}, S("meteoric_hoe"), category="equipment")

    # ---- armor
    for prefix, mat in (("meteoric", M), ("starforged", S("astral_alloy_ingot")), ("voidwalker", S("voidsteel_ingot"))):
        shaped(f"{prefix}_helmet", ["XXX", "X X"], {"X": mat}, S(f"{prefix}_helmet"), category="equipment")
        shaped(f"{prefix}_chestplate", ["X X", "XXX", "XXX"], {"X": mat}, S(f"{prefix}_chestplate"), category="equipment")
        shaped(f"{prefix}_leggings", ["XXX", "X X", "X X"], {"X": mat}, S(f"{prefix}_leggings"), category="equipment")
        shaped(f"{prefix}_boots", ["X X", "X X"], {"X": mat}, S(f"{prefix}_boots"), category="equipment")

    # ---- signature weapons & gadgets
    A, V = S("astral_alloy_ingot"), S("voidsteel_ingot")
    shaped("starbreaker", ["AFA", " M ", " M "], {"A": A, "F": S("star_fragment"), "M": M}, S("starbreaker"), category="equipment")
    shaped("meteor_hammer", ["BCB", " M ", " M "], {"B": S("meteoric_iron_block"), "C": S("molten_core"), "M": M},
           S("meteor_hammer"), category="equipment")
    shaped("nebula_blade", [" A ", "NAN", " M "], {"A": A, "N": S("nebula_gel"), "M": M}, S("nebula_blade"), category="equipment")
    shaped("constellation_bow", [" AS", "F S", " AS"], {"A": A, "S": "minecraft:string", "F": S("star_fragment")},
           S("constellation_bow"), category="equipment")
    shaped("riftblade", [" V ", "EVE", " A "], {"V": V, "E": S("void_essence"), "A": A}, S("riftblade"), category="equipment")
    shaped("singularity_gauntlet", ["VEV", "VFV", " V "], {"V": V, "E": S("eclipse_shard"), "F": S("star_fragment")},
           S("singularity_gauntlet"), category="equipment")
    shaped("starcaller_staff", [" CF", " AC", "A  "], {"C": S("molten_core"), "F": S("fallen_star"), "A": A},
           S("starcaller_staff"), category="equipment")
    shaped("eclipse_scythe", ["EEH", " V ", "V  "], {"E": S("eclipse_shard"), "H": S("heart_of_astraeon"), "V": V},
           S("eclipse_scythe"), category="equipment")
    shaped("halo_of_the_fallen_star", ["GFG", "FHF", "GFG"], {"G": "minecraft:gold_ingot", "F": S("star_fragment"),
                                                                "H": S("heart_of_astraeon")},
           S("halo_of_the_fallen_star"), category="equipment")
    shaped("totem_of_the_fallen_star", [" F ", "FTF", " H "], {"F": S("star_fragment"), "T": "minecraft:totem_of_undying",
                                                                  "H": S("heart_of_astraeon")},
           S("totem_of_the_fallen_star"), category="misc")
    shaped("starfall_beacon", [" F ", "DAD", "MMM"], {"F": S("star_fragment"), "D": S("stardust"), "A": A, "M": M},
           S("starfall_beacon"), category="misc")
    shaped("star_tether", ["  M", " DM", "S  "], {"M": M, "D": S("stardust"), "S": "minecraft:string"}, S("star_tether"), category="equipment")
    shapeless("starseer_journal", ["minecraft:book", S("stardust")], S("starseer_journal"))


# =============================================================================================
# Tags
# =============================================================================================

def tag(ns, kind, name, values, replace=False):
    write_json(data(ns, "tags", kind, f"{name}.json"), {"replace": replace, "values": values})


PICKAXE = ["meteorite", "molten_meteorite", "meteoric_iron_ore", "meteoric_iron_block", "starlit_crystal", "fallen_star",
           "astral_alloy_block", "voidsteel_block", "astral_bricks", "cracked_astral_bricks", "chiseled_astral_bricks",
           "astral_brick_stairs", "astral_brick_slab", "astral_brick_wall", "astral_pillar", "void_bricks", "cracked_void_bricks",
           "chiseled_void_bricks", "void_brick_stairs", "void_brick_slab", "void_brick_wall", "void_pillar", "starfield_tiles",
           "starlight_lamp", "spike_trap", "flame_jet", "sentinel_eye", "crumbling_void_bricks", "astral_brazier",
           "astral_telescope", "stellar_egg"]


def tags():
    tag("minecraft", "blocks", "mineable/pickaxe", [sf(b) for b in PICKAXE])
    tag("minecraft", "blocks", "needs_stone_tool", [sf("meteorite"), sf("molten_meteorite")])
    tag("minecraft", "blocks", "needs_iron_tool", [sf(b) for b in ("meteoric_iron_ore", "meteoric_iron_block", "astral_alloy_block", "fallen_star")])
    tag("minecraft", "blocks", "needs_diamond_tool", [sf("voidsteel_block")])
    for n in ("needs_meteoric_tool", "needs_astral_tool", "needs_voidsteel_tool", "needs_celestial_tool"):
        tag(NS, "blocks", n, [])
    for kind in ("blocks", "items"):
        tag("minecraft", kind, "stairs", [sf("astral_brick_stairs"), sf("void_brick_stairs")])
        tag("minecraft", kind, "slabs", [sf("astral_brick_slab"), sf("void_brick_slab")])
        tag("minecraft", kind, "walls", [sf("astral_brick_wall"), sf("void_brick_wall")])
    tag("minecraft", "blocks", "crystal_sound_blocks", [sf("starlit_crystal")])
    tag("minecraft", "blocks", "beacon_base_blocks", [sf("meteoric_iron_block"), sf("astral_alloy_block"), sf("voidsteel_block")])
    tag("minecraft", "items", "beacon_payment_items", [sf("meteoric_iron_ingot"), sf("astral_alloy_ingot"), sf("voidsteel_ingot")])
    tag("minecraft", "blocks", "dragon_immune", [sf("sanctum_seal"), sf("seal_keystone"), sf("star_altar")])
    tag("minecraft", "blocks", "wither_immune", [sf("sanctum_seal"), sf("seal_keystone"), sf("star_altar")])
    tag("minecraft", "items", "trimmable_armor", [sf(a) for a in ARMOR])
    # forge tags for other mods' benefit
    tag("forge", "items", "ingots", [sf("meteoric_iron_ingot"), sf("astral_alloy_ingot"), sf("voidsteel_ingot")])
    tag("forge", "items", "ingots/meteoric_iron", [sf("meteoric_iron_ingot")])
    tag("forge", "items", "nuggets", [sf("meteoric_iron_nugget")])
    tag("forge", "items", "raw_materials", [sf("raw_meteoric_iron")])
    tag("forge", "blocks", "ores", [sf("meteoric_iron_ore")])
    tag("forge", "items", "ores", [sf("meteoric_iron_ore")])
    tag("forge", "blocks", "storage_blocks", [sf("meteoric_iron_block"), sf("astral_alloy_block"), sf("voidsteel_block")])
    tag("forge", "items", "storage_blocks", [sf("meteoric_iron_block"), sf("astral_alloy_block"), sf("voidsteel_block")])

    # terrain that meteors may blast apart
    tag(NS, "blocks", "meteor_carvable", [
        "#minecraft:dirt", "#minecraft:sand", "#minecraft:terracotta", "#minecraft:base_stone_overworld", "#minecraft:base_stone_nether",
        "#minecraft:coal_ores", "#minecraft:iron_ores", "#minecraft:copper_ores", "#minecraft:gold_ores", "#minecraft:redstone_ores",
        "#minecraft:lapis_ores", "#minecraft:diamond_ores", "#minecraft:emerald_ores", "#minecraft:leaves",
        "#minecraft:overworld_natural_logs", "#minecraft:snow", "#minecraft:ice", "#minecraft:nylium", "#minecraft:wart_blocks",
        "minecraft:gravel", "minecraft:clay", "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:calcite",
        "minecraft:dripstone_block", "minecraft:moss_block", "minecraft:mud", "minecraft:packed_mud", "minecraft:soul_sand",
        "minecraft:soul_soil", "minecraft:magma_block", "minecraft:mushroom_stem", "minecraft:brown_mushroom_block",
        "minecraft:red_mushroom_block", "minecraft:pointed_dripstone", "minecraft:amethyst_block", "minecraft:smooth_basalt",
        sf("meteorite"), sf("molten_meteorite"), sf("meteoric_iron_ore"), sf("starlit_crystal"),
    ])

    # damage types
    tag("minecraft", "damage_type", "is_fire", [sf("starfire")])
    tag("minecraft", "damage_type", "is_explosion", [sf("meteor")])
    tag("minecraft", "damage_type", "damages_helmet", [sf("meteor")])
    tag("minecraft", "damage_type", "is_projectile", [sf("star_bolt")])
    tag("minecraft", "damage_type", "bypasses_shield", [sf("astral_beam"), sf("shockwave")])

    # worldgen
    tag(NS, "worldgen/biome", "has_structure/astral_observatory", [
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:savanna", "minecraft:savanna_plateau",
        "minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:snowy_plains", "minecraft:cherry_grove",
        "minecraft:windswept_savanna"])
    tag(NS, "worldgen/biome", "has_structure/sanctum", [
        "#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_savanna", "#minecraft:is_jungle", "#minecraft:is_badlands",
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:desert", "minecraft:snowy_plains",
        "minecraft:swamp", "minecraft:cherry_grove", "minecraft:windswept_hills"])
    tag(NS, "worldgen/structure", "on_sanctum_maps", [sf("sanctum")])


# =============================================================================================
# Damage types
# =============================================================================================

def damage_types():
    for name, effect in (("spikes", "poking"), ("starfire", "burning"), ("meteor", "hurt"), ("star_bolt", "hurt"),
                         ("void_rend", "hurt"), ("astral_beam", "burning"), ("shockwave", "hurt")):
        obj = {"message_id": f"starfallen.{name}", "scaling": "when_caused_by_living_non_player", "exhaustion": 0.1}
        if effect != "hurt":
            obj["effects"] = effect
        write_json(data(NS, "damage_type", f"{name}.json"), obj)


# =============================================================================================
# Advancements
# =============================================================================================

def adv(name, parent, icon, criteria, frame="task", hidden=False, xp=None, toast=True, background=None, requirements=None):
    display = {"icon": {"item": icon}, "title": {"translate": f"advancements.starfallen.{name}.title"},
               "description": {"translate": f"advancements.starfallen.{name}.description"},
               "frame": frame, "show_toast": toast, "announce_to_chat": toast, "hidden": hidden}
    if background:
        display["background"] = background
    obj = {"display": display, "criteria": criteria}
    if parent:
        obj["parent"] = sf(parent)
    obj["requirements"] = requirements or [[k] for k in criteria]
    if xp:
        obj["rewards"] = {"experience": xp}
    write_json(data(NS, "advancements", f"{name}.json"), obj)


IMPOSSIBLE = {"trigger": {"trigger": "minecraft:impossible"}}


def has_items(*items_):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": list(items_)}]}}


def killed(entity):
    return {"trigger": "minecraft:player_killed_entity",
            "conditions": {"entity": [{"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": entity}}]}}


def in_structure(structure):
    return {"trigger": "minecraft:location",
            "conditions": {"player": [{"condition": "minecraft:entity_properties", "entity": "this",
                                       "predicate": {"location": {"structure": structure}}}]}}


def advancements():
    adv("root", None, sf("fallen_star"), {"tick": {"trigger": "minecraft:tick"}}, toast=False,
        background=sf("textures/block/starfield_tiles.png"))
    adv("look_up", "root", sf("stardust"), {"seen": IMPOSSIBLE["trigger"]})
    adv("iron_from_the_sky", "look_up", sf("raw_meteoric_iron"), {"iron": has_items(sf("raw_meteoric_iron"))})
    adv("starforged", "iron_from_the_sky", sf("astral_alloy_ingot"), {"alloy": has_items(sf("astral_alloy_ingot"))})
    adv("into_the_void", "starforged", sf("voidsteel_ingot"), {"voidsteel": has_items(sf("voidsteel_ingot"))}, xp=50)
    adv("skyfall", "iron_from_the_sky", sf("meteor_hammer"), {"slam": IMPOSSIBLE["trigger"]}, frame="goal", xp=50)
    adv("wisp_friend", "root", sf("stardust"), {"tamed": IMPOSSIBLE["trigger"]})
    adv("comet_rider", "wisp_friend", sf("stellar_egg"), {"tamed": IMPOSSIBLE["trigger"]}, frame="goal", xp=50)
    adv("stargazer", "root", sf("astral_telescope"), {"gazed": IMPOSSIBLE["trigger"]})
    adv("the_high_seat", "stargazer", sf("starcaller_staff"),
        {"killed": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": sf("starseer"), "nbt": "{Elite:1b}"}}]}}},
        frame="goal", xp=100)
    adv("the_sanctum", "stargazer", sf("chiseled_void_bricks"), {"entered": in_structure(sf("sanctum"))})
    adv("dont_blink", "the_sanctum", sf("void_essence"), {"killed": killed(sf("void_stalker"))})
    adv("surprise", "the_sanctum", "minecraft:chest", {"bitten": IMPOSSIBLE["trigger"]}, hidden=True)
    adv("the_sigil", "the_sanctum", sf("sigil_of_the_fallen_star"), {"sigil": has_items(sf("sigil_of_the_fallen_star"))}, frame="goal")
    adv("seal_breaker", "the_sanctum", sf("seal_keystone"), {"broken": IMPOSSIBLE["trigger"]}, frame="goal", xp=100)
    adv("starfallen", "seal_breaker", sf("heart_of_astraeon"), {"slain": IMPOSSIBLE["trigger"]}, frame="challenge", xp=500)
    adv("crown_of_stars", "starfallen", sf("halo_of_the_fallen_star"), {"halo": has_items(sf("halo_of_the_fallen_star"))}, frame="goal")
    adv("reaper_of_suns", "starfallen", sf("eclipse_scythe"), {"scythe": has_items(sf("eclipse_scythe"))}, frame="goal")
    adv("second_sunrise", "starfallen", sf("totem_of_the_fallen_star"), {"nova": IMPOSSIBLE["trigger"]}, frame="challenge", hidden=True, xp=100)


# =============================================================================================
# Worldgen
# =============================================================================================

def worldgen():
    write_json(data(NS, "worldgen", "configured_feature", "crater.json"), {"type": sf("crater"), "config": {}})
    write_json(data(NS, "worldgen", "placed_feature", "crater.json"), {
        "feature": sf("crater"),
        "placement": [{"type": "minecraft:rarity_filter", "chance": 56}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}],
    })
    write_json(data(NS, "forge", "biome_modifier", "add_craters.json"), {
        "type": "forge:add_features", "biomes": "#minecraft:is_overworld", "features": sf("crater"), "step": "local_modifications"})
    write_json(data(NS, "forge", "biome_modifier", "add_sky_creatures.json"), {
        "type": "forge:add_spawns", "biomes": "#minecraft:is_overworld",
        "spawners": [{"type": sf("star_wisp"), "weight": 8, "minCount": 1, "maxCount": 3},
                     {"type": sf("nebula_jelly"), "weight": 3, "minCount": 1, "maxCount": 2}]})
    write_json(data(NS, "forge", "biome_modifier", "add_monsters.json"), {
        "type": "forge:add_spawns", "biomes": "#minecraft:is_overworld",
        "spawners": [{"type": sf("void_stalker"), "weight": 6, "minCount": 1, "maxCount": 1},
                     {"type": sf("starseer"), "weight": 4, "minCount": 1, "maxCount": 2}]})

    write_json(data(NS, "worldgen", "structure", "sanctum.json"), {
        "type": sf("sanctum"), "biomes": "#starfallen:has_structure/sanctum", "step": "underground_structures",
        "terrain_adaptation": "none",
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": [
            {"type": sf("void_stalker"), "weight": 5, "minCount": 1, "maxCount": 1},
            {"type": "minecraft:skeleton", "weight": 10, "minCount": 1, "maxCount": 2},
            {"type": "minecraft:zombie", "weight": 10, "minCount": 1, "maxCount": 2},
            {"type": "minecraft:spider", "weight": 5, "minCount": 1, "maxCount": 1}]}},
    })
    write_json(data(NS, "worldgen", "structure", "astral_observatory.json"), {
        "type": sf("astral_observatory"), "biomes": "#starfallen:has_structure/astral_observatory", "step": "surface_structures",
        "terrain_adaptation": "none", "spawn_overrides": {},
    })
    write_json(data(NS, "worldgen", "structure_set", "sanctum.json"), {
        "structures": [{"structure": sf("sanctum"), "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 44, "separation": 18, "salt": 70411221}})
    write_json(data(NS, "worldgen", "structure_set", "astral_observatory.json"), {
        "structures": [{"structure": sf("astral_observatory"), "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 32, "separation": 12, "salt": 41829731}})


# =============================================================================================
# Language
# =============================================================================================

def language():
    write_json(asset("lang", "en_us.json"), lang_en.build())


def clean():
    for d in (asset("models"), asset("blockstates"), asset("particles"), asset("lang"),
              data(NS, "loot_tables"), data(NS, "recipes"), data(NS, "tags"), data(NS, "advancements"), data(NS, "worldgen"),
              data(NS, "damage_type"), data(NS, "forge"), data(NS, "loot_modifiers"), data("minecraft", "tags"),
              data("forge", "tags"), data("forge", "loot_modifiers")):
        if os.path.isdir(d):
            shutil.rmtree(d)


def main():
    clean()
    blocks()
    items()
    particles()
    block_loot_tables()
    entity_loot_tables()
    chest_loot_tables()
    loot_modifiers()
    recipes()
    tags()
    damage_types()
    advancements()
    worldgen()
    language()
    count = sum(len(files) for _, _, files in os.walk(RES))
    print(f"data generated: {count} files under resources")


if __name__ == "__main__":
    main()
