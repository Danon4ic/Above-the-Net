package com.volleyball.game.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The interface language. The game's texts are written in English; for Russian every text drawn
 * by the interface goes through {@link #t}, which looks it up in {@code i18n/ru.txt}:
 * <ul>
 *   <li>exact entries ("Serve receive" → "Приём подачи");</li>
 *   <li>the same entry in capitals ("SERVE RECEIVE" → "ПРИЁМ ПОДАЧИ");</li>
 *   <li>texts joined with " · " or line breaks: each part on its own;</li>
 *   <li>templates with {} for the changing parts ("Point {}" → "Очко: {}"): the parts are
 *       translated too, numbers and names stay as they are.</li>
 * </ul>
 * A text without a translation stays English (and is listed by {@link #missing()} for checking).
 */
public final class Lang {
    public enum Language {
        EN("English"), RU("Русский");

        public final String label;

        Language(String label) {
            this.label = label;
        }

        public Language next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static Language current = Language.EN;
    private static final Map<String, String> exact = new HashMap<>();
    private static final Map<String, String> capitals = new HashMap<>();
    private static final List<Template> templates = new ArrayList<>();
    private static final Set<String> missing = Collections.synchronizedSet(new TreeSet<>());
    private static final int CACHE_SIZE = 4000;
    private static final Map<String, String> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private static boolean loaded;
    private static final String[] SEPARATORS = {"\n", "   ·   ", "  ·  ", " · ", "  |  "};
    private static final Pattern NO_WORDS = Pattern.compile("[^\\p{L}]*");

    /** A translation with placeholders: the English pattern and the Russian text with {} in the same order. */
    private record Template(Pattern pattern, Pattern anyCase, String russian, int literalLength) {
    }

    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");

    private Lang() {}

    public static Language current() {
        return current;
    }

    public static void set(Language language) {
        current = language == null ? Language.EN : language;
        synchronized (cache) {
            cache.clear();
        }
        if (current != Language.EN) load();
    }

    /** The language from its saved name ("EN", "RU"); English for anything else. */
    public static Language parse(String name) {
        if (name != null) for (Language l : Language.values()) if (l.name().equalsIgnoreCase(name)) return l;
        return Language.EN;
    }

    /** The text in the current language. */
    public static String t(String text) {
        if (current == Language.EN || text == null || text.isEmpty()) return text;
        synchronized (cache) {
            String hit = cache.get(text);
            if (hit != null) return hit;
        }
        String result = translate(text, 0);
        synchronized (cache) {
            cache.put(text, result);
        }
        return result;
    }

    /** {@link String#format} with the template translated first. */
    public static String f(String template, Object... args) {
        return String.format(Locale.ROOT, t(template), args);
    }

    /** Texts that had no translation (for checking the dictionary). */
    public static Set<String> missing() {
        return missing;
    }

    private static String translate(String text, int depth) {
        if (NO_WORDS.matcher(text).matches()) return text;
        String hit = exact.get(text);
        if (hit != null) return hit;
        if (hasLetters(text) && text.equals(text.toUpperCase(Locale.ROOT))) {
            hit = capitals.get(text);
            if (hit != null) return hit.toUpperCase(RUSSIAN);
        }
        // Surrounding spaces are kept, the inside is translated.
        String trimmed = text.strip();
        if (trimmed.length() != text.length() && !trimmed.isEmpty()) {
            int start = text.indexOf(trimmed);
            return text.substring(0, start) + translate(trimmed, depth) + text.substring(start + trimmed.length());
        }
        if (depth < 6) {
            for (String separator : SEPARATORS) {
                if (!text.contains(separator)) continue;
                String[] parts = text.split(Pattern.quote(separator), -1);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) b.append(separator);
                    b.append(parts[i].isEmpty() ? "" : translate(parts[i], depth + 1));
                }
                return b.toString();
            }
            boolean capitalsOnly = hasLetters(text) && text.equals(text.toUpperCase(Locale.ROOT));
            for (Template template : templates) {
                Matcher m = (capitalsOnly ? template.anyCase() : template.pattern()).matcher(text);
                if (!m.matches()) continue;
                String out = template.russian();
                for (int g = 1; g <= m.groupCount(); g++) {
                    String part = translate(m.group(g), depth + 1);
                    out = out.replaceFirst(Pattern.quote("{}"), Matcher.quoteReplacement(part));
                }
                return capitalsOnly ? out.toUpperCase(RUSSIAN) : out;
            }
        }
        if (hasLetters(text)) missing.add(text);
        return text;
    }

    private static boolean hasLetters(String text) {
        for (int i = 0; i < text.length(); i++) if (Character.isLetter(text.charAt(i))) return true;
        return false;
    }

    /** Reads the dictionary once: lines "English => Russian", # starts a comment, \n is a line break. */
    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Lang.class.getResourceAsStream("/i18n/ru.txt")) {
            if (in == null) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                // A comment starts with # and has no " => " (texts may start with #: "#7 Volkov...").
                if (line.isBlank() || (line.startsWith("#") && !line.contains(" => "))) continue;
                int arrow = line.indexOf(" => ");
                if (arrow <= 0) continue;
                String en = line.substring(0, arrow).replace("\\n", "\n");
                String ru = line.substring(arrow + 4).strip().replace("\\n", "\n");
                add(en, ru);
            }
        } catch (IOException ignored) {
            // No dictionary: the game stays English.
        }
        templates.sort((a, b) -> Integer.compare(b.literalLength(), a.literalLength()));
    }

    /** Adds one entry (also used by tests). */
    static void add(String en, String ru) {
        if (en.contains("{}")) {
            String[] literal = en.split(Pattern.quote("{}"), -1);
            StringBuilder regex = new StringBuilder();
            int length = 0;
            for (int i = 0; i < literal.length; i++) {
                if (i > 0) regex.append("(.*?)");
                regex.append(Pattern.quote(literal[i]));
                length += literal[i].length();
            }
            templates.add(new Template(Pattern.compile(regex.toString(), Pattern.DOTALL),
                    Pattern.compile(regex.toString(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), ru, length));
            return;
        }
        exact.put(en, ru);
        capitals.putIfAbsent(en.toUpperCase(Locale.ROOT), ru);
    }
}
