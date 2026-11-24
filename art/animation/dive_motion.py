"""Author or export the forward dive using Blender's editable F-curves."""

import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from jump_motion import block, drive, material, pivot


DIRECTORY = Path(__file__).resolve().parent
FPS = 100
LAST = 143
CHANNELS = (
    "hip_height", "pitch", "advance", "lean", "bank", "head",
    "support_raise", "support_spread", "support_elbow",
    "reach_raise", "reach_spread", "reach_elbow",
    "support_ankle", "support_depth", "support_pitch",
    "trail_ankle", "trail_depth", "trail_pitch",
)
# Lengths in metres, angles in radians. Time includes 0.32 s diving,
# 0.60 s sliding/gathering the limbs, and 0.50 s getting up.
# Each row contains pelvis/trunk/head, support and reach arms, then the two ankles.
POSES = {
    # Ready, lower onto the push leg, then extend the reach arm ahead of the body.
    1:   (.93, .00, .00, -.09, .00, .04,   .25,.17,.55,  .32,.16,.55,   .02,.87,.00, -.02,.87,.00),
    6:   (.78, .22, .10, -.23, .06, .20,   .35,.25,.68,  .68,.17,.58,   .16,.67,.05, -.20,.70,-.26),
    12:  (.63, .66, .35, -.15, .08, .39,   .91,.32,.55, 1.75,.04,.36,   .16,.66,-.22,-.21,.85,-.48),
    19:  (.44, 1.22,.78, -.01, .07, .58,   .79,.25,1.63,2.97,-.08,.06, -.06,.82,-.68,-.25,.80,-.68),
    # The support palm comes down before the chest. Leave the reach arm out as the body slides.
    26:  (.29,1.49,.98,  .03, .045,.62,   .62,.29,2.16,2.92,-.10,.035,-.08,.87,-.88,-.14,.83,-.83),
    33:  (.22,1.56,1.00, .05, .018,.57,   .43,.30,2.43,2.92,-.11,.05, -.06,.88,-.84,-.12,.86,-.88),
    47:  (.19,1.56,1.00, .08, .00, .53,   .28,.35,2.52,2.93,-.10,.05, -.06,.88,-.82,-.09,.88,-.92),
    63:  (.21,1.53,.98,  .02,-.03, .48,   .24,.35,2.48,2.86,-.08,.12, -.08,.83,-.80,-.13,.80,-.90),
    78:  (.25,1.45,.91, -.07,-.04, .40,   .34,.33,2.34, .42,.33,2.26, -.12,.69,-.78,-.20,.78,-.94),
    93:  (.33,1.24,.76, -.16,-.03, .32,   .37,.31,1.93, .38,.29,1.95, -.06,.49,-.72,-.19,.69,-.79),
    # Push the floor away, gather the first foot under the hip, then rise through a split stance.
    104: (.44, .94,.55, -.22,-.04, .25,   .26,.32,1.51, .25,.30,1.50,  .09,.40,-.45,-.21,.58,-.77),
    115: (.56, .55,.31, -.25,-.06, .17,   .58,.28,.56,  .38,.29,.65,   .18,.44,-.26,-.21,.56,-.50),
    126: (.72, .21,.13, -.22,-.04, .10,   .56,.20,.66,  .40,.24,.61,   .14,.68,-.18,-.14,.66,-.55),
    136: (.89, .055,.025,-.10,-.01,.03,   .23,.17,.49,  .31,.17,.46,   .065,.82,.03,-.07,.81,-.11),
    143: (.945,.00,.00, -.035,.00,.00,    .11,.10,.31,  .13,.10,.31,   .0,.89,.00, -.02,.885,.00),
}

KNEE_DROP = {
    # Lead foot plants, rear knee lowers, then the lead leg brings the hips back up.
    1:   (.94,.00,.00,-.07, .00,.02,   .23,.18,.62, .31,.18,.59,   .10,.88,.00,-.06,.88,.00),
    9:   (.77,.12,.15,-.23,-.08,.14,   .74,.28,.66, .93,.11,.45,   .26,.66,.00,-.23,.74,-.36),
    19:  (.59,.26,.33,-.36,-.12,.24,  1.21,.34,.47,1.62,.00,.14,   .36,.54,-.17,-.36,.54,-.66),
    33:  (.47,.35,.43,-.38,-.10,.31,  1.02,.39,.69,1.72,-.07,.12,  .30,.49,-.29,-.37,.43,-.93),
    51:  (.45,.34,.43,-.34,-.07,.27,   .82,.41,.78,1.70,-.04,.16,  .28,.48,-.28,-.35,.43,-.86),
    72:  (.47,.31,.39,-.30,-.04,.21,   .67,.24,.87,1.08,.12,.68,   .26,.51,-.16,-.32,.45,-.80),
    93:  (.49,.27,.32,-.30,-.02,.17,   .61,.22,.74, .72,.20,.82,   .25,.54,-.12,-.31,.46,-.79),
    105: (.58,.22,.22,-.26,-.04,.13,   .76,.19,.67, .59,.16,.87,   .21,.62,-.06,-.26,.50,-.63),
    121: (.77,.11,.10,-.19,-.03,.08,   .61,.16,.71, .36,.19,.68,   .15,.76,-.05,-.16,.67,-.43),
    143: (.945,0,0,-.035,0,0,         .11,.10,.31, .13,.10,.31,   .0,.89,0,-.02,.885,0),
}

SIDE_ROLL = {
    # Lateral reach leads into the hip, then knees tuck and the shoulder carries the roll.
    1:   (.94,.00,.00,-.06, .00,.02,   .23,.19,.62, .31,.22,.59,   .06,.88,.00,-.04,.87,.00),
    9:   (.74,.23,.16,-.12, .00,.13,   .50,.17,.84, .65,1.01,.26, -.06,.61,-.15, .03,.72,-.30),
    21:  (.49,.81,.37,-.19, .08,.28,   .65,.12,.85,1.02,1.42,.16, -.13,.53,-.30, .12,.65,-.42),
    33:  (.24,1.40,.63,-.26,.20,.40,   .52,.15,.94,1.77,1.01,.20,  .05,.47,-.24, .19,.59,-.40),
    49:  (.23,1.57,.66,-.39,.65,.53,   .63,.03,.99,2.04, .54,.41,  .29,.35,-.18, .32,.39,-.25),
    64:  (.235,1.55,.65,-.48,.90,.54,  .76,.02,1.13,1.53,.49,.82,  .35,.32,-.26, .28,.36,-.37),
    80:  (.275,1.33,.53,-.38,.59,.39,  .81,.19,.84, .97,.51,1.02,  .20,.36,-.28, .22,.51,-.29),
    # Set the palm, then rotate the pelvis onto the tucked knee while staying low.
    90:  (.30,.92,.43,-.47,.34,.32,    .89,.18,.77, .73,.46,1.28,  .27,.35,-.17,-.18,.35,-.61),
    98:  (.34,.59,.33,-.61,.19,.23,    .94,.17,.75, .61,.31,1.11,  .34,.34,-.10,-.30,.34,-.91),
    105: (.37,.25,.25,-.64,.11,.19,    .84,.16,.73, .65,.26,.87,   .34,.38,-.07,-.32,.34,-.97),
    # The front foot bears weight before the pushing hand comes away.
    114: (.43,.11,.18,-.60,.05,.14,    .74,.18,.66, .71,.18,.70,   .31,.44,-.03,-.31,.38,-.86),
    124: (.60,.065,.10,-.45,.02,.10,  .59,.18,.60, .76,.17,.66,   .23,.60, .00,-.24,.55,-.61),
    134: (.79,.025,.045,-.26,0,.06,    .35,.17,.55, .52,.16,.54,   .10,.77, .00,-.13,.72,-.31),
    143: (.945,0,0,-.035,0,0,          .11,.10,.31, .13,.10,.31,   .0,.89,0,-.02,.885,0),
}

CLIPS = {
    "dive_motion": ("Forward dive", POSES),
    "knee_drop": ("Knee drop", KNEE_DROP),
    "side_roll": ("Side roll", SIDE_ROLL),
}


def key(item, channel, value, frame):
    item[channel] = float(value)
    item.keyframe_insert(data_path=f'["{channel}"]', frame=frame, group="Dive pose")


def create(clip):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.frame_start, scene.frame_end = 1, LAST
    scene.render.fps = FPS
    markers = {
        "dive_motion": (("Push", 6), ("Reach", 19), ("Palm to floor", 26), ("Slide", 33), ("Gather hands", 78)),
        "knee_drop": (("Brace", 9), ("Rear knee down", 33), ("Lead foot loads", 72)),
        "side_roll": (("Lateral reach", 21), ("Hip", 33), ("Tucked shoulder roll", 64),
                      ("Palm braced", 98), ("Weight onto front foot", 114), ("Stand", 124)),
    }
    for name, frame in (*markers[clip], ("Get up", 93), ("Ready", LAST)):
        scene.timeline_markers.new(name, frame=frame)

    name, poses = CLIPS[clip]
    sideways = clip == "side_roll"
    controls = pivot(name)
    for frame, values in poses.items():
        for channel, value in zip(CHANNELS, values, strict=True):
            key(controls, channel, value, frame)
    controls.animation_data.action.name = f"{name} and recovery"
    travel = pivot("Preview travel", controls)
    for frame, x in ((1, -1.8), (19, -1.15), (33, -.55), (55, .12), (78, .34), (LAST, .38)):
        axis = 1 if sideways else 0
        travel.location[axis] = -x * .7 if sideways else x
        travel.keyframe_insert(data_path="location", index=axis, frame=frame)
    pelvis = pivot("Pelvis", travel)
    drive(pelvis, "location", 1 if sideways else 0, controls, "-advance" if sideways else "advance", advance="advance")
    drive(pelvis, "location", 2, controls, "height", height="hip_height")
    drive(pelvis, "rotation_euler", 0 if sideways else 1, controls, "pitch", pitch="pitch")
    if sideways:
        pelvis.rotation_mode = "ZXY"
        drive(pelvis, "rotation_euler", 2, controls, "-rock", rock="bank")
    chest = pivot("Chest", pelvis)
    drive(chest, "rotation_euler", 1, controls, "-lean", lean="lean")
    if not sideways:
        drive(chest, "rotation_euler", 0, controls, "-bank", bank="bank")
    head = pivot("Head joint", chest, (0, 0, .54))
    drive(head, "rotation_euler", 1, controls, "-head", head="head")
    shirt = material("Training jersey", (.08, .27, .78))
    shorts = material("Shorts", (.82, .86, .92))
    skin = material("Skin", (.78, .55, .39))
    dark = material("Pads and shoes", (.075, .075, .085))
    floor = material("Indoor court", (.79, .46, .17))
    line = material("Court markings", (.93, .91, .86))
    block("Floor", None, (0, 0, -.05), (7.0, 3.0, .10), floor)
    block("Court line", None, (0, -.78, .004), (7.0, .045, .008), line)
    block("Jersey", chest, (0, 0, .235), (.26, .46, .49), shirt)
    block("Shorts", pelvis, (0, 0, -.028), (.27, .41, .18), shorts)
    block("Head", head, (.024, 0, .135), (.24, .24, .27), skin)
    block("Hair", head, (.002, 0, .27), (.255, .251, .062), dark)
    for side, sign in (("support", 1), ("reach", -1)):
        shoulder = pivot(f"{side} shoulder", chest, (0, .26 * sign, .44))
        shoulder.rotation_mode = "YXZ"
        drive(shoulder, "rotation_euler", 0, controls, f"{sign} * spread", spread=f"{side}_spread")
        drive(shoulder, "rotation_euler", 1, controls, "-raise_angle", raise_angle=f"{side}_raise")
        elbow = pivot(f"{side} elbow", shoulder, (0, 0, -.34))
        drive(elbow, "rotation_euler", 1, controls, "-bend", bend=f"{side}_elbow")
        block("Upper arm", shoulder, (0, 0, -.17), (.119, .122, .34), shirt)
        block("Forearm and palm", elbow, (.014, 0, -.175), (.104, .102, .35), skin)
    for side, sign in (("support", 1), ("trail", -1)):
        hip = pivot(f"{side} hip", pelvis, (0, .11 * sign, -.02))
        knee = pivot(f"{side} knee", hip, (0, 0, -.43))
        foot = pivot(f"{side} foot", knee, (0, 0, -.465))
        # Same planar two-link solve as the renderer. The preview omits the small lateral offset.
        d = "min(.894, max(.045, sqrt(x*x + y*y)))"
        a = f"acos(max(-1, min(1, (.43*.43 + {d}**2 - .465*.465) / (.86*{d}))))"
        b = f"acos(max(-1, min(1, (.465*.465 + {d}**2 - .43*.43) / (.93*{d}))))"
        bend = f"acos(max(-1, min(1, ({d}**2 - .43*.43 - .465*.465)/.3999)))"
        for joint, expr in ((hip, f"-atan2(x,y)-{a}"), (knee, bend),
                            (foot, f"atan2(x,y)-{b}-pitch")):
            drive(joint, "rotation_euler", 1, controls, expr, x=f"{side}_ankle",
                  y=f"{side}_depth", pitch=f"{side}_pitch")
        block("Thigh", hip, (0, 0, -.215), (.155, .156, .43), skin)
        block("Shin", knee, (0, 0, -.2325), (.125, .127, .465), skin)
        block("Knee pad", knee, (.034, 0, -.035), (.156, .153, .14), dark)
        block("Shoe", foot, (.055, 0, -.003), (.25, .14, .07), dark)

    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
    bpy.ops.object.camera_add(location=(3.4, -5.2, 2.5))
    camera = bpy.context.object
    camera.rotation_euler = (Vector((0, 0, .62)) - camera.location).to_track_quat("-Z", "Y").to_euler()
    camera.data.type, camera.data.ortho_scale = "ORTHO", 5.25
    scene.camera = camera
    scene.render.resolution_x, scene.render.resolution_y = 1280, 720
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == "VIEW_3D":
                area.spaces.active.region_3d.view_perspective = "CAMERA"
                area.spaces.active.shading.color_type = "MATERIAL"
    bpy.ops.object.select_all(action="DESELECT")
    controls.select_set(True)
    bpy.context.view_layer.objects.active = controls
    scene.frame_set(105 if sideways else 26)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(DIRECTORY / f"{clip}.blend"), compress=True)


def export(clip):
    scene = bpy.context.scene
    controls = bpy.data.objects[CLIPS[clip][0]]
    path = DIRECTORY.parents[1] / "core/src/main/resources/animation" / f"{clip}.csv"
    first_frame = scene.frame_current
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [",".join(CHANNELS)]
    for frame in range(1, LAST + 1):
        scene.frame_set(frame)
        pose = controls.evaluated_get(bpy.context.evaluated_depsgraph_get())
        lines.append(",".join(f"{pose[channel]:.5f}" for channel in CHANNELS))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    scene.frame_set(first_frame)
    print(f"Exported {len(lines) - 1} pose samples at {FPS} Hz")


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    clip = next((arg.split("=", 1)[1] for arg in args if arg.startswith("--clip=")), "dive_motion")
    if "--create" in args:
        create(clip)
    export(clip)
