package com.volleyball.game.config;

import com.badlogic.gdx.Input;
import com.volleyball.game.input.PlayerAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @TempDir
    Path dir;

    @Test
    void missingFileGivesDefaults() {
        GameConfig config = new ConfigStore(dir).load();
        assertEquals(1600, config.windowWidth);
        assertEquals("FIVB_ARENA", config.courtTheme);
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        ConfigStore store = new ConfigStore(dir);
        GameConfig config = new GameConfig();
        config.windowWidth = 1920;
        config.mouseSensitivity = 2.5f;
        config.courtTheme = "WOOD_GYM";
        config.netCategory = "WOMEN";
        config.keyBindings.put("JUMP", "J");
        store.save(config);

        GameConfig loaded = store.load();
        assertEquals(1920, loaded.windowWidth);
        assertEquals(2.5f, loaded.mouseSensitivity, 1e-6f);
        assertEquals("WOOD_GYM", loaded.courtTheme);
        assertEquals(2.24f, loaded.netCategory().height(), 1e-6f);
        assertEquals(Input.Keys.J, loaded.keyBindings().key(PlayerAction.JUMP));
        assertEquals(Input.Keys.W, loaded.keyBindings().key(PlayerAction.MOVE_FORWARD));
    }

    @Test
    void corruptFileFallsBackToBackupAndIsPreserved() throws IOException {
        ConfigStore store = new ConfigStore(dir);
        GameConfig first = new GameConfig();
        first.windowWidth = 1024;
        store.save(first);
        GameConfig second = new GameConfig();
        second.windowWidth = 1280;
        store.save(second); // first version becomes the backup

        Files.writeString(store.mainFile(), "{ this is : not json ,,,");
        GameConfig loaded = store.load();
        assertEquals(1024, loaded.windowWidth, "restored from .bak");
        assertTrue(Files.exists(dir.resolve(ConfigStore.FILE_NAME + ".corrupt")));
    }

    @Test
    void corruptFileWithoutBackupGivesDefaults() throws IOException {
        Files.writeString(dir.resolve(ConfigStore.FILE_NAME), "garbage");
        GameConfig loaded = new ConfigStore(dir).load();
        assertEquals(new GameConfig().windowWidth, loaded.windowWidth);
    }

    @Test
    void soundAndSecondPlayerKeysAreSavedAndClamped() throws IOException {
        ConfigStore store = new ConfigStore(dir);
        GameConfig config = new GameConfig();
        config.soundEnabled = false;
        config.crowdVolume = 0.3f;
        config.musicVolume = 7f;
        config.keyBindingsPlayer2.put("JUMP", "Numpad 0");
        store.save(config.sanitize());

        GameConfig loaded = store.load();
        assertFalse(loaded.soundEnabled);
        assertEquals(0.3f, loaded.crowdVolume, 1e-6f);
        assertEquals(1f, loaded.musicVolume, 1e-6f);
        assertEquals(Input.Keys.NUMPAD_0, loaded.keyBindingsPlayer2().key(PlayerAction.JUMP));
        assertEquals(Input.Keys.UP, loaded.keyBindingsPlayer2().key(PlayerAction.MOVE_FORWARD), "unchanged keys keep the arrows");
        assertEquals(Input.Keys.SPACE, loaded.keyBindings().key(PlayerAction.JUMP), "person 1 is not affected");
    }

    @Test
    void graphicsQualityIsSavedAndUnknownNamesFallBack() throws IOException {
        ConfigStore store = new ConfigStore(dir);
        GameConfig config = new GameConfig();
        assertEquals(com.volleyball.game.render.GraphicsQuality.HIGH, config.graphicsQuality());
        config.graphicsQuality = "ULTRA";
        store.save(config.sanitize());
        assertEquals(com.volleyball.game.render.GraphicsQuality.ULTRA, store.load().graphicsQuality());
        config.graphicsQuality = "RAYTRACED";
        assertEquals("HIGH", config.sanitize().graphicsQuality);
        assertEquals(com.volleyball.game.render.GraphicsQuality.LOW, com.volleyball.game.render.GraphicsQuality.ULTRA.next());
    }

    @Test
    void outOfRangeAndUnknownValuesAreSanitized() throws IOException {
        Files.writeString(dir.resolve(ConfigStore.FILE_NAME), """
                { "windowWidth": 10, "msaaSamples": 3, "fieldOfView": 500,
                  "courtTheme": "DISCO", "lighting": "WARM_GYM", "unknownField": true }
                """);
        GameConfig loaded = new ConfigStore(dir).load();
        assertEquals(800, loaded.windowWidth);
        assertEquals(4, loaded.msaaSamples);
        assertEquals(90f, loaded.fieldOfView);
        assertEquals("FIVB_ARENA", loaded.courtTheme);
        assertEquals("WARM_GYM", loaded.lighting);
    }

    @Test
    void aMissingOrUnknownLanguageIsEnglish() throws IOException {
        Files.writeString(dir.resolve(ConfigStore.FILE_NAME), "{ \"language\": null, \"venue\": null }");
        assertEquals("EN", new ConfigStore(dir).load().language);
        Files.writeString(dir.resolve(ConfigStore.FILE_NAME), "{ \"language\": \"ru\" }");
        assertEquals("RU", new ConfigStore(dir).load().language);
    }

    @Test
    void settingsSavedWithAByteOrderMarkAreRead() throws IOException {
        Files.write(dir.resolve(ConfigStore.FILE_NAME),
                ("﻿{ \"fullscreen\": true, \"windowWidth\": 1280 }").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GameConfig loaded = new ConfigStore(dir).load();
        assertTrue(loaded.fullscreen);
        assertEquals(1280, loaded.windowWidth);
        assertFalse(Files.exists(dir.resolve(ConfigStore.FILE_NAME + ".corrupt")));
    }
}
