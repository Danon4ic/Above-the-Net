package com.volleyball.game.tactics;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Saves and loads the custom plays of the playbook editor as JSON ({@code playbook.json} next to
 * the settings). Every value is checked on load: unknown names fall back to defaults, positions
 * are clamped to the court, plays without a usable set are dropped. A corrupt file is kept as
 * {@code .corrupt} and the backup is used; loading never throws.
 */
public final class PlaybookStore {
    private static final Logger LOG = Logger.getLogger(PlaybookStore.class.getName());
    public static final String FILE_NAME = "playbook.json";
    static final int MAX_PLAYS = 40;

    /** JSON layout of the file. */
    public static final class FileData {
        public int version = 1;
        public ArrayList<PlayData> plays = new ArrayList<>();
    }

    public static final class PlayData {
        public String id;
        public String name;
        public String description;
        public String minPass;
        public boolean readBlock;
        public ArrayList<RunData> runs = new ArrayList<>();
        public ArrayList<OptionData> options = new ArrayList<>();
    }

    public static final class RunData {
        public String lane;
        public float startDepth;
        public float startLateral;
        public float targetDepth;
        public float targetLateral;
        public String tempo;
        public boolean decoy;
    }

    public static final class OptionData {
        /** A set type name, or "DUMP" for the setter's dump. */
        public String set;
        public String hitter;
    }

    private final Path directory;
    private final Json json;

    public PlaybookStore(Path directory) {
        this.directory = directory;
        this.json = new Json(JsonWriter.OutputType.json);
        this.json.setIgnoreUnknownFields(true);
        this.json.setUsePrototypes(false);
        this.json.setElementType(FileData.class, "plays", PlayData.class);
        this.json.setElementType(PlayData.class, "runs", RunData.class);
        this.json.setElementType(PlayData.class, "options", OptionData.class);
    }

    public Path mainFile() {
        return directory.resolve(FILE_NAME);
    }

    /** The built-in plays plus the valid custom plays from the file (only built-ins if there is none). */
    public Playbook load() {
        Playbook book = Playbook.standard();
        List<Combination> plays = readPlays(mainFile());
        if (plays == null) {
            if (Files.exists(mainFile())) preserveCorrupt();
            plays = readPlays(directory.resolve(FILE_NAME + ".bak"));
        }
        if (plays != null) for (Combination c : plays) book.putCustom(c);
        return book;
    }

    public void save(Playbook book) throws IOException {
        FileData data = new FileData();
        for (Combination c : book.custom()) data.plays.add(toData(c));
        Files.createDirectories(directory);
        Path main = mainFile();
        Path temp = directory.resolve(FILE_NAME + ".tmp");
        Files.writeString(temp, json.prettyPrint(data), StandardCharsets.UTF_8);
        if (Files.exists(main) && readPlays(main) != null) {
            Files.copy(main, directory.resolve(FILE_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, main, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            Files.move(temp, main, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean saveQuietly(Playbook book) {
        try {
            save(book);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not save the playbook to " + mainFile(), e);
            return false;
        }
    }

    /** The valid plays of a file, an empty list for a missing file, null if the file is unreadable. */
    private List<Combination> readPlays(Path file) {
        if (!Files.exists(file)) return file.equals(mainFile()) ? new ArrayList<>() : null;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) text = text.substring(1);
            if (text.isBlank()) return new ArrayList<>();
            FileData data = json.fromJson(FileData.class, text);
            if (data == null || data.plays == null) return null;
            List<Combination> plays = new ArrayList<>();
            for (PlayData p : data.plays) {
                Combination c = fromData(p);
                if (c != null && plays.size() < MAX_PLAYS) plays.add(c);
            }
            return plays;
        } catch (IOException | SerializationException | IllegalArgumentException | ClassCastException e) {
            LOG.log(Level.WARNING, "Invalid playbook file " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** A checked play, or null if it has no usable set. */
    static Combination fromData(PlayData p) {
        if (p == null) return null;
        List<Combination.Run> runs = new ArrayList<>();
        if (p.runs != null) {
            for (RunData r : p.runs) {
                if (r == null) continue;
                SetType.Lane lane = lane(r.lane);
                if (lane == null || runs.stream().anyMatch(x -> x.lane() == lane)) continue;
                runs.add(new Combination.Run(lane, depth(r.startDepth, lane), lateral(r.startLateral), depth(r.targetDepth, lane),
                        lateral(r.targetLateral), Tempo.fromName(r.tempo), r.decoy));
            }
        }
        List<Combination.Option> options = new ArrayList<>();
        if (p.options != null) {
            for (OptionData o : p.options) {
                if (o == null || options.size() >= 4) continue;
                SetType.Lane hitter = lane(o.hitter);
                if ("DUMP".equalsIgnoreCase(o.set)) {
                    options.add(new Combination.Option(null, SetType.Lane.RIGHT));
                    continue;
                }
                SetType set = setType(o.set);
                if (set == null) continue;
                options.add(new Combination.Option(set, hitter == null ? set.lane : hitter));
            }
        }
        if (options.isEmpty()) return null;
        String id = sanitizeId(p.id);
        if (id == null || Playbook.standard().find(id) != null) return null;
        String name = text(p.name, 40, "Custom play");
        String description = text(p.description, 160, "");
        PassRating minPass = PassRating.GOOD;
        for (PassRating r : PassRating.values()) if (r.name().equalsIgnoreCase(String.valueOf(p.minPass))) minPass = r;
        return new Combination(id, name, description, minPass, List.copyOf(runs), List.copyOf(options), p.readBlock, false, false);
    }

    static PlayData toData(Combination c) {
        PlayData p = new PlayData();
        p.id = c.id();
        p.name = c.name();
        p.description = c.description();
        p.minPass = c.minPass().name();
        p.readBlock = c.readBlock();
        for (Combination.Run r : c.runs()) {
            RunData d = new RunData();
            d.lane = r.lane().name();
            d.startDepth = r.startDepth();
            d.startLateral = r.startLateral();
            d.targetDepth = r.targetDepth();
            d.targetLateral = r.targetLateral();
            d.tempo = r.tempo().name();
            d.decoy = r.decoy();
            p.runs.add(d);
        }
        for (Combination.Option o : c.options()) {
            OptionData d = new OptionData();
            d.set = o.dump() ? "DUMP" : o.set().name();
            d.hitter = o.hitter().name();
            p.options.add(d);
        }
        return p;
    }

    private static SetType.Lane lane(String name) {
        for (SetType.Lane l : SetType.Lane.values()) if (l.name().equalsIgnoreCase(String.valueOf(name))) return l;
        return null;
    }

    private static SetType setType(String name) {
        for (SetType t : SetType.values()) if (t.name().equalsIgnoreCase(String.valueOf(name))) return t;
        return null;
    }

    /** Front-row spots stay in front of the attack line, back-row ones behind it (the 3 m rule). */
    private static float depth(float d, SetType.Lane lane) {
        if (!Float.isFinite(d)) d = 3f;
        boolean backRow = lane == SetType.Lane.BACK_MIDDLE || lane == SetType.Lane.BACK_RIGHT;
        return backRow ? Math.max(3.3f, Math.min(8.5f, d)) : Math.max(0.5f, Math.min(8.5f, d));
    }

    private static float lateral(float l) {
        if (!Float.isFinite(l)) l = 0f;
        return Math.max(-4.3f, Math.min(4.3f, l));
    }

    private static String sanitizeId(String id) {
        if (id == null) return null;
        String s = id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        if (s.isEmpty() || s.length() > 40) return null;
        return s;
    }

    private static String text(String t, int max, String fallback) {
        if (t == null || t.isBlank()) return fallback;
        String s = t.strip();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private void preserveCorrupt() {
        try {
            Files.move(mainFile(), directory.resolve(FILE_NAME + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not move the corrupt playbook aside", e);
        }
    }
}
