package com.volleyball.game.config;

import java.util.Locale;

/**
 * Command-line options, e.g. {@code VolleyballSimulator3D.exe --drill=match --difficulty=pro --players=versus}.
 *
 * @param drill       drill name to start with (case-insensitive), or null for the default
 * @param autoplay    start in demo mode: the trainee plays the drill by itself
 * @param poseGallery open the developer pose gallery instead of the game
 * @param difficulty  AI difficulty name for the quick match (beginner, intermediate, pro), or null
 * @param players     players of the quick match: single, versus or coop, or null
 * @param role        role person 1 plays in the quick match (setter, outside, opposite, middle, libero), or null
 * @param poseFocus   pose gallery: index of the pose shown in close-up (keys 1-9, 0 also switch), -1 for the overview
 * @param venue       venue to play in (a {@code Venue} name, e.g. ROOFTOP), or null for the saved setting
 * @param playbook    open the playbook editor
 * @param home        quick match home team (catalog id such as "titans", or "random"), or null for the default
 * @param away        quick match away team, as {@code home}
 * @param language    interface language for this run ("en", "ru"), or null for the saved setting
 * @param seed        seed of a match or drill ({@code --seed=123}), to replay the same random choices, or null for a fresh one
 * @param capture     developer aid: directory to save screenshots to ({@code --capture=DIR}), or null
 * @param captureAt   when to save them, seconds after the start ({@code --capture-at=1.5,2,2.5}), or null
 */
public record LaunchOptions(String drill, boolean autoplay, boolean poseGallery, String difficulty, String players, String role,
                            int poseFocus, String venue, boolean playbook, String home, String away, String language, Long seed,
                            String capture, String captureAt) {
    public static final LaunchOptions DEFAULT = new LaunchOptions(null, false, false, null, null, null, -1, null, false, null, null, null, null, null, null);

    public static LaunchOptions parse(String[] args) {
        String drill = null;
        boolean autoplay = false;
        boolean poseGallery = false;
        String difficulty = null;
        String players = null;
        String role = null;
        int poseFocus = -1;
        String venue = null;
        boolean playbook = false;
        String home = null;
        String away = null;
        String language = null;
        Long seed = null;
        String capture = null;
        String captureAt = null;
        if (args != null) {
            for (String arg : args) {
                if (arg == null) continue;
                // The capture directory keeps its case (a path).
                if (arg.trim().startsWith("--capture=")) {
                    capture = arg.trim().substring("--capture=".length());
                    continue;
                }
                String a = arg.trim().toLowerCase(Locale.ROOT);
                if (a.equals("--autoplay") || a.equals("--demo")) autoplay = true;
                else if (a.equals("--pose-gallery")) poseGallery = true;
                else if (a.equals("--playbook")) playbook = true;
                else if (a.startsWith("--drill=")) drill = a.substring("--drill=".length()).toUpperCase(Locale.ROOT);
                else if (a.startsWith("--difficulty=")) difficulty = a.substring("--difficulty=".length());
                else if (a.startsWith("--players=")) players = a.substring("--players=".length());
                else if (a.startsWith("--role=")) role = a.substring("--role=".length());
                else if (a.startsWith("--home=")) home = a.substring("--home=".length());
                else if (a.startsWith("--away=")) away = a.substring("--away=".length());
                else if (a.startsWith("--lang=")) language = a.substring("--lang=".length());
                else if (a.startsWith("--seed=")) {
                    try {
                        seed = Long.parseLong(a.substring("--seed=".length()));
                    } catch (NumberFormatException ignored) {
                        // a fresh seed
                    }
                }
                else if (a.startsWith("--capture-at=")) captureAt = a.substring("--capture-at=".length());
                else if (a.startsWith("--venue=")) venue = a.substring("--venue=".length()).toUpperCase(Locale.ROOT);
                else if (a.startsWith("--pose-focus=")) {
                    poseGallery = true;
                    try {
                        poseFocus = Integer.parseInt(a.substring("--pose-focus=".length()));
                    } catch (NumberFormatException ignored) {
                        // keep the overview
                    }
                }
            }
        }
        return new LaunchOptions(drill, autoplay, poseGallery, difficulty, players, role, poseFocus, venue, playbook, home, away, language, seed, capture, captureAt);
    }
}
