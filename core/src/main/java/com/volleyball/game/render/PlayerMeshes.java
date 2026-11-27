package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.volleyball.game.entities.VolleyballPlayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Roster identities and reusable joint-local surfaces from player_models.blend. */
final class PlayerMeshes {
    enum Surface { SKIN, LIP, EYE, HAIR, BAND, JERSEY, DARK_JERSEY, TRIM, TEAM_TRIM, SHORTS,
        DARK_SHORTS, PAD, PAD_FACE, SOCK, ACCENT, SHOE, SHOE_TRIM, SOLE }

    record Style(String face, String build, String hair, String equipment, Color skin, Color hairColor, Color accent) {
        Map<Surface, Color> colors(Color jersey, Color shorts, Color teamTrim) {
            Map<Surface, Color> colors = new EnumMap<>(Surface.class);
            colors.put(Surface.SKIN, skin);
            colors.put(Surface.LIP, tint(skin, 0.73f));
            colors.put(Surface.EYE, new Color(0x312A2BFF));
            colors.put(Surface.HAIR, hairColor);
            colors.put(Surface.BAND, new Color(0xE3EFF1FF));
            colors.put(Surface.JERSEY, jersey);
            colors.put(Surface.DARK_JERSEY, tint(jersey, 0.78f));
            colors.put(Surface.TRIM, new Color(jersey).lerp(Color.WHITE, 0.67f));
            colors.put(Surface.TEAM_TRIM, teamTrim);
            colors.put(Surface.SHORTS, shorts);
            colors.put(Surface.DARK_SHORTS, tint(shorts, 0.58f));
            colors.put(Surface.PAD, new Color(0x222834FF));
            colors.put(Surface.PAD_FACE, new Color(0x4E5564FF));
            colors.put(Surface.SOCK, new Color(0xDFE4E7FF));
            colors.put(Surface.ACCENT, accent);
            colors.put(Surface.SHOE, new Color(0x252B3BFF));
            colors.put(Surface.SHOE_TRIM, new Color(0x697684FF));
            colors.put(Surface.SOLE, new Color(0xE1DDD5FF));
            return colors;
        }
    }

    private record Piece(Surface surface, float[] vertices) {
    }

    private static final String RESOURCE = "/art/player_parts.csv";
    private static final Map<String, List<Piece>> PARTS = load();
    private static final String[] FACES = {"oval", "square", "long"};
    private static final String[] BUILDS = {"compact", "broad", "lean"};
    private static final String[] HAIR = {"crew", "ridge", "swept", ""};
    private static final String[] KITS = {"quick", "brace", "sleeve"};
    private static final int[] ACCENTS = {0x2EB1BDFF, 0xE6D6B5FF, 0xF18A48FF, 0x848EC9FF, 0x86AC68FF, 0xBA685AFF};

    private PlayerMeshes() {
    }

    static Style of(String club, VolleyballPlayer player) {
        if (club.equals("NWO")) {
            switch (player.name) {
                case "Sokolov": return new Style("oval", "compact", "crew", "quick",
                        new Color(0xC89270FF), new Color(0x383339FF), new Color(0x2AB9C7FF));
                case "Orlov": return new Style("square", "broad", "ridge", "brace",
                        new Color(0x8F6049FF), new Color(0x252327FF), new Color(0xE5D4ACFF));
                case "Smirnov": return new Style("long", "lean", "swept", "sleeve",
                        new Color(0xE3B791FF), new Color(0x9E6437FF), new Color(0xF08B49FF));
                default: break;
            }
        }
        LookDevelopment.PlayerLook look = LookDevelopment.player(club + "/" + player.name);
        int key = Integer.rotateLeft((club + "/" + player.name).hashCode() * 0x9E3779B9, 11);
        return new Style(FACES[Math.floorMod(key, FACES.length)], BUILDS[(key >>> 6) % BUILDS.length],
                HAIR[(key >>> 11) % HAIR.length], KITS[(key >>> 16) % KITS.length], look.skin(), look.hair(),
                new Color(ACCENTS[(key >>> 23) % ACCENTS.length]));
    }

    static int triangles(String asset, String joint) {
        return PARTS.getOrDefault(asset + "/" + joint, List.of()).stream().mapToInt(p -> p.vertices.length / 9).sum();
    }

    static void draw(GeometryBuilder geometry, String asset, String joint, Map<Surface, Color> colors) {
        for (Piece piece : PARTS.getOrDefault(asset + "/" + joint, List.of())) {
            geometry.surface(piece.vertices, colors.get(piece.surface));
        }
    }

    private static Color tint(Color color, float factor) {
        return new Color(color.r * factor, color.g * factor, color.b * factor, 1f);
    }

    private static Map<String, List<Piece>> load() {
        Map<String, List<Piece>> parts = new HashMap<>();
        try (var input = Objects.requireNonNull(PlayerMeshes.class.getResourceAsStream(RESOURCE), RESOURCE);
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (!"asset,joint,palette,triangle_vertices".equals(reader.readLine())) {
                throw new IOException("Unexpected mesh columns");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length < 12 || (fields.length - 3) % 9 != 0) throw new IOException("Incomplete triangle");
                Surface surface = Surface.valueOf(fields[2].toUpperCase(Locale.ROOT));
                float[] vertices = new float[fields.length - 3];
                for (int i = 0; i < vertices.length; i++) {
                    vertices[i] = Float.parseFloat(fields[i + 3]);
                    if (!Float.isFinite(vertices[i])) throw new IOException("Invalid vertex");
                }
                parts.computeIfAbsent(fields[0] + "/" + fields[1], key -> new ArrayList<>()).add(new Piece(surface, vertices));
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
        return parts;
    }
}
