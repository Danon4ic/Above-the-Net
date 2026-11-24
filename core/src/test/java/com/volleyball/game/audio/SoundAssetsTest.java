package com.volleyball.game.audio;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundAssetsTest {
    @Test
    void everySoundAndVariantIsBundled() throws Exception {
        for (SoundId id : SoundId.values()) {
            for (int i = 0; i < id.variants; i++) {
                String path = "/" + id.resource(i);
                try (InputStream asset = getClass().getResourceAsStream(path)) {
                    assertNotNull(asset, path);
                    byte[] file = asset.readAllBytes();
                    String magic = new String(file, 0, Math.min(file.length, 4), java.nio.charset.StandardCharsets.US_ASCII);
                    assertTrue(file.length > 1024 && magic.equals(id.isLoop() ? "OggS" : "RIFF"), path);
                }
            }
        }
    }
}
