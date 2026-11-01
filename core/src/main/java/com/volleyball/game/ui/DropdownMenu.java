package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The drop-down list of the interface: opens under its field (above it when there is no room
 * below), with the chosen value marked and the item under the mouse or the keys highlighted.
 * Mouse: click an item; a click outside closes it. Keys: up and down, Enter or Space picks, Esc
 * closes. It opens in 0.18 s (fade and a small slide) and closes at once.
 */
public final class DropdownMenu {
    static final float OPEN_TIME = 0.18f;

    private final UiFonts fonts;
    private List<String> items = List.of();
    private int selected;
    private int highlighted;
    private boolean open;
    private float openTime;
    private boolean upwards;
    private IntConsumer onPick;
    private Object owner;
    // The field it belongs to, and the list's rectangle (y up).
    private float fieldX;
    private float fieldW;
    private float listY;
    private float listH;
    private float itemH;
    private float pad;

    public DropdownMenu(UiFonts fonts) {
        this.fonts = fonts;
    }

    /**
     * Opens the list for a field at (x, y, w, h); {@code owner} identifies the field (clicking the
     * same field again closes it), {@code onPick} receives the chosen index.
     */
    public void open(Object owner, List<String> items, int selected, float x, float y, float w, float h, float s, IntConsumer onPick) {
        this.owner = owner;
        this.items = items;
        this.selected = Math.max(0, selected);
        this.highlighted = this.selected;
        this.onPick = onPick;
        fieldX = x;
        fieldW = w;
        itemH = 34f * s;
        pad = 5f * s;
        listH = items.size() * itemH + pad * 2f;
        // Below the field if it fits, else above it.
        upwards = y - 4f * s - listH < 10f * s;
        listY = upwards ? y + h + 4f * s : y - 4f * s - listH;
        open = true;
        openTime = 0f;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isOpenFor(Object field) {
        return open && owner != null && owner.equals(field);
    }

    public void close() {
        open = false;
        owner = null;
    }

    public void update(float delta) {
        if (open) openTime += delta;
    }

    /** Keyboard while open: returns true (the keys are taken). */
    public boolean handleKeys() {
        if (!open) return false;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            highlighted = (highlighted - 1 + items.size()) % items.size();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            highlighted = (highlighted + 1) % items.size();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) pick(highlighted);
        else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) close();
        return true;
    }

    /** Mouse over the list: highlights the item. */
    public void mouseMoved(float x, float y) {
        int item = itemAt(x, y);
        if (item >= 0) highlighted = item;
    }

    /** A click while open: picks an item, or closes the list when outside it. Always taken. */
    public void touchDown(float x, float y) {
        int item = itemAt(x, y);
        if (item >= 0) pick(item);
        else close();
    }

    public boolean contains(float x, float y) {
        return open && x >= fieldX && x <= fieldX + fieldW && y >= listY && y <= listY + listH;
    }

    private int itemAt(float x, float y) {
        if (!contains(x, y)) return -1;
        int fromTop = (int) ((listY + listH - pad - y) / itemH);
        return fromTop >= 0 && fromTop < items.size() ? fromTop : -1;
    }

    private void pick(int index) {
        IntConsumer callback = onPick;
        close();
        if (callback != null) callback.accept(index);
    }

    public void render(ShapeRenderer shapes, SpriteBatch batch, float s) {
        if (!open) return;
        float a = UiKit.ease(openTime / OPEN_TIME);
        float slide = (1f - a) * 6f * s * (upwards ? -1f : 1f);
        float x = fieldX;
        float y = listY + slide;
        float w = fieldW;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.popup(shapes, x, y, w, listH, a);
        for (int i = 0; i < items.size(); i++) {
            float iy = y + listH - pad - (i + 1) * itemH;
            if (i == highlighted) {
                shapes.setColor(UiKit.BORDER.r, UiKit.BORDER.g, UiKit.BORDER.b, a);
                shapes.rect(x + pad, iy + 1f, w - 2f * pad, itemH - 2f);
            }
            if (i == selected) {
                shapes.setColor(UiKit.PRIMARY.r, UiKit.PRIMARY.g, UiKit.PRIMARY.b, a);
                shapes.rect(x + pad, iy + 1f, 3f * s, itemH - 2f);
            }
        }
        shapes.end();
        batch.begin();
        BitmapFont font = fonts.get(UiFonts.Weight.SEMIBOLD, 15f * s);
        for (int i = 0; i < items.size(); i++) {
            float iy = y + listH - pad - (i + 1) * itemH;
            Color c = i == selected ? UiKit.JADE : i == highlighted ? UiKit.TEXT : UiKit.TEXT_DIM;
            UiKit.text(batch, font, UiKit.fit(font, items.get(i), w - 2f * pad - 22f * s), x + pad + 16f * s,
                    iy + itemH / 2f + font.getCapHeight() / 2f, c, a);
        }
        batch.end();
    }
}
