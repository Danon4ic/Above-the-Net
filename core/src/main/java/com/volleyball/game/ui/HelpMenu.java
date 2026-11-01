package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.input.KeyBindings;
import com.volleyball.game.input.PlayerAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls and hints (F1), laid out like the settings: a short list of topics on the left (your
 * role, movement, ball control, shots and sets, match, team tactics, training, camera, sound and
 * display, player 2) and the selected topic's keys on the right. Up / down (or the mouse) choose
 * the topic. The game is paused while it is open. Key names come from the current bindings.
 */
public final class HelpMenu implements Disposable {
    /** One row: a key (or keys) and what it does. */
    private record Row(String key, String text) {
    }

    /** A topic: a title, one line about it, and its rows. */
    private record Topic(String title, String summary, List<Row> rows) {
    }

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private final List<Topic> topics = new ArrayList<>();
    private PlayerRole role;
    private boolean open;
    /** Showing only the role guide (pause menu, "Role guide"), not the controls. */
    private boolean roleOnly;
    private int selected;
    private float openTime;
    private int width;
    private int height;
    /** Screen rectangles of the topic rows and of the whole panel (for the mouse). */
    private final List<float[]> rowRects = new ArrayList<>();
    private final float[] panelRect = new float[4];

    public HelpMenu(KeyBindings keys, UiFonts fonts) {
        this.fonts = fonts;
        String move = key(keys, PlayerAction.MOVE_FORWARD) + " " + key(keys, PlayerAction.MOVE_LEFT) + " "
                + key(keys, PlayerAction.MOVE_BACK) + " " + key(keys, PlayerAction.MOVE_RIGHT);
        String primary = key(keys, PlayerAction.PRIMARY_ACTION);
        String crouch = key(keys, PlayerAction.CROUCH);
        topic("Movement", "How your player runs, turns and jumps.",
                row(move, "Move relative to the camera: forward is away from it."),
                row("Mouse", "Your player faces the point under the cursor and shuffles sideways or backwards - slide along "
                        + "the net for the block, square up to set or to hit. Settings: Face the mouse."),
                row(key(keys, PlayerAction.SPRINT), "Sprint. The body turns into the run."),
                row(crouch, "Low ready stance: shuffle facing the ball (slower, a lower platform for digging)."),
                row(key(keys, PlayerAction.JUMP), "Jump on release: hold up to 0.45 s for a higher jump (70 - 105 %)."),
                row("Stamina", "Runs, jumps and dives tire the player a little; rest between rallies brings it back. "
                        + "The bar is on your card (Settings: Fatigue)."));
        topic("Ball control", "Every contact depends on where you stand and when you press.",
                row(primary, "Play the ball: toss and hit on the serve, forearm pass, set on the 2nd touch, spike in the air. "
                        + "A setter in the air on the 2nd touch makes a jump set."),
                row(key(keys, PlayerAction.SECONDARY_ACTION), "Overhead pass on the ground, tip in the air (the setter's dump)."),
                row(crouch + " + " + primary, "Dive in the direction you move (chest first); sideways from the low stance: fall and roll."),
                row("Timing", "Press shortly before the ball arrives: the contact happens when the ball is closest to your "
                        + "hands. Too early wastes the press; late or off-position gives a poor contact."),
                row("Block", "Jump close to the net while the opponent attacks: the arms block by themselves. Jump just after "
                        + "the hitter; late or too far from the net lets the ball through."),
                row("Cursor", "The cyan ring on the floor: where serves, attacks and free balls go."),
                row("Marker", "The white circle: where a ball coming to your side drops to passing height (Settings: Landing marker). "
                        + "Your player also reaches a little farther than the computer's."));
        topic("Shots and sets", "The bar at the bottom of the screen shows the options of the moment.",
                row("1 - 9", "Choose directly: serve type, set (with the hitter it goes to), shot, block line or diagonal."),
                row("0", "Auto: the game picks the set or shot from the pass, the block and the defence (cyan dot)."),
                row(key(keys, PlayerAction.VARIANT_PREVIOUS) + " / " + key(keys, PlayerAction.VARIANT_NEXT),
                        "Step through the same options."),
                row("Shots", "Power, cut (sharp angle), roll, tip (over the block; the setter's dump), tool (off the block, out)."),
                row("Sets", "High 4, shoot 4, quick, short 3, slide, back 2, pipe, D from zone 1. Dimmed: no hitter there."));
        topic("Match", "A quick match with official rules.",
                row("Esc", "Pause menu: resume, restart, substitutions, timeout, statistics, replay, role guide, main menu."),
                row("B", "Bench: substitutions (6 per set; a starter returns only to their own place)."),
                row("U", "Timeout (2 per set, taken at the next break). Space skips it."),
                row("K", "Statistics of both teams and every player (Tab: the other team)."),
                row("Tab", "Switch to another player of your team."),
                row("F10", "Let the computer play your player (and back)."),
                row("J", "Show or hide the key hints in the corner (they fold away a few seconds into the match)."),
                row("Traits", "Every player has one or two traits (cannon, crafty, wall, clutch...): they change how the "
                        + "player plays and decides. See them on your card and in the bench."),
                row("M", "Match options: AI level (D), players (P), your role (O), system (Y)."),
                row("Ends", "The teams change ends after every set and at 8 in the deciding set; the set point is replayed."));
        topic("Team tactics", "Commands for your team and the plays it runs.",
                row("G", "Show the next group of commands: offense, defense, serve."),
                row("Alt + 1-9", "Give a command of the group shown (the same again takes it back)."),
                row("Alt + 0", "Let the team decide in that group."),
                row("PLAY", "The line above the bar tells you your part in the team's combination."),
                row("Playbook", "Main menu: study the plays and design your own; the computer teams use them."));
        topic("Training and tools", "Drills and developer views.",
                row("M", "Menu: drills (serve, receive, setting, attack, block, defense, free) and the quick match."),
                row("F5 - F8", "Quick drills: serve, serve receive, setting, attack."),
                row("F9", "Free mode / ball lab (1-8 launch test balls)."),
                row("R", "Restart the repetition (free mode: park the ball)."),
                row("T", "Show the forecast flight path."),
                row("H", "Details: ball speed and spin, rotations, contacts, FPS."),
                row("I", "AI view: what every computer player is doing, the plays and the defence."));
        topic("Camera and replay", "Views of the court.",
                row("C", "Next camera: TV, third person, first person, sideline, behind the baseline, overhead, attacker, behind the server, free."),
                row("First person", "Through your player's eyes: the view follows the mouse, the aim is the centre of the screen, "
                        + "V looks back at the net."),
                row("Middle mouse", "Drag to rotate; in third person the camera turns around your player."),
                row("Wheel", "Zoom."),
                row("V", "Between rallies: replay the last rally (Space slow motion, C angle). Otherwise: the camera back "
                        + "behind your player."));
        topic("Sound and display", "Audio and graphics in the game.",
                row("N", "Sound on / off."),
                row("- / =", "Master volume (the other volumes are in Settings)."),
                row("F2", "Court theme."),
                row("F3", "Lighting (the time of day on the beach)."),
                row("F4", "Net height: men / women."),
                row("F12", "Graphics: Low / Medium / High / Ultra."),
                row("F11", "Fullscreen on / off (also Alt + Enter)."));
        topic("Player 2", "Two people on one keyboard.",
                row("Arrows", "Move; held while hitting, they aim serves and attacks."),
                row("R-Ctrl", "Jump."),
                row("/", "Play the ball."),
                row(".", "Overhead pass, tip."),
                row(",", "Low stance."),
                row("[  ]", "Variant (serve, set, shot)."));
    }

    private static String key(KeyBindings keys, PlayerAction action) {
        return KeyBindings.nameOf(keys.key(action));
    }

    private void topic(String title, String summary, Row... rows) {
        topics.add(new Topic(title, summary, List.of(rows)));
    }

    private static Row row(String key, String text) {
        return new Row(key, text);
    }

    public boolean isOpen() {
        return open;
    }

    /** Opens or closes the controls and hints (the role guide is the first topic when a role is set). */
    public void toggle() {
        open = !open;
        roleOnly = false;
        selected = 0;
        openTime = 0f;
    }

    /** Opens the guide of {@code role} on its own, without the controls. */
    public void openRoleGuide(PlayerRole role) {
        this.role = role;
        roleOnly = role != null;
        selected = 0;
        openTime = 0f;
        open = true;
    }

    public void close() {
        open = false;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    /** Shows the guide of the person's role as the first topic (null: none). */
    public void setRole(PlayerRole role) {
        this.role = role;
    }

    /** Whether the screen point (y up) is on the panel; a click outside closes it. */
    public boolean contains(float x, float y) {
        return x >= panelRect[0] && x <= panelRect[0] + panelRect[2] && y >= panelRect[1] && y <= panelRect[1] + panelRect[3];
    }

    /** The topics shown: the role guide first (if any), then the controls. */
    private List<Topic> shown() {
        List<Topic> list = new ArrayList<>();
        if (role != null) {
            RoleGuide guide = RoleGuide.of(role);
            List<Row> rows = new ArrayList<>();
            for (RoleGuide.Item item : guide.items()) rows.add(new Row(item.topic(), item.text()));
            list.add(new Topic("Your role: " + guide.title(), guide.motto(), rows));
            if (roleOnly) return list;
        }
        list.addAll(topics);
        return list;
    }

    /** Up / down (W / S) and the mouse choose the topic. Call each frame while open. */
    public void handleInput() {
        if (!open) return;
        int count = shown().size();
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) selected = (selected + count - 1) % count;
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) selected = (selected + 1) % count;
        boolean mouse = Gdx.input.getDeltaX() != 0 || Gdx.input.getDeltaY() != 0 || Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        if (!mouse) return;
        int mx = Gdx.input.getX();
        int my = height - Gdx.input.getY();
        for (int i = 0; i < rowRects.size() && i < count; i++) {
            float[] r = rowRects.get(i);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) selected = i;
        }
    }

    public void render() {
        if (!open) return;
        openTime += Gdx.graphics.getDeltaTime();
        float a = UiKit.ease(openTime / 0.2f);
        float s = UiFonts.uiScale(height);
        List<Topic> list = shown();
        selected = Math.max(0, Math.min(selected, list.size() - 1));
        Topic topic = list.get(selected);

        BitmapFont title = fonts.get(UiFonts.Weight.BLACK, 28f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 12.5f * s);
        BitmapFont item = fonts.get(UiFonts.Weight.BOLD, 16f * s);
        BitmapFont number = fonts.get(UiFonts.Weight.SEMIBOLD, 12f * s);
        BitmapFont cardTitle = fonts.get(UiFonts.Weight.EXTRABOLD, 24f * s);
        BitmapFont summary = fonts.get(UiFonts.Weight.MEDIUM, 15f * s);

        float panelW = Math.min(width - 60f * s, (roleOnly ? 1000f : 1240f) * s);
        float panelH = Math.min(height - 60f * s, 720f * s);
        float x0 = (width - panelW) / 2f;
        float y0 = (height - panelH) / 2f - (1f - a) * 14f * s;
        panelRect[0] = x0;
        panelRect[1] = y0;
        panelRect[2] = panelW;
        panelRect[3] = panelH;
        float pad = 30f * s;
        float top = y0 + panelH - pad;
        float listW = roleOnly ? 0f : 290f * s;
        float rowH = Math.min(46f * s, (top - 64f * s - (y0 + pad)) / Math.max(1, list.size()));
        float listTop = top - 64f * s;
        float cardX = x0 + pad + listW + (roleOnly ? 0f : 22f * s);
        float cardW = x0 + panelW - pad - cardX;
        float cardTop = listTop;
        float cardBottom = y0 + pad;
        float inner = 26f * s;
        float summaryH = UiKit.wrappedHeight(summary, topic.summary(), cardW - 2f * inner);
        float dividerY = cardTop - 76f * s - summaryH;

        // The topic's rows: key caps on the left, the text wrapped beside them; the font shrinks
        // until every row fits the card.
        float size = (roleOnly ? 17f : 16f) * s;
        BitmapFont body;
        BitmapFont keyFont;
        float keyW;
        float textX;
        float textW;
        float capH;
        List<Float> rowY = new ArrayList<>();
        while (true) {
            body = fonts.get(UiFonts.Weight.MEDIUM, size);
            keyFont = fonts.get(UiFonts.Weight.EXTRABOLD, size * 0.82f);
            capH = keyFont.getLineHeight() * 1.3f;
            keyW = 0f;
            for (Row r : topic.rows()) keyW = Math.max(keyW, UiKit.keycapWidth(keyFont, r.key()));
            keyW = Math.min(keyW, cardW * 0.3f);
            textX = cardX + inner + keyW + 20f * s;
            textW = cardX + cardW - inner - textX;
            rowY.clear();
            float y = dividerY - 24f * s;
            for (Row r : topic.rows()) {
                rowY.add(y);
                float h = Math.max(capH, UiKit.wrappedHeight(body, r.text(), textW));
                y -= h + size * 1.05f;
            }
            if (y + size * 1.05f >= cardBottom + 14f * s || size <= 10f) break;
            size -= 0.5f;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.dim(shapes, width, height, 0.62f * a);
        UiKit.panel(shapes, x0, y0, panelW, panelH, 0.97f * a, 4f * s);
        rowRects.clear();
        if (!roleOnly) {
            for (int i = 0; i < list.size(); i++) {
                float ry = listTop - (i + 1) * rowH;
                rowRects.add(new float[]{x0 + pad, ry, listW, rowH - 4f * s});
                if (i != selected) continue;
                Color glow = new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.2f * a);
                Color none = new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0f);
                shapes.rect(x0 + pad, ry, listW, rowH - 4f * s, glow, none, none, glow);
                shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, a);
                shapes.rect(x0 + pad, ry, 3f * s, rowH - 4f * s);
            }
        }
        UiKit.roundedRect(shapes, cardX, cardBottom, cardW, cardTop - cardBottom, 10f * s,
                new Color(1f, 1f, 1f, 0.025f * a), new Color(1f, 1f, 1f, 0.06f * a));
        UiKit.divider(shapes, cardX + inner, dividerY, cardW - 2f * inner, a);
        for (int i = 0; i < topic.rows().size(); i++) {
            Row r = topic.rows().get(i);
            float y = rowY.get(i);
            UiKit.keycap(shapes, cardX + inner, y - capH + body.getCapHeight() * 0.25f + 2f * s,
                    Math.min(keyW, UiKit.keycapWidth(keyFont, r.key())), capH, false, a);
        }
        shapes.end();

        batch.begin();
        UiKit.text(batch, title, roleOnly ? "ROLE GUIDE" : "CONTROLS AND HINTS", x0 + pad, top, UiKit.TEXT, a);
        String hint = roleOnly ? "F1 / ESC TO CLOSE" : "GAME PAUSED   ·   UP / DOWN: TOPIC   ·   F1 / ESC TO CLOSE";
        UiKit.text(batch, small, hint, x0 + panelW - pad - UiKit.width(small, hint), top - 9f * s, UiKit.JADE, a);
        if (!roleOnly) {
            for (int i = 0; i < list.size(); i++) {
                float ry = listTop - (i + 1) * rowH;
                boolean sel = i == selected;
                float cy = ry + (rowH - 4f * s) / 2f;
                UiKit.text(batch, number, String.format("%02d", i + 1), x0 + pad + 16f * s, cy + number.getCapHeight() / 2f,
                        sel ? UiKit.JADE : UiKit.TEXT_FAINT, a);
                UiKit.text(batch, item, list.get(i).title().toUpperCase(), x0 + pad + 48f * s + (sel ? 4f * s : 0f),
                        cy + item.getCapHeight() / 2f, listW - 56f * s, Align.left, false, sel ? UiKit.TEXT : UiKit.TEXT_DIM, a);
            }
        }
        UiKit.text(batch, cardTitle, topic.title().toUpperCase(), cardX + inner, cardTop - 24f * s, UiKit.TEXT, a);
        UiKit.text(batch, summary, topic.summary(), cardX + inner, cardTop - 62f * s, cardW - 2f * inner, Align.left, true,
                UiKit.MINT, a);
        for (int i = 0; i < topic.rows().size(); i++) {
            Row r = topic.rows().get(i);
            float y = rowY.get(i);
            float capY = y - capH + body.getCapHeight() * 0.25f + 2f * s;
            UiKit.text(batch, keyFont, r.key(), cardX + inner, capY + capH / 2f + keyFont.getCapHeight() / 2f,
                    Math.min(keyW, UiKit.keycapWidth(keyFont, r.key())), Align.center, false, UiKit.TEXT, a);
            UiKit.text(batch, body, r.text(), textX, y, textW, Align.left, true, UiKit.TEXT, a);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
