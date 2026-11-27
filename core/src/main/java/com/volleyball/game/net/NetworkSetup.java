package com.volleyball.game.net;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.match.FirstServe;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.screen.MatchSetup;
import com.volleyball.game.simulation.TeamCatalog;
import com.volleyball.game.training.Drill;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.Venue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** The host's immutable lobby settings. Both peers build the same roster from catalog ids and seed. */
public record NetworkSetup(long matchId, long seed, MatchSetup match, NetCategory net, Venue venue,
                           boolean fatigue, boolean air) {
    public static final int TICKS_PER_SECOND = 120;
    // Bump the game-data revision when roster, rules or serialized animation ids change.
    static final long GAME_DATA = 0x41544e0056330001L;

    void write(ByteBuffer out) {
        out.putLong(matchId).putLong(seed).putShort((short) TICKS_PER_SECOND);
        out.put((byte) (match.players() == SessionMode.ONLINE_COOP ? 1 : 0));
        out.put((byte) match.difficulty().ordinal()).put((byte) match.system().ordinal())
                .put((byte) match.role().ordinal()).put((byte) match.firstServe().ordinal())
                .put((byte) net.ordinal()).put((byte) venue.ordinal())
                .put((byte) ((fatigue ? 1 : 0) | (air ? 2 : 0) | (match.awayKit() ? 4 : 0)));
        MatchFormat format = match.format();
        out.put((byte) format.setsToWin()).putShort((short) format.pointsPerSet())
                .putShort((short) format.decidingSetPoints()).putShort((short) format.winBy())
                .put((byte) format.mode().ordinal()).putFloat(format.serveTimeLimit());
        writeId(out, match.homeTeam());
        writeId(out, match.awayTeam());
    }

    static NetworkSetup read(ByteBuffer in) {
        long id = in.getLong();
        long seed = in.getLong();
        if (Short.toUnsignedInt(in.getShort()) != TICKS_PER_SECOND) throw new IllegalArgumentException("tick rate");
        int mode = Byte.toUnsignedInt(in.get());
        if (mode > 1) throw new IllegalArgumentException("session mode");
        Difficulty difficulty = readEnum(in, Difficulty.values());
        OffenseSystem system = readEnum(in, OffenseSystem.values());
        PlayerRole role = readEnum(in, PlayerRole.values());
        FirstServe serve = readEnum(in, FirstServe.values());
        NetCategory net = readEnum(in, NetCategory.values());
        Venue venue = readEnum(in, Venue.values());
        int flags = Byte.toUnsignedInt(in.get());
        if ((flags & ~7) != 0) throw new IllegalArgumentException("match flags");
        int sets = Byte.toUnsignedInt(in.get());
        int points = Short.toUnsignedInt(in.getShort());
        int deciding = Short.toUnsignedInt(in.getShort());
        int margin = Short.toUnsignedInt(in.getShort());
        RuleMode rules = readEnum(in, RuleMode.values());
        float time = in.getFloat();
        if (!Float.isFinite(time)) throw new IllegalArgumentException("serve time");
        String home = readId(in);
        String away = readId(in);
        if (home.equals(away) || in.hasRemaining()) throw new IllegalArgumentException("match setup");
        MatchSetup match = new MatchSetup(Drill.MATCH, false, difficulty,
                mode == 1 ? SessionMode.ONLINE_COOP : SessionMode.ONLINE_VERSUS, role,
                new MatchFormat(sets, points, deciding, margin, rules, time), system, home, away, serve, (flags & 4) != 0);
        return new NetworkSetup(id, seed, match, net, venue, (flags & 1) != 0, (flags & 2) != 0);
    }

    static <T> T readEnum(ByteBuffer in, T[] values) {
        int id = Byte.toUnsignedInt(in.get());
        if (id >= values.length) throw new IllegalArgumentException("enum id");
        return values[id];
    }

    private static void writeId(ByteBuffer out, String id) {
        byte[] bytes = id.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length == 0 || bytes.length > 48) throw new IllegalArgumentException("team id");
        out.put((byte) bytes.length).put(bytes);
    }

    private static String readId(ByteBuffer in) {
        int count = Byte.toUnsignedInt(in.get());
        if (count == 0 || count > 48) throw new IllegalArgumentException("team id");
        byte[] bytes = new byte[count];
        in.get(bytes);
        String id = new String(bytes, StandardCharsets.US_ASCII);
        if (TeamCatalog.byId(id) == null) throw new IllegalArgumentException("unknown team");
        return id;
    }
}
