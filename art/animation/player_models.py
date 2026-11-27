"""Blender source and export for the articulated low-poly player geometry."""

from pathlib import Path
import sys

import bpy
from mathutils import Vector

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from jump_motion import block, material, pivot
from arena_and_players import camera

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT.parents[1] / "core/src/main/resources/art/player_parts.csv"

# Each shape is a bevelled cuboid in the same joint-local units as PlayerModelFactory.
# x is forwards, y is up. Only the shape varies; sockets stay on the game's skeleton.
PROFILES = {
    "Sokolov": ("oval", "compact", "crew", "quick", (0.76, 0.49, 0.33), (.20, .109, .058), .95),
    "Orlov": ("square", "broad", "ridge", "brace", (.52, .32, .23), (.065, .055, .052), 1.07),
    "Smirnov": ("long", "lean", "swept", "sleeve", (.85, .63, .46), (.43, .26, .105), 1.02),
}
FACES = {
    "oval": (.215, .215, .148, .150, .047, .034, .016),
    "square": (.225, .237, .188, .163, .056, .041, .012),
    "long": (.219, .205, .143, .169, .064, .032, .019),
}
BODIES = {
    "compact": (.395, .331, .35, .151, .112),
    "broad": (.475, .375, .398, .163, .131),
    "lean": (.410, .329, .354, .149, .117),
}
HAIR = {
    "crew": [
        ((-.018,.224,0),(.221,.065,.222),.014),
        ((-.088,.175,0),(.077,.108,.205),.013),
        ((.037,.237,.005),(.112,.051,.211),.010),
    ],
    "ridge": [
        ((-.027,.23,0),(.215,.070,.234),.012),
        ((-.038,.274,0),(.170,.077,.180),.016),
        ((-.087,.176,0),(.079,.114,.215),.010),
        ((.023,.300,-.038),(.085,.036,.093),.010),
    ],
    "swept": [
        ((-.027,.239,0),(.229,.074,.216),.014),
        ((.050,.272,-.034),(.168,.078,.130),.016),
        ((.092,.231,-.079),(.079,.083,.057),.011),
        ((-.087,.186,.006),(.087,.135,.207),.012),
    ],
}


def blender_axes(xyz):
    x, y, z = xyz
    return x, -z, y


def game_axes(xyz):
    x, y, z = xyz
    return x, z, -y


def piece(socket, asset, joint, name, palette, center, dimensions, bevel, materials):
    x, y, z = dimensions
    mesh = block(name, socket, blender_axes(center), (x, z, y), materials[palette])
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel and min(dimensions) >= .026:
        edge = mesh.modifiers.new("Single faceted chamfer", "BEVEL")
        edge.width = bevel
        edge.segments = 1
    if socket["export"]:
        mesh["part_asset"] = asset
        mesh["part_joint"] = joint
        mesh["part_color"] = palette
    return mesh


def at(root, joints, joint, point, materials, export=True, key=None):
    socket = pivot(key or joint, root, blender_axes(point))
    # Store the export flag in the scene. Keep Blender Material references in Python.
    socket["export"] = export
    joints[key or joint] = (socket, materials)


def author(root, profile, materials):
    face, body, hairstyle, kit, _, _, _ = profile
    joints = {}
    # (x, y, z) in standing game coordinates. These are preview sockets only.
    at(root, joints, "head", (0,1.57,0), materials)
    at(root, joints, "torso", (0,1.05,0), materials)
    at(root, joints, "pelvis", (0,.95,0), materials)
    for side, label in ((1, ""), (-1, "_left")):
        at(root, joints, "upper", (0,1.49,.26*side), materials, side == 1, key="upper"+label)
        at(root, joints, "forearm", (0,1.15,.26*side), materials, True, key="forearm"+label)
        at(root, joints, "thigh", (0,.93,.11*side), materials, side == 1, key="thigh"+label)
        at(root, joints, "shin", (0,.50,.11*side), materials, side == 1, key="shin"+label)
        at(root, joints, "foot", (0,.035,.11*side), materials, side == 1, key="foot"+label)

    def add(joint, asset, name, tint, center, dims, bevel=0, suffix=""):
        socket, colors = joints[joint + suffix]
        return piece(socket, asset, joint, name, tint, center, dims, bevel, colors)

    face_width, crown, jaw, chin, nose, nose_width, edge = FACES[face]
    asset = "face_" + face
    add("head",asset,"Temples","skin",(-.006,.145,0),(.224,.185,face_width),edge)
    add("head",asset,"Jaw","skin",(.014,.063,0),(chin,.104,jaw),edge*.82)
    add("head",asset,"Nose bridge","skin",(.109,.125,0),(nose,.052,nose_width),.006)
    add("head",asset,"Mouth","lip",(.097,.069,0),(.009,.009,.046 if face == "square" else .040))
    for side in (-1,1):
        add("head",asset,"Ear","skin",(-.021,.123,side*face_width*.493),(.053,.061,.024),.005)
        add("head",asset,"Eye","eye",(.105,.155,side*.046),(.018,.018,.031),.003)
        add("head",asset,"Brow","hair",(.103,.180+(crown-.215)*.08,side*.046),(.018,.009,.038),.002)

    for center, dims, bevel in HAIR[hairstyle]:
        add("head","hair_"+hairstyle,"Hair plane","hair",center,dims,bevel)
    if hairstyle == "crew":
        add("head","hair_crew","Fabric headband","band",(-.004,.200,0),(.232,.030,.226),.010)
    else:
        for side in (-1,1):
            add("head","hair_"+hairstyle,"Temple fade","hair",(-.065,.183,.094*side),(.091,.065,.034),.007)

    chest, waist, hips, shoulder, upper = BODIES[body]
    asset = "body_"+body
    add("torso",asset,"Jersey chest","jersey",(.004,.27,0),(.248,.340,chest),.023)
    add("torso",asset,"Jersey waist","jersey",(.001,.059,0),(.220,.156,waist),.018)
    add("torso",asset,"Collar","trim",(.082,.429,0),(.115,.049,.165),.012)
    add("torso",asset,"Collar opening","jersey",(.086,.447,0),(.075,.024,.107),.007)
    add("torso",asset,"Hem","dark_jersey",(.001,-.004,0),(.222,.024,waist+.009),.006)
    for side in (-1,1):
        add("torso",asset,"Side panel","team_trim",(0,.235,side*(chest*.49)),(.151,.160,.020),.005)
    add("pelvis",asset,"Shorts","shorts",(0,.012,0),(.256,.211,hips),.018)
    add("pelvis",asset,"Waistband","dark_shorts",(0,.109,0),(.255,.025,hips+.005),.006)
    for suffix in ("","_left"):
        add("upper",asset,"Shoulder cap","jersey",(0,-.029,0),(.140,.111,shoulder),.016,suffix)
        add("upper",asset,"Sleeve","jersey",(0,-.091,0),(.115,.141,upper),.012,suffix)
        add("upper",asset,"Cuff","trim",(0,-.156,0),(.115,.027,upper+.004),.007,suffix)
        add("thigh",asset,"Shorts leg","shorts",(0,-.060,0),(.154,.143,.151),.012,suffix)
        add("thigh",asset,"Shorts seam","dark_shorts",(0,-.122,0),(.150,.018,.151),.004,suffix)

    # The three equipment kits can be paired with any face and build.
    asset = "kit_"+kit
    pad_height = {"quick":.107,"brace":.149,"sleeve":.124}[kit]
    for suffix in ("","_left"):
        add("shin",asset,"Pad sleeve","pad",(.009,-.028,0),(.132,pad_height,.133),.014,suffix)
        add("shin",asset,"Pad face","pad_face",(.064,-.031,0),(.044,pad_height*.78,.114),.014,suffix)
        add("shin",asset,"Sock cuff","sock",(.001,-(.379 if kit == "sleeve" else .411),0),
            (.105,.152 if kit == "sleeve" else .090,.105),.007,suffix)
        add("shin",asset,"Cuff stripe","accent",(.002,-(.314 if kit == "sleeve" else .381),0),
            (.105,.014,.107),.003,suffix)
        add("foot",asset,"Court shoe","shoe",(.047,-.007,0),(.267,.052,.118),.011,suffix)
        add("foot",asset,"Sole","sole",(.050,-.029,0),(.275,.012,.121),.004,suffix)
        add("foot",asset,"Heel strip","accent",(-.063,.006,0),(.038,.039,.122),.006,suffix)
        add("foot",asset,"Toe panel","shoe_trim",(.100,.017,0),(.113,.021,.090),.007,suffix)
    if kit == "sleeve":
        add("forearm",asset,"Compression sleeve","pad",(0,-.125,0),(.085,.255,.085),.010,"_left")
        add("forearm",asset,"Sleeve band","accent",(0,-.232,0),(.088,.024,.088),.004,"_left")

    # Garnish the standing preview with the game's unchanged exposed sections.
    for suffix in ("","_left"):
        for joint, center, dimensions in (
            ("upper",(0,-.236,0),(.085,.20,.085)),
            ("forearm",(0,-.148,0),(.075,.281,.075)),
            ("forearm",(.005,-.345,0),(.075,.11,.05)),
            ("thigh",(0,-.252,0),(.148,.34,.145)),
            ("shin",(0,-.215,0),(.115,.34,.115)),
        ):
            socket, colors = joints[joint+suffix]
            block("Preview anatomy", socket, blender_axes(center),
                  (dimensions[0],dimensions[2],dimensions[1]), colors["skin"])
    block("Preview neck",joints["torso"][0],(0,0,.49),(.13,.13,.12),materials["skin"])


def create():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.name = "Northern Wolves | player model study"
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.show_cavity = True
    scene.display.shading.show_shadows = True
    court = material("Warm court", (.47,.30,.15))
    block("Court study",None,(0,0,-.045),(4.2,5.2,.09),court)
    for i,(name, p) in enumerate(PROFILES.items()):
        root = pivot(name, location=(0,(i-1)*1.25,0))
        root.scale = (p[-1],)*3
        jersey = (.78,.83,.87) if name == "Sokolov" else (.055,.20,.61)
        accent = ((.18,.65,.73),(.86,.81,.70),(.92,.44,.17))[i]
        palette = dict(skin=p[4], hair=p[5], jersey=jersey, dark_jersey=tuple(c*.7 for c in jersey),
                       trim=(.90,.90,.91), team_trim=(.08,.24,.63), shorts=(.22,.24,.31),
                       dark_shorts=(.13,.14,.18), lip=tuple(c*.75 for c in p[4]), eye=(.06,.05,.05),
                       band=(.74,.89,.88), pad=(.09,.105,.14), pad_face=(.22,.25,.30),
                       sock=(.87,.87,.86), accent=accent, shoe=(.065,.082,.13),
                       shoe_trim=(.33,.37,.42), sole=(.82,.81,.78))
        author(root, p, {name: material(name, color) for name,color in palette.items()})
    camera(scene, (6,-2.2,2.8), (0,0,1.04), 4.52)
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == "VIEW_3D":
                area.spaces.active.region_3d.view_perspective = "CAMERA"
                area.spaces.active.shading.color_type = "MATERIAL"
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "player_models.blend"), compress=True)


def export():
    depsgraph = bpy.context.evaluated_depsgraph_get()
    lines = ["asset,joint,palette,triangle_vertices"]
    shapes = sorted((obj for obj in bpy.context.scene.objects if obj.get("part_asset")),
                    key=lambda item:(item["part_asset"],item["part_joint"],item.name))
    for obj in shapes:
        evaluated = obj.evaluated_get(depsgraph)
        to_socket = obj.parent.matrix_world.inverted() @ evaluated.matrix_world
        mesh = evaluated.to_mesh()
        mesh.calc_loop_triangles()
        coords = [game_axes(to_socket @ vertex.co) for vertex in mesh.vertices]
        values = (f"{value:.5f}" for face in mesh.loop_triangles for index in face.vertices for value in coords[index])
        lines.append(",".join((obj["part_asset"],obj["part_joint"],obj["part_color"],*values)))
        evaluated.to_mesh_clear()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines)+"\n",encoding="utf-8",newline="\n")
    print(f"Exported {len(shapes)} player model parts to {OUTPUT}")


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else []
    if "--create" in args:
        create()
    export()
