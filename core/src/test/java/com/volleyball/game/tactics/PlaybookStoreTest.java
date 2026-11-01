package com.volleyball.game.tactics;

import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Custom plays are saved as JSON, checked on load, and a broken file never breaks the game. */
class PlaybookStoreTest {
    private static Combination customPlay() {
        return new Combination("custom-1", "Overload left", "Two hitters on the left.", PassRating.GOOD,
                List.of(new Combination.Run(SetType.Lane.MIDDLE, 2.6f, -1f, 0.8f, -1.5f, Tempo.FIRST, true),
                        new Combination.Run(SetType.Lane.OUTSIDE, 3.8f, -3.3f, 0.9f, -3.6f, Tempo.SECOND, false)),
                List.of(new Combination.Option(SetType.SHOOT, SetType.Lane.OUTSIDE), new Combination.Option(null, SetType.Lane.RIGHT)),
                true, false, false);
    }

    @Test
    void customPlaysSurviveASaveAndALoad(@TempDir Path dir) throws Exception {
        PlaybookStore store = new PlaybookStore(dir);
        Playbook book = store.load();
        assertEquals(Playbook.builtIn().size(), book.all().size(), "no file: the built-in plays");
        book.putCustom(customPlay());
        store.save(book);
        Playbook loaded = store.load();
        Combination c = loaded.find("custom-1");
        assertNotNull(c);
        assertEquals("Overload left", c.name());
        assertEquals(2, c.runs().size());
        assertTrue(c.runs().get(0).decoy());
        assertEquals(Tempo.FIRST, c.runs().get(0).tempo());
        assertEquals(SetType.SHOOT, c.options().get(0).set());
        assertTrue(c.options().get(1).dump());
        assertTrue(c.readBlock());
        assertFalse(c.builtIn());
    }

    @Test
    void invalidValuesAreRepairedAndUnusablePlaysDropped(@TempDir Path dir) throws Exception {
        String json = """
                {"version":1,"plays":[
                  {"id":"Weird ID!","name":"x","minPass":"NONSENSE","runs":[
                    {"lane":"BACK_MIDDLE","startDepth":20,"startLateral":-99,"targetDepth":0.1,"targetLateral":0,"tempo":"FAST"},
                    {"lane":"NOBODY"}],
                   "options":[{"set":"PIPE","hitter":"BACK_MIDDLE"}]},
                  {"id":"no-sets","name":"empty","options":[{"set":"MAGIC"}]},
                  {"id":"first-tempo","name":"steals a built-in id","options":[{"set":"QUICK_MIDDLE"}]}
                ]}""";
        Files.writeString(dir.resolve(PlaybookStore.FILE_NAME), json, StandardCharsets.UTF_8);
        Playbook book = new PlaybookStore(dir).load();
        assertEquals(1, book.custom().size(), "only the usable play is kept");
        Combination c = book.custom().get(0);
        assertEquals("weird-id-", c.id());
        assertEquals(PassRating.GOOD, c.minPass());
        Combination.Run run = c.runs().get(0);
        assertTrue(run.startDepth() <= 8.5f && run.startLateral() >= -4.3f, "clamped to the court");
        assertTrue(run.targetDepth() >= 3.3f, "a back-row hitter takes off behind the 3 m line");
        assertEquals(Tempo.THIRD, run.tempo());
        assertEquals("Quick to zone 3", Playbook.standard().find("first-tempo").options().get(0).set().label, "built-ins untouched");
    }

    @Test
    void aCorruptFileFallsBackToTheBackupAndIsKept(@TempDir Path dir) throws Exception {
        PlaybookStore store = new PlaybookStore(dir);
        Playbook book = store.load();
        book.putCustom(customPlay());
        store.save(book);
        store.save(book); // the second save keeps the first as the backup
        Files.writeString(store.mainFile(), "{ this is not json", StandardCharsets.UTF_8);
        Playbook loaded = store.load();
        assertNotNull(loaded.find("custom-1"), "restored from the backup");
        assertTrue(Files.exists(dir.resolve(PlaybookStore.FILE_NAME + ".corrupt")), "the broken file is kept for inspection");
    }
}
