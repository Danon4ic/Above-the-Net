"""Create and export the arena light study and player silhouettes for the game."""

import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from jump_motion import block, material, pivot

DIRECTORY = Path(__file__).resolve().parent
RESOURCES = DIRECTORY.parents[1] / "core/src/main/resources/art"

# The game uses Y up and +X forward. Blender uses Z up.
LIGHTS = {
    "CHAMPIONSHIP": ((.18, 1, .24), 7.0, .31),
    "BEACH": ((-.46, .70, .54), .65, .10),
    "SCHOOL_GYM": ((-.26, 1, .31), 6.0, .27),
    "SPORTS_PALACE": ((.25, 1, .18), 4.3, .24),
    "ROOFTOP": ((-.26, 1, .26), 2.5, .19),
}

# Position and dimensions relative to the head joint in game axes.
HAIR = {
    "crop": [(-.019,.232,0,.224,.048,.218), (-.081,.179,0,.096,.106,.208)],
    "part": [(-.015,.242,0,.241,.059,.221), (.043,.261,-.049,.15,.065,.111),
             (-.075,.175,0,.097,.125,.209)],
    "curls": [(-.025,.247,0,.214,.078,.222), (.041,.276,-.051,.117,.056,.109),
              (-.056,.282,.038,.14,.052,.123), (-.083,.187,0,.093,.135,.217)],
    "quiff": [(-.021,.24,0,.225,.067,.204), (.053,.286,.015,.132,.08,.155),
              (-.079,.175,0,.092,.116,.198)],
    "shaved": [(-.03,.225,0,.195,.04,.209), (-.079,.183,0,.087,.075,.198)],
    "tied": [(-.032,.241,0,.23,.063,.214), (-.085,.181,0,.093,.119,.209),
             (-.148,.203,0,.084,.087,.107), (-.172,.15,0,.059,.096,.084)],
}


def camera(scene, eye, center, scale):
    bpy.ops.object.camera_add(location=eye)
    cam = bpy.context.object
    cam.rotation_euler = (Vector(center) - cam.location).to_track_quat("-Z", "Y").to_euler()
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = scale
    scene.camera = cam
    scene.render.resolution_x, scene.render.resolution_y = 1280, 720


def arena(scene, name, direction, angle, fill):
    scene.name = name
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 24
    scene.cycles.use_denoising = True
    scene.world = bpy.data.worlds.new(f"{name} bounce")
    scene.world.use_nodes = True
    scene.world.node_tree.nodes.get("Background").inputs["Strength"].default_value = fill
    sun = bpy.data.lights.new("Shadow reference", "SUN")
    sun.angle = math.radians(angle)
    sun.energy = 2.2
    sun["shadow_fill"] = fill
    fixture = bpy.data.objects.new("Shadow reference", sun)
    scene.collection.objects.link(fixture)
    toward = Vector((direction[0], -direction[2], direction[1]))
    fixture.location = toward.normalized() * 8
    fixture.rotation_euler = (-toward).to_track_quat("-Z", "Y").to_euler()
    court = material("Court", (.52,.33,.16))
    white = material("Court lines", (.86,.86,.83))
    jersey = material("Jersey", (.047,.20,.65))
    block("Study court", None, (0,0,-.06), (8,7,.12), court)
    block("Line", None, (0,0,.002), (.045,7,.004), white)
    for x,y in ((-1,0), (1.4,.6), (1.5,-1.6)):
        block("Player silhouette", None, (x,y,.93), (.28,.48,1.86), jersey)
    camera(scene, (6,-8,6.5), (0,0,.40), 10)


def create():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    hair_scene = bpy.context.scene
    hair_scene.name = "Player silhouettes"
    hair_scene.render.engine = "BLENDER_WORKBENCH"
    hair_scene.display.shading.color_type = "MATERIAL"
    hair_scene.display.shading.show_cavity = True
    hair = material("Hair", (.14,.084,.05))
    skin = material("Skin", (.68,.47,.33))
    for i,(name, shapes) in enumerate(HAIR.items()):
        root = pivot(name, location=(0,(i - 2.5) * .42,.20))
        block("Reference head", root, (0,0,.125), (.22,.223,.23), skin)
        block("Reference nose", root, (.12,0,.12), (.035,.035,.05), skin)
        for j,(x,y,z,w,h,d) in enumerate(shapes):
            item = block(f"Hair {j+1}", root, (x,-z,y), (w,d,h), hair)
            item["hair_piece"] = True
    camera(hair_scene, (4,-1.1,1.4), (0,0,.31), 3.0)
    for name,(direction, angle, fill) in LIGHTS.items():
        bpy.context.window.scene = bpy.data.scenes.new(name)
        arena(bpy.context.scene, name, direction, angle, fill)
    bpy.context.window.scene = hair_scene
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == "VIEW_3D":
                area.spaces.active.region_3d.view_perspective = "CAMERA"
                area.spaces.active.shading.color_type = "MATERIAL"
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(DIRECTORY / "arena_and_players.blend"), compress=True)


def write(name, header, rows):
    lines = [header, *(name + "," + ",".join(f"{value:.6f}" for value in values) for name, values in rows)]
    (RESOURCES / name).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def export():
    RESOURCES.mkdir(parents=True, exist_ok=True)
    light_rows = []
    for name in LIGHTS:
        fixture = next(obj for obj in bpy.data.scenes[name].objects if obj.type == "LIGHT")
        toward = fixture.rotation_euler.to_quaternion() @ Vector((0,0,1))
        light_rows.append((name, (toward.x,toward.z,-toward.y,fixture.data.angle,fixture.data["shadow_fill"])))
    hair_rows = []
    for name in HAIR:
        root = bpy.data.objects[name]
        for piece in sorted(root.children,key=lambda item:item.name):
            if piece.get("hair_piece"):
                x, y, z = piece.location
                w, d, h = (abs(axis) for axis in piece.scale)  # unit cubes
                hair_rows.append((name,(x,z,-y,w,h,d)))
    write("arena_lights.csv", "venue,x,y,z,angle,fill", light_rows)
    write("player_hair.csv", "style,x,y,z,width,height,depth", hair_rows)
    print(f"Exported {len(light_rows)} lighting profiles and {len(hair_rows)} silhouette pieces")


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if "--create" in args:
        create()
    export()
