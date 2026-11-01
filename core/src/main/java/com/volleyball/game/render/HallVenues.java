package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.world.Venue;

import static com.volleyball.game.render.ArenaModelFactory.END_STAND_START_X;
import static com.volleyball.game.render.ArenaModelFactory.ROW_DEPTH;
import static com.volleyball.game.render.ArenaModelFactory.SIDE_STAND_START_Z;
import static com.volleyball.game.world.CourtDimensions.FREE_ZONE_END;
import static com.volleyball.game.world.CourtDimensions.FREE_ZONE_SIDE;
import static com.volleyball.game.world.CourtDimensions.HALF_LENGTH;
import static com.volleyball.game.world.CourtDimensions.HALF_WIDTH;

/**
 * The three smaller venues, built like the others from boxes in real metres (see
 * {@link ArenaModelFactory}, which calls these per part):
 * <ul>
 *   <li><b>School gym</b>: a tall hall with fold-out bleachers along both sides, high windows,
 *       fluorescent light rows, wall bars, basketball backboards, climbing ropes, mats and a ball cart.</li>
 *   <li><b>Sports palace</b>: one steep bowl of wooden benches all around, a ribbed concrete ceiling
 *       with a ring of round lamps, a mosaic panel over one end, a scoreboard over the other and
 *       club pennants.</li>
 *   <li><b>Rooftop court</b>: aluminium stands on a skyscraper roof at night, floodlight masts in the
 *       corners, string lights, glass barriers, a helipad, and the city's lights all around.</li>
 * </ul>
 * The stand fronts stay where the ball's arena walls are (12.5 m and 19 m from the centre); hall
 * walls stand behind the broadcast cameras' positions so they never block the view.
 */
final class HallVenues {
    // School gym.
    static final float GYM_SIDE_WALL_Z = 20.5f;
    static final float GYM_END_WALL_X = 27f;
    static final float GYM_CEILING = 16f;
    static final int GYM_ROWS = 8;
    static final float GYM_STAND_HALF_X = 16f;
    // Sports palace.
    static final int PALACE_ROWS = 18;
    static final float PALACE_ROOF = 22f;
    static final float PALACE_LAMP_Y = 19.5f;
    static final int PALACE_LAMPS = 24;
    static final float PALACE_RING_X = 13f;
    static final float PALACE_RING_Z = 9f;
    // Rooftop.
    static final int ROOF_ROWS = 6;
    static final float ROOF_STAND_HALF_X = 15f;
    static final float MAST_X = 21f;
    static final float MAST_Z = 14f;
    static final float MAST_HEIGHT = 15f;

    private static final Color WHITE = new Color(0.94f, 0.94f, 0.92f, 1f);
    private static final Color STEEL = new Color(0.55f, 0.57f, 0.60f, 1f);
    private static final Color STEEL_DARK = new Color(0.22f, 0.23f, 0.26f, 1f);
    private static final Color DARK = new Color(0.10f, 0.10f, 0.12f, 1f);
    private static final Color BLACK = new Color(0.04f, 0.04f, 0.05f, 1f);
    private static final Color WOOD = new Color(0.72f, 0.52f, 0.30f, 1f);
    private static final Color RED = new Color(0.82f, 0.12f, 0.12f, 1f);

    private final Matrix4 transform = new Matrix4();

    static float gymRowTop(int row) {
        return 0.45f + row * 0.42f;
    }

    static float palaceRowTop(int row) {
        return 0.7f + row * 0.48f;
    }

    static float roofRowTop(int row) {
        return 0.5f + row * 0.45f;
    }

    // ------------------------------------------------------------------ structure (themeable)

    /** Stand steps (stands material) and the hall's walls, ceiling or parapet (walls material). */
    void structure(Venue venue, MeshPartBuilder stands, MeshPartBuilder walls) {
        switch (venue) {
            case SCHOOL_GYM -> {
                for (int row = 0; row < GYM_ROWS; row++) {
                    float top = gymRowTop(row);
                    for (int s = -1; s <= 1; s += 2) {
                        BoxShapeBuilder.build(stands, 0f, top / 2f, s * (SIDE_STAND_START_Z + row * ROW_DEPTH + ROW_DEPTH / 2f),
                                GYM_STAND_HALF_X * 2f, top, ROW_DEPTH);
                    }
                }
                for (int s = -1; s <= 1; s += 2) {
                    BoxShapeBuilder.build(walls, 0f, GYM_CEILING / 2f, s * (GYM_SIDE_WALL_Z + 0.2f), GYM_END_WALL_X * 2f + 0.8f, GYM_CEILING, 0.4f);
                    BoxShapeBuilder.build(walls, s * (GYM_END_WALL_X + 0.2f), GYM_CEILING / 2f, 0f, 0.4f, GYM_CEILING, GYM_SIDE_WALL_Z * 2f + 0.8f);
                }
                BoxShapeBuilder.build(walls, 0f, GYM_CEILING + 0.2f, 0f, GYM_END_WALL_X * 2f + 0.8f, 0.4f, GYM_SIDE_WALL_Z * 2f + 0.8f);
            }
            case SPORTS_PALACE -> {
                for (int row = 0; row < PALACE_ROWS; row++) {
                    float top = palaceRowTop(row);
                    float offset = row * ROW_DEPTH;
                    for (int s = -1; s <= 1; s += 2) {
                        BoxShapeBuilder.build(stands, 0f, top / 2f, s * (SIDE_STAND_START_Z + offset + ROW_DEPTH / 2f),
                                (END_STAND_START_X + offset) * 2f, top, ROW_DEPTH);
                        BoxShapeBuilder.build(stands, s * (END_STAND_START_X + offset + ROW_DEPTH / 2f), top / 2f, 0f,
                                ROW_DEPTH, top, (SIDE_STAND_START_Z + offset) * 2f);
                    }
                }
                float back = PALACE_ROWS * ROW_DEPTH + 0.6f;
                float sideWallZ = SIDE_STAND_START_Z + back;
                float endWallX = END_STAND_START_X + back;
                for (int s = -1; s <= 1; s += 2) {
                    BoxShapeBuilder.build(walls, 0f, PALACE_ROOF / 2f, s * sideWallZ, endWallX * 2f, PALACE_ROOF, 0.5f);
                    BoxShapeBuilder.build(walls, s * endWallX, PALACE_ROOF / 2f, 0f, 0.5f, PALACE_ROOF, sideWallZ * 2f);
                }
                BoxShapeBuilder.build(walls, 0f, PALACE_ROOF, 0f, endWallX * 2f, 0.5f, sideWallZ * 2f);
            }
            case ROOFTOP -> {
                for (int row = 0; row < ROOF_ROWS; row++) {
                    float top = roofRowTop(row);
                    for (int s = -1; s <= 1; s += 2) {
                        BoxShapeBuilder.build(stands, 0f, top / 2f, s * (SIDE_STAND_START_Z + row * ROW_DEPTH + ROW_DEPTH / 2f),
                                ROOF_STAND_HALF_X * 2f, top, ROW_DEPTH);
                    }
                }
                // The parapet at the edge of the roof, and the stair and lift housing.
                float hx = ArenaModelFactory.HALL_HALF_X;
                float hz = ArenaModelFactory.HALL_HALF_Z;
                for (int s = -1; s <= 1; s += 2) {
                    BoxShapeBuilder.build(walls, 0f, 0.55f, s * (hz + 0.2f), hx * 2f + 0.8f, 1.1f, 0.4f);
                    BoxShapeBuilder.build(walls, s * (hx + 0.2f), 0.55f, 0f, 0.4f, 1.1f, hz * 2f + 0.8f);
                }
                BoxShapeBuilder.build(walls, -26f, 2.2f, -19.5f, 5f, 4.4f, 4f);
            }
            default -> throw new IllegalArgumentException(venue.name());
        }
    }

    // ------------------------------------------------------------------ crowd

    /** Spectators in the stands of the venue. */
    void crowd(Venue venue, CrowdBuilder crowd, Color home, Color away) {
        RandomXS128 random = new RandomXS128(91);
        float seat = 0.55f;
        switch (venue) {
            case SCHOOL_GYM -> {
                // Parents and classmates: the lower rows fill first.
                for (int row = 0; row < GYM_ROWS; row++) {
                    float density = Math.max(0.12f, 0.62f - row * 0.07f);
                    sideRow(crowd, random, gymRowTop(row), SIDE_STAND_START_Z + row * ROW_DEPTH + ROW_DEPTH / 2f,
                            GYM_STAND_HALF_X - 0.5f, seat, density, 0.02f, home, away);
                }
            }
            case SPORTS_PALACE -> {
                for (int row = 0; row < PALACE_ROWS; row++) {
                    float top = palaceRowTop(row);
                    float offset = row * ROW_DEPTH;
                    float density = row < 12 ? 0.86f : 0.7f;
                    sideRow(crowd, random, top, SIDE_STAND_START_Z + offset + ROW_DEPTH / 2f, END_STAND_START_X + offset - 0.5f,
                            seat, density, 0.1f, home, away);
                    float endX = END_STAND_START_X + offset + ROW_DEPTH / 2f;
                    float endHalf = SIDE_STAND_START_Z + offset - 0.5f;
                    for (float z = -endHalf; z < endHalf; z += seat) {
                        for (int s = -1; s <= 1; s += 2) {
                            if (random.nextFloat() > density * 0.92f) continue;
                            crowd.spectator(s * endX, top, z, -s, 0f, 0.14f, s < 0 ? home : away);
                        }
                    }
                }
            }
            case ROOFTOP -> {
                for (int row = 0; row < ROOF_ROWS; row++) {
                    sideRow(crowd, random, roofRowTop(row), SIDE_STAND_START_Z + row * ROW_DEPTH + ROW_DEPTH / 2f,
                            ROOF_STAND_HALF_X - 0.5f, seat, 0.74f, 0.04f, home, away);
                }
            }
            default -> throw new IllegalArgumentException(venue.name());
        }
    }

    /** One row on both long sides: home fans behind the home half (x < 0), away fans behind the other. */
    private static void sideRow(CrowdBuilder crowd, RandomXS128 random, float top, float z, float halfX, float seat,
                                float density, float flagChance, Color home, Color away) {
        for (float x = -halfX; x < halfX; x += seat) {
            for (int s = -1; s <= 1; s += 2) {
                if (random.nextFloat() > density) continue;
                crowd.spectator(x, top, s * z, 0f, -s, flagChance, x < 0 ? home : away);
            }
        }
    }

    /** Shirt colours of the spectators. */
    static Color[] shirts(Venue venue) {
        return switch (venue) {
            case SCHOOL_GYM -> new Color[]{WHITE, new Color(0.2f, 0.3f, 0.6f, 1f), new Color(0.85f, 0.75f, 0.2f, 1f),
                    new Color(0.45f, 0.45f, 0.5f, 1f), new Color(0.75f, 0.2f, 0.25f, 1f), new Color(0.3f, 0.55f, 0.35f, 1f)};
            case SPORTS_PALACE -> new Color[]{new Color(0.25f, 0.25f, 0.28f, 1f), new Color(0.55f, 0.15f, 0.12f, 1f),
                    new Color(0.18f, 0.25f, 0.45f, 1f), new Color(0.75f, 0.7f, 0.6f, 1f), new Color(0.4f, 0.32f, 0.22f, 1f),
                    WHITE};
            default -> new Color[]{new Color(0.12f, 0.12f, 0.14f, 1f), WHITE, new Color(0.85f, 0.2f, 0.6f, 1f),
                    new Color(0.15f, 0.75f, 0.85f, 1f), new Color(0.35f, 0.3f, 0.6f, 1f), new Color(0.9f, 0.85f, 0.3f, 1f)};
        };
    }

    // ------------------------------------------------------------------ props (vertex coloured)

    void props(Venue venue, GeometryBuilder g) {
        switch (venue) {
            case SCHOOL_GYM -> gymProps(g);
            case SPORTS_PALACE -> palaceProps(g);
            case ROOFTOP -> rooftopProps(g);
            default -> throw new IllegalArgumentException(venue.name());
        }
    }

    private void gymProps(GeometryBuilder g) {
        Color paint = new Color(0.24f, 0.38f, 0.6f, 1f);
        Color stripe = new Color(0.9f, 0.75f, 0.2f, 1f);
        float wx = GYM_END_WALL_X - 0.03f;
        float wz = GYM_SIDE_WALL_Z - 0.03f;
        // A painted dado with a yellow stripe along the end walls.
        for (int s = -1; s <= 1; s += 2) {
            g.box(s * wx, 0.9f, 0f, 0.04f, 1.8f, GYM_SIDE_WALL_Z * 2f, paint);
            g.box(s * (wx - 0.01f), 1.85f, 0f, 0.04f, 0.12f, GYM_SIDE_WALL_Z * 2f, stripe);
        }
        // Roof trusses across the hall.
        for (float x = -24f; x <= 24f; x += 4.5f) {
            g.box(x, GYM_CEILING - 0.35f, 0f, 0.25f, 0.5f, GYM_SIDE_WALL_Z * 2f, STEEL_DARK);
            g.box(x, GYM_CEILING - 1.1f, 0f, 0.15f, 0.12f, GYM_SIDE_WALL_Z * 2f, STEEL_DARK);
            for (float z = -GYM_SIDE_WALL_Z + 1.5f; z < GYM_SIDE_WALL_Z; z += 3f) {
                g.box(x, GYM_CEILING - 0.72f, z, 0.08f, 0.75f, 0.08f, STEEL_DARK);
            }
        }
        // Window frames (the panes are lit fixtures).
        Color frame = new Color(0.86f, 0.87f, 0.85f, 1f);
        for (int s = -1; s <= 1; s += 2) {
            for (float x = -22.5f; x <= 22.6f; x += 4.5f) {
                float z = s * (wz - 0.02f);
                g.box(x, 12.5f, z, 0.12f, 4.2f, 0.06f, frame);
                g.box(x, 12.5f, z, 3.4f, 0.12f, 0.06f, frame);
                g.box(x, 10.45f, z, 3.4f, 0.12f, 0.08f, frame);
                g.box(x, 14.55f, z, 3.4f, 0.12f, 0.08f, frame);
                g.box(x - 1.65f, 12.5f, z, 0.12f, 4.2f, 0.08f, frame);
                g.box(x + 1.65f, 12.5f, z, 0.12f, 4.2f, 0.08f, frame);
            }
        }
        // Fluorescent light housings (the tubes are lit fixtures).
        Color housing = new Color(0.8f, 0.8f, 0.78f, 1f);
        for (float z : GYM_LIGHT_ROWS) {
            for (float x = -21f; x <= 21.1f; x += 3f) g.box(x, GYM_CEILING - 1.0f, z, 2.4f, 0.12f, 0.36f, housing);
        }
        for (int s = -1; s <= 1; s += 2) {
            float wall = s * wx;
            // Basketball backboard with its frame, the shooter's square and the rim with a net.
            float board = s * (GYM_END_WALL_X - 1.25f);
            g.box(board, 3.45f, 0f, 0.05f, 1.05f, 1.8f, WHITE);
            g.box(board - s * 0.03f, 3.45f, 0f, 0.02f, 1.09f, 1.84f, DARK);
            g.box(board - s * 0.04f, 3.45f, 0f, 0.02f, 1.01f, 1.76f, WHITE);
            g.box(board - s * 0.05f, 3.3f, -0.29f, 0.02f, 0.45f, 0.05f, RED);
            g.box(board - s * 0.05f, 3.3f, 0.29f, 0.02f, 0.45f, 0.05f, RED);
            g.box(board - s * 0.05f, 3.52f, 0f, 0.02f, 0.05f, 0.63f, RED);
            g.box(board - s * 0.05f, 3.1f, 0f, 0.02f, 0.05f, 0.63f, RED);
            for (int k = -1; k <= 1; k += 2) g.box(s * (GYM_END_WALL_X - 0.62f), 3.45f, k * 0.55f, 1.24f, 0.09f, 0.09f, STEEL);
            float rimX = board - s * 0.4f;
            Color orange = new Color(0.95f, 0.45f, 0.1f, 1f);
            for (int k = 0; k < 14; k++) {
                float a = k / 14f * MathUtils.PI2;
                g.box(rimX + MathUtils.cos(a) * 0.23f, 3.05f, MathUtils.sin(a) * 0.23f, 0.06f, 0.025f, 0.06f, orange);
                if (k % 2 == 0) g.box(rimX + MathUtils.cos(a) * 0.19f, 2.85f, MathUtils.sin(a) * 0.19f, 0.015f, 0.38f, 0.015f, WHITE);
            }
            // Wall bars either side of the backboard.
            for (int side = -1; side <= 1; side += 2) {
                float z0 = side * 8f;
                float z1 = side * 18f;
                float mid = (z0 + z1) / 2f;
                for (float z = Math.min(z0, z1); z <= Math.max(z0, z1) + 0.01f; z += 0.95f) {
                    g.box(wall - s * 0.15f, 1.45f, z, 0.07f, 2.9f, 0.07f, WOOD);
                }
                for (float y = 0.3f; y < 2.9f; y += 0.26f) g.box(wall - s * 0.15f, y, mid, 0.04f, 0.04f, 10f, WOOD);
            }
            // Climbing ropes from the trusses.
            for (int k = -1; k <= 1; k += 2) g.box(s * 22.5f, (GYM_CEILING - 1.2f + 1.1f) / 2f, k * 3f, 0.05f, GYM_CEILING - 2.3f,
                    0.05f, new Color(0.78f, 0.68f, 0.48f, 1f));
            // Stacks of blue mats in the corners.
            for (int k = -1; k <= 1; k += 2) {
                for (int i = 0; i < 6; i++) {
                    Color mat = i % 2 == 0 ? new Color(0.14f, 0.3f, 0.7f, 1f) : new Color(0.1f, 0.22f, 0.55f, 1f);
                    g.box(s * 24.6f, 0.05f + i * 0.1f, k * 18.8f, 2f, 0.1f, 1.1f, mat);
                }
            }
            // A double door in each end wall corner.
            Color door = new Color(0.42f, 0.28f, 0.18f, 1f);
            g.box(wall - s * 0.06f, 1.1f, -s * 19.2f, 0.06f, 2.2f, 1.8f, door);
            g.box(wall - s * 0.08f, 1.1f, -s * 19.2f, 0.06f, 2.2f, 0.05f, DARK);
            // School championship pennants on the side walls, above the bleachers.
            Color[] pennants = {new Color(0.75f, 0.15f, 0.2f, 1f), new Color(0.15f, 0.3f, 0.65f, 1f), new Color(0.2f, 0.55f, 0.3f, 1f),
                    new Color(0.85f, 0.7f, 0.15f, 1f)};
            for (int i = 0; i < 6; i++) {
                float x = -15f + i * 6f;
                g.box(x, 6.4f, s * (wz - 0.02f), 1.3f, 2.4f, 0.03f, pennants[(i + (s > 0 ? 1 : 0)) % pennants.length]);
                g.box(x, 6.9f, s * (wz - 0.04f), 1.3f, 0.18f, 0.03f, WHITE);
            }
        }
        // A wall clock over the far door end, and the scoreboard's frame.
        g.box(-wx + 0.02f, 9f, 0f, 0.05f, 0.95f, 0.95f, DARK);
        g.box(-wx + 0.05f, 9f, 0f, 0.04f, 0.85f, 0.85f, WHITE);
        g.box(-wx + 0.08f, 9.15f, 0f, 0.02f, 0.32f, 0.04f, BLACK);
        g.box(-wx + 0.08f, 9f, 0.12f, 0.02f, 0.04f, 0.26f, BLACK);
        g.box(wx - 0.02f, 7.1f, 0f, 0.04f, 2.1f, 3.6f, DARK);
        // A cart of training balls by the far end.
        float cx = -21f;
        float cz = -6f;
        g.box(cx, 0.15f, cz, 1.0f, 0.04f, 0.7f, STEEL);
        for (int ix = -1; ix <= 1; ix += 2) for (int iz = -1; iz <= 1; iz += 2) g.box(cx + ix * 0.48f, 0.5f, cz + iz * 0.33f, 0.03f, 1f, 0.03f, STEEL);
        Color[] balls = {new Color(0.95f, 0.85f, 0.2f, 1f), new Color(0.15f, 0.35f, 0.85f, 1f), WHITE};
        int b = 0;
        for (int layer = 0; layer < 3; layer++) {
            for (int ix = 0; ix < 4; ix++) {
                for (int iz = 0; iz < 3; iz++) {
                    if ((ix + iz + layer) % 3 == 2 && layer == 2) continue;
                    g.sphere(cx - 0.33f + ix * 0.22f, 0.28f + layer * 0.21f, cz - 0.22f + iz * 0.22f, 0.21f, balls[b++ % balls.length]);
                }
            }
        }
    }

    /** Rows of fluorescent lights along the gym (z positions). */
    static final float[] GYM_LIGHT_ROWS = {-7.5f, -2.5f, 2.5f, 7.5f};

    private void palaceProps(GeometryBuilder g) {
        // Wooden benches on the concrete steps, aisles, and a railing in front of the first row.
        Color benchA = new Color(0.58f, 0.36f, 0.2f, 1f);
        Color benchB = new Color(0.66f, 0.43f, 0.24f, 1f);
        Color aisle = new Color(0.5f, 0.48f, 0.45f, 1f);
        for (int row = 0; row < PALACE_ROWS; row++) {
            float top = palaceRowTop(row);
            float offset = row * ROW_DEPTH;
            Color c = row % 2 == 0 ? benchA : benchB;
            float sideZ = SIDE_STAND_START_Z + offset + ROW_DEPTH * 0.6f;
            float endX = END_STAND_START_X + offset + ROW_DEPTH * 0.6f;
            for (int s = -1; s <= 1; s += 2) {
                g.box(0f, top + 0.04f, s * sideZ, (END_STAND_START_X + offset) * 2f - 1f, 0.08f, 0.36f, c);
                g.box(s * endX, top + 0.04f, 0f, 0.36f, 0.08f, (SIDE_STAND_START_Z + offset) * 2f - 1f, c);
                for (float x = -12f; x <= 12f; x += 12f) g.box(x, top + 0.06f, s * (SIDE_STAND_START_Z + offset + ROW_DEPTH / 2f), 1.1f, 0.12f, ROW_DEPTH, aisle);
                for (float z = -6f; z <= 6f; z += 12f) g.box(s * (END_STAND_START_X + offset + ROW_DEPTH / 2f), top + 0.06f, z, ROW_DEPTH, 0.12f, 1.1f, aisle);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            float railY = palaceRowTop(0) + 0.95f;
            g.box(0f, railY, s * (SIDE_STAND_START_Z + 0.05f), END_STAND_START_X * 2f, 0.06f, 0.06f, STEEL);
            g.box(s * (END_STAND_START_X + 0.05f), railY, 0f, 0.06f, 0.06f, SIDE_STAND_START_Z * 2f, STEEL);
            for (float x = -END_STAND_START_X + 1f; x < END_STAND_START_X; x += 2.5f) g.box(x, railY / 2f + 0.3f, s * (SIDE_STAND_START_Z + 0.05f), 0.05f, railY - 0.6f, 0.05f, STEEL);
        }
        // The ribbed concrete ceiling: ribs across the hall and three main beams.
        Color concrete = new Color(0.66f, 0.63f, 0.58f, 1f);
        float back = PALACE_ROWS * ROW_DEPTH + 0.6f;
        float sideWallZ = SIDE_STAND_START_Z + back;
        float endWallX = END_STAND_START_X + back;
        for (float x = -endWallX + 1.5f; x < endWallX; x += 2.5f) g.box(x, PALACE_ROOF - 0.85f, 0f, 0.35f, 1.2f, sideWallZ * 2f, concrete);
        for (float z = -9f; z <= 9f; z += 9f) g.box(0f, PALACE_ROOF - 1.1f, z, endWallX * 2f, 1.7f, 0.6f, concrete);
        // The lamp ring: round lamps on a hoop hanging from the ceiling.
        for (int i = 0; i < PALACE_LAMPS; i++) {
            Vector3 p = palaceLamp(i);
            g.verticalCylinder(p.x, p.y - 0.05f, p.z, 0.9f, 0.4f, DARK);
            g.box(p.x, (p.y + PALACE_ROOF - 1.9f) / 2f + 0.2f, p.z, 0.04f, PALACE_ROOF - 1.9f - p.y, 0.04f, STEEL_DARK);
            Vector3 q = palaceLamp((i + 1) % PALACE_LAMPS);
            chord(g, p.x, p.y + 0.35f, p.z, q.x, q.z, 0.14f, STEEL_DARK);
        }
        // (The mosaic over the far end is a lit panel: see mosaicPanel.)
        // The scoreboard's frame over the near end, under the ceiling.
        g.box(endWallX - 0.3f, 14.5f, 0f, 0.1f, 5.6f, 10.6f, DARK);
        g.box(endWallX - 0.33f, 17.5f, 0f, 0.06f, 0.4f, 10.6f, new Color(0.7f, 0.55f, 0.2f, 1f));
        // Club pennants hanging over the long stands.
        Color[] pennants = {new Color(0.75f, 0.12f, 0.15f, 1f), new Color(0.12f, 0.28f, 0.62f, 1f), new Color(0.9f, 0.75f, 0.15f, 1f),
                new Color(0.15f, 0.5f, 0.3f, 1f), new Color(0.45f, 0.15f, 0.5f, 1f), new Color(0.95f, 0.5f, 0.1f, 1f), WHITE};
        int k = 0;
        for (int s = -1; s <= 1; s += 2) {
            for (float x = -15f; x <= 15.1f; x += 5f) {
                Color c = pennants[k++ % pennants.length];
                float z = s * 22.5f;
                g.box(x, 18.4f, z, 1.9f, 0.06f, 0.06f, STEEL);
                g.box(x, 15.3f, z, 1.6f, 6f, 0.05f, c);
                g.box(x, 17.2f, z - s * 0.03f, 1.6f, 0.3f, 0.03f, c == WHITE ? RED : WHITE);
                transform.setToTranslation(x, 12.3f, z).rotate(Vector3.Z, 45f);
                g.transformedBox(transform, 1.13f, 1.13f, 0.05f, c);
            }
        }
    }

    /** Position of one lamp of the palace's lamp ring. */
    static Vector3 palaceLamp(int i) {
        float a = i / (float) PALACE_LAMPS * MathUtils.PI2;
        return new Vector3(MathUtils.cos(a) * PALACE_RING_X, PALACE_LAMP_Y, MathUtils.sin(a) * PALACE_RING_Z);
    }

    /** A horizontal bar from (x0, y, z0) to (x1, y, z1). */
    private void chord(GeometryBuilder g, float x0, float y, float z0, float x1, float z1, float thickness, Color color) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = MathUtils.atan2(-dz, dx) * MathUtils.radiansToDegrees;
        transform.setToTranslation((x0 + x1) / 2f, y, (z0 + z1) / 2f).rotate(Vector3.Y, yaw);
        g.transformedBox(transform, length + thickness, thickness, thickness, color);
    }

    /**
     * Sports palace: the mosaic over the far end, floodlit (rendered unlit, so it reads from every
     * camera even though the lamps light the court).
     */
    void mosaicPanel(ModelBuilder modelBuilder) {
        GeometryBuilder g = new GeometryBuilder(modelBuilder, "mosaic", new Material("mosaic",
                ColorAttribute.createDiffuse(Color.WHITE), IntAttribute.createCullFace(GL20.GL_NONE)));
        mosaic(g, -(END_STAND_START_X + PALACE_ROWS * ROW_DEPTH + 0.6f - 0.27f));
    }

    /**
     * The mosaic panel on the end wall at {@code x} (facing +X): small tiles with a little colour
     * variation each, a sunburst behind a ball and a hitter reaching for it.
     */
    private static void mosaic(GeometryBuilder g, float x) {
        RandomXS128 random = new RandomXS128(1974);
        float tile = 0.6f;
        float step = 0.66f;
        float y0 = 10.4f;
        float z0 = -13f;
        Color c = new Color();
        for (float y = y0; y < 20.4f; y += step) {
            for (float z = z0; z < 13f; z += step) {
                mosaicColour(z, y, c);
                // Slightly darker than the paint: the panel is lit, not glowing.
                float v = (0.92f + random.nextFloat() * 0.16f) * 0.78f;
                c.r = Math.min(1f, c.r * v);
                c.g = Math.min(1f, c.g * v);
                c.b = Math.min(1f, c.b * v);
                g.box(x, y, z, 0.05f, tile, tile, c);
            }
        }
    }

    private static void mosaicColour(float z, float y, Color out) {
        // The hitter: a stylised figure in red ochre, arm up towards the ball.
        if (nearSegment(z, y, -5.6f, 14.6f, -5.0f, 17.4f, 0.62f) || nearSegment(z, y, -5.0f, 17.3f, -3.4f, 19.4f, 0.3f)
                || nearSegment(z, y, -5.3f, 17.0f, -6.9f, 15.9f, 0.28f) || nearSegment(z, y, -5.6f, 14.6f, -6.7f, 11.2f, 0.36f)
                || nearSegment(z, y, -5.5f, 14.6f, -4.1f, 11.5f, 0.36f) || dst(z, y, -4.9f, 18.5f) < 0.7f) {
            out.set(0.78f, 0.27f, 0.14f, 1f);
            return;
        }
        // The ball: three-coloured panels.
        float bz = 1.5f;
        float by = 17.2f;
        float r = dst(z, y, bz, by);
        if (r < 1.9f) {
            float a = MathUtils.atan2(y - by, z - bz) + MathUtils.PI;
            int panel = (int) (a / (MathUtils.PI2 / 3f)) % 3;
            if (r > 1.75f) out.set(0.15f, 0.15f, 0.2f, 1f);
            else out.set(panel == 0 ? 0.95f : panel == 1 ? 0.15f : 0.95f, panel == 0 ? 0.85f : panel == 1 ? 0.35f : 0.95f,
                    panel == 0 ? 0.25f : panel == 1 ? 0.75f : 0.92f, 1f);
            return;
        }
        // The sunburst behind the ball: gold and orange rays.
        float a = MathUtils.atan2(y - by, z - bz);
        if (r < 8.5f && y > 11.5f) {
            int ray = (int) Math.floor((a + MathUtils.PI) / (MathUtils.PI2 / 24f));
            if (ray % 2 == 0) {
                out.set(0.95f, 0.72f, 0.2f, 1f);
                return;
            }
            out.set(0.92f, 0.5f, 0.15f, 1f);
            return;
        }
        // Background: deep blue to teal from the top down, with a white band at the bottom.
        if (y < 11.3f) {
            out.set(0.9f, 0.9f, 0.86f, 1f);
            return;
        }
        float t = MathUtils.clamp((y - 11.3f) / 9f, 0f, 1f);
        out.set(MathUtils.lerp(0.1f, 0.06f, t), MathUtils.lerp(0.45f, 0.18f, t), MathUtils.lerp(0.55f, 0.42f, t), 1f);
    }

    private static float dst(float x0, float y0, float x1, float y1) {
        return (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
    }

    private static boolean nearSegment(float px, float py, float ax, float ay, float bx, float by, float radius) {
        float dx = bx - ax;
        float dy = by - ay;
        float t = MathUtils.clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy), 0f, 1f);
        return dst(px, py, ax + dx * t, ay + dy * t) < radius;
    }

    private void rooftopProps(GeometryBuilder g) {
        Color glass = new Color(0.32f, 0.46f, 0.52f, 1f);
        // Glass barriers behind the ends (where the ball's end walls are).
        for (int s = -1; s <= 1; s += 2) {
            float x = s * (END_STAND_START_X + 0.3f);
            g.box(x, 0.6f, 0f, 0.04f, 1.2f, 18f, glass);
            g.box(x, 1.22f, 0f, 0.08f, 0.05f, 18f, STEEL);
            for (float z = -9f; z <= 9.01f; z += 2f) g.box(x, 0.62f, z, 0.07f, 1.24f, 0.07f, STEEL);
            // A railing along the top row of the stands.
            float rz = s * (SIDE_STAND_START_Z + ROOF_ROWS * ROW_DEPTH + 0.05f);
            float top = roofRowTop(ROOF_ROWS - 1);
            g.box(0f, top + 1.0f, rz, ROOF_STAND_HALF_X * 2f, 0.06f, 0.06f, STEEL);
            for (float px = -ROOF_STAND_HALF_X; px <= ROOF_STAND_HALF_X; px += 2.5f) g.box(px, top + 0.5f, rz, 0.06f, 1f, 0.06f, STEEL);
            // Scaffold legs under the open back of the stands.
            for (float px = -ROOF_STAND_HALF_X + 1f; px < ROOF_STAND_HALF_X; px += 3f) {
                g.box(px, top / 2f, rz - s * 0.02f, 0.08f, top, 0.08f, STEEL_DARK);
            }
        }
        // A railing on the parapet at the edge of the roof.
        float hx = ArenaModelFactory.HALL_HALF_X + 0.2f;
        float hz = ArenaModelFactory.HALL_HALF_Z + 0.2f;
        for (int s = -1; s <= 1; s += 2) {
            g.box(0f, 1.75f, s * hz, hx * 2f, 0.06f, 0.06f, STEEL);
            g.box(s * hx, 1.75f, 0f, 0.06f, 0.06f, hz * 2f, STEEL);
            for (float x = -hx; x <= hx; x += 3f) g.box(x, 1.42f, s * hz, 0.05f, 0.66f, 0.05f, STEEL);
            for (float z = -hz; z <= hz; z += 3f) g.box(s * hx, 1.42f, z, 0.05f, 0.66f, 0.05f, STEEL);
        }
        // Floodlight masts in the corners, their heads turned to the court.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x = sx * MAST_X;
                float z = sz * MAST_Z;
                g.box(x, 0.3f, z, 1.2f, 0.6f, 1.2f, DARK);
                g.verticalCylinder(x, 0f, z, 0.4f, MAST_HEIGHT, STEEL_DARK);
                mastHead(transform, x, z);
                g.transformedBox(transform, 0.45f, 1.7f, 2.8f, BLACK);
                for (float y = 1.5f; y < MAST_HEIGHT - 1f; y += 0.6f) g.box(x - sx * 0.26f, y, z, 0.06f, 0.04f, 0.3f, STEEL);
            }
        }
        // String-light wires between the masts along the long sides.
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < STRING_BULBS; i++) {
                Vector3 p = stringBulb(i, s);
                Vector3 q = stringBulb(i + 1, s);
                transform.setToTranslation((p.x + q.x) / 2f, (p.y + q.y) / 2f + 0.07f, p.z)
                        .rotate(Vector3.Z, MathUtils.atan2(q.y - p.y, q.x - p.x) * MathUtils.radiansToDegrees);
                g.transformedBox(transform, q.x - p.x + 0.02f, 0.02f, 0.02f, BLACK);
            }
        }
        // Rooftop machinery: air-conditioning units, a water tank, planters.
        Color unit = new Color(0.6f, 0.62f, 0.64f, 1f);
        for (int i = 0; i < 3; i++) {
            float x = 23f + (i % 2) * 3f;
            float z = -21f + (i / 2) * 3.2f;
            g.box(x, 0.75f, z, 2.2f, 1.5f, 1.8f, unit);
            g.box(x, 1.52f, z, 1.3f, 0.04f, 1.3f, DARK);
        }
        Color tank = new Color(0.42f, 0.38f, 0.34f, 1f);
        for (int ix = -1; ix <= 1; ix += 2) for (int iz = -1; iz <= 1; iz += 2) g.box(-26f + ix * 1f, 0.6f, 19.5f + iz * 1f, 0.12f, 1.2f, 0.12f, STEEL_DARK);
        g.verticalCylinder(-26f, 1.2f, 19.5f, 3f, 3.2f, tank);
        g.verticalCylinder(-26f, 4.4f, 19.5f, 2.2f, 0.5f, tank);
        Color planter = new Color(0.2f, 0.2f, 0.22f, 1f);
        Color leaf = new Color(0.16f, 0.36f, 0.2f, 1f);
        for (int i = 0; i < 6; i++) {
            float x = -18f + i * 7.2f;
            for (int s = -1; s <= 1; s += 2) {
                if (s > 0 && i == 5) continue;
                g.box(x, 0.35f, s * (ArenaModelFactory.HALL_HALF_Z - 1.2f), 3f, 0.7f, 0.9f, planter);
                g.sphere(x - 0.7f, 1.0f, s * (ArenaModelFactory.HALL_HALF_Z - 1.2f), 1.1f, leaf);
                g.sphere(x + 0.6f, 0.95f, s * (ArenaModelFactory.HALL_HALF_Z - 1.2f), 0.95f, leaf);
            }
        }
        // A helipad painted on the roof beyond the near end.
        Color yellow = new Color(0.95f, 0.8f, 0.15f, 1f);
        float hpx = 25.5f;
        float hpz = 15.5f;
        for (int k = 0; k < 32; k++) {
            float a = k / 32f * MathUtils.PI2;
            transform.setToTranslation(hpx + MathUtils.cos(a) * 3.2f, 0.006f, hpz + MathUtils.sin(a) * 3.2f)
                    .rotate(Vector3.Y, -a * MathUtils.radiansToDegrees);
            g.transformedBox(transform, 0.25f, 0.012f, 0.66f, yellow);
        }
        g.box(hpx - 0.8f, 0.006f, hpz, 0.3f, 0.012f, 2.4f, WHITE);
        g.box(hpx + 0.8f, 0.006f, hpz, 0.3f, 0.012f, 2.4f, WHITE);
        g.box(hpx, 0.006f, hpz, 1.3f, 0.012f, 0.3f, WHITE);
        // The big screen's frame behind the near end.
        g.box(23.62f, 6.6f, 0f, 0.1f, 4.4f, 7.6f, DARK);
        for (int k = -1; k <= 1; k += 2) g.box(23.62f, 4.2f, k * 3.9f, 0.2f, 8.4f, 0.2f, STEEL_DARK);
    }

    /** Transform of a mast's lamp head: at the top, turned towards the court centre. */
    static void mastHead(Matrix4 out, float x, float z) {
        float yaw = MathUtils.atan2(z, -x) * MathUtils.radiansToDegrees;
        out.setToTranslation(x - Math.signum(x) * 0.5f, MAST_HEIGHT + 0.4f, z - Math.signum(z) * 0.4f).rotate(Vector3.Y, yaw)
                .rotate(Vector3.Z, -18f);
    }

    static final int STRING_BULBS = 28;

    /** Bulb {@code i} of the string lights along the long side {@code s}: sagging between the masts. */
    static Vector3 stringBulb(int i, int s) {
        float t = i / (float) STRING_BULBS;
        float x = MathUtils.lerp(-MAST_X, MAST_X, t);
        float sag = 1f - (2f * t - 1f) * (2f * t - 1f);
        // High enough to stay above the side camera's view of the court.
        return new Vector3(x, 13f - 2.2f * sag, s * MAST_Z);
    }

    // ------------------------------------------------------------------ lit fixtures

    /** Light-emitting surfaces: window panes and tubes, the lamp ring, or floodlights and string lights. */
    void fixtures(Venue venue, MeshPartBuilder lamps) {
        switch (venue) {
            case SCHOOL_GYM -> {
                for (int s = -1; s <= 1; s += 2) {
                    for (float x = -22.5f; x <= 22.6f; x += 4.5f) {
                        BoxShapeBuilder.build(lamps, x, 12.5f, s * (GYM_SIDE_WALL_Z - 0.01f), 3.3f, 4.1f, 0.02f);
                    }
                }
                for (float z : GYM_LIGHT_ROWS) {
                    for (float x = -21f; x <= 21.1f; x += 3f) BoxShapeBuilder.build(lamps, x, GYM_CEILING - 1.08f, z, 2.2f, 0.04f, 0.22f);
                }
            }
            case SPORTS_PALACE -> {
                for (int i = 0; i < PALACE_LAMPS; i++) {
                    Vector3 p = palaceLamp(i);
                    BoxShapeBuilder.build(lamps, p.x, p.y - 0.08f, p.z, 0.72f, 0.04f, 0.72f);
                }
            }
            case ROOFTOP -> {
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        mastHead(transform, sx * MAST_X, sz * MAST_Z);
                        // The lenses on the face of the head towards the court (2 x 3).
                        for (int r = 0; r < 2; r++) {
                            for (int c = 0; c < 3; c++) {
                                Matrix4 lens = new Matrix4(transform).translate(0.24f, -0.4f + r * 0.8f, -0.9f + c * 0.9f);
                                lamps.setVertexTransform(lens);
                                BoxShapeBuilder.build(lamps, 0f, 0f, 0f, 0.02f, 0.62f, 0.75f);
                            }
                        }
                    }
                }
                lamps.setVertexTransform(null);
                for (int s = -1; s <= 1; s += 2) {
                    for (int i = 0; i <= STRING_BULBS; i++) {
                        Vector3 p = stringBulb(i, s);
                        BoxShapeBuilder.build(lamps, p.x, p.y - 0.06f, p.z, 0.13f, 0.16f, 0.13f);
                    }
                }
            }
            default -> throw new IllegalArgumentException(venue.name());
        }
    }

    // ------------------------------------------------------------------ screens and boards

    /** Scoreboard screens: over the near end (gym, palace) or on a frame behind it (rooftop). */
    void screens(Venue venue, MeshPartBuilder screen) {
        float x;
        float y0;
        float y1;
        float w;
        switch (venue) {
            case SCHOOL_GYM -> {
                x = GYM_END_WALL_X - 0.1f;
                y0 = 6.25f;
                y1 = 7.95f;
                w = 1.6f;
            }
            case SPORTS_PALACE -> {
                x = END_STAND_START_X + PALACE_ROWS * ROW_DEPTH + 0.6f - 0.37f;
                y0 = 12.2f;
                y1 = 16.8f;
                w = 5f;
            }
            case ROOFTOP -> {
                x = 23.55f;
                y0 = 4.6f;
                y1 = 8.6f;
                w = 3.6f;
            }
            default -> throw new IllegalArgumentException(venue.name());
        }
        screen.rect(x, y0, -w, x, y0, w, x, y1, w, x, y1, -w, -1f, 0f, 0f);
    }

    /** LED or painted boards (the texture's U runs along the board). */
    void ledBoards(Venue venue, MeshPartBuilder led) {
        switch (venue) {
            case SCHOOL_GYM -> {
                // Painted banners high on the end walls.
                float x = GYM_END_WALL_X - 0.05f;
                ArenaModelFactory.board(led, -x, 10.2f, 8f, -x, 11.2f, -8f, 1f, 0f, 0f, 2f);
                ArenaModelFactory.board(led, x, 10.2f, -8f, x, 11.2f, 8f, -1f, 0f, 0f, 2f);
            }
            case SPORTS_PALACE -> {
                // Perimeter boards around the free zone (not in front of the scorer's table).
                float zb = HALF_WIDTH + FREE_ZONE_SIDE + 0.3f;
                float xb = HALF_LENGTH + FREE_ZONE_END + 0.3f;
                ArenaModelFactory.board(led, -15f, 0f, -zb, 15f, 0.9f, -zb, 0f, 0f, 1f, 4f);
                ArenaModelFactory.board(led, -xb, 0f, 8f, -xb, 0.9f, -8f, 1f, 0f, 0f, 2f);
                ArenaModelFactory.board(led, xb, 0f, -8f, xb, 0.9f, 8f, -1f, 0f, 0f, 2f);
            }
            case ROOFTOP -> {
                // Neon strips on the stand fronts and on the glass barriers.
                float zb = SIDE_STAND_START_Z - 0.02f;
                float xb = END_STAND_START_X + 0.26f;
                ArenaModelFactory.board(led, -ROOF_STAND_HALF_X, 0.05f, -zb, ROOF_STAND_HALF_X, 0.48f, -zb, 0f, 0f, 1f, 4f);
                ArenaModelFactory.board(led, ROOF_STAND_HALF_X, 0.05f, zb, -ROOF_STAND_HALF_X, 0.48f, zb, 0f, 0f, -1f, 4f);
                ArenaModelFactory.board(led, -xb, 0.05f, 9f, -xb, 0.42f, -9f, 1f, 0f, 0f, 2.4f);
                ArenaModelFactory.board(led, xb, 0.05f, -9f, xb, 0.42f, 9f, -1f, 0f, 0f, 2.4f);
            }
            default -> throw new IllegalArgumentException(venue.name());
        }
    }

    // ------------------------------------------------------------------ the night sky and the city

    /**
     * Rooftop: a night sky dome with stars and the moon, and the city around and below: towers
     * with lit windows, red aviation lights and glowing streets far below (rendered unlit).
     */
    void citySky(ModelBuilder modelBuilder) {
        Material material = new Material("sky", ColorAttribute.createDiffuse(Color.WHITE), IntAttribute.createCullFace(GL20.GL_NONE));
        MeshPartBuilder dome = modelBuilder.part("dome", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal | Usage.ColorUnpacked, material);
        Color horizon = new Color(0.13f, 0.1f, 0.24f, 1f);
        Color zenith = new Color(0.008f, 0.012f, 0.045f, 1f);
        float r = 215f;
        int rings = 10;
        int segments = 32;
        Vector3 position = new Vector3();
        Vector3 normal = new Vector3();
        Color color = new Color();
        short[][] index = new short[rings + 1][segments + 1];
        for (int i = 0; i <= rings; i++) {
            float elevation = MathUtils.lerp(-0.6f, MathUtils.HALF_PI, i / (float) rings);
            float t = (float) Math.pow(Math.max(0f, MathUtils.sin(elevation)), 0.5f);
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
            for (int j = 0; j < segments; j++) dome.rect(index[i][j], index[i][j + 1], index[i + 1][j + 1], index[i + 1][j]);
        }
        GeometryBuilder g = new GeometryBuilder(modelBuilder, "city", new Material("city", ColorAttribute.createDiffuse(Color.WHITE),
                IntAttribute.createCullFace(GL20.GL_NONE)));
        RandomXS128 random = new RandomXS128(2077);
        // Stars and the moon.
        for (int i = 0; i < 320; i++) {
            float elevation = MathUtils.lerp(0.25f, 1.5f, random.nextFloat());
            float azimuth = random.nextFloat() * MathUtils.PI2;
            float b = 0.5f + random.nextFloat() * 0.5f;
            float size = 0.35f + random.nextFloat() * 0.5f;
            g.box(MathUtils.cos(elevation) * MathUtils.cos(azimuth) * 205f, MathUtils.sin(elevation) * 205f,
                    MathUtils.cos(elevation) * MathUtils.sin(azimuth) * 205f, size, size, size, new Color(b, b, b * 0.95f + 0.05f, 1f));
        }
        g.sphere(110f, 95f, -140f, 14f, new Color(0.96f, 0.94f, 0.86f, 1f));
        // Streets far below: a grid of glowing orange lines.
        Color street = new Color(0.85f, 0.5f, 0.2f, 1f);
        g.box(0f, -150.5f, 0f, 420f, 0.5f, 420f, new Color(0.02f, 0.02f, 0.03f, 1f));
        for (float c = -195f; c <= 195f; c += 32f) {
            g.box(c, -150f, 0f, 1.6f, 0.4f, 400f, street);
            g.box(0f, -150f, c, 400f, 0.4f, 1.6f, street);
        }
        // The towers: dark blocks with lit windows on the side facing this roof.
        Color[] lit = {new Color(1f, 0.84f, 0.52f, 1f), new Color(0.78f, 0.86f, 1f, 1f), new Color(0.55f, 0.48f, 0.34f, 1f)};
        for (int i = 0; i < 70; i++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float dist = 55f + random.nextFloat() * 135f;
            float cx = MathUtils.cos(angle) * dist;
            float cz = MathUtils.sin(angle) * dist;
            float w = 10f + random.nextFloat() * 14f;
            float d = 10f + random.nextFloat() * 14f;
            float top = -45f + random.nextFloat() * random.nextFloat() * 130f;
            float bottom = -150f;
            float shade = 0.035f + random.nextFloat() * 0.04f;
            g.box(cx, (top + bottom) / 2f, cz, w, top - bottom, d, new Color(shade, shade * 1.05f, shade * 1.3f, 1f));
            if (top > 25f) g.box(cx, top + 0.6f, cz, 0.8f, 0.8f, 0.8f, new Color(1f, 0.1f, 0.08f, 1f));
            // The face towards the roof.
            boolean alongX = Math.abs(cx) > Math.abs(cz);
            float faceOffset = (alongX ? w : d) / 2f + 0.06f;
            float fx = alongX ? cx - Math.signum(cx) * faceOffset : cx;
            float fz = alongX ? cz : cz - Math.signum(cz) * faceOffset;
            float span = alongX ? d : w;
            int columns = Math.max(2, (int) (span / 2.8f));
            int rows = 0;
            for (float y = top - 3f; y > Math.max(bottom + 10f, top - 80f) && rows < 26; y -= 3.3f, rows++) {
                for (int c = 0; c < columns; c++) {
                    if (random.nextFloat() > 0.34f) continue;
                    float along = -span / 2f + (c + 0.5f) * span / columns;
                    Color wc = lit[random.nextInt(lit.length)];
                    if (alongX) g.box(fx, y, fz + along, 0.1f, 1.8f, 1.5f, wc);
                    else g.box(fx + along, y, fz, 1.5f, 1.8f, 0.1f, wc);
                }
            }
        }
    }
}
