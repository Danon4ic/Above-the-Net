package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.RandomXS128;
import com.volleyball.game.world.Net;
import com.volleyball.game.world.Venue;

import static com.volleyball.game.world.CourtDimensions.*;

/**
 * Builds the procedural venues. No external assets are required: everything is made of boxes,
 * cylinders and spheres in real-world metres, using the coordinate system documented in
 * {@link com.volleyball.game.world.CourtDimensions}.
 *
 * <ul>
 *   <li><b>Championship arena</b>: a two-tier bowl with seats, a dark roof, light trusses with
 *       floodlight banks, LED boards around the court and between the tiers, a centre-hung video
 *       cube and end screens, TV cameras with operators, a camera crane and photographers.</li>
 *   <li><b>Beach stadium</b>: sand, open-air bleachers on scaffolding with sponsor banners, sky,
 *       sun and sea, palm trees, flags and a camera tower.</li>
 *   <li>The school gym, the sports palace and the rooftop court: see {@link HallVenues}.</li>
 * </ul>
 * Themeable surfaces (floor zones, stands, walls) use named materials; static decoration uses
 * vertex colours merged into a few draw calls; the crowd is a separate, GPU-animated mesh.
 */
final class ArenaModelFactory {
    static final String MAT_HALL_FLOOR = "hallFloor";
    static final String MAT_FREE_ZONE = "freeZone";
    static final String MAT_COURT = "court";
    static final String MAT_LINES = "lines";
    static final String MAT_STANDS = "stands";
    static final String MAT_WALLS = "walls";
    static final String MAT_FIXTURES = "fixtures";

    // Hall layout (outside the official free zone). The stand fronts are the ball's arena walls.
    static final float HALL_HALF_X = 31f;
    static final float HALL_HALF_Z = 25f;
    static final float SIDE_STAND_START_Z = 12.5f;
    static final float END_STAND_START_X = 19f;
    // Championship bowl: a lower tier, a walkway and an upper tier.
    static final int LOWER_ROWS = 12;
    static final int UPPER_ROWS = 10;
    static final float ROW_DEPTH = 0.8f;
    static final float LOWER_RISE = 0.42f;
    static final float UPPER_RISE = 0.55f;
    static final float WALKWAY = 1.6f;
    static final float ROOF_HEIGHT = 26f;
    static final float TRUSS_HEIGHT = 17f;
    /** Centre-hung video cube. */
    static final float CUBE_BOTTOM = 18.5f;
    static final float CUBE_SIZE = 5.6f;
    static final float CUBE_HEIGHT = 3.4f;
    // Beach bleachers.
    static final int BEACH_ROWS = 9;
    static final float BEACH_RISE = 0.45f;

    private static final float FLOOR_THICKNESS = 0.1f;
    /**
     * Markings are painted on the floor; they are raised 8 mm so they never depth-fight with the
     * surface underneath at broadcast distances. Visually indistinguishable from flat paint.
     */
    private static final float LINE_TOP = 0.008f;

    private static final Color WHITE = new Color(0.96f, 0.96f, 0.96f, 1f);
    private static final Color NET_CORD = new Color(0.05f, 0.05f, 0.06f, 1f);
    private static final Color ANTENNA_RED = new Color(0.85f, 0.08f, 0.10f, 1f);
    private static final Color STEEL = new Color(0.55f, 0.57f, 0.60f, 1f);
    private static final Color STEEL_DARK = new Color(0.22f, 0.23f, 0.26f, 1f);
    private static final Color PAD_BLUE = new Color(0.10f, 0.22f, 0.55f, 1f);
    private static final Color DARK = new Color(0.10f, 0.10f, 0.12f, 1f);
    private static final Color BLACK = new Color(0.04f, 0.04f, 0.05f, 1f);
    private static final Color BENCH_BLUE = new Color(0.12f, 0.30f, 0.65f, 1f);
    private static final Color TABLE = new Color(0.22f, 0.24f, 0.30f, 1f);
    private static final Color SKIN = new Color(0.86f, 0.68f, 0.55f, 1f);
    private static final Color SAND = new Color(0.86f, 0.74f, 0.52f, 1f);

    private final ModelBuilder modelBuilder = new ModelBuilder();
    private final Matrix4 transform = new Matrix4();
    private final HallVenues halls = new HallVenues();

    /** The venues built by {@link HallVenues}. */
    private static boolean hall(Venue venue) {
        return venue == Venue.SCHOOL_GYM || venue == Venue.SPORTS_PALACE || venue == Venue.ROOFTOP;
    }

    // ------------------------------------------------------------------ floor and net (all venues)

    /**
     * Hall floor, free zone, court surface and all official markings. The three floor zones are
     * laid out edge to edge (the outer ones as rectangular frames) instead of being stacked, so
     * no two coplanar surfaces overlap and there is no depth fighting.
     */
    Model buildFloor() {
        modelBuilder.begin();
        long attrs = Usage.Position | Usage.Normal;
        float freeHalfX = HALF_LENGTH + FREE_ZONE_END;
        float freeHalfZ = HALF_WIDTH + FREE_ZONE_SIDE;

        MeshPartBuilder hall = modelBuilder.part("hall", GL20.GL_TRIANGLES, attrs, material(MAT_HALL_FLOOR, Color.GRAY));
        floorFrame(hall, freeHalfX, freeHalfZ, HALL_HALF_X, HALL_HALF_Z);

        MeshPartBuilder free = modelBuilder.part("free", GL20.GL_TRIANGLES, attrs, material(MAT_FREE_ZONE, Color.BLUE));
        floorFrame(free, HALF_LENGTH, HALF_WIDTH, freeHalfX, freeHalfZ);

        MeshPartBuilder court = modelBuilder.part("court", GL20.GL_TRIANGLES, attrs, material(MAT_COURT, Color.ORANGE));
        BoxShapeBuilder.build(court, 0f, -FLOOR_THICKNESS / 2f, 0f, LENGTH, FLOOR_THICKNESS, WIDTH);

        MeshPartBuilder lines = modelBuilder.part("lines", GL20.GL_TRIANGLES, attrs, material(MAT_LINES, Color.WHITE));
        buildMarkings(lines);
        return modelBuilder.end();
    }

    /** Floor slab covering the rectangle |x| <= outer, |z| <= outer minus the inner rectangle. */
    private static void floorFrame(MeshPartBuilder part, float innerHalfX, float innerHalfZ, float outerHalfX, float outerHalfZ) {
        float y = -FLOOR_THICKNESS / 2f;
        float sideDepth = outerHalfZ - innerHalfZ;
        float endDepth = outerHalfX - innerHalfX;
        for (int s = -1; s <= 1; s += 2) {
            BoxShapeBuilder.build(part, 0f, y, s * (innerHalfZ + sideDepth / 2f), outerHalfX * 2f, FLOOR_THICKNESS, sideDepth);
            BoxShapeBuilder.build(part, s * (innerHalfX + endDepth / 2f), y, 0f, endDepth, FLOOR_THICKNESS, innerHalfZ * 2f);
        }
    }

    private void buildMarkings(MeshPartBuilder lines) {
        float y = LINE_TOP / 2f;
        float h = LINE_TOP;
        float lw = LINE_WIDTH;
        for (int s = -1; s <= 1; s += 2) {
            BoxShapeBuilder.build(lines, 0f, y, s * (HALF_WIDTH - lw / 2f), LENGTH, h, lw);
            BoxShapeBuilder.build(lines, s * (HALF_LENGTH - lw / 2f), y, 0f, lw, h, WIDTH - 2f * lw);
        }
        BoxShapeBuilder.build(lines, 0f, y, 0f, lw, h, WIDTH - 2f * lw);
        for (int side = -1; side <= 1; side += 2) {
            float attackX = side * (ATTACK_LINE_DISTANCE - lw / 2f);
            BoxShapeBuilder.build(lines, attackX, y, 0f, lw, h, WIDTH - 2f * lw);
            for (int s = -1; s <= 1; s += 2) {
                for (int i = 0; i < ATTACK_EXTENSION_DASHES; i++) {
                    float start = HALF_WIDTH + ATTACK_EXTENSION_GAP + i * (ATTACK_EXTENSION_DASH + ATTACK_EXTENSION_GAP);
                    BoxShapeBuilder.build(lines, attackX, y, s * (start + ATTACK_EXTENSION_DASH / 2f), lw, h, ATTACK_EXTENSION_DASH);
                }
            }
            float markX = side * (HALF_LENGTH + SERVICE_MARK_OFFSET + SERVICE_MARK_LENGTH / 2f);
            for (int s = -1; s <= 1; s += 2) {
                BoxShapeBuilder.build(lines, markX, y, s * (HALF_WIDTH - lw / 2f), SERVICE_MARK_LENGTH, h, lw);
            }
        }
    }

    /** Net mesh, bands, antennas, cables, posts and post padding for the given net height. */
    Model buildNet(Net net) {
        modelBuilder.begin();
        GeometryBuilder g = new GeometryBuilder(modelBuilder, "net", vertexColoured("net"));
        float top = net.topHeight();
        float bottom = net.bottomHeight();
        float mid = (top + bottom) / 2f;
        float half = Net.LENGTH / 2f;
        float cord = 0.007f;
        int columns = Math.round(Net.LENGTH / Net.MESH_CELL);
        for (int i = 0; i <= columns; i++) {
            g.box(0f, mid, -half + i * Net.MESH_CELL, cord, Net.MESH_HEIGHT, cord, NET_CORD);
        }
        int rows = Math.round(Net.MESH_HEIGHT / Net.MESH_CELL);
        for (int i = 0; i <= rows; i++) {
            g.box(0f, bottom + i * Net.MESH_CELL, 0f, cord, cord, Net.LENGTH, NET_CORD);
        }
        g.box(0f, top - Net.TOP_BAND_HEIGHT / 2f, 0f, 0.014f, Net.TOP_BAND_HEIGHT, Net.LENGTH, WHITE);
        g.box(0f, bottom + Net.BOTTOM_BAND_HEIGHT / 2f, 0f, 0.012f, Net.BOTTOM_BAND_HEIGHT, Net.LENGTH, WHITE);
        float postZ = net.postAxisZ();
        for (int s = -1; s <= 1; s += 2) {
            g.box(0f, mid, s * (HALF_WIDTH - Net.SIDE_BAND_WIDTH / 2f), 0.016f, Net.MESH_HEIGHT, Net.SIDE_BAND_WIDTH, WHITE);
            int stripes = Math.round(Net.ANTENNA_LENGTH / 0.1f);
            for (int i = 0; i < stripes; i++) {
                g.box(0f, net.antennaBottom() + 0.05f + i * 0.1f, s * net.antennaAxisZ(), 0.02f, 0.1f, 0.02f,
                        i % 2 == 0 ? ANTENNA_RED : WHITE);
            }
            float cableLength = postZ - half;
            g.box(0f, top - 0.01f, s * (half + cableLength / 2f), 0.008f, 0.008f, cableLength, DARK);
            g.box(0f, bottom + 0.02f, s * (half + cableLength / 2f), 0.008f, 0.008f, cableLength, DARK);
            g.verticalCylinder(0f, 0f, s * postZ, Net.POST_DIAMETER, Net.POST_HEIGHT, STEEL);
            g.box(0f, 0.95f, s * postZ, 0.28f, 1.9f, 0.28f, PAD_BLUE);
            g.box(0f, 0.015f, s * postZ, 0.6f, 0.03f, 0.6f, STEEL);
        }
        return modelBuilder.end();
    }

    // ------------------------------------------------------------------ stands (themeable)

    /** Stand tiers, walls and roof (championship) or bleacher planks (beach). */
    Model buildStructure(Venue venue) {
        modelBuilder.begin();
        long attrs = Usage.Position | Usage.Normal;
        MeshPartBuilder stands = modelBuilder.part("stands", GL20.GL_TRIANGLES, attrs, material(MAT_STANDS, Color.RED));
        MeshPartBuilder walls = modelBuilder.part("walls", GL20.GL_TRIANGLES, attrs, material(MAT_WALLS, Color.DARK_GRAY));
        if (hall(venue)) {
            halls.structure(venue, stands, walls);
            return modelBuilder.end();
        }
        if (venue == Venue.BEACH) {
            for (int row = 0; row < BEACH_ROWS; row++) {
                float top = beachRowTop(row);
                for (int s = -1; s <= 1; s += 2) {
                    float z0 = SIDE_STAND_START_Z + row * ROW_DEPTH;
                    // Seat plank and foot plank of each row (open steps, scaffolding below).
                    BoxShapeBuilder.build(stands, 0f, top - 0.03f, s * (z0 + ROW_DEPTH * 0.65f), END_STAND_START_X * 2f - 2f, 0.06f, ROW_DEPTH * 0.5f);
                    BoxShapeBuilder.build(stands, 0f, top - 0.45f, s * (z0 + ROW_DEPTH * 0.2f), END_STAND_START_X * 2f - 2f, 0.04f, ROW_DEPTH * 0.4f);
                    float x0 = END_STAND_START_X + row * ROW_DEPTH;
                    BoxShapeBuilder.build(stands, s * (x0 + ROW_DEPTH * 0.65f), top - 0.03f, 0f, ROW_DEPTH * 0.5f, 0.06f, SIDE_STAND_START_Z * 2f - 4f);
                    BoxShapeBuilder.build(stands, s * (x0 + ROW_DEPTH * 0.2f), top - 0.45f, 0f, ROW_DEPTH * 0.4f, 0.04f, SIDE_STAND_START_Z * 2f - 4f);
                }
            }
            // A low back rail behind the top row.
            float backZ = SIDE_STAND_START_Z + BEACH_ROWS * ROW_DEPTH;
            float backX = END_STAND_START_X + BEACH_ROWS * ROW_DEPTH;
            for (int s = -1; s <= 1; s += 2) {
                BoxShapeBuilder.build(walls, 0f, beachRowTop(BEACH_ROWS - 1) + 0.6f, s * backZ, END_STAND_START_X * 2f, 0.08f, 0.08f);
                BoxShapeBuilder.build(walls, s * backX, beachRowTop(BEACH_ROWS - 1) + 0.6f, 0f, 0.08f, 0.08f, SIDE_STAND_START_Z * 2f);
            }
            return modelBuilder.end();
        }
        for (int row = 0; row < LOWER_ROWS + UPPER_ROWS; row++) {
            float top = rowTop(row);
            float offset = rowOffset(row);
            for (int s = -1; s <= 1; s += 2) {
                float z0 = SIDE_STAND_START_Z + offset;
                BoxShapeBuilder.build(stands, 0f, top / 2f, s * (z0 + ROW_DEPTH / 2f), (END_STAND_START_X + offset) * 2f, top, ROW_DEPTH);
                float x0 = END_STAND_START_X + offset;
                BoxShapeBuilder.build(stands, s * (x0 + ROW_DEPTH / 2f), top / 2f, 0f, ROW_DEPTH, top, (SIDE_STAND_START_Z + offset) * 2f);
            }
        }
        // Walkway between the tiers.
        float walkTop = rowTop(LOWER_ROWS - 1) + 0.25f;
        float walkOffset = rowOffset(LOWER_ROWS - 1) + ROW_DEPTH;
        for (int s = -1; s <= 1; s += 2) {
            BoxShapeBuilder.build(stands, 0f, walkTop / 2f, s * (SIDE_STAND_START_Z + walkOffset + WALKWAY / 2f),
                    (END_STAND_START_X + walkOffset + WALKWAY) * 2f, walkTop, WALKWAY);
            BoxShapeBuilder.build(stands, s * (END_STAND_START_X + walkOffset + WALKWAY / 2f), walkTop / 2f, 0f,
                    WALKWAY, walkTop, (SIDE_STAND_START_Z + walkOffset) * 2f);
        }
        // Outer walls and the roof.
        float back = rowOffset(LOWER_ROWS + UPPER_ROWS - 1) + ROW_DEPTH + 0.6f;
        float sideWallZ = SIDE_STAND_START_Z + back;
        float endWallX = END_STAND_START_X + back;
        for (int s = -1; s <= 1; s += 2) {
            BoxShapeBuilder.build(walls, 0f, ROOF_HEIGHT / 2f, s * sideWallZ, endWallX * 2f, ROOF_HEIGHT, 0.5f);
            BoxShapeBuilder.build(walls, s * endWallX, ROOF_HEIGHT / 2f, 0f, 0.5f, ROOF_HEIGHT, sideWallZ * 2f);
        }
        BoxShapeBuilder.build(walls, 0f, ROOF_HEIGHT, 0f, endWallX * 2f, 0.5f, sideWallZ * 2f);
        return modelBuilder.end();
    }

    /** Seat height of a bowl row (upper tier rows start above the walkway). */
    static float rowTop(int row) {
        if (row < LOWER_ROWS) return 0.6f + row * LOWER_RISE;
        return 0.6f + LOWER_ROWS * LOWER_RISE + 1.3f + (row - LOWER_ROWS) * UPPER_RISE;
    }

    /** Distance of a bowl row's front from the stand front. */
    static float rowOffset(int row) {
        return row < LOWER_ROWS ? row * ROW_DEPTH : row * ROW_DEPTH + WALKWAY;
    }

    static float beachRowTop(int row) {
        return 0.55f + row * BEACH_RISE;
    }

    // ------------------------------------------------------------------ crowd (GPU-animated)

    /** All spectators of the venue, one mesh with animation data (see {@link CrowdBuilder}). */
    Model buildCrowd(Venue venue, Color homeColor, Color awayColor) {
        modelBuilder.begin();
        Material material = new Material("crowd", ColorAttribute.createDiffuse(Color.WHITE), ShadingAttribute.body());
        Color[] skins = {SKIN, new Color(0.74f, 0.55f, 0.42f, 1f), new Color(0.55f, 0.38f, 0.27f, 1f),
                new Color(0.38f, 0.26f, 0.19f, 1f), new Color(0.93f, 0.78f, 0.66f, 1f)};
        Color[] hair = {new Color(0.12f, 0.09f, 0.07f, 1f), new Color(0.32f, 0.2f, 0.1f, 1f), new Color(0.75f, 0.6f, 0.32f, 1f),
                new Color(0.05f, 0.05f, 0.05f, 1f), new Color(0.55f, 0.55f, 0.55f, 1f), new Color(0.6f, 0.25f, 0.1f, 1f)};
        boolean beach = venue == Venue.BEACH;
        Color[] shirts = hall(venue) ? HallVenues.shirts(venue) : beach
                ? new Color[]{WHITE, new Color(0.98f, 0.85f, 0.2f, 1f), new Color(0.2f, 0.75f, 0.85f, 1f), new Color(0.95f, 0.45f, 0.55f, 1f),
                new Color(0.4f, 0.85f, 0.45f, 1f), new Color(0.95f, 0.55f, 0.15f, 1f), new Color(0.3f, 0.4f, 0.9f, 1f)}
                : new Color[]{WHITE, new Color(0.2f, 0.2f, 0.22f, 1f), new Color(0.85f, 0.15f, 0.15f, 1f), new Color(0.15f, 0.35f, 0.85f, 1f),
                new Color(0.95f, 0.8f, 0.15f, 1f), new Color(0.2f, 0.6f, 0.3f, 1f), new Color(0.55f, 0.55f, 0.6f, 1f)};
        Color[] trousers = beach
                ? new Color[]{new Color(0.25f, 0.45f, 0.75f, 1f), SKIN, new Color(0.85f, 0.8f, 0.7f, 1f), new Color(0.2f, 0.25f, 0.35f, 1f)}
                : new Color[]{new Color(0.15f, 0.2f, 0.35f, 1f), new Color(0.12f, 0.12f, 0.14f, 1f), new Color(0.45f, 0.4f, 0.35f, 1f)};
        CrowdBuilder crowd = new CrowdBuilder(modelBuilder, material, 2024, shirts, skins, hair, trousers);
        if (hall(venue)) {
            halls.crowd(venue, crowd, homeColor, awayColor);
            return modelBuilder.end();
        }
        float seat = 0.55f;
        RandomXS128 random = new RandomXS128(77);
        int rows = beach ? BEACH_ROWS : LOWER_ROWS + UPPER_ROWS;
        for (int row = 0; row < rows; row++) {
            float top = beach ? beachRowTop(row) : rowTop(row);
            float offset = beach ? row * ROW_DEPTH : rowOffset(row);
            float density = beach ? 0.78f : row < LOWER_ROWS ? 0.88f : 0.7f;
            float sideZ = SIDE_STAND_START_Z + offset + ROW_DEPTH * (beach ? 0.62f : 0.5f);
            float sideHalf = beach ? END_STAND_START_X - 1.5f : END_STAND_START_X + offset - 0.5f;
            for (float x = -sideHalf; x < sideHalf; x += seat) {
                for (int s = -1; s <= 1; s += 2) {
                    if (random.nextFloat() > density) continue;
                    // Home fans behind the home half of the court (x < 0), away fans on the other half.
                    Color team = x < 0 ? homeColor : awayColor;
                    crowd.spectator(x, top, s * sideZ, 0f, -s, 0.05f, team);
                }
            }
            float endX = END_STAND_START_X + offset + ROW_DEPTH * (beach ? 0.62f : 0.5f);
            float endHalf = beach ? SIDE_STAND_START_Z - 2.5f : SIDE_STAND_START_Z + offset - 0.5f;
            for (float z = -endHalf; z < endHalf; z += seat) {
                for (int s = -1; s <= 1; s += 2) {
                    if (random.nextFloat() > density * 0.92f) continue;
                    crowd.spectator(s * endX, top, z, -s, 0f, 0.07f, s < 0 ? homeColor : awayColor);
                }
            }
        }
        return modelBuilder.end();
    }

    // ------------------------------------------------------------------ venue props

    /** Static, vertex-coloured furniture and decoration of the venue. */
    Model buildVenue(Venue venue) {
        modelBuilder.begin();
        GeometryBuilder g = new GeometryBuilder(modelBuilder, "venue", vertexColoured("venue"));
        buildRefereeStand(g);
        buildScorerTableAndBenches(g);
        if (hall(venue)) {
            halls.props(venue, g);
        } else if (venue == Venue.BEACH) {
            buildBeachProps(g);
        } else {
            buildSeats(g);
            buildTrusses(g);
            buildCameras(g);
            buildVideoCubeFrame(g);
        }
        return modelBuilder.end();
    }

    /** Empty seats show where nobody sits: seat pans and backs in two colours. */
    private void buildSeats(GeometryBuilder g) {
        Color a = new Color(0.12f, 0.22f, 0.5f, 1f);
        Color b = new Color(0.08f, 0.14f, 0.32f, 1f);
        for (int row = 0; row < LOWER_ROWS + UPPER_ROWS; row++) {
            float top = rowTop(row);
            float offset = rowOffset(row);
            Color c = row % 2 == 0 ? a : b;
            float sideZ = SIDE_STAND_START_Z + offset + ROW_DEPTH * 0.5f;
            for (int s = -1; s <= 1; s += 2) {
                // One long seat strip per row and side (cheap), with a back rest.
                g.box(0f, top + 0.03f, s * sideZ, (END_STAND_START_X + offset) * 2f - 1f, 0.06f, 0.42f, c);
                g.box(0f, top + 0.3f, s * (sideZ + 0.24f), (END_STAND_START_X + offset) * 2f - 1f, 0.5f, 0.05f, c);
                float endX = END_STAND_START_X + offset + ROW_DEPTH * 0.5f;
                g.box(s * endX, top + 0.03f, 0f, 0.42f, 0.06f, (SIDE_STAND_START_Z + offset) * 2f - 1f, c);
                g.box(s * (endX + 0.24f), top + 0.3f, 0f, 0.05f, 0.5f, (SIDE_STAND_START_Z + offset) * 2f - 1f, c);
            }
        }
    }

    /**
     * The lighting rig: a rectangular truss over the court with four floodlight banks (housings
     * here; the lenses are emissive fixtures) and hanging points to the roof.
     */
    private void buildTrusses(GeometryBuilder g) {
        float hx = 12f;
        float hz = 9f;
        for (int s = -1; s <= 1; s += 2) {
            truss(g, 0f, TRUSS_HEIGHT, s * hz, hx * 2f, true);
            truss(g, s * hx, TRUSS_HEIGHT, 0f, hz * 2f, false);
        }
        // Chains to the roof.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                g.box(sx * hx, (TRUSS_HEIGHT + ROOF_HEIGHT) / 2f, sz * hz, 0.05f, ROOF_HEIGHT - TRUSS_HEIGHT, 0.05f, STEEL_DARK);
            }
        }
        // Floodlight housings: six per side, tilted towards the court (boxes).
        for (FloodlightSpot spot : FloodlightSpot.ALL) {
            g.box(spot.x, spot.y + 0.25f, spot.z, 0.7f, 0.5f, 0.7f, BLACK);
            g.box(spot.x, spot.y + 0.55f, spot.z, 0.1f, 0.3f, 0.1f, STEEL_DARK);
        }
    }

    /** A box truss: two chords and diagonal lacing. */
    private static void truss(GeometryBuilder g, float cx, float y, float cz, float length, boolean alongX) {
        float h = 0.6f;
        for (int top = 0; top <= 1; top++) {
            for (int side = -1; side <= 1; side += 2) {
                float off = side * 0.3f;
                if (alongX) g.box(cx, y + top * h, cz + off, length, 0.06f, 0.06f, STEEL_DARK);
                else g.box(cx + off, y + top * h, cz, 0.06f, 0.06f, length, STEEL_DARK);
            }
        }
        int n = Math.round(length / 0.8f);
        for (int i = 0; i <= n; i++) {
            float t = -length / 2f + i * length / n;
            if (alongX) g.box(cx + t, y + h / 2f, cz, 0.04f, h, 0.64f, STEEL_DARK);
            else g.box(cx, y + h / 2f, cz + t, 0.64f, h, 0.04f, STEEL_DARK);
        }
    }

    /** Floodlight positions shared by the housings, the emissive lenses and the light rig. */
    static final class FloodlightSpot {
        final float x;
        final float y;
        final float z;

        FloodlightSpot(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static final FloodlightSpot[] ALL = build();

        private static FloodlightSpot[] build() {
            FloodlightSpot[] spots = new FloodlightSpot[16];
            int i = 0;
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 0; k < 6; k++) spots[i++] = new FloodlightSpot(-10f + k * 4f, TRUSS_HEIGHT - 0.5f, s * 9f);
                for (int k = 0; k < 2; k++) spots[i++] = new FloodlightSpot(s * 12f, TRUSS_HEIGHT - 0.5f, -3f + k * 6f);
            }
            return spots;
        }
    }

    /** TV cameras with operators, a camera crane and photographers. */
    private void buildCameras(GeometryBuilder g) {
        float endX = HALF_LENGTH + FREE_ZONE_END - 0.8f;
        // Behind each end line, slightly off centre, on a low platform.
        for (int s = -1; s <= 1; s += 2) {
            g.box(s * endX, 0.2f, -2.5f, 1.6f, 0.4f, 1.6f, DARK);
            tvCamera(g, s * endX, 0.4f, -2.5f, -s, 0f);
        }
        // Main camera high on the side stand opposite the benches.
        float platformZ = -(SIDE_STAND_START_Z + rowOffset(LOWER_ROWS - 1) + ROW_DEPTH + WALKWAY / 2f);
        float platformY = rowTop(LOWER_ROWS - 1) + 0.25f;
        g.box(0f, platformY + 0.1f, platformZ, 3f, 0.2f, 1.4f, DARK);
        tvCamera(g, -0.8f, platformY + 0.2f, platformZ, 0f, 1f);
        tvCamera(g, 0.8f, platformY + 0.2f, platformZ, 0f, 1f);
        // Handheld cameras at the side of the court (near the benches' corners).
        float sideZ = HALF_WIDTH + FREE_ZONE_SIDE - 0.6f;
        tvCamera(g, -HALF_LENGTH - 2f, 0f, sideZ, 0.3f, -1f);
        tvCamera(g, HALF_LENGTH + 2f, 0f, -sideZ, -0.3f, 1f);
        // A camera crane in a corner: base, column, long arm towards the court, camera head.
        float cx = -(HALF_LENGTH + FREE_ZONE_END - 1.5f);
        float cz = -(HALF_WIDTH + FREE_ZONE_SIDE - 1.5f);
        g.box(cx, 0.25f, cz, 1.2f, 0.5f, 1.2f, DARK);
        g.box(cx, 1.6f, cz, 0.25f, 2.2f, 0.25f, STEEL_DARK);
        transform.setToTranslation(cx + 1.6f, 3.2f, cz + 1.2f).rotate(0f, 1f, 0f, -37f).rotate(0f, 0f, 1f, 14f);
        g.transformedBox(transform, 7.5f, 0.18f, 0.18f, STEEL_DARK);
        g.box(cx + 4.6f, 4.3f, cz + 3.4f, 0.5f, 0.35f, 0.3f, BLACK);
        g.box(cx - 1.3f, 2.3f, cz - 1f, 0.7f, 0.7f, 0.7f, STEEL);
        // Photographers kneeling at the end-line corners.
        Color[] vests = {new Color(0.95f, 0.55f, 0.1f, 1f), new Color(0.2f, 0.7f, 0.3f, 1f)};
        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float px = sx * (HALF_LENGTH + 2.8f);
                float pz = sz * (HALF_WIDTH + 1.2f);
                g.box(px, 0.25f, pz, 0.4f, 0.5f, 0.35f, DARK);
                g.box(px, 0.75f, pz, 0.42f, 0.5f, 0.3f, vests[i++ % 2]);
                g.box(px, 1.12f, pz, 0.2f, 0.22f, 0.2f, SKIN);
                g.box(px - sx * 0.25f, 1.1f, pz, 0.25f, 0.15f, 0.15f, BLACK);
            }
        }
    }

    /** A broadcast camera on a tripod with its operator standing behind it. {@code (fx, fz)} is where it looks. */
    private void tvCamera(GeometryBuilder g, float x, float baseY, float z, float fx, float fz) {
        float len = (float) Math.sqrt(fx * fx + fz * fz);
        fx /= len;
        fz /= len;
        float yaw = MathUtils.atan2(-fz, fx) * MathUtils.radiansToDegrees;
        // Tripod legs.
        for (int k = 0; k < 3; k++) {
            float a = k * MathUtils.PI2 / 3f;
            transform.setToTranslation(x + MathUtils.cos(a) * 0.2f, baseY + 0.65f, z + MathUtils.sin(a) * 0.2f)
                    .rotate(MathUtils.sin(a), 0f, -MathUtils.cos(a), 12f);
            g.transformedBox(transform, 0.04f, 1.3f, 0.04f, STEEL_DARK);
        }
        // Camera body, lens with hood, viewfinder.
        transform.setToTranslation(x, baseY + 1.45f, z).rotate(0f, 1f, 0f, yaw);
        g.transformedBox(transform, 0.6f, 0.32f, 0.26f, BLACK);
        transform.setToTranslation(x + fx * 0.45f, baseY + 1.45f, z + fz * 0.45f).rotate(0f, 1f, 0f, yaw);
        g.transformedBox(transform, 0.35f, 0.18f, 0.18f, DARK);
        transform.setToTranslation(x + fx * 0.66f, baseY + 1.45f, z + fz * 0.66f).rotate(0f, 1f, 0f, yaw);
        g.transformedBox(transform, 0.06f, 0.26f, 0.26f, BLACK);
        // Operator behind the camera.
        float ox = x - fx * 0.6f;
        float oz = z - fz * 0.6f;
        g.box(ox, baseY + 0.45f, oz, 0.32f, 0.9f, 0.25f, DARK);
        g.box(ox, baseY + 1.2f, oz, 0.42f, 0.6f, 0.26f, BLACK);
        g.box(ox, baseY + 1.62f, oz, 0.2f, 0.24f, 0.21f, SKIN);
        g.box(ox, baseY + 1.75f, oz, 0.24f, 0.05f, 0.24f, BLACK);
    }

    /** The video cube's housing (its screens are textured, see {@link #buildScreens}). */
    private void buildVideoCubeFrame(GeometryBuilder g) {
        float half = CUBE_SIZE / 2f;
        g.box(0f, CUBE_BOTTOM + CUBE_HEIGHT / 2f, 0f, CUBE_SIZE - 0.1f, CUBE_HEIGHT - 0.1f, CUBE_SIZE - 0.1f, BLACK);
        g.box(0f, CUBE_BOTTOM - 0.25f, 0f, CUBE_SIZE * 0.8f, 0.5f, CUBE_SIZE * 0.8f, DARK);
        g.box(0f, CUBE_BOTTOM + CUBE_HEIGHT + 0.2f, 0f, CUBE_SIZE, 0.4f, CUBE_SIZE, DARK);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                g.box(sx * half * 0.8f, (CUBE_BOTTOM + CUBE_HEIGHT + ROOF_HEIGHT) / 2f, sz * half * 0.8f, 0.05f,
                        ROOF_HEIGHT - CUBE_BOTTOM - CUBE_HEIGHT, 0.05f, STEEL_DARK);
            }
        }
    }

    /** Sand, scaffolding, palm trees, flags, a camera tower, umbrellas. */
    private void buildBeachProps(GeometryBuilder g) {
        // Sand all around, slightly below the playing floor.
        g.box(0f, -0.08f, 0f, 260f, 0.1f, 260f, SAND);
        // Scaffolding under the bleachers.
        for (int row = 0; row < BEACH_ROWS; row += 2) {
            float top = beachRowTop(row);
            for (int s = -1; s <= 1; s += 2) {
                float z = s * (SIDE_STAND_START_Z + row * ROW_DEPTH + ROW_DEPTH * 0.6f);
                for (float x = -END_STAND_START_X + 1f; x <= END_STAND_START_X - 1f; x += 3f) {
                    g.box(x, top / 2f, z, 0.06f, top, 0.06f, STEEL);
                }
                float ex = s * (END_STAND_START_X + row * ROW_DEPTH + ROW_DEPTH * 0.6f);
                for (float z2 = -SIDE_STAND_START_Z + 2f; z2 <= SIDE_STAND_START_Z - 2f; z2 += 3f) {
                    g.box(ex, top / 2f, z2, 0.06f, top, 0.06f, STEEL);
                }
            }
        }
        RandomXS128 random = new RandomXS128(9);
        // Palm trees behind the stands.
        for (int i = 0; i < 26; i++) {
            float angle = i / 26f * MathUtils.PI2 + random.nextFloat() * 0.1f;
            float r = 34f + random.nextFloat() * 18f;
            palm(g, MathUtils.cos(angle) * r * 1.2f, MathUtils.sin(angle) * r, 7f + random.nextFloat() * 5f, random);
        }
        // Flags on poles at the stand corners.
        Color[] flagColors = {new Color(0.95f, 0.2f, 0.2f, 1f), new Color(0.2f, 0.5f, 0.95f, 1f), new Color(0.98f, 0.8f, 0.15f, 1f)};
        int f = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x = sx * (END_STAND_START_X + 1f);
                float z = sz * (SIDE_STAND_START_Z + 1f);
                g.verticalCylinder(x, 0f, z, 0.1f, 9f, WHITE);
                g.box(x + sx * 0.8f, 8.3f, z, 1.6f, 1f, 0.03f, flagColors[f++ % flagColors.length]);
            }
        }
        // Camera tower behind one end: scaffold, platform, camera.
        float tx = -(END_STAND_START_X + BEACH_ROWS * ROW_DEPTH + 3f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) g.box(tx + sx * 1f, 4f, sz * 1f, 0.1f, 8f, 0.1f, STEEL);
        }
        g.box(tx, 8f, 0f, 2.6f, 0.2f, 2.6f, DARK);
        tvCamera(g, tx + 0.4f, 8.1f, 0f, 1f, 0f);
        // Sun umbrellas on the sand by the ends.
        Color[] umbrellas = {new Color(0.95f, 0.3f, 0.3f, 1f), new Color(0.2f, 0.7f, 0.9f, 1f), new Color(0.98f, 0.85f, 0.2f, 1f)};
        for (int i = 0; i < 10; i++) {
            float x = (i % 2 == 0 ? -1 : 1) * (END_STAND_START_X + BEACH_ROWS * ROW_DEPTH + 6f + random.nextFloat() * 8f);
            float z = -14f + random.nextFloat() * 28f;
            g.verticalCylinder(x, 0f, z, 0.06f, 2.3f, WHITE);
            g.box(x, 2.3f, z, 2.2f, 0.12f, 2.2f, umbrellas[i % umbrellas.length]);
        }
    }

    private void palm(GeometryBuilder g, float x, float z, float height, RandomXS128 random) {
        Color trunk = new Color(0.5f, 0.38f, 0.25f, 1f);
        Color leaf = new Color(0.18f, 0.5f + random.nextFloat() * 0.15f, 0.2f, 1f);
        float lean = (random.nextFloat() - 0.5f) * 0.12f;
        int segments = 8;
        for (int i = 0; i < segments; i++) {
            float t = (i + 0.5f) / segments;
            g.box(x + lean * height * t * t, height * t, z, 0.38f - 0.12f * t, height / segments + 0.05f, 0.38f - 0.12f * t, trunk);
        }
        float topX = x + lean * height;
        for (int k = 0; k < 7; k++) {
            float a = k / 7f * 360f + random.nextFloat() * 20f;
            transform.setToTranslation(topX, height, z).rotate(0f, 1f, 0f, a).rotate(0f, 0f, 1f, -25f).translate(1.4f, 0f, 0f);
            g.transformedBox(transform, 2.8f, 0.06f, 0.55f, leaf);
        }
    }

    private void buildRefereeStand(GeometryBuilder g) {
        float z = -(HALF_WIDTH + Net.POST_DISTANCE_FROM_SIDELINE + 0.75f);
        float platform = 1.45f;
        for (int ix = -1; ix <= 1; ix += 2) {
            for (int iz = -1; iz <= 1; iz += 2) {
                g.box(ix * 0.35f, platform / 2f, z + iz * 0.35f, 0.05f, platform, 0.05f, STEEL);
            }
        }
        g.box(0f, platform, z, 0.85f, 0.06f, 0.85f, DARK);
        g.box(0f, platform + 0.5f, z + 0.42f, 0.85f, 0.05f, 0.03f, STEEL);
        for (int step = 1; step <= 4; step++) {
            g.box(0f, step * platform / 5f, z - 0.45f - step * 0.12f, 0.6f, 0.04f, 0.2f, STEEL);
        }
        g.box(0f, platform + 0.25f, z - 0.1f, 0.35f, 0.5f, 0.35f, DARK);
        // The referee is a figure of their own (see match.Referee), standing on the platform.
    }

    private void buildScorerTableAndBenches(GeometryBuilder g) {
        float tableZ = HALF_WIDTH + FREE_ZONE_SIDE + 0.8f;
        g.box(0f, 0.38f, tableZ, 3.2f, 0.76f, 0.8f, TABLE);
        g.box(0f, 0.55f, tableZ - 0.41f, 3.2f, 0.4f, 0.02f, WHITE);
        for (int i = -1; i <= 1; i++) chair(g, i * 1.0f, tableZ + 0.7f, DARK);
        for (int s = -1; s <= 1; s += 2) {
            float centreX = s * 6f;
            for (int i = 0; i < 9; i++) chair(g, centreX + (i - 4) * 0.55f, tableZ + 0.4f, BENCH_BLUE);
            chair(g, s * 9.6f, tableZ + 0.4f, ANTENNA_RED);
        }
    }

    private static void chair(GeometryBuilder g, float x, float z, Color color) {
        g.box(x, 0.45f, z, 0.45f, 0.05f, 0.45f, color);
        g.box(x, 0.7f, z + 0.21f, 0.45f, 0.5f, 0.04f, color);
        g.box(x, 0.21f, z, 0.04f, 0.42f, 0.04f, STEEL);
    }

    // ------------------------------------------------------------------ emissive and textured parts

    /** Light-emitting surfaces (rendered unlit): floodlight lenses, the sun. */
    Model buildFixtures(Venue venue) {
        modelBuilder.begin();
        MeshPartBuilder lamps = modelBuilder.part("lamps", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                material(MAT_FIXTURES, Color.WHITE));
        if (hall(venue)) {
            halls.fixtures(venue, lamps);
        } else if (venue == Venue.BEACH) {
            // The sun: a bright disc far away (its direction matches the light rig's sun).
            transform.setToTranslation(LightRig.SUN_DIRECTION.x * 200f, LightRig.SUN_DIRECTION.y * 200f, LightRig.SUN_DIRECTION.z * 200f);
            lamps.setVertexTransform(transform);
            SphereShapeBuilder.build(lamps, 12f, 12f, 12f, 16, 12);
            lamps.setVertexTransform(null);
        } else {
            for (FloodlightSpot spot : FloodlightSpot.ALL) {
                BoxShapeBuilder.build(lamps, spot.x, spot.y - 0.02f, spot.z, 0.55f, 0.04f, 0.55f);
            }
        }
        return modelBuilder.end();
    }

    /**
     * Screens showing the live scoreboard image: the video cube's four faces and two end screens
     * (arena), or one big screen on a frame behind an end (beach).
     */
    Model buildScreens(Venue venue, Texture screenTexture) {
        modelBuilder.begin();
        Material material = new Material("scoreboard", TextureAttribute.createDiffuse(screenTexture),
                IntAttribute.createCullFace(GL20.GL_NONE));
        MeshPartBuilder screen = modelBuilder.part("screens", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates, material);
        if (hall(venue)) {
            halls.screens(venue, screen);
        } else if (venue == Venue.BEACH) {
            float x = END_STAND_START_X + BEACH_ROWS * ROW_DEPTH + 1f;
            float y0 = 5.5f;
            float y1 = 9.5f;
            float w = 4f;
            screen.rect(x, y0, -w, x, y0, w, x, y1, w, x, y1, -w, -1f, 0f, 0f);
        } else {
            float h = CUBE_SIZE / 2f + 0.01f;
            float y0 = CUBE_BOTTOM + 0.15f;
            float y1 = CUBE_BOTTOM + CUBE_HEIGHT - 0.15f;
            float w = CUBE_SIZE / 2f - 0.15f;
            // Four faces, each seen from outside (bottom-left, bottom-right, top-right, top-left).
            screen.rect(h, y0, -w, h, y0, w, h, y1, w, h, y1, -w, 1f, 0f, 0f);
            screen.rect(-h, y0, w, -h, y0, -w, -h, y1, -w, -h, y1, w, -1f, 0f, 0f);
            screen.rect(w, y0, -h, -w, y0, -h, -w, y1, -h, w, y1, -h, 0f, 0f, -1f);
            screen.rect(-w, y0, h, w, y0, h, w, y1, h, -w, y1, h, 0f, 0f, 1f);
            // End screens high on the end walls, under the roof.
            float x = END_STAND_START_X + rowOffset(LOWER_ROWS + UPPER_ROWS - 1) + ROW_DEPTH + 0.3f;
            float ey0 = rowTop(LOWER_ROWS + UPPER_ROWS - 1) + 2.5f;
            float ey1 = ey0 + 4.5f;
            float ew = 4.5f;
            screen.rect(x, ey0, -ew, x, ey0, ew, x, ey1, ew, x, ey1, -ew, -1f, 0f, 0f);
            screen.rect(-x, ey0, ew, -x, ey0, -ew, -x, ey1, -ew, -x, ey1, ew, 1f, 0f, 0f);
        }
        return modelBuilder.end();
    }

    /**
     * LED ribbon boards (scrolling sponsor messages): around the free zone and along the fascia
     * between the tiers (arena), or banners on the bleacher fronts (beach). The texture's U
     * coordinate runs along the board, so shifting it scrolls the text.
     */
    Model buildLedBoards(Venue venue, Texture ledTexture) {
        modelBuilder.begin();
        TextureAttribute texture = TextureAttribute.createDiffuse(ledTexture);
        Material material = new Material("led", texture, IntAttribute.createCullFace(GL20.GL_NONE));
        MeshPartBuilder led = modelBuilder.part("led", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal | Usage.TextureCoordinates, material);
        if (hall(venue)) {
            halls.ledBoards(venue, led);
        } else if (venue == Venue.BEACH) {
            float zb = SIDE_STAND_START_Z - 0.02f;
            float xb = END_STAND_START_X - 0.02f;
            float h = 0.5f;
            board(led, -END_STAND_START_X + 1f, 0.05f, -zb, END_STAND_START_X - 1f, h, -zb, 0f, 0f, 1f, 4f);
            board(led, END_STAND_START_X - 1f, 0.05f, zb, -END_STAND_START_X + 1f, h, zb, 0f, 0f, -1f, 4f);
            board(led, -xb, 0.05f, SIDE_STAND_START_Z - 2f, -xb, h, -SIDE_STAND_START_Z + 2f, 1f, 0f, 0f, 2.4f);
            board(led, xb, 0.05f, -SIDE_STAND_START_Z + 2f, xb, h, SIDE_STAND_START_Z - 2f, -1f, 0f, 0f, 2.4f);
        } else {
            // Perimeter boards around the free zone (not in front of the scorer's table and benches).
            float zb = HALF_WIDTH + FREE_ZONE_SIDE + 0.3f;
            float xb = HALF_LENGTH + FREE_ZONE_END + 0.3f;
            board(led, -15f, 0f, -zb, 15f, 0.9f, -zb, 0f, 0f, 1f, 4f);
            board(led, -xb, 0f, 8f, -xb, 0.9f, -8f, 1f, 0f, 0f, 2f);
            board(led, xb, 0f, -8f, xb, 0.9f, 8f, -1f, 0f, 0f, 2f);
            // Fascia ribbon between the tiers, all around the bowl.
            float off = rowOffset(LOWER_ROWS - 1) + ROW_DEPTH + WALKWAY + 0.02f;
            float y0 = rowTop(LOWER_ROWS - 1) + 0.3f;
            float y1 = y0 + 0.9f;
            float fz = SIDE_STAND_START_Z + off - 0.02f;
            float fx = END_STAND_START_X + off - 0.02f;
            board(led, -fx, y0, -fz, fx, y1, -fz, 0f, 0f, 1f, 7f);
            board(led, fx, y0, fz, -fx, y1, fz, 0f, 0f, -1f, 7f);
            board(led, -fx, y0, fz, -fx, y1, -fz, 1f, 0f, 0f, 5f);
            board(led, fx, y0, -fz, fx, y1, fz, -1f, 0f, 0f, 5f);
        }
        return modelBuilder.end();
    }

    /** A vertical board from (x0, y0, z0) to (x1, y1, z1) facing (nx, ny, nz); the texture repeats {@code repeats} times. */
    static void board(MeshPartBuilder part, float x0, float y0, float z0, float x1, float y1, float z1,
                              float nx, float ny, float nz, float repeats) {
        part.setUVRange(0f, 1f, repeats, 0f);
        part.rect(x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, nx, ny, nz);
    }

    /**
     * Unlit backdrops: the beach's day sky and sea, the rooftop's night sky and city, or the sports
     * palace's floodlit mosaic.
     */
    Model buildSky(Venue venue) {
        modelBuilder.begin();
        if (venue == Venue.ROOFTOP) {
            halls.citySky(modelBuilder);
            return modelBuilder.end();
        }
        if (venue == Venue.SPORTS_PALACE) {
            halls.mosaicPanel(modelBuilder);
            return modelBuilder.end();
        }
        Material material = new Material("sky", ColorAttribute.createDiffuse(Color.WHITE), IntAttribute.createCullFace(GL20.GL_NONE));
        MeshPartBuilder dome = modelBuilder.part("dome", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal | Usage.ColorUnpacked, material);
        // Hazy pale blue at the horizon, deep blue at the zenith.
        Color horizon = new Color(0.84f, 0.92f, 0.98f, 1f);
        Color zenith = new Color(0.25f, 0.5f, 0.88f, 1f);
        float r = 215f;
        int rings = 10;
        int segments = 32;
        Vector3 position = new Vector3();
        Vector3 normal = new Vector3();
        Color color = new Color();
        short[][] index = new short[rings + 1][segments + 1];
        for (int i = 0; i <= rings; i++) {
            // From 12 degrees below the horizon to the zenith.
            float elevation = MathUtils.lerp(-0.21f, MathUtils.HALF_PI, i / (float) rings);
            float t = (float) Math.pow(Math.max(0f, MathUtils.sin(elevation)), 0.6f);
            color.set(horizon).lerp(zenith, t);
            for (int j = 0; j <= segments; j++) {
                float azimuth = MathUtils.PI2 * j / segments;
                position.set(MathUtils.cos(elevation) * MathUtils.cos(azimuth), MathUtils.sin(elevation),
                        MathUtils.cos(elevation) * MathUtils.sin(azimuth)).scl(r);
                normal.set(position).nor().scl(-1f);
                index[i][j] = dome.vertex(position, normal, color, null);
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                dome.rect(index[i][j], index[i][j + 1], index[i + 1][j + 1], index[i + 1][j]);
            }
        }
        GeometryBuilder g = new GeometryBuilder(modelBuilder, "sea", new Material("sea", ColorAttribute.createDiffuse(Color.WHITE),
                IntAttribute.createCullFace(GL20.GL_NONE)));
        // The sea: deep blue towards the horizon on the +Z side, a lighter band of surf at the shore.
        g.box(0f, -0.4f, 150f, 2f * r, 0.2f, 130f, new Color(0.12f, 0.42f, 0.62f, 1f));
        g.box(0f, -0.35f, 83f, 2f * r, 0.2f, 6f, new Color(0.5f, 0.78f, 0.8f, 1f));
        return modelBuilder.end();
    }

    /** The cable camera's pod; its four cables use a unit box scaled per frame. */
    Model buildSpidercamPod() {
        modelBuilder.begin();
        GeometryBuilder g = new GeometryBuilder(modelBuilder, "pod", vertexColoured("pod"));
        g.box(0f, 0.2f, 0f, 0.6f, 0.4f, 0.6f, DARK);
        g.box(0f, -0.1f, 0f, 0.3f, 0.25f, 0.4f, BLACK);
        g.box(0f, -0.1f, 0.25f, 0.18f, 0.18f, 0.12f, STEEL_DARK);
        return modelBuilder.end();
    }

    /** A unit-length cable along +Y, from y = 0 to y = 1. */
    Model buildCable() {
        modelBuilder.begin();
        MeshPartBuilder part = modelBuilder.part("cable", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new Material(ColorAttribute.createDiffuse(BLACK)));
        BoxShapeBuilder.build(part, 0f, 0.5f, 0f, 0.025f, 1f, 0.025f);
        return modelBuilder.end();
    }

    private static Material material(String id, Color color) {
        return new Material(id, ColorAttribute.createDiffuse(color));
    }

    private static Material vertexColoured(String id) {
        return new Material(id, ColorAttribute.createDiffuse(Color.WHITE));
    }
}
