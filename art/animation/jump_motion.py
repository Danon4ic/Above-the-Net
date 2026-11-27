"""Create the editable motion study or export its Blender curves with --background --python."""

import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector


DIRECTORY = Path(__file__).resolve().parent
CHANNELS = ("lean", "twist", "bank", "compression", "left_arm", "right_arm",
            "left_ankle", "right_ankle", "ankle_lift")
LAST_FRAME = 33
SAMPLES = 65
REST = (0.0,) * len(CHANNELS)
# Signed offsets in game axes, in radians or metres. The short plant ramps into
# takeoff; contact poses leave the existing ball-hand alignment to the game.
CLIPS = {
    "plant": {
        1: REST,
        8: (-.05, -.06, .03, .025, -.16, -.24, 0, 0, 0),
        18: (-.12, -.11, .045, .063, -.35, -.47, 0, 0, 0),
        25: (-.10, -.06, .02, .048, -.14, -.24, 0, 0, 0),
        33: (+.01, 0, 0, .01, .30, .27, 0, 0, 0),
    },
    "jump_set": {
        1: REST,
        8: (-.09, 0, 0, 0, -.22, -.22, .05, .03, .025),
        15: (-.04, 0, 0, 0, -.06, -.06, .15, .11, -.08),
        18: (+.035, 0, 0, 0, .08, .08, .13, .10, -.11),
        23: (-.08, 0, 0, 0, -.06, -.06, .15, .10, -.065),
        29: (-.06, 0, 0, 0, -.27, -.27, .06, .04, -.03),
        33: REST,
    },
    "back_attack": {
        1: REST,
        7: (-.08, -.08, .03, 0, .12, -.11, .09, -.04, .02),
        13: (+.11, -.20, .06, 0, .16, -.12, .08, -.13, .073),
        17: (+.015, -.08, .025, 0, .035, 0, .14, -.14, .05),
        21: (-.17, .18, -.075, 0, -.28, -.08, .25, -.07, .015),
        26: (-.13, .12, -.04, 0, -.24, -.15, .17, .055, -.06),
        33: REST,
    },
    "landing": {
        1: REST,
        7: (-.12, 0, 0, .060, .27, .27, 0, 0, 0),
        11: (-.16, 0, 0, .078, .31, .31, 0, 0, 0),
        18: (-.10, 0, 0, .046, .19, .19, 0, 0, 0),
        26: (-.034, 0, 0, .014, .07, .07, 0, 0, 0),
        33: REST,
    },
    "jump_float_serve": {
        1: REST,
        7: (-.06, -.04, .015, 0, .22, .16, .06, .02, -.035),
        13: (+.015, -.07, .012, 0, .18, .08, .20, .14, -.073),
        17: (-.045, -.04, 0, 0, .05, .04, .19, .14, -.078),
        22: (-.075, .03, -.01, 0, -.10, .12, .13, .10, -.05),
        28: (-.05, 0, 0, 0, -.10, -.05, .065, .045, -.024),
        33: REST,
    },
    "jump_topspin_serve": {
        1: REST,
        7: (-.08, -.14, .04, 0, .27, .12, .10, -.07, .045),
        13: (+.24, -.38, .09, 0, .35, -.06, .21, -.18, .13),
        17: (+.10, -.15, .05, 0, .07, .04, .30, -.16, .10),
        22: (-.22, .24, -.08, 0, -.25, -.14, .32, -.07, .055),
        28: (-.13, .13, -.05, 0, -.12, -.12, .15, .06, -.03),
        33: REST,
    },
    "quick_set": {
        1: REST,
        7: (-.04, 0, 0, 0, .16, .16, .07, .06, -.02),
        12: (-.09, 0, 0, 0, .11, .11, .17, .15, -.11),
        17: (-.13, 0, 0, 0, .035, .035, .16, .14, -.13),
        21: (-.08, 0, 0, 0, -.14, -.14, .13, .12, -.10),
        27: (-.04, 0, 0, 0, -.11, -.11, .055, .04, -.036),
        33: REST,
    },
    "quick_attack": {
        1: REST,
        7: (-.09, -.09, .035, 0, .24, .10, .10, -.05, .03),
        13: (+.06, -.10, .038, 0, .10, .04, .16, -.09, .052),
        17: (-.04, .055, -.025, 0, -.08, .06, .15, -.045, .034),
        22: (-.13, .10, -.045, 0, -.15, -.08, .19, .035, -.015),
        28: (-.06, .045, -.018, 0, -.09, -.07, .08, .055, -.038),
        33: REST,
    },
    "wing_attack": {
        1: REST,
        7: (-.06, -.09, .028, 0, .20, .06, .13, -.06, .025),
        13: (.10, -.18, .043, 0, .17, -.09, .12, -.15, .052),
        17: (.045, -.07, .018, 0, .05, 0, .11, -.09, .036),
        22: (-.15, .16, -.048, 0, -.14, -.09, .18, .01, .020),
        28: (-.08, .08, -.022, 0, -.09, -.13, .09, .05, -.023),
        33: REST,
    },
    "block_jump": {
        1: REST,
        7: (-.055, 0, 0, 0, .16, .16, -.025, -.025, .022),
        13: (-.075, 0, 0, 0, .055, .055, -.02, -.02, .032),
        17: (-.06, 0, 0, 0, 0, 0, -.045, -.045, .028),
        22: (-.055, 0, 0, 0, -.08, -.08, -.015, -.015, .028),
        28: (-.035, 0, 0, 0, -.15, -.15, .05, .05, .025),
        33: REST,
    },
}


def pivot(name, parent=None, location=(0, 0, 0)):
    item = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(item)
    item.parent = parent
    item.location = location
    item.empty_display_size = .08
    return item


def material(name, colour):
    item = bpy.data.materials.new(name)
    item.diffuse_color = (*colour, 1)
    return item


def block(name, parent, location, dimensions, surface):
    bpy.ops.mesh.primitive_cube_add(size=1)
    item = bpy.context.object
    item.name = name
    item.parent = parent
    item.location = location
    item.dimensions = dimensions
    item.data.materials.append(surface)
    return item


def drive(item, path, axis, controls, expression, **channels):
    driver = item.driver_add(path, axis).driver
    driver.expression = expression
    for name, prop in channels.items():
        variable = driver.variables.new()
        variable.name = name
        target = variable.targets[0]
        target.id = controls
        target.data_path = f'["{prop}"]'


def preview(name, controls, track, shirt, shorts, skin, shoe):
    hips = pivot(f"{name} · pelvis", controls, (0, 0, .98))
    chest = pivot(f"{name} · chest", hips)
    drive(hips, "location", 2, controls, ".98 + height - drop", height="preview_height", drop="compression")
    drive(chest, "rotation_euler", 1, controls, "base - lean", base="preview_lean", lean="lean")
    drive(chest, "rotation_euler", 2, controls, "-twist", twist="twist")
    drive(chest, "rotation_euler", 0, controls, "-bank", bank="bank")
    block("Jersey", chest, (0, 0, .25), (.27, .47, .50), shirt)
    block("Shorts", hips, (0, 0, -.027), (.29, .40, .19), shorts)
    block("Head", chest, (.02, 0, .66), (.23, .25, .27), skin)
    block("Hair", chest, (.005, 0, .80), (.25, .26, .075), shoe)
    for side, y in (("left", .245), ("right", -.245)):
        arm = pivot(f"{side} shoulder", chest, (0, y, .43))
        drive(arm, "rotation_euler", 1, controls, "base - offset",
              base=f"preview_{side}_arm", offset=f"{side}_arm")
        arm.rotation_euler.x = math.copysign(.13, y)
        elbow = pivot(f"{side} elbow", arm, (0, 0, -.265))
        elbow.rotation_euler.y = -.58
        block("Upper arm", arm, (0, 0, -.132), (.12, .13, .265), shirt)
        block("Forearm", elbow, (0, 0, -.148), (.102, .104, .295), skin)
        hip = pivot(f"{side} hip", hips, (0, y * .52, -.055))
        knee = pivot(f"{side} knee", hip, (0, 0, -.42))
        for segment, expression in (
            (hip, "-atan2(x + dx, depth - lift - drop) - acos(min(.999, sqrt((x+dx)**2+(depth-lift-drop)**2)/.89))"),
            (knee, "2*acos(min(.999, sqrt((x+dx)**2+(depth-lift-drop)**2)/.89))"),
        ):
            drive(segment, "rotation_euler", 1, controls, expression, x="preview_ankle",
                  dx=f"{side}_ankle", depth="preview_depth", lift="ankle_lift", drop="compression")
        block("Thigh", hip, (0, 0, -.21), (.163, .16, .42), skin)
        block("Shin", knee, (0, 0, -.215), (.125, .13, .43), skin)
        block("Shoe", knee, (.05, 0, -.456), (.25, .14, .10), shoe)
    for frame, values in track.items():
        for channel, value in zip(("height", "lean", "left_arm", "right_arm", "ankle", "depth"), values):
            key(controls, "preview_" + channel, frame, value)


def key(controls, channel, frame, value):
    controls[channel] = float(value)
    controls.keyframe_insert(data_path=f'["{channel}"]', frame=frame, group=(
        "Game motion offsets" if channel in CHANNELS else "Preview only"))


def create():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.frame_start, scene.frame_end = 1, LAST_FRAME
    scene.render.fps = 48
    scene.timeline_markers.new("Start", frame=1)
    scene.timeline_markers.new("Load / hover / strike", frame=17)
    scene.timeline_markers.new("Transition", frame=LAST_FRAME)
    shirt = material("Blue jersey", (.045, .22, .8))
    shorts = material("Shorts", (.81, .86, .95))
    skin = material("Skin", (.72, .48, .31))
    shoe = material("Shoe and hair", (.055, .048, .062))
    floor = material("Arena floor", (.76, .46, .16))
    block("Floor", None, (2.1, 0, -.06), (11., 6.2, .12), floor)
    tracks = {
        "plant": {1:(0,.08,-.1,.2,0,.82), 18:(0,.21,.63,.69,.04,.76), 33:(0,.07,-.95,-.9,-.02,.85)},
        "jump_set": {1:(0,.08,-.75,-.75,.01,.86), 9:(.38,.03,-2.18,-2.18,-.07,.78),
                     17:(.55,-.04,-2.78,-2.78,-.14,.72), 25:(.39,.06,-1.72,-1.72,-.06,.79), 33:(0,.06,-.55,-.55,.01,.87)},
        "back_attack": {1:(0,.06,-1.0,-1.0,-.03,.87), 10:(.45,-.07,-2.25,-2.94,-.12,.75),
                        17:(.65,0,-2.1,-2.91,-.19,.71), 21:(.55,.17,-1.15,-2.63,-.12,.75),
                        26:(.36,.15,-.7,-.87,-.07,.82), 33:(0,.1,-.52,-.43,.02,.87)},
        "landing": {1:(0,.07,-.4,-.4,.03,.86), 11:(0,.13,-.5,-.5,.016,.78), 33:(0,.035,-.1,-.1,0,.87)},
        "jump_float_serve": {1:(0,.02,-1.7,-1.5,0,.87), 9:(.40,.02,-2.73,-2.52,-.06,.83),
                             17:(.58,.07,-1.62,-2.77,-.11,.84), 24:(.42,.13,-1.8,-1.65,0,.84), 33:(0,.04,-.48,-.55,.02,.87)},
        "jump_topspin_serve": {1:(0,.08,-1.25,-1.4,0,.87), 9:(.57,-.08,-2.58,-2.85,-.12,.81),
                               17:(.87,-.06,-2.14,-3.0,-.15,.75), 22:(.73,.26,-.91,-1.6,-.09,.78),
                               29:(.33,.19,-.78,-.60,-.02,.81), 33:(0,.10,-.46,-.30,.02,.88)},
        "quick_set": {1:(0,.04,-1.4,-1.4,0,.88), 8:(.34,.095,-2.37,-2.37,-.07,.82),
                      16:(.52,.10,-2.66,-2.66,-.13,.76), 23:(.43,.06,-1.66,-1.66,-.07,.77),
                      33:(0,.03,-.42,-.42,.01,.87)},
        "quick_attack": {1:(0,.1,-1.38,-1.32,0,.87), 9:(.40,-.03,-2.48,-2.67,-.14,.78),
                         17:(.61,.07,-1.82,-2.71,-.16,.77), 23:(.42,.21,-.92,-1.42,-.10,.79),
                         33:(0,.08,-.40,-.50,.01,.87)},
        "wing_attack": {1:(0,.12,-.75,-.55,0,.87), 9:(.42,-.04,-2.43,-2.82,-.12,.80),
                        17:(.64,.08,-1.70,-2.82,-.17,.75), 24:(.46,.23,-.77,-1.31,-.08,.78),
                        33:(0,.09,-.55,-.50,.02,.87)},
        "block_jump": {1:(0,.16,-1.35,-1.35,0,.87), 9:(.36,.10,-2.46,-2.46,-.018,.86),
                       17:(.58,.14,-2.59,-2.59,0,.86), 24:(.43,.07,-1.95,-1.95,.02,.85),
                       33:(0,.10,-.83,-.83,.03,.87)},
    }
    for i, (name, frames) in enumerate(CLIPS.items()):
        controls = pivot(name, location=(3.05 * (i // 4), ((i % 4) - 1.5) * 1.5, 0))
        for frame, values in frames.items():
            for channel, value in zip(CHANNELS, values):
                key(controls, channel, frame, value)
        preview(name, controls, tracks[name], shirt, shorts, skin, shoe)
        controls.animation_data.action.name = name.replace("_", " ").title()
        bpy.ops.object.text_add(location=(controls.location.x + 1.12, controls.location.y, .012))
        label = bpy.context.object
        label.name = label.data.body = name.replace("_", " ").upper()
        label.rotation_euler.z = math.pi / 2
        label.data.align_x = "CENTER"
        label.data.size = .18
        label.data.materials.append(shoe)
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
    bpy.ops.object.camera_add(location=(9, -5.8, 5.6))
    camera = bpy.context.object
    camera.rotation_euler = (Vector((2.5, 0, .85)) - camera.location).to_track_quat("-Z", "Y").to_euler()
    camera.data.type, camera.data.ortho_scale = "ORTHO", 12
    scene.camera = camera
    scene.render.resolution_x, scene.render.resolution_y = 1280, 720
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == "VIEW_3D":
                area.spaces.active.region_3d.view_perspective = "CAMERA"
                area.spaces.active.shading.color_type = "MATERIAL"
    bpy.ops.object.select_all(action="DESELECT")
    active = bpy.data.objects["jump_set"]
    active.select_set(True)
    bpy.context.view_layer.objects.active = active
    scene.frame_set(17)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(DIRECTORY / "jump_motion.blend"), compress=True)


def export():
    scene = bpy.context.scene
    path = DIRECTORY.parents[1] / "core/src/main/resources/animation/jump_motion.csv"
    original_frame = scene.frame_current
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [",".join(("clip", *CHANNELS))]
    for name in CLIPS:
        controls = bpy.data.objects[name]
        for sample in range(SAMPLES):
            frame = 1 + (LAST_FRAME - 1) * sample / (SAMPLES - 1)
            scene.frame_set(math.floor(frame), subframe=frame % 1)
            evaluated = controls.evaluated_get(bpy.context.evaluated_depsgraph_get())
            lines.append(",".join((name, *(f"{evaluated[channel]:.5f}" for channel in CHANNELS))))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    scene.frame_set(original_frame)
    print(f"Exported {len(lines) - 1} samples to {path}")


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if "--create" in args:
        create()
    export()
