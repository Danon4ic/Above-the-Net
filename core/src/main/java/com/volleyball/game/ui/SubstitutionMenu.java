package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.i18n.Lang;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.rules.SubstitutionManager;
import com.volleyball.game.simulation.GameWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * The bench (key B in a match): pick a player of the line-up (up / down) and a substitute
 * (left / right), Enter asks for the substitution; it is made before the next serve. The rules
 * check is shown live (6 per set, starters leave once and return to their place, the libero is
 * not substituted). The game is paused while it is open.
 */
public final class SubstitutionMenu implements Disposable {
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private final GameWorld world;
    private MatchController match;
    private Team team;
    private boolean open;
    private int courtIndex;
    private int benchIndex;
    private String message = "";
    private Color messageColor = UiKit.TEXT_DIM;
    private int width;
    private int height;

    public SubstitutionMenu(GameWorld world, UiFonts fonts) {
        this.world = world;
        this.fonts = fonts;
    }

    public boolean isOpen() {
        return open;
    }

    public void open(MatchController match, Team team) {
        this.match = match;
        this.team = team;
        open = true;
        message = "";
        benchIndex = 0;
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

    /** Line-up players in zones 1..6 (the rotation order, without the libero). */
    private List<VolleyballPlayer> lineUp() {
        List<VolleyballPlayer> list = new ArrayList<>();
        RotationManager rotation = match.rules().rotation();
        for (int zone = 1; zone <= 6; zone++) list.add(world.player(rotation.slotPlayer(team.side, zone)));
        return list;
    }

    /** Players who could come in: everyone on the team sheet not in the line-up, except the libero. */
    private List<VolleyballPlayer> bench() {
        List<VolleyballPlayer> list = new ArrayList<>();
        RotationManager rotation = match.rules().rotation();
        for (VolleyballPlayer p : team.squad()) {
            if (p == team.libero() || rotation.slotZone(team.side, p.id) > 0) continue;
            list.add(p);
        }
        return list;
    }

    public void handleInput() {
        if (!open) return;
        List<VolleyballPlayer> court = lineUp();
        List<VolleyballPlayer> bench = bench();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.B)) {
            open = false;
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) courtIndex = (courtIndex + 5) % 6;
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) courtIndex = (courtIndex + 1) % 6;
        if (!bench.isEmpty()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
                benchIndex = (benchIndex + bench.size() - 1) % bench.size();
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
                benchIndex = (benchIndex + 1) % bench.size();
            }
            benchIndex = Math.min(benchIndex, bench.size() - 1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) && !bench.isEmpty()) {
            VolleyballPlayer out = court.get(courtIndex);
            VolleyballPlayer in = bench.get(benchIndex);
            SubstitutionManager.Result result = match.requestSubstitution(team.side, out, in);
            if (result == SubstitutionManager.Result.OK) {
                message = "#" + in.number + " " + in.name + " comes in for #" + out.number + " " + out.name + " before the next serve";
                messageColor = UiKit.GREEN;
            } else {
                message = result.label;
                messageColor = UiKit.RED;
            }
        }
    }

    public void render() {
        if (!open) return;
        float s = UiFonts.uiScale(height);
        List<VolleyballPlayer> court = lineUp();
        List<VolleyballPlayer> bench = bench();
        RotationManager rotation = match.rules().rotation();
        SubstitutionManager subs = match.rules().substitutions();
        int liberoId = team.libero() == null ? -1 : team.libero().id;
        BitmapFont title = fonts.get(UiFonts.Weight.EXTRABOLD, 26f * s);
        BitmapFont head = fonts.get(UiFonts.Weight.EXTRABOLD, 14f * s);
        BitmapFont row = fonts.get(UiFonts.Weight.SEMIBOLD, 18f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.MEDIUM, 14f * s);
        float pad = 32f * s;
        float panelW = Math.min(width - 60f * s, 980f * s);
        float rowH = 40f * s;
        float panelH = 170f * s + 6 * rowH + 70f * s;
        float x0 = (width - panelW) / 2f;
        float y0 = (height - panelH) / 2f;
        float top = y0 + panelH - pad;
        float listTop = top - 100f * s;
        float colW = (panelW - 3 * pad) / 2f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.dim(shapes, width, height, 0.55f);
        UiKit.panel(shapes, x0, y0, panelW, panelH, 0.97f, 0f);
        float selY = listTop - (courtIndex + 1) * rowH;
        UiKit.rowHighlight(shapes, x0 + pad - 10f * s, selY + 4f * s, colW, rowH - 6f * s, 1f, s, 1f);
        shapes.end();

        batch.begin();
        UiKit.text(batch, title, "SUBSTITUTIONS  ·  " + team.shortName, x0 + pad, top, UiKit.TEXT, 1f);
        String used = subs.used(team.side) + " / " + SubstitutionManager.MAX_PER_SET + " used this set"
                + (match.pendingSubstitutions(team.side) > 0 ? "  ·  " + match.pendingSubstitutions(team.side) + " waiting" : "");
        UiKit.text(batch, small, used, x0 + pad, top - 40f * s, UiKit.TEXT_DIM, 1f);
        UiKit.text(batch, head, "LINE-UP  (↑ ↓)", x0 + pad, listTop + 4f * s, UiKit.TEXT_FAINT, 1f);
        UiKit.text(batch, head, "SUBSTITUTE  (← →)", x0 + 2 * pad + colW, listTop + 4f * s, UiKit.TEXT_FAINT, 1f);
        for (int i = 0; i < court.size(); i++) {
            VolleyballPlayer p = court.get(i);
            float y = listTop - i * rowH - rowH / 2f + row.getCapHeight() / 2f - 4f * s;
            String libero = rotation.replacedByLibero(team.side) == p.id ? "   (libero in)" : "";
            UiKit.text(batch, small, "Z" + (i + 1), x0 + pad, y, UiKit.TEXT_FAINT, 1f);
            UiKit.text(batch, row, "#" + p.number + "  " + p.name, x0 + pad + 40f * s, y, i == courtIndex ? UiKit.TEXT : UiKit.TEXT_DIM, 1f);
            // The role column ends with the line-up column (cut short rather than run into the bench side).
            float roleX = x0 + pad + 200f * s;
            String role = UiKit.fit(small, Lang.t(p.role.label + libero), x0 + pad - 10f * s + colW - 8f * s - roleX);
            UiKit.text(batch, small, role, roleX, y, UiKit.TEXT_FAINT, 1f);
        }
        float bx = x0 + 2 * pad + colW;
        if (bench.isEmpty()) {
            UiKit.text(batch, row, "Nobody on the bench", bx, listTop - rowH / 2f, UiKit.TEXT_DIM, 1f);
        } else {
            VolleyballPlayer in = bench.get(Math.min(benchIndex, bench.size() - 1));
            VolleyballPlayer out = court.get(courtIndex);
            SubstitutionManager.Result check = subs.check(team.side, out.id, in.id, rotation, liberoId);
            float y = listTop - rowH / 2f;
            UiKit.text(batch, fonts.get(UiFonts.Weight.EXTRABOLD, 22f * s), "‹  #" + in.number + " " + in.name + "  ›", bx, y + 6f * s, UiKit.TEXT, 1f);
            UiKit.text(batch, small, in.role.label + "   ·   " + (benchIndex + 1) + " of " + bench.size(), bx, y - 26f * s, UiKit.TEXT_DIM, 1f);
            UiKit.text(batch, small, traitsLine(in), bx, y - 46f * s, colW, com.badlogic.gdx.utils.Align.left, false, UiKit.JADE, 1f);
            UiKit.text(batch, small, "for #" + out.number + " " + out.name + " (" + out.role.label + ")", bx, y - 72f * s, UiKit.TEXT_DIM, 1f);
            UiKit.text(batch, small, traitsLine(out), bx, y - 92f * s, colW, com.badlogic.gdx.utils.Align.left, false, UiKit.TEXT_FAINT, 1f);
            boolean ok = check == SubstitutionManager.Result.OK;
            UiKit.text(batch, row, ok ? "Allowed - press ENTER" : check.label, bx, y - 128f * s, colW, com.badlogic.gdx.utils.Align.left, true,
                    ok ? UiKit.GREEN : UiKit.RED, 1f);
        }
        UiKit.text(batch, small, message, x0 + pad, y0 + 56f * s, messageColor, 1f);
        UiKit.text(batch, small, "ENTER  substitute      B / ESC  close      The libero replaces players by itself.",
                x0 + pad, y0 + 26f * s, UiKit.TEXT_FAINT, 1f);
        batch.end();
    }

    /** "Traits: Cannon · Clutch" (or nothing). */
    private static String traitsLine(VolleyballPlayer p) {
        String t = p.traitLabel();
        return t.isEmpty() ? "" : "Traits: " + t;
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
