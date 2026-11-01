package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchStatistics;
import com.volleyball.game.world.CourtSide;

import java.util.List;

/**
 * The statistics window (K in a match, and after the final point): the two teams side by side
 * (points, kills and attack efficiency, aces and serve errors, blocks, digs, reception, assists,
 * errors, rallies) with bars, then every player of the selected team in a table.
 */
public final class StatsPanel implements Disposable {
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private boolean open;
    private float openTime;
    private CourtSide shown = CourtSide.HOME;
    private int width;
    private int height;

    public StatsPanel(UiFonts fonts) {
        this.fonts = fonts;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        if (!open) openTime = 0f;
        open = true;
    }

    public void close() {
        open = false;
    }

    public void toggle() {
        if (open) close();
        else open();
    }

    /** Shows the other team's players. */
    public void switchTeam() {
        shown = shown.opposite();
    }

    public void show(CourtSide side) {
        shown = side;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    /**
     * @param title  "MATCH STATISTICS" or the final result
     * @param footer the key hints at the bottom
     */
    public void render(float delta, MatchStatistics stats, String homeName, String awayName, Color homeColor, Color awayColor,
                       String title, String subtitle, String footer) {
        if (!open) return;
        openTime += delta;
        float a = UiKit.ease(openTime / 0.25f);
        float s = UiFonts.uiScale(height);
        BitmapFont titleFont = fonts.get(UiFonts.Weight.BLACK, 32f * s);
        BitmapFont sub = fonts.get(UiFonts.Weight.SEMIBOLD, 14f * s);
        BitmapFont team = fonts.get(UiFonts.Weight.EXTRABOLD, 18f * s);
        BitmapFont rowLabel = fonts.get(UiFonts.Weight.SEMIBOLD, 13.5f * s);
        BitmapFont value = fonts.get(UiFonts.Weight.EXTRABOLD, 14.5f * s);
        BitmapFont head = fonts.get(UiFonts.Weight.EXTRABOLD, 11.5f * s);
        BitmapFont cell = fonts.get(UiFonts.Weight.SEMIBOLD, 13f * s);

        float panelW = Math.min(width - 60f * s, 1180f * s);
        float panelH = Math.min(height - 50f * s, 860f * s);
        float x0 = (width - panelW) / 2f;
        float y0 = (height - panelH) / 2f - (1f - a) * 16f * s;
        float pad = 34f * s;
        float top = y0 + panelH - pad;

        MatchStatistics.Line home = stats.total(CourtSide.HOME);
        MatchStatistics.Line away = stats.total(CourtSide.AWAY);
        String[][] rows = {
                {"Points won", n(stats.points(CourtSide.HOME)), n(stats.points(CourtSide.AWAY))},
                {"Attack kills", n(home.kills), n(away.kills)},
                {"Attack efficiency", pct(home.attackEfficiency()), pct(away.attackEfficiency())},
                {"Kill rate", pct(home.killRate()), pct(away.killRate())},
                {"Aces", n(home.aces), n(away.aces)},
                {"Serve errors", n(home.serveErrors), n(away.serveErrors)},
                {"Block points", n(home.blockPoints), n(away.blockPoints)},
                {"Digs", n(home.digs), n(away.digs)},
                {"Positive reception", pct(home.positiveReception()), pct(away.positiveReception())},
                {"Setter assists", n(home.assists), n(away.assists)},
                {"Team errors", n(home.errors), n(away.errors)}};
        float[][] bars = {
                {stats.points(CourtSide.HOME), stats.points(CourtSide.AWAY)}, {home.kills, away.kills},
                {pos(home.attackEfficiency()), pos(away.attackEfficiency())}, {pos(home.killRate()), pos(away.killRate())},
                {home.aces, away.aces}, {home.serveErrors, away.serveErrors}, {home.blockPoints, away.blockPoints},
                {home.digs, away.digs}, {pos(home.positiveReception()), pos(away.positiveReception())},
                {home.assists, away.assists}, {home.errors, away.errors}};

        float compareTop = top - 96f * s;
        float rowH = 25f * s;
        float midX = x0 + panelW / 2f;
        float barW = panelW * 0.22f;
        float tableTop = compareTop - rows.length * rowH - 108f * s;
        List<VolleyballPlayer> players = stats.active(shown);
        String[] columns = {"#", "PLAYER", "PTS", "K", "ATT", "EFF", "ACE", "SE", "BLK", "DIG", "REC", "AST", "ERR"};
        float[] colX = {0f, 0.05f, 0.33f, 0.40f, 0.46f, 0.53f, 0.61f, 0.67f, 0.73f, 0.79f, 0.85f, 0.91f, 0.96f};
        float tableX = x0 + pad;
        float tableW = panelW - 2f * pad;
        float tRowH = 24f * s;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.dim(shapes, width, height, 0.65f * a);
        UiKit.panel(shapes, x0, y0, panelW, panelH, 0.97f * a, 0f);
        // Team colour strips beside the names.
        shapes.setColor(homeColor.r, homeColor.g, homeColor.b, a);
        shapes.rect(midX - barW - 60f * s, compareTop + 18f * s, 4f * s, 22f * s);
        shapes.setColor(awayColor.r, awayColor.g, awayColor.b, a);
        shapes.rect(midX + barW + 56f * s, compareTop + 18f * s, 4f * s, 22f * s);
        for (int i = 0; i < rows.length; i++) {
            float ry = compareTop - (i + 1) * rowH;
            if (i % 2 == 0) {
                shapes.setColor(UiKit.DEEP.r, UiKit.DEEP.g, UiKit.DEEP.b, a);
                shapes.rect(x0 + pad, ry + 2f * s, panelW - 2f * pad, rowH - 2f * s);
            }
            float total = bars[i][0] + bars[i][1];
            float fh = total <= 0f ? 0f : bars[i][0] / total;
            float fa = total <= 0f ? 0f : bars[i][1] / total;
            float by = ry + rowH / 2f - 3f * s;
            // Bars grow outwards from the centre labels.
            UiKit.bar(shapes, midX - 120f * s - barW, by, barW, 6f * s, 0f, homeColor, a);
            if (fh > 0f) {
                shapes.setColor(homeColor.r, homeColor.g, homeColor.b, a);
                shapes.rect(midX - 120f * s - barW * fh, by, barW * fh, 6f * s);
            }
            UiKit.bar(shapes, midX + 120f * s, by, barW, 6f * s, fa, awayColor, a);
        }
        UiKit.divider(shapes, x0 + pad, tableTop + 30f * s, panelW - 2f * pad, a);
        for (int i = 0; i < players.size(); i++) {
            float ry = tableTop - (i + 1) * tRowH - 6f * s;
            if (i % 2 == 0) {
                shapes.setColor(UiKit.DEEP.r, UiKit.DEEP.g, UiKit.DEEP.b, a);
                shapes.rect(tableX, ry, tableW, tRowH);
            }
        }
        shapes.end();

        batch.begin();
        UiKit.text(batch, titleFont, title, x0, top, panelW, Align.center, false, UiKit.TEXT, a);
        UiKit.text(batch, sub, subtitle.toUpperCase(java.util.Locale.ROOT), x0, top - 44f * s, panelW, Align.center, false, UiKit.JADE, a);
        UiKit.text(batch, team, homeName, midX - barW - 300f * s, compareTop + 36f * s, 230f * s, Align.right, false, UiKit.TEXT, a);
        UiKit.text(batch, team, awayName, midX + barW + 70f * s, compareTop + 36f * s, UiKit.TEXT, a);
        for (int i = 0; i < rows.length; i++) {
            float ry = compareTop - (i + 1) * rowH;
            float ty = ry + rowH / 2f + rowLabel.getCapHeight() / 2f;
            UiKit.text(batch, rowLabel, rows[i][0], midX - 110f * s, ty, 220f * s, Align.center, false, UiKit.TEXT_DIM, a);
            UiKit.text(batch, value, rows[i][1], midX - 120f * s - barW - 70f * s, ty, 56f * s, Align.right, false, UiKit.TEXT, a);
            UiKit.text(batch, value, rows[i][2], midX + 130f * s + barW, ty, UiKit.TEXT, a);
        }
        UiKit.text(batch, sub, "RALLIES PLAYED: " + stats.rallies(), x0, compareTop - rows.length * rowH - 14f * s, panelW,
                Align.center, false, UiKit.TEXT_FAINT, a);
        UiKit.sectionTitle(batch, team, "Players  ·  " + (shown == CourtSide.HOME ? homeName : awayName), tableX, tableTop + 62f * s, a);
        for (int c = 0; c < columns.length; c++) {
            UiKit.text(batch, head, columns[c], tableX + colX[c] * tableW, tableTop + 18f * s, UiKit.TEXT_FAINT, a);
        }
        for (int i = 0; i < players.size(); i++) {
            VolleyballPlayer p = players.get(i);
            MatchStatistics.Line l = stats.of(p);
            float ty = tableTop - i * tRowH - 6f * s - tRowH / 2f + cell.getCapHeight() / 2f;
            String[] v = {String.valueOf(p.number), p.name + "  ·  " + p.role.label, n(l.points()), n(l.kills), n(l.attacks),
                    pct(l.attackEfficiency()), n(l.aces), n(l.serveErrors), n(l.blockPoints), n(l.digs),
                    l.receptions == 0 ? "-" : String.format(java.util.Locale.ROOT, "%.1f", l.receptionAverage()), n(l.assists), n(l.errors)};
            for (int c = 0; c < v.length; c++) {
                Color color = c == 2 ? UiKit.JADE : c == 1 ? UiKit.TEXT : UiKit.TEXT_DIM;
                UiKit.text(batch, c == 2 ? value : cell, v[c], tableX + colX[c] * tableW, ty, color, a);
            }
        }
        if (players.isEmpty()) {
            UiKit.text(batch, cell, "No statistics yet - play a few rallies.", tableX, tableTop - 20f * s, UiKit.TEXT_DIM, a);
        }
        UiKit.text(batch, sub, footer, x0, y0 + 26f * s, panelW, Align.center, false, UiKit.TEXT_DIM, a);
        BitmapFont legend = fonts.get(UiFonts.Weight.MEDIUM, 11f * s);
        UiKit.text(batch, legend, "PTS points  ·  K kills  ·  ATT attacks  ·  EFF (kills - errors - blocked) / attacks  ·  ACE aces  ·  "
                        + "SE serve errors  ·  BLK block points  ·  DIG digs  ·  REC reception 0-3  ·  AST assists  ·  ERR errors",
                x0, y0 + 50f * s, panelW, Align.center, false, UiKit.TEXT_FAINT, a);
        batch.end();
    }

    private static String n(int v) {
        return String.valueOf(v);
    }

    private static String pct(float v) {
        return Float.isNaN(v) ? "-" : Math.round(v * 100f) + "%";
    }

    private static float pos(float v) {
        return Float.isNaN(v) ? 0f : Math.max(0f, v);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
