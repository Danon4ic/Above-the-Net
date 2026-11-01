package com.volleyball.game.config;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves {@link GameConfig} as JSON using plain java.nio, so it works before the libGDX
 * application starts (the launcher needs the window size) and in unit tests.
 *
 * <p>Safety rules: a save first writes a temp file and atomically replaces the main file, keeping
 * the previous version as {@code .bak}. A load that finds a corrupt main file tries the backup,
 * and if that fails too it returns defaults. The corrupt file is preserved as {@code .corrupt} for
 * inspection. Loading never throws.
 */
public final class ConfigStore {
    private static final Logger LOG = Logger.getLogger(ConfigStore.class.getName());
    public static final String FILE_NAME = "settings.json";

    private final Path directory;
    private final Json json;

    public ConfigStore(Path directory) {
        this.directory = directory;
        this.json = new Json(JsonWriter.OutputType.json);
        this.json.setIgnoreUnknownFields(true);
        this.json.setUsePrototypes(false);
    }

    /** Default per-user location: {@code ~/.volleyball-sim}. */
    public static ConfigStore userDefault() {
        return new ConfigStore(userDataDirectory());
    }

    public static Path userDataDirectory() {
        return Path.of(System.getProperty("user.home"), ".volleyball-sim");
    }

    public Path mainFile() {
        return directory.resolve(FILE_NAME);
    }

    public Path backupFile() {
        return directory.resolve(FILE_NAME + ".bak");
    }

    public GameConfig load() {
        Path main = mainFile();
        if (Files.exists(main)) {
            GameConfig config = tryRead(main);
            if (config != null) return config;
            preserveCorrupt(main);
        }
        Path backup = backupFile();
        if (Files.exists(backup)) {
            GameConfig config = tryRead(backup);
            if (config != null) {
                LOG.warning("Settings restored from backup " + backup);
                return config;
            }
        }
        return new GameConfig().sanitize();
    }

    public void save(GameConfig config) throws IOException {
        config.sanitize();
        Files.createDirectories(directory);
        Path main = mainFile();
        Path temp = directory.resolve(FILE_NAME + ".tmp");
        Files.writeString(temp, json.prettyPrint(config), StandardCharsets.UTF_8);
        if (Files.exists(main) && tryRead(main) != null) {
            Files.copy(main, backupFile(), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, main, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            Files.move(temp, main, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Saves and logs instead of throwing: settings persistence must never crash the game. */
    public boolean saveQuietly(GameConfig config) {
        try {
            save(config);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not save settings to " + mainFile(), e);
            return false;
        }
    }

    private GameConfig tryRead(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            // Some Windows editors save UTF-8 with a byte-order mark; it is not part of the JSON.
            if (text.startsWith("﻿")) text = text.substring(1);
            if (text.isBlank()) return null;
            GameConfig config = json.fromJson(GameConfig.class, text);
            return config == null ? null : config.sanitize();
        } catch (IOException | SerializationException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Invalid settings file " + file + ": " + e.getMessage());
            return null;
        }
    }

    private void preserveCorrupt(Path main) {
        try {
            Files.move(main, directory.resolve(FILE_NAME + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not move corrupt settings file aside", e);
        }
    }
}
