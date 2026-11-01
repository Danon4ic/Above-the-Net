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

import java.util.List;

/**
 * The pause menu (Esc in the game): a short list of actions, chosen with the arrows and Enter
 * or with the mouse. The game is paused while it is open.
 */
public final class PauseMenu implements Disposable {
    public enum Action {
        RESUME("Resume"), RESTART("Restart"), SUBSTITUTIONS("Substitutions"), TIMEOUT("Timeout"), STATISTICS("Statistics"),
        REPLAY("Replay the last rally"), ROLE_GUIDE("Role guide"), CONTROLS("Controls and hints"),
        MAIN_MENU("Main menu"), QUIT("Quit game");

        public final String label;

        Action(String label) {
            this.label = label;
        }
    }

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private List<Action> actions = List.of(Action.values());
    private boolean open;
    private int selected;
    private float openTime;
    private int width;
    private int height;
    private String subtitle = "";
    private final float[][] rows = new float[Action.values().length][4];

    public PauseMenu(UiFonts fonts) {
        this.fonts = fonts;
    }

    public boolean isOpen() {
        return open;
    }

    public void open(List<Action> available, String subtitle) {
        actions = available;
        this.subtitle = subtitle;
        open = true;
        selected = 0;
        openTime = 0f;
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

    /** Keyboard and mouse; returns the chosen action this frame, or null. */
    public Action handleInput() {
        if (!open) return null;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            selected = (selected + actions.size() - 1) % actions.size();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            selected = (selected + 1) % actions.size();
        }
        int mx = Gdx.input.getX();
        int my = height - Gdx.input.getY();
        for (int i = 0; i < actions.size(); i++) {
            float[] r = rows[i];
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                if (Gdx.input.getDeltaX() != 0 || Gdx.input.getDeltaY() != 0) selected = i;
                if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) return actions.get(i);
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) return actions.get(selected);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) return Action.RESUME;
        return null;
    }

    public void render(float delta) {
        if (!open) return;
        openTime += delta;
        float s = UiFonts.uiScale(height);
        float appear = UiKit.ease(openTime / 0.25f);
        BitmapFont title = fonts.get(UiFonts.Weight.BLACK, 40f * s);
        BitmapFont item = fonts.get(UiFonts.Weight.BOLD, 22f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 14f * s);
        float rowH = 50f * s;
        float panelW = 460f * s;
        float panelH = 150f * s + actions.size() * rowH + 30f * s;
        float x0 = (width - panelW) / 2f;
        float y0 = (height - panelH) / 2f - (1f - appear) * 20f * s;
        float top = y0 + panelH - 34f * s;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.dim(shapes, width, height, 0.6f * appear);
        UiKit.panel(shapes, x0, y0, panelW, panelH, 0.97f * appear, 4f * s);
        float listTop = top - 120f * s;
        for (int i = 0; i < actions.size(); i++) {
            float rowY = listTop - (i + 1) * rowH;
            rows[i][0] = x0 + 24f * s;
            rows[i][1] = rowY + 4f * s;
            rows[i][2] = panelW - 48f * s;
            rows[i][3] = rowH - 8f * s;
            if (i == selected) {
                Color glow = new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.18f * appear);
                Color none = new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0f);
                shapes.rect(rows[i][0], rows[i][1], rows[i][2], rows[i][3], glow, none, none, glow);
                shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, appear);
                shapes.rect(rows[i][0], rows[i][1], 3f * s, rows[i][3]);
            }
        }
        shapes.end();

        batch.begin();
        UiKit.text(batch, title, "PAUSED", x0, top, panelW, Align.center, false, UiKit.TEXT, appear);
        UiKit.text(batch, small, subtitle.toUpperCase(), x0, top - 56f * s, panelW, Align.center, false, UiKit.JADE, appear);
        for (int i = 0; i < actions.size(); i++) {
            float rowY = listTop - (i + 1) * rowH;
            boolean sel = i == selected;
            UiKit.text(batch, item, actions.get(i).label.toUpperCase(), rows[i][0] + 24f * s + (sel ? 6f * s : 0f),
                    rowY + rowH / 2f + item.getCapHeight() / 2f, sel ? UiKit.TEXT : UiKit.TEXT_DIM, appear);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
