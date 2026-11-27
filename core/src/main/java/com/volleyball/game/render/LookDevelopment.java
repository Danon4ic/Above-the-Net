package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.world.Venue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Arena light and player silhouette data exported from the Blender study. */
final class LookDevelopment {
    record ArenaLight(Vector3 direction, float angularDiameter, float shadowFill) {
    }

    record HairBox(float x, float y, float z, float width, float height, float depth) {
        void draw(GeometryBuilder geometry, Color color) {
            geometry.box(x, y, z, width, height, depth, color);
        }
    }

    record PlayerLook(Color skin, Color hair, List<HairBox> silhouette, boolean headband, float faceWidth) {
    }

    private static final Map<Venue, ArenaLight> LIGHTS = new EnumMap<>(Venue.class);
    private static final List<List<HairBox>> SILHOUETTES;
    private static final int[] SKIN = {0xDBAD8CFF, 0xEAC3A7FF, 0xC18E69FF, 0xA57454FF, 0x815A44FF, 0x704936FF};
    private static final int[] HAIR = {0x322520FF, 0x765039FF, 0x171923FF, 0x996A42FF, 0xBC935AFF, 0x533129FF};

    static {
        read("arena_lights.csv", "venue,x,y,z,angle,fill", 5, (name, v) -> {
            Vector3 direction = new Vector3(v[0], v[1], v[2]);
            if (direction.y <= 0f || v[3] <= 0f || v[4] < 0f || v[4] >= 1f) {
                throw new IllegalArgumentException("Invalid light for " + name);
            }
            LIGHTS.put(Venue.valueOf(name), new ArenaLight(direction.nor(), v[3], v[4]));
        });
        Map<String, List<HairBox>> styles = new LinkedHashMap<>();
        read("player_hair.csv", "style,x,y,z,width,height,depth", 6, (name, v) -> {
            if (v[3] <= 0f || v[4] <= 0f || v[5] <= 0f) throw new IllegalArgumentException("Empty hair piece");
            styles.computeIfAbsent(name, id -> new ArrayList<>()).add(new HairBox(v[0], v[1], v[2], v[3], v[4], v[5]));
        });
        SILHOUETTES = styles.values().stream().map(List::copyOf).toList();
        if (LIGHTS.size() != Venue.values().length || SILHOUETTES.isEmpty()) {
            throw new IllegalStateException("Incomplete Blender appearance export");
        }
    }

    private LookDevelopment() {
    }

    static ArenaLight light(Venue venue) {
        return LIGHTS.get(venue);
    }

    static PlayerLook player(String name) {
        int hash = name.hashCode() * 0x9E3779B9;
        hash ^= hash >>> 16;
        return new PlayerLook(new Color(SKIN[Math.floorMod(hash, SKIN.length)]),
                new Color(HAIR[(hash >>> 10) % HAIR.length]), SILHOUETTES.get((hash >>> 3) % SILHOUETTES.size()),
                (hash >>> 23) % 6 == 0, 0.90f + 0.05f * ((hash >>> 19) & 3));
    }

    private static void read(String name, String header, int columns, BiConsumer<String, float[]> accept) {
        String path = "/art/" + name;
        try (var input = Objects.requireNonNull(LookDevelopment.class.getResourceAsStream(path), path);
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (!header.equals(reader.readLine())) throw new IOException("Unexpected columns");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length != columns + 1) throw new IOException("Incomplete row");
                float[] values = new float[columns];
                for (int i = 0; i < columns; i++) {
                    values[i] = Float.parseFloat(parts[i + 1]);
                    if (!Float.isFinite(values[i])) throw new IOException("Non-finite value");
                }
                accept.accept(parts[0], values);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
