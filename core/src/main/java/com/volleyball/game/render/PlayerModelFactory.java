package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.volleyball.game.entities.VolleyballPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Procedural, skeleton-like humanoid used until skinned character models exist. Each body
 * segment is a node whose origin is its joint, and the nodes form a hierarchy
 * (pelvis → torso → head / upper arm → forearm; pelvis → thigh → shin → foot), so rotating a
 * joint moves everything below it — knees and elbows bend instead of limbs being squashed.
 *
 * <p>Model space: 1.90 m tall, standing on y = 0, facing +X; the player's left is -Z. The face,
 * the shirt front and the number panel on the back make the facing direction readable.
 */
final class PlayerModelFactory {
    record Rig(Model model, Map<String, List<FloorContact>> floorContacts) {
    }

    private final Map<String, List<FloorContact>> floorContacts = new LinkedHashMap<>();
    static final float MODEL_HEIGHT = 1.90f;

    static final String PELVIS = "pelvis";
    static final String TORSO = "torso";
    static final String HEAD = "head";
    static final String UPPER_ARM_LEFT = "upperArmL";
    static final String UPPER_ARM_RIGHT = "upperArmR";
    static final String FOREARM_LEFT = "forearmL";
    static final String FOREARM_RIGHT = "forearmR";
    static final String REFEREE_FINGER = "refereeFinger";
    static final String THIGH_LEFT = "thighL";
    static final String THIGH_RIGHT = "thighR";
    static final String SHIN_LEFT = "shinL";
    static final String SHIN_RIGHT = "shinR";
    static final String FOOT_LEFT = "footL";
    static final String FOOT_RIGHT = "footR";

    /** Pelvis (root) height when standing straight. */
    static final float PELVIS_HEIGHT = 0.95f;
    /** Hip joints sit this far below the pelvis origin. */
    static final float HIP_DROP = 0.02f;
    static final float HIP_WIDTH = 0.11f;
    static final float THIGH_LENGTH = 0.43f;
    static final float SHIN_LENGTH = 0.465f;
    /** Ankle joint height above the sole. */
    static final float ANKLE_HEIGHT = 0.035f;
    static final float SHOULDER_HEIGHT = 0.44f;
    static final float TORSO_HEIGHT = 0.10f;
    static final float SHOULDER_WIDTH = 0.26f;
    static final float UPPER_ARM_LENGTH = 0.34f;
    /** The palm in the forearm's frame (a little ahead of the forearm's axis, below the elbow). */
    static final float HAND_X = 0.03f;
    static final float HAND_Y = -0.345f;

    private static final Color SKIN = new Color(0.86f, 0.68f, 0.55f, 1f);
    private static final Color COACH_HAIR = new Color(0.52f, 0.50f, 0.48f, 1f);
    private static final Color SHOE = new Color(0.12f, 0.12f, 0.14f, 1f);
    private static final Color SOLE = new Color(0.92f, 0.92f, 0.92f, 1f);
    private static final Color EYE = new Color(0.08f, 0.08f, 0.1f, 1f);
    private static final Color NUMBER_PANEL = new Color(0.97f, 0.97f, 0.97f, 1f);
    private static final Color DARK_DIGITS = new Color(0.1f, 0.1f, 0.13f, 1f);

    private final ModelBuilder builder = new ModelBuilder();

    /** Seven-segment patterns (a b c d e f g) for digits 0-9. */
    private static final String[] DIGIT_SEGMENTS = {
            "abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg"};

    Rig build(Color shirt, Color shorts, int number, String club, VolleyballPlayer player) {
        return build(shirt, shorts, number, null, club, player);
    }

    /**
     * The libero: a shirt of its own colour with trim in the team's colour (collar, shoulders and
     * side panels), so it is obviously a teammate and obviously the libero.
     */
    Rig buildLibero(Color shirt, Color teamColour, Color shorts, int number, String club, VolleyballPlayer player) {
        return build(shirt, shorts, number, teamColour, club, player);
    }

    /**
     * A coach on the same skeleton (so the same poses work): a team-coloured track jacket with
     * long sleeves and a white collar, long trousers, grey hair, no number.
     */
    Rig buildCoach(Color jacket, Color trousers) {
        return build(jacket, trousers, 0, null, null, null);
    }

    Rig buildReferee(Color shirt, Color trousers) {
        return build(shirt, trousers, 0, null, null, null, true);
    }

    private Rig build(Color shirt, Color shorts, int number, Color liberoTrim, String club, VolleyballPlayer player) {
        return build(shirt, shorts, number, liberoTrim, club, player, false);
    }

    private Rig build(Color shirt, Color shorts, int number, Color liberoTrim, String club, VolleyballPlayer player,
                      boolean referee) {
        floorContacts.clear();
        builder.begin();
        Material material = new Material(ColorAttribute.createDiffuse(Color.WHITE), ShadingAttribute.body());
        Material cloth = new Material(ColorAttribute.createDiffuse(Color.WHITE),
                new ShadingAttribute(0f, 13f, 0.055f, 0.27f, ShadingAttribute.PATTERN_NONE, 0f));
        Material hairMaterial = new Material(ColorAttribute.createDiffuse(Color.WHITE),
                new ShadingAttribute(0f, 31f, 0.19f, 0.19f, ShadingAttribute.PATTERN_NONE, 0f));
        Material rubber = new Material(ColorAttribute.createDiffuse(Color.WHITE), ShadingAttribute.matte());
        Color shirtTrim = liberoTrim != null ? liberoTrim : new Color(shirt).lerp(Color.WHITE, 0.55f);
        boolean coach = player == null;
        PlayerMeshes.Style look = coach ? null : PlayerMeshes.of(club, player);
        Map<PlayerMeshes.Surface, Color> palette = coach ? Map.of() : look.colors(shirt, shorts, shirtTrim);
        Color skin = coach ? SKIN : look.skin();
        Color sleeve = coach ? shirt : skin;
        Color leg = coach ? shorts : skin;
        String body = coach ? null : "body_" + look.build();
        String equipment = coach ? null : "kit_" + look.equipment();

        GeometryBuilder pelvis = node(PELVIS, 0f, PELVIS_HEIGHT, 0f, coach ? material : cloth);
        if (coach) pelvis.box(0f, 0.01f, 0f, 0.26f, 0.22f, 0.38f, shorts);
        else PlayerMeshes.draw(pelvis, body, "pelvis", palette);

        GeometryBuilder torso = node(TORSO, 0f, TORSO_HEIGHT, 0f, coach ? material : cloth);
        if (coach) {
            torso.box(0f, 0.05f, 0f, 0.22f, 0.12f, 0.36f, shirt);
            torso.box(0f, 0.27f, 0f, 0.25f, 0.34f, 0.44f, shirt);
            torso.box(0.127f, 0.24f, 0f, 0.01f, 0.36f, 0.025f, shirtTrim);   // zip
            torso.box(0.127f, 0.41f, 0f, 0.012f, 0.06f, 0.16f, NUMBER_PANEL); // white collar
            torso.box(0f, 0.30f, -0.222f, 0.26f, 0.03f, 0.01f, shirtTrim);   // shoulder stripes
            torso.box(0f, 0.30f, 0.222f, 0.26f, 0.03f, 0.01f, shirtTrim);
        } else {
            PlayerMeshes.draw(torso, body, "torso", palette);
            torso.box(-0.127f, 0.25f, 0f, 0.012f, 0.24f, 0.30f, NUMBER_PANEL); // number panel (back)
            torso.box(0.13f, 0.315f, -0.096f, 0.009f, 0.045f, 0.047f, shirtTrim); // chest badge
            // A light shirt gets dark digits (white on the white panel would vanish).
            Color digits = com.volleyball.game.entities.Team.luminance(shirt) > 0.7f ? DARK_DIGITS : shirt;
            backNumber(torso, Math.max(0, Math.min(99, number)), digits);
            if (liberoTrim != null) {
                torso.box(0f, 0.475f, 0f, 0.21f, 0.012f, 0.31f, liberoTrim);
            }
        }
        torso.box(0f, 0.43f, 0f, 0.20f, 0.07f, 0.30f, shirt);           // shoulders (trapezius)
        torso.box(0f, 0.49f, 0f, 0.13f, 0.12f, 0.13f, skin);            // neck

        GeometryBuilder head = node(HEAD, 0f, 0.52f, 0f, material);
        head.box(-0.005f, 0.0f, 0f, 0.12f, 0.12f, 0.12f, skin);          // top of the neck (inside the head)
        if (coach) {
            head.sphere(0.01f, 0.125f, 0f, 0.23f, skin);
            head.box(-0.005f, 0.13f, -0.118f, 0.04f, 0.06f, 0.02f, skin);
            head.box(-0.005f, 0.13f, 0.118f, 0.04f, 0.06f, 0.02f, skin);
            head.box(-0.025f, 0.235f, 0f, 0.21f, 0.06f, 0.21f, COACH_HAIR);
            head.box(-0.075f, 0.16f, 0f, 0.1f, 0.14f, 0.2f, COACH_HAIR);
            head.box(0.125f, 0.12f, 0f, 0.04f, 0.05f, 0.035f, skin);
            head.box(0.112f, 0.165f, -0.045f, 0.02f, 0.025f, 0.03f, EYE);
            head.box(0.112f, 0.165f, 0.045f, 0.02f, 0.025f, 0.03f, EYE);
        } else {
            PlayerMeshes.draw(head, "face_" + look.face(), "head", palette);
            GeometryBuilder hair = new GeometryBuilder(builder, "hair", hairMaterial, floorContacts.get(HEAD));
            if (look.hair().isEmpty()) {
                for (LookDevelopment.HairBox piece : LookDevelopment.player(club + "/" + player.name).silhouette()) {
                    piece.draw(hair, look.hairColor());
                }
            } else {
                PlayerMeshes.draw(hair, "hair_" + look.hair(), "head", palette);
            }
        }

        for (int side = -1; side <= 1; side += 2) {
            boolean left = side < 0;
            GeometryBuilder upper = node(left ? UPPER_ARM_LEFT : UPPER_ARM_RIGHT, 0f, SHOULDER_HEIGHT, side * SHOULDER_WIDTH,
                    coach ? material : cloth);
            if (coach) {
                upper.box(0f, -0.07f, 0f, 0.115f, 0.15f, 0.115f, shirt);
                upper.box(0f, -0.01f, 0f, 0.13f, 0.09f, 0.13f, shirt);
            } else PlayerMeshes.draw(upper, body, "upper", palette);
            upper.box(0f, -0.24f, 0f, coach ? 0.1f : 0.085f, 0.2f, coach ? 0.1f : 0.085f, sleeve);
            GeometryBuilder fore = node(left ? FOREARM_LEFT : FOREARM_RIGHT, 0f, -UPPER_ARM_LENGTH, 0f, material);
            fore.box(0f, -0.14f, 0f, coach ? 0.09f : 0.075f, coach ? 0.27f : 0.29f, coach ? 0.09f : 0.075f, sleeve);
            if (referee && !left) {
                fore.box(0.005f, -0.328f, 0f, 0.063f, 0.077f, 0.079f, skin);
                for (int finger = 0; finger < 4; finger++) {
                    float length = 0.065f - 0.005f * Math.abs(1 - finger);
                    node(REFEREE_FINGER + finger, 0.006f, -0.36f, (finger - 1.5f) * 0.021f, material)
                            .box(0f, -length / 2f, 0f, 0.016f, length, 0.015f, skin);
                }
            } else fore.box(0.005f, -0.345f, 0f, 0.075f, 0.11f, 0.05f, skin);
            if (!coach && left != player.isLeftHanded()) PlayerMeshes.draw(fore, equipment, "forearm", palette);

            GeometryBuilder thigh = node(left ? THIGH_LEFT : THIGH_RIGHT, 0f, -HIP_DROP, side * HIP_WIDTH, material);
            thigh.box(0f, -0.21f, 0f, coach ? 0.16f : 0.15f, 0.42f, coach ? 0.16f : 0.15f, leg);
            if (!coach) PlayerMeshes.draw(thigh, body, "thigh", palette);
            GeometryBuilder shin = node(left ? SHIN_LEFT : SHIN_RIGHT, 0f, -THIGH_LENGTH, 0f, material);
            if (coach) {
                shin.box(0f, -0.21f, 0f, 0.13f, 0.44f, 0.13f, leg);     // trouser leg down to the shoe
            } else {
                shin.box(0f, -0.22f, 0f, 0.115f, 0.34f, 0.115f, skin);
                PlayerMeshes.draw(shin, equipment, "shin", palette);
            }
            GeometryBuilder foot = node(left ? FOOT_LEFT : FOOT_RIGHT, 0f, -SHIN_LENGTH, 0f, coach ? material : rubber);
            if (coach) {
                foot.box(0.05f, -0.01f, 0f, 0.27f, 0.05f, 0.115f, SHOE);
                foot.box(0.05f, -0.03f, 0f, 0.275f, 0.012f, 0.12f, SOLE);
            } else PlayerMeshes.draw(foot, equipment, "foot", palette);
        }
        Model model = builder.end();

        attach(model, TORSO, PELVIS);
        attach(model, HEAD, TORSO);
        attach(model, UPPER_ARM_LEFT, TORSO);
        attach(model, UPPER_ARM_RIGHT, TORSO);
        attach(model, FOREARM_LEFT, UPPER_ARM_LEFT);
        attach(model, FOREARM_RIGHT, UPPER_ARM_RIGHT);
        if (referee) {
            for (int finger = 0; finger < 4; finger++) attach(model, REFEREE_FINGER + finger, FOREARM_RIGHT);
        }
        attach(model, THIGH_LEFT, PELVIS);
        attach(model, THIGH_RIGHT, PELVIS);
        attach(model, SHIN_LEFT, THIGH_LEFT);
        attach(model, SHIN_RIGHT, THIGH_RIGHT);
        attach(model, FOOT_LEFT, SHIN_LEFT);
        attach(model, FOOT_RIGHT, SHIN_RIGHT);
        model.calculateTransforms();
        return new Rig(model, Map.copyOf(floorContacts));
    }

    /**
     * Jersey number on the back panel as seven-segment digits. Seen from behind (looking along
     * +X) the reader's right is +Z, so digits run towards +Z.
     */
    private static void backNumber(GeometryBuilder torso, int number, Color color) {
        String text = Integer.toString(number);
        float digitWidth = 0.085f;
        float gap = 0.035f;
        float total = text.length() * digitWidth + (text.length() - 1) * gap;
        float start = -total / 2f;
        for (int i = 0; i < text.length(); i++) {
            float left = start + i * (digitWidth + gap);
            digit(torso, text.charAt(i) - '0', left, digitWidth, color);
        }
    }

    private static void digit(GeometryBuilder g, int value, float left, float width, Color color) {
        float x = -0.134f;          // just behind the panel surface
        float bottom = 0.17f;
        float height = 0.16f;
        float t = 0.022f;           // stroke
        float mid = bottom + height / 2f;
        float top = bottom + height;
        float right = left + width;
        String segments = DIGIT_SEGMENTS[value];
        for (char segment : segments.toCharArray()) {
            switch (segment) {
                case 'a' -> g.box(x, top - t / 2f, left + width / 2f, 0.004f, t, width, color);
                case 'g' -> g.box(x, mid, left + width / 2f, 0.004f, t, width, color);
                case 'd' -> g.box(x, bottom + t / 2f, left + width / 2f, 0.004f, t, width, color);
                case 'b' -> g.box(x, (mid + top) / 2f, right - t / 2f, 0.004f, height / 2f, t, color);
                case 'c' -> g.box(x, (bottom + mid) / 2f, right - t / 2f, 0.004f, height / 2f, t, color);
                case 'f' -> g.box(x, (mid + top) / 2f, left + t / 2f, 0.004f, height / 2f, t, color);
                case 'e' -> g.box(x, (bottom + mid) / 2f, left + t / 2f, 0.004f, height / 2f, t, color);
                default -> {
                }
            }
        }
    }

    /** Creates a node whose translation is relative to its future parent (see {@link #attach}). */
    private GeometryBuilder node(String id, float x, float y, float z, Material material) {
        Node node = builder.node();
        node.id = id;
        node.translation.set(x, y, z);
        List<FloorContact> shapes = new ArrayList<>();
        floorContacts.put(id, shapes);
        return new GeometryBuilder(builder, id, material, shapes);
    }

    /** ModelBuilder only creates root nodes; this moves a node under its parent joint. */
    private static void attach(Model model, String childId, String parentId) {
        Node child = model.getNode(childId);
        Node parent = model.getNode(parentId);
        model.nodes.removeValue(child, true);
        parent.addChild(child);
    }
}
