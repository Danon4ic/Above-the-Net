package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

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
    static final float MODEL_HEIGHT = 1.90f;

    static final String PELVIS = "pelvis";
    static final String TORSO = "torso";
    static final String HEAD = "head";
    static final String UPPER_ARM_LEFT = "upperArmL";
    static final String UPPER_ARM_RIGHT = "upperArmR";
    static final String FOREARM_LEFT = "forearmL";
    static final String FOREARM_RIGHT = "forearmR";
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
    static final float SHOULDER_WIDTH = 0.235f;
    static final float UPPER_ARM_LENGTH = 0.29f;

    private static final Color SKIN = new Color(0.86f, 0.68f, 0.55f, 1f);
    private static final Color HAIR = new Color(0.20f, 0.14f, 0.10f, 1f);
    private static final Color COACH_HAIR = new Color(0.52f, 0.50f, 0.48f, 1f);
    private static final Color SHOE = new Color(0.12f, 0.12f, 0.14f, 1f);
    private static final Color SOLE = new Color(0.92f, 0.92f, 0.92f, 1f);
    private static final Color SOCK = new Color(0.95f, 0.95f, 0.95f, 1f);
    private static final Color KNEE_PAD = new Color(0.15f, 0.15f, 0.17f, 1f);
    private static final Color EYE = new Color(0.08f, 0.08f, 0.1f, 1f);
    private static final Color NUMBER_PANEL = new Color(0.97f, 0.97f, 0.97f, 1f);
    private static final Color DARK_DIGITS = new Color(0.1f, 0.1f, 0.13f, 1f);

    private final ModelBuilder builder = new ModelBuilder();

    /** Seven-segment patterns (a b c d e f g) for digits 0-9. */
    private static final String[] DIGIT_SEGMENTS = {
            "abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg"};

    Model build(Color shirt, Color shorts, int number) {
        return build(shirt, shorts, number, false, null);
    }

    /**
     * The libero: a shirt of its own colour with trim in the team's colour (collar, shoulders and
     * side panels), so it is obviously a teammate and obviously the libero.
     */
    Model buildLibero(Color shirt, Color teamColour, Color shorts, int number) {
        return build(shirt, shorts, number, false, teamColour);
    }

    /**
     * A coach on the same skeleton (so the same poses work): a team-coloured track jacket with
     * long sleeves and a white collar, long trousers, grey hair, no number.
     */
    Model buildCoach(Color jacket, Color trousers) {
        return build(jacket, trousers, 0, true, null);
    }

    private Model build(Color shirt, Color shorts, int number, boolean coach, Color liberoTrim) {
        builder.begin();
        Material material = new Material(ColorAttribute.createDiffuse(Color.WHITE), ShadingAttribute.body());
        Color shirtTrim = liberoTrim != null ? liberoTrim : new Color(shirt).lerp(Color.WHITE, 0.55f);
        Color sleeve = coach ? shirt : SKIN;
        Color leg = coach ? shorts : SKIN;
        Color hair = coach ? COACH_HAIR : HAIR;

        // Pelvis and hips (shorts): a rounded block, wider than deep.
        GeometryBuilder pelvis = node(PELVIS, 0f, PELVIS_HEIGHT, 0f, material);
        pelvis.frustum(0f, 0f, 0.11f, -0.07f, 0.21f, 0.33f, 0.24f, 0.37f, shorts);
        pelvis.ellipsoid(-0.02f, -0.06f, 0f, 0.22f, 0.12f, 0.36f, shorts);  // seat

        // Trunk: abdomen, then a chest that widens to the shoulders; shoulders and neck rounded.
        GeometryBuilder torso = node(TORSO, 0f, 0.10f, 0f, material);
        torso.frustum(0f, 0f, 0.19f, -0.01f, 0.225f, 0.35f, 0.21f, 0.32f, shirt);   // abdomen
        torso.frustum(0f, 0f, 0.41f, 0.18f, 0.25f, 0.42f, 0.23f, 0.36f, shirt);     // chest
        torso.ellipsoid(0f, 0.405f, 0f, 0.235f, 0.11f, 0.42f, liberoTrim != null ? liberoTrim : shirt); // shoulder line
        if (coach) {
            torso.box(0.124f, 0.24f, 0f, 0.01f, 0.34f, 0.025f, shirtTrim);   // zip
            torso.box(0.12f, 0.43f, 0f, 0.03f, 0.06f, 0.16f, NUMBER_PANEL);  // white collar
        } else {
            torso.box(0.118f, 0.425f, 0f, 0.02f, 0.035f, 0.13f, shirtTrim);   // collar (front)
            torso.box(-0.126f, 0.25f, 0f, 0.012f, 0.22f, 0.28f, NUMBER_PANEL); // number panel (back)
            // A light shirt gets dark digits (white on the white panel would vanish).
            Color digits = com.volleyball.game.entities.Team.luminance(shirt) > 0.7f ? DARK_DIGITS : shirt;
            backNumber(torso, Math.max(0, Math.min(99, number)), digits);
        }
        torso.frustum(0.005f, 0f, 0.56f, 0.43f, 0.105f, 0.115f, 0.12f, 0.13f, SKIN); // neck

        GeometryBuilder head = node(HEAD, 0f, 0.52f, 0f, material);
        head.ellipsoid(0.01f, 0.13f, 0f, 0.215f, 0.24f, 0.19f, SKIN);
        head.ellipsoid(0.035f, 0.065f, 0f, 0.16f, 0.1f, 0.15f, SKIN);         // jaw and chin
        head.ellipsoid(-0.005f, 0.13f, -0.098f, 0.05f, 0.065f, 0.025f, SKIN); // ears
        head.ellipsoid(-0.005f, 0.13f, 0.098f, 0.05f, 0.065f, 0.025f, SKIN);
        head.ellipsoid(-0.02f, 0.2f, 0f, 0.225f, 0.14f, 0.2f, hair);          // hair: crown...
        head.ellipsoid(-0.07f, 0.15f, 0f, 0.1f, 0.17f, 0.19f, hair);          // ...and back of the head
        head.ellipsoid(0.108f, 0.115f, 0f, 0.035f, 0.055f, 0.03f, SKIN);      // nose
        head.ellipsoid(0.1f, 0.155f, -0.04f, 0.02f, 0.022f, 0.03f, EYE);
        head.ellipsoid(0.1f, 0.155f, 0.04f, 0.02f, 0.022f, 0.03f, EYE);
        head.box(0.107f, 0.19f, -0.04f, 0.012f, 0.012f, 0.04f, hair);        // brows
        head.box(0.107f, 0.19f, 0.04f, 0.012f, 0.012f, 0.04f, hair);

        for (int side = -1; side <= 1; side += 2) {
            boolean left = side < 0;
            Color cap = liberoTrim != null ? liberoTrim : coach ? shirtTrim : shirt;
            // Upper arm: a rounded shoulder (deltoid) in the shirt, the sleeve, then the arm to the elbow.
            GeometryBuilder upper = node(left ? UPPER_ARM_LEFT : UPPER_ARM_RIGHT, 0f, SHOULDER_HEIGHT, side * SHOULDER_WIDTH, material);
            upper.ellipsoid(0f, -0.02f, -side * 0.01f, 0.12f, 0.125f, 0.112f, cap);
            upper.frustum(0f, 0f, -0.02f, -0.15f, 0.118f, 0.112f, 0.108f, 0.104f, shirt);
            upper.frustum(0f, 0f, -0.14f, -UPPER_ARM_LENGTH, coach ? 0.105f : 0.1f, coach ? 0.105f : 0.097f,
                    coach ? 0.088f : 0.08f, coach ? 0.088f : 0.077f, sleeve);
            upper.ellipsoid(0f, -UPPER_ARM_LENGTH, 0f, 0.084f, 0.08f, 0.08f, sleeve); // elbow
            // Forearm, wrist and hand: palm, fingers together, thumb on the front.
            GeometryBuilder fore = node(left ? FOREARM_LEFT : FOREARM_RIGHT, 0f, -UPPER_ARM_LENGTH, 0f, material);
            fore.frustum(0f, 0f, -0.01f, -0.24f, coach ? 0.095f : 0.09f, coach ? 0.095f : 0.086f, 0.064f, 0.056f, sleeve);
            fore.frustum(0.003f, 0f, -0.235f, -0.33f, 0.072f, 0.032f, 0.08f, 0.03f, SKIN);   // palm
            fore.ellipsoid(0.005f, -0.355f, 0f, 0.075f, 0.065f, 0.026f, SKIN);              // fingers
            fore.ellipsoid(0.04f, -0.265f, -side * 0.012f, 0.024f, 0.055f, 0.022f, SKIN);   // thumb

            // Thigh: the shorts' leg, then the bare thigh tapering to the knee.
            GeometryBuilder thigh = node(left ? THIGH_LEFT : THIGH_RIGHT, 0f, -HIP_DROP, side * HIP_WIDTH, material);
            if (coach) {
                thigh.frustum(0f, 0f, 0.03f, -THIGH_LENGTH, 0.19f, 0.185f, 0.13f, 0.125f, leg);
            } else {
                thigh.frustum(0f, 0f, 0.03f, -0.17f, 0.2f, 0.195f, 0.182f, 0.178f, shorts);
                thigh.frustum(0.006f, 0f, -0.15f, -THIGH_LENGTH + 0.02f, 0.172f, 0.165f, 0.118f, 0.112f, leg);
            }
            thigh.ellipsoid(0.006f, -THIGH_LENGTH, 0f, 0.12f, 0.105f, 0.11f, leg);   // knee
            // Shin: knee pad, calf (fuller at the back), the ankle in a sock.
            GeometryBuilder shin = node(left ? SHIN_LEFT : SHIN_RIGHT, 0f, -THIGH_LENGTH, 0f, material);
            if (coach) {
                shin.frustum(0f, 0f, 0f, -SHIN_LENGTH + 0.04f, 0.125f, 0.12f, 0.11f, 0.105f, leg);
            } else {
                shin.frustum(0.022f, 0f, 0.02f, -0.1f, 0.135f, 0.13f, 0.125f, 0.12f, KNEE_PAD);
                shin.frustum(-0.01f, 0f, -0.09f, -0.24f, 0.128f, 0.115f, 0.118f, 0.106f, SKIN);    // calf
                shin.frustum(-0.004f, 0f, -0.23f, -0.36f, 0.112f, 0.1f, 0.075f, 0.072f, SKIN);
                shin.frustum(0f, 0f, -0.33f, -SHIN_LENGTH + 0.02f, 0.08f, 0.078f, 0.076f, 0.074f, SOCK);
            }
            // Shoe: a rounded upper on a flat sole, toe to the front.
            GeometryBuilder foot = node(left ? FOOT_LEFT : FOOT_RIGHT, 0f, -SHIN_LENGTH, 0f, material);
            foot.ellipsoid(0.055f, 0.0f, 0f, 0.27f, 0.085f, 0.112f, SHOE);
            foot.ellipsoid(-0.035f, 0.02f, 0f, 0.1f, 0.09f, 0.1f, SHOE);           // heel
            foot.box(0.045f, -0.032f, 0f, 0.23f, 0.014f, 0.1f, SOLE);
        }
        Model model = builder.end();

        attach(model, TORSO, PELVIS);
        attach(model, HEAD, TORSO);
        attach(model, UPPER_ARM_LEFT, TORSO);
        attach(model, UPPER_ARM_RIGHT, TORSO);
        attach(model, FOREARM_LEFT, UPPER_ARM_LEFT);
        attach(model, FOREARM_RIGHT, UPPER_ARM_RIGHT);
        attach(model, THIGH_LEFT, PELVIS);
        attach(model, THIGH_RIGHT, PELVIS);
        attach(model, SHIN_LEFT, THIGH_LEFT);
        attach(model, SHIN_RIGHT, THIGH_RIGHT);
        attach(model, FOOT_LEFT, SHIN_LEFT);
        attach(model, FOOT_RIGHT, SHIN_RIGHT);
        model.calculateTransforms();
        return model;
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
        return new GeometryBuilder(builder, id, material);
    }

    /** ModelBuilder only creates root nodes; this moves a node under its parent joint. */
    private static void attach(Model model, String childId, String parentId) {
        Node child = model.getNode(childId);
        Node parent = model.getNode(parentId);
        model.nodes.removeValue(child, true);
        parent.addChild(child);
    }
}
