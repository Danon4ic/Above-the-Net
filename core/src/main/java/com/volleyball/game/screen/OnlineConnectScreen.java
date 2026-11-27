package com.volleyball.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.volleyball.game.VolleyballGame;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.i18n.Lang;
import com.volleyball.game.net.MatchLink;
import com.volleyball.game.net.UdpTransport;
import com.volleyball.game.ui.UiFonts;
import com.volleyball.game.ui.UiKit;

/** Connection feedback during the join handshake, including readable version and timeout errors. */
public final class OnlineConnectScreen extends ScreenAdapter {
    private final VolleyballGame game;
    private final String endpoint;
    private MatchLink link;
    private SpriteBatch batch;
    private String error;

    public OnlineConnectScreen(VolleyballGame game, String endpoint, PlayerRole role) {
        this.game = game;
        this.endpoint = endpoint;
        try {
            String[] address = splitEndpoint(endpoint);
            link = MatchLink.client(UdpTransport.connect(address[0], Integer.parseInt(address[1])), role);
        } catch (IllegalArgumentException | java.io.UncheckedIOException e) {
            error = "Could not connect to the address";
        }
    }

    private OnlineConnectScreen(VolleyballGame game, String error) {
        this.game = game;
        this.error = error;
        endpoint = "";
    }

    public static OnlineConnectScreen error(VolleyballGame game, String text) { return new OnlineConnectScreen(game, text); }

    static String[] splitEndpoint(String endpoint) {
        int colon = endpoint.lastIndexOf(':');
        String host = endpoint;
        int port = UdpTransport.DEFAULT_PORT;
        if (endpoint.startsWith("[")) {
            int close = endpoint.indexOf(']');
            if (close < 0) throw new IllegalArgumentException("IPv6 brackets");
            host = endpoint.substring(1, close);
            if (endpoint.length() > close + 1) port = Integer.parseInt(endpoint.substring(close + 2));
        } else if (colon >= 0 && colon == endpoint.indexOf(':')) {
            host = endpoint.substring(0, colon);
            port = Integer.parseInt(endpoint.substring(colon + 1));
        }
        if (host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("endpoint");
        return new String[]{host, Integer.toString(port)};
    }

    @Override public void show() {
        batch = new SpriteBatch();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override public void resize(int width, int height) {
        if (batch != null) batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.showMainMenu();
            return;
        }
        if (link != null) {
            link.exchange(System.nanoTime());
            if (link.setup() != null) {
                game.enterOnline(link);
                link = null; // MatchScreen owns it from here.
                return;
            }
            if (link.status() == MatchLink.Status.INCOMPATIBLE) error = "Incompatible game version";
            if (link.status() == MatchLink.Status.ENDED) error = "Connection timed out or the host is busy";
        }
        Gdx.gl.glClearColor(0.045f, 0.057f, 0.087f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        var headline = game.fonts().get(UiFonts.Weight.BOLD, 28f);
        var detail = game.fonts().get(UiFonts.Weight.MEDIUM, 16f);
        batch.begin();
        UiKit.text(batch, headline, Lang.t(error == null ? "Connecting to host" : error),
                12, h / 2f + 25, w - 24, Align.center, false, UiKit.JADE, 1f);
        UiKit.text(batch, detail, UiKit.fit(detail, endpoint, w - 32f), 12, h / 2f - 12, w - 24,
                Align.center, false, com.badlogic.gdx.graphics.Color.WHITE, 1f);
        UiKit.text(batch, detail, Lang.t("Esc · Main menu"), 12, h / 2f - 55, w - 24,
                Align.center, false, com.badlogic.gdx.graphics.Color.WHITE, 1f);
        batch.end();
    }

    @Override public void dispose() {
        if (link != null) link.close();
        if (batch != null) batch.dispose();
    }
}
