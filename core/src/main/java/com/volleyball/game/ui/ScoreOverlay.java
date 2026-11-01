package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.i18n.Lang;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.match.MatchListener;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RallyManager;
import com.volleyball.game.rules.RallyOutcome;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.List;

/**
 * The match's score graphics, all driven by the real match state and its events:
 * <ul>
 *   <li>the score bug in the top-right corner: team colours and names (your team marked), the
 *       serving team's ball, sets won and the points in big type — a new point rolls in with a
 *       short blue flash; the set number and the finished sets above; the set point / match
 *       point tag and the serve clock below;</li>
 *   <li>a caption after each point (who scored and why), substitutions and timeouts;</li>
 *   <li>the set result between sets: who won it, its score, the table of all sets and the
 *       countdown to the next one;</li>
 *   <li>the action strip at the bottom: the keys of what your player can do right now (given by
 *       the match screen from the rally's state).</li>
 * </ul>
 */
public final class ScoreOverlay implements MatchListener, Disposable {
    static final float ROLL_TIME = 0.4f;
    static final float FLASH_TIME = 0.5f;
    static final float BANNER_TIME = 2.4f;

    private final GameWorld world;
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private int width;
    private int height;
    private float s = 1f;

    private float clock;
    private CourtSide yourSide;
    // Points as shown: the current value, the one before it and when it changed (the roll).
    private final int[] shownPoints = {-1, -1};
    private final int[] previousPoints = {0, 0};
    private final float[] changedAt = {-10f, -10f};
    // Caption.
    private String bannerTitle;
    private String bannerText;
    private Color bannerColor = UiKit.TEXT;
    private final Color cellColor = new Color();
    private float bannerStart = -10f;
    private float bannerDuration;
    private boolean bannerBig;
    // Set result card.
    private MatchScore.SetResult setResult;
    private float setResultAt = -100f;
    private final List<MatchScore.SetResult> setsSoFar = new ArrayList<>();
    private int setsHome;
    private int setsAway;
    // Action strip.
    private String contextTitle;
    private final List<String[]> contextKeys = new ArrayList<>();
    private String contextSignature = "";
    private float contextAlpha;
    private float contextChangedAt = -10f;
    /** Height kept free at the bottom (the play caller), so captions sit above it. */
    private float bottomClear;

    public ScoreOverlay(GameWorld world, UiFonts fonts) {
        this.world = world;
        this.fonts = fonts;
    }

    /** The person's team (marked in the score bug), or null when watching. */
    public void setYourSide(CourtSide side) {
        yourSide = side;
    }

    /** Shows a caption now (e.g. the person's role at the start of the match). */
    public void announce(String title, String text, Color color, float seconds) {
        banner(title, text, color, seconds, false);
    }

    /** The bottom edge below which the captions must not go (the top of the play caller), 0 for none. */
    public void setBottomClear(float y) {
        bottomClear = y;
    }

    /** The baseline of the lower-third captions: low on the screen, above the play caller. */
    private float captionBase() {
        return Math.max(height * 0.1f, bottomClear + 12f * s);
    }

    /** A title line for the action strip (e.g. "YOUR SERVE · FLOAT"), or null. */
    public void setPrompt(String text) {
        contextTitle = text;
    }

    /** The keys of what can be done now: pairs {key, action}; empty for none. */
    public void setActions(List<String[]> keys) {
        StringBuilder signature = new StringBuilder(contextTitle == null ? "" : contextTitle);
        for (String[] k : keys) signature.append('|').append(k[0]).append(k[1]);
        if (!signature.toString().equals(contextSignature)) {
            contextSignature = signature.toString();
            contextChangedAt = clock;
        }
        contextKeys.clear();
        contextKeys.addAll(keys);
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        s = UiFonts.uiScale(height);
    }

    // ------------------------------------------------------------------ events

    @Override
    public void onChangeOfEnds(boolean swapped) {
        // After a set the set result says enough; in the deciding set the change at 8 gets its own.
        if (clock - setResultAt < MatchController.SET_BREAK) return;
        banner("CHANGE OF ENDS", "Half of the deciding set: the teams switch sides", UiKit.TEXT_DIM, BANNER_TIME, false);
    }

    @Override
    public void onPoint(MatchRules.PointSummary point, MatchScore score) {
        RallyOutcome outcome = point.outcome();
        if (outcome.isReplay()) {
            banner("NO POINT", outcome.reason().label, UiKit.TEXT_DIM, BANNER_TIME, false);
            return;
        }
        Team winner = team(outcome.winner());
        if (point.matchWon()) {
            banner(winner.name.toUpperCase(java.util.Locale.ROOT) + " WIN", "Sets " + score.sets(CourtSide.HOME) + " : " + score.sets(CourtSide.AWAY)
                    + "   " + setHistory(score), winner.shirtColor, 1e6f, true);
        } else if (point.setWon()) {
            setsSoFar.clear();
            setsSoFar.addAll(score.finishedSets());
            setResult = setsSoFar.get(setsSoFar.size() - 1);
            setsHome = score.sets(CourtSide.HOME);
            setsAway = score.sets(CourtSide.AWAY);
            setResultAt = clock;
            bannerTitle = null;
        } else {
            VolleyballPlayer who = outcome.playerId() >= 0 ? world.player(outcome.playerId()) : null;
            String detail = outcome.reason().label + (who != null ? "   ·   #" + who.number + " " + who.name : "")
                    + (point.sideOut() ? "   ·   side-out" : "");
            banner("POINT " + winner.shortName, detail, winner.shirtColor, BANNER_TIME, false);
        }
    }

    @Override
    public void onSubstitution(CourtSide side, VolleyballPlayer out, VolleyballPlayer in, int used) {
        Team team = team(side);
        banner("SUBSTITUTION " + team.shortName, "#" + in.number + " " + in.name + " in for #" + out.number + " " + out.name
                + "   (" + used + " / 6)", team.shirtColor, BANNER_TIME, false);
    }

    @Override
    public void onTimeout(CourtSide side, int used) {
        Team team = team(side);
        banner("TIMEOUT  " + team.shortName, "Timeout " + used + " of 2   ·   both teams at the bench   ·   Space: skip", team.shirtColor,
                3.5f, false);
    }

    private void banner(String title, String text, Color color, float duration, boolean big) {
        bannerTitle = title;
        bannerText = text;
        bannerColor = color;
        bannerStart = clock;
        bannerDuration = duration;
        bannerBig = big;
    }

    // ------------------------------------------------------------------ drawing

    public void render(MatchController match, float dt) {
        clock += dt;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (match != null) {
            MatchScore score = match.rules().score();
            for (CourtSide side : CourtSide.values()) {
                int i = side.ordinal();
                int points = score.points(side);
                if (shownPoints[i] >= 0 && points != shownPoints[i]) {
                    previousPoints[i] = shownPoints[i];
                    changedAt[i] = clock;
                }
                shownPoints[i] = points;
            }
            drawScoreBug(match, score);
        }
        drawBanner();
        drawSetResult();
        drawActions(dt);
    }

    private void begin() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
    }

    private void drawScoreBug(MatchController match, MatchScore score) {
        float headerH = 26f * s;
        float rowH = 40f * s;
        float stripe = 6f * s;
        float nameW = 84f * s;
        float serveW = 20f * s;
        float setsW = 34f * s;
        float pointsW = 60f * s;
        float pad = 6f * s;
        float bugW = pad + stripe + nameW + serveW + setsW + pointsW + pad;
        float x0 = width - bugW - 18f * s;
        float top = height - 16f * s;
        float rowsTop = top - headerH;
        RallyManager rally = match.rules().rally();
        CourtSide serving = match.rules().servingSide();
        CourtSide setPoint = setPointSide(score);
        boolean waitingServe = rally.state() == RallyManager.State.WAITING_SERVE && !world.ball().isActive();
        float bugH = headerH + 2f * rowH + pad;

        begin();
        UiKit.panel(shapes, x0, top - bugH, bugW, bugH, 0.94f, 0f);
        float pointsX = x0 + pad + stripe + nameW + serveW + setsW;
        for (CourtSide side : CourtSide.values()) {
            int i = side.ordinal();
            float rowY = rowsTop - (i + 1) * rowH;
            Team team = team(side);
            shapes.setColor(team.shirtColor);
            shapes.rect(x0 + pad, rowY + 5f * s, stripe, rowH - 10f * s);
            // The points cell: a raised surface, briefly blue when a point is won.
            float flash = Math.max(0f, 1f - (clock - changedAt[i]) / FLASH_TIME);
            Color cell = cellColor.set(UiKit.DEEP).lerp(UiKit.PRIMARY, flash);
            UiKit.roundedRect(shapes, pointsX, rowY + 3f * s, pointsW, rowH - 6f * s, UiKit.RADIUS * s, cell);
            // Touches of the current possession: three dots under the name.
            int touches = rally.touches(side);
            for (int k = 0; k < 3; k++) {
                shapes.setColor(k < touches ? UiKit.ACCENT : UiKit.BORDER);
                shapes.circle(x0 + pad + stripe + 10f * s + k * 9f * s, rowY + 7f * s, 2.6f * s, 10);
            }
            if (side == serving && !score.isMatchOver()) {
                float cx = x0 + pad + stripe + nameW + serveW / 2f;
                shapes.setColor(UiKit.TEXT);
                shapes.circle(cx, rowY + rowH / 2f, 4.5f * s, 16);
            }
        }
        shapes.setColor(UiKit.BORDER);
        shapes.rect(x0 + pad, rowsTop - rowH, bugW - 2f * pad, 1f);
        float below = top - bugH - 6f * s;
        if (setPoint != null && !score.isMatchOver()) {
            UiKit.roundedRect(shapes, x0, below - 24f * s, bugW, 24f * s, UiKit.RADIUS * s, UiKit.PRIMARY);
            below -= 30f * s;
        }
        if (waitingServe) {
            float limit = match.rules().format().serveTimeLimit();
            float left = Math.max(0f, 1f - (float) rally.elapsed(world.time()) / limit);
            UiKit.bar(shapes, x0, below - 5f * s, bugW, 4f * s, left, left > 0.3f ? UiKit.GREEN : UiKit.RED, 1f);
        }
        shapes.end();

        batch.begin();
        BitmapFont header = fonts.get(UiFonts.Weight.EXTRABOLD, 12f * s);
        BitmapFont history = fonts.get(UiFonts.Weight.SEMIBOLD, 12f * s);
        BitmapFont name = fonts.get(UiFonts.Weight.EXTRABOLD, 20f * s);
        BitmapFont sets = fonts.get(UiFonts.Weight.BOLD, 19f * s);
        BitmapFont points = fonts.get(UiFonts.Weight.BLACK, 30f * s);
        BitmapFont you = fonts.get(UiFonts.Weight.EXTRABOLD, 9f * s);
        String title = score.isMatchOver() ? "FINAL" : "SET " + score.setNumber();
        UiKit.text(batch, header, title, x0 + pad + 2f * s, top - 8f * s, UiKit.TEXT_DIM, 1f);
        String hist = setHistory(score);
        if (!hist.isEmpty()) {
            float w = UiKit.width(history, hist);
            UiKit.text(batch, history, hist, x0 + bugW - pad - w, top - 8f * s, UiKit.TEXT_DIM, 1f);
        }
        for (CourtSide side : CourtSide.values()) {
            int i = side.ordinal();
            float rowY = rowsTop - (i + 1) * rowH;
            Team team = team(side);
            float nameX = x0 + pad + stripe + 8f * s;
            UiKit.text(batch, name, team.shortName, nameX, rowY + rowH - 9f * s, UiKit.TEXT, 1f);
            if (side == yourSide) {
                float w = UiKit.width(name, team.shortName);
                UiKit.text(batch, you, "YOU", nameX + w + 5f * s, rowY + rowH - 11f * s, UiKit.JADE, 1f);
            }
            UiKit.text(batch, sets, String.valueOf(score.sets(side)), x0 + pad + stripe + nameW + serveW,
                    rowY + rowH / 2f + sets.getCapHeight() / 2f, setsW, Align.center, false, UiKit.TEXT_DIM, 1f);
            // The points roll: the old number slides up and fades, the new one comes in from below.
            float t = UiKit.ease((clock - changedAt[i]) / ROLL_TIME);
            float cy = rowY + rowH / 2f + points.getCapHeight() / 2f;
            float travel = rowH * 0.45f;
            Color numberColor = UiKit.TEXT;
            if (t < 1f) {
                UiKit.text(batch, points, String.valueOf(previousPoints[i]), pointsX, cy + t * travel, pointsW, Align.center, false,
                        numberColor, 1f - t);
            }
            UiKit.text(batch, points, String.valueOf(shownPoints[i]), pointsX, cy - (1f - t) * travel, pointsW, Align.center, false,
                    numberColor, t);
        }
        if (setPoint != null && !score.isMatchOver()) {
            boolean matchPoint = score.sets(setPoint) == score.format().setsToWin() - 1;
            UiKit.text(batch, header, (matchPoint ? "MATCH POINT " : "SET POINT ") + team(setPoint).shortName, x0,
                    top - bugH - 6f * s - 12f * s + header.getCapHeight() / 2f, bugW, Align.center, false, UiKit.TEXT, 1f);
        }
        batch.end();
    }

    private void drawBanner() {
        if (bannerTitle == null) return;
        float age = clock - bannerStart;
        if (age > bannerDuration) return;
        float in = UiKit.ease(age / 0.25f);
        float out = bannerDuration > 1e5f ? 1f : Math.min(1f, (bannerDuration - age) / 0.35f);
        float alpha = Math.min(in, out);
        float panelW = Math.min(width - 40f, (bannerBig ? 760f : 600f) * s);
        float panelH = (bannerBig ? 96f : 72f) * s;
        float x = (width - panelW) / 2f;
        // Lower third, like a TV caption: clear of the player's card and the score bug.
        float y = (bannerBig ? Math.max(height * 0.2f, captionBase()) : captionBase()) - (1f - in) * 24f * s;

        begin();
        UiKit.panel(shapes, x, y, panelW, panelH, 0.94f * alpha, 0f);
        shapes.setColor(bannerColor.r, bannerColor.g, bannerColor.b, alpha);
        shapes.rect(x + 1f, y + panelH - 3f * s, panelW - 2f, 2f * s);
        shapes.end();

        batch.begin();
        BitmapFont title = fonts.get(UiFonts.Weight.BLACK, (bannerBig ? 34f : 26f) * s);
        BitmapFont text = fonts.get(UiFonts.Weight.MEDIUM, 15f * s);
        UiKit.text(batch, title, bannerTitle, x, y + panelH * 0.66f + title.getCapHeight() / 2f, panelW, Align.center, false, UiKit.TEXT, alpha);
        UiKit.text(batch, text, bannerText, x, y + panelH * 0.24f + text.getCapHeight() / 2f, panelW, Align.center, false,
                UiKit.TEXT_DIM, alpha);
        batch.end();
    }

    /** Between sets: the set's winner and score, the table of all sets and the countdown. */
    private void drawSetResult() {
        if (setResult == null) return;
        float age = clock - setResultAt;
        float duration = MatchController.SET_BREAK;
        if (age > duration) return;
        float a = Math.min(UiKit.ease(age / 0.3f), Math.min(1f, (duration - age) / 0.35f));
        CourtSide winnerSide = setResult.winner();
        Team winner = team(winnerSide);
        int columns = setsSoFar.size();
        float cellW = 54f * s;
        float nameW = 120f * s;
        float tableW = nameW + columns * cellW + 70f * s;
        float w = Math.max(520f * s, tableW + 60f * s);
        float h = 300f * s;
        float x = (width - w) / 2f;
        float scaleIn = 0.96f + 0.04f * UiKit.ease(age / 0.3f);
        float y = height * 0.5f - h * 0.35f;

        BitmapFont label = fonts.get(UiFonts.Weight.EXTRABOLD, 13f * s);
        BitmapFont title = fonts.get(UiFonts.Weight.BLACK, 26f * s * scaleIn);
        BitmapFont big = fonts.get(UiFonts.Weight.BLACK, 54f * s * scaleIn);
        BitmapFont cell = fonts.get(UiFonts.Weight.BOLD, 17f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 13f * s);

        float tableX = x + (w - tableW) / 2f;
        float rowH = 30f * s;
        float tableTop = y + 30f * s + 3f * rowH;

        begin();
        UiKit.dim(shapes, width, height, 0.35f * a);
        UiKit.panel(shapes, x, y, w, h, 0.96f * a, 0f);
        shapes.setColor(winner.shirtColor.r, winner.shirtColor.g, winner.shirtColor.b, a);
        shapes.rect(x + 1f, y + h - 3f * s, w - 2f, 2f * s);
        // Table rows: the team colour chips.
        for (CourtSide side : CourtSide.values()) {
            float ry = tableTop - (side.ordinal() + 2) * rowH;
            Team t = team(side);
            shapes.setColor(t.shirtColor.r, t.shirtColor.g, t.shirtColor.b, a);
            shapes.rect(tableX, ry + 7f * s, 4f * s, rowH - 14f * s);
            if (side == CourtSide.HOME) {
                shapes.setColor(UiKit.DEEP.r, UiKit.DEEP.g, UiKit.DEEP.b, a);
                shapes.rect(tableX + 4f * s, ry, tableW - 4f * s, rowH);
            }
        }
        shapes.end();

        batch.begin();
        float ty = y + h - 26f * s;
        UiKit.text(batch, label, "SET " + setResult.setNumber(), x, ty, w, Align.center, false, UiKit.TEXT_DIM, a);
        ty -= 26f * s;
        UiKit.text(batch, title, Lang.f("%s win the set", winner.name.toUpperCase(java.util.Locale.ROOT)), x, ty, w, Align.center, false, UiKit.TEXT, a);
        ty -= 42f * s;
        String scoreText = setResult.home() + " : " + setResult.away();
        UiKit.text(batch, big, scoreText, x, ty, w, Align.center, false, UiKit.TEXT, a);
        // The table: sets as columns.
        float headY = tableTop - rowH + rowH / 2f + small.getCapHeight() / 2f;
        for (int c = 0; c < columns; c++) {
            UiKit.text(batch, small, String.valueOf(c + 1), tableX + nameW + c * cellW, headY, cellW, Align.center, false, UiKit.TEXT_FAINT, a);
        }
        UiKit.text(batch, small, "SETS WON", tableX + nameW + columns * cellW, headY, 70f * s, Align.center, false, UiKit.TEXT_FAINT, a);
        for (CourtSide side : CourtSide.values()) {
            float ry = tableTop - (side.ordinal() + 2) * rowH;
            float cy = ry + rowH / 2f + cell.getCapHeight() / 2f;
            Team t = team(side);
            UiKit.text(batch, cell, t.shortName, tableX + 14f * s, cy, side == yourSide ? UiKit.JADE : UiKit.TEXT, a);
            for (int c = 0; c < columns; c++) {
                MatchScore.SetResult r = setsSoFar.get(c);
                int pts = side == CourtSide.HOME ? r.home() : r.away();
                boolean won = r.winner() == side;
                UiKit.text(batch, cell, String.valueOf(pts), tableX + nameW + c * cellW, cy, cellW, Align.center, false,
                        won ? UiKit.TEXT : UiKit.TEXT_FAINT, a);
            }
            int setsWon = side == CourtSide.HOME ? setsHome : setsAway;
            UiKit.text(batch, cell, String.valueOf(setsWon), tableX + nameW + columns * cellW, cy, 70f * s, Align.center, false,
                    UiKit.JADE, a);
        }
        int left = Math.max(1, (int) Math.ceil(duration - age));
        UiKit.text(batch, small, Lang.f("Next set in %d", left), x, y + 22f * s, w, Align.center, false, UiKit.TEXT_DIM, a);
        batch.end();
    }

    /** The action strip: the title (if any) and the keys of what can be done now. */
    private void drawActions(float dt) {
        boolean has = contextTitle != null || !contextKeys.isEmpty();
        contextAlpha = has ? Math.min(1f, contextAlpha + dt / 0.15f) : Math.max(0f, contextAlpha - dt / 0.15f);
        if (contextAlpha <= 0f || !has) return;
        float a = contextAlpha * Math.min(1f, 0.4f + (clock - contextChangedAt) / 0.2f);
        BitmapFont titleFont = fonts.get(UiFonts.Weight.BOLD, 14f * s);
        BitmapFont key = fonts.get(UiFonts.Weight.EXTRABOLD, 12f * s);
        BitmapFont label = fonts.get(UiFonts.Weight.SEMIBOLD, 14f * s);
        float capH = 24f * s;
        float gap = 18f * s;
        float keysW = 0f;
        for (String[] k : contextKeys) keysW += UiKit.keycapWidth(key, k[0]) + 7f * s + UiKit.width(label, k[1]) + gap;
        keysW = Math.max(0f, keysW - gap);
        float titleW = contextTitle == null ? 0f : UiKit.width(titleFont, contextTitle);
        float pad = 16f * s;
        float w = Math.max(keysW, titleW) + 2f * pad;
        float h = (contextKeys.isEmpty() ? 0f : capH + 8f * s) + (contextTitle == null ? 0f : 22f * s) + 16f * s;
        float x = (width - w) / 2f;
        float y = captionBase() + 82f * s;
        begin();
        UiKit.panel(shapes, x, y, w, h, 0.88f * a, 0f);
        shapes.setColor(UiKit.PRIMARY.r, UiKit.PRIMARY.g, UiKit.PRIMARY.b, a);
        shapes.rect(x, y, 3f * s, h);
        float kx = (width - keysW) / 2f;
        float ky = y + 8f * s;
        for (String[] k : contextKeys) {
            float kw = UiKit.keycapWidth(key, k[0]);
            UiKit.keycap(shapes, kx, ky, kw, capH, false, a);
            kx += kw + 7f * s + UiKit.width(label, k[1]) + gap;
        }
        shapes.end();
        batch.begin();
        if (contextTitle != null) {
            UiKit.text(batch, titleFont, contextTitle, x, y + h - 10f * s, w, Align.center, false, UiKit.JADE, a);
        }
        kx = (width - keysW) / 2f;
        for (String[] k : contextKeys) {
            float kw = UiKit.keycapLabel(batch, key, k[0], kx, ky, capH, false, a);
            UiKit.text(batch, label, k[1], kx + kw + 7f * s, ky + capH / 2f + label.getCapHeight() / 2f, UiKit.TEXT, a);
            kx += kw + 7f * s + UiKit.width(label, k[1]) + gap;
        }
        batch.end();
    }

    /** The side that can win the current set with the next point, or null. */
    private static CourtSide setPointSide(MatchScore score) {
        if (score.isMatchOver()) return null;
        if (score.isSetPoint(CourtSide.HOME)) return CourtSide.HOME;
        if (score.isSetPoint(CourtSide.AWAY)) return CourtSide.AWAY;
        return null;
    }

    private static String setHistory(MatchScore score) {
        StringBuilder text = new StringBuilder();
        for (MatchScore.SetResult set : score.finishedSets()) {
            if (!text.isEmpty()) text.append("  ");
            text.append(set.home()).append('-').append(set.away());
        }
        return text.toString();
    }

    private Team team(CourtSide side) {
        return side == CourtSide.HOME ? world.home() : world.away();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
