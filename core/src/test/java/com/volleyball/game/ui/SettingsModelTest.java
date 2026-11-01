package com.volleyball.game.ui;

import com.volleyball.game.config.GameConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsModelTest {
    private final List<SettingsView.Setting<?>> settings =
            SettingsView.buildSettings(SettingsView.resolutions(1920, 1080, 1600, 900));

    private SettingsView.Setting<?> setting(String label) {
        for (SettingsView.Setting<?> s : settings) if (s.label.equals(label)) return s;
        throw new AssertionError(label);
    }

    @Test
    void aDraftIsIndependentUntilItIsCommitted() {
        GameConfig saved = new GameConfig();
        GameConfig draft = saved.copy();
        assertNotSame(saved.keyBindings, draft.keyBindings);
        setting("Music").step(draft, 1);
        setting("Fullscreen").step(draft, 1);
        assertEquals(0.5f, saved.musicVolume, 1e-6f);
        assertTrue(setting("Music").differs(draft, saved));
        saved.copyFrom(draft);
        assertEquals(0.55f, saved.musicVolume, 1e-6f);
        assertTrue(saved.fullscreen);
        for (SettingsView.Setting<?> s : settings) assertFalse(s.differs(draft, saved), s.label);
    }

    @Test
    void slidersSnapToTheirStepsAndStayInRange() {
        GameConfig c = new GameConfig();
        SettingsView.Slider fov = (SettingsView.Slider) setting("Field of view");
        fov.setFraction(c, 0.51f);
        assertEquals(65f, c.fieldOfView, 1e-4f);
        for (int i = 0; i < 30; i++) fov.step(c, 1);
        assertEquals(90f, c.fieldOfView, 1e-4f);
        SettingsView.Slider master = (SettingsView.Slider) setting("Master volume");
        master.setFraction(c, -3f);
        assertEquals(0f, c.masterVolume, 1e-6f);
    }

    @Test
    void choicesCycleThroughTheirValues() {
        GameConfig c = new GameConfig();
        SettingsView.Setting<?> fps = setting("FPS limit");
        assertEquals("144", fps.display(c));
        fps.step(c, 1);
        assertEquals(165, c.fpsLimit);
        SettingsView.Setting<?> resolution = setting("Resolution");
        resolution.step(c, 1);
        assertEquals(1920, c.windowWidth);
        assertEquals(1080, c.windowHeight);
        // Sizes bigger than the monitor are not offered.
        for (int[] r : SettingsView.resolutions(1920, 1080, 1600, 900)) assertTrue(r[0] <= 1920 && r[1] <= 1080);
    }

    @Test
    void onlyAntiAliasingNeedsARestart() {
        for (SettingsView.Setting<?> s : settings) assertEquals("Anti-aliasing".equals(s.label), s.restart, s.label);
    }

    @Test
    void everyCategoryHasSettings() {
        for (SettingsView.Category category : SettingsView.Category.values()) {
            assertTrue(settings.stream().anyMatch(s -> s.category == category), category.name());
        }
    }
}
