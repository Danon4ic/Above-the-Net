package com.volleyball.lwjgl3;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.volleyball.game.VolleyballGame;
import com.volleyball.game.config.ConfigStore;
import com.volleyball.game.config.GameConfig;
import com.volleyball.game.config.LaunchOptions;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Desktop launcher (Windows / Linux). Reads settings before the window is created. */
public final class Lwjgl3Launcher {
    private Lwjgl3Launcher() {}

    public static void main(String[] args) {
        try {
            ConfigStore store = ConfigStore.userDefault();
            GameConfig config = store.load();
            new Lwjgl3Application(new VolleyballGame(store, config, LaunchOptions.parse(args)), createConfiguration(config));
        } catch (RuntimeException | Error e) {
            Path log = writeCrashLog(e);
            System.err.println("Above the Net crashed. Details: " + log);
            throw e;
        }
    }

    private static Lwjgl3ApplicationConfiguration createConfiguration(GameConfig config) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Above the Net");
        configuration.setWindowIcon(Files.FileType.Classpath, "icons/icon128.png", "icons/icon32.png", "icons/icon16.png");
        // RGBA 8 bits, 24-bit depth, no stencil, MSAA samples from settings.
        configuration.setBackBufferConfig(8, 8, 8, 8, 24, 0, config.msaaSamples);
        if (config.fullscreen) {
            configuration.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        } else {
            // Never open a window larger than the monitor (e.g. settings copied from a bigger screen).
            Graphics.DisplayMode display = Lwjgl3ApplicationConfiguration.getDisplayMode();
            int width = Math.min(config.windowWidth, Math.round(display.width * 0.95f));
            int height = Math.min(config.windowHeight, Math.round(display.height * 0.9f));
            configuration.setWindowedMode(width, height);
        }
        configuration.setWindowSizeLimits(800, 600, -1, -1);
        configuration.useVsync(config.vsync);
        configuration.setForegroundFPS(config.fpsLimit);
        configuration.setIdleFPS(30);
        return configuration;
    }

    private static Path writeCrashLog(Throwable error) {
        Path directory = ConfigStore.userDataDirectory().resolve("logs");
        Path file = directory.resolve("crash-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log");
        try {
            java.nio.file.Files.createDirectories(directory);
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            java.nio.file.Files.writeString(file, trace.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Nothing more we can do; the stack trace is still printed to stderr by the caller.
        }
        return file;
    }
}
