package com.volleyball.game.simulation;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.CourtZones;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the teams of the {@link TeamCatalog}. Line-up is the classic 5-1 order:
 * setter in zone 1, opposite in 4, outside hitters in 2 and 5, middle blockers in 3 and 6.
 */
public final class TeamFactory {
    /** Zone of each line-up slot in the starting rotation. */
    private static final int[] START_ZONES = {1, 2, 3, 4, 5, 6};
    private static final PlayerRole[] START_ROLES = {
            PlayerRole.SETTER, PlayerRole.OUTSIDE_HITTER, PlayerRole.MIDDLE_BLOCKER,
            PlayerRole.OPPOSITE, PlayerRole.OUTSIDE_HITTER, PlayerRole.MIDDLE_BLOCKER};

    private TeamFactory() {}

    public static Team home(int firstPlayerId) {
        return build(TeamCatalog.defaultHome(), CourtSide.HOME, firstPlayerId, TeamCatalog.defaultAway());
    }

    public static Team away(int firstPlayerId) {
        return build(TeamCatalog.defaultAway(), CourtSide.AWAY, firstPlayerId, TeamCatalog.defaultHome());
    }

    /**
     * Builds a club of the catalog on {@code side}: its kit (the away kit when it would look like
     * the opponent's), its players with the club's strength added to their ratings, their traits
     * (always the same for the same club) and its coach.
     */
    public static Team build(TeamCatalog.Entry entry, CourtSide side, int firstPlayerId, TeamCatalog.Entry opponent) {
        return build(entry, side, firstPlayerId, opponent.shirt(), false);
    }

    /**
     * A club for a match against an opponent wearing {@code opponentShirt}; {@code awayKit} puts
     * it in its away kit (otherwise the visitors change only when the kits clash).
     */
    public static Team build(TeamCatalog.Entry entry, CourtSide side, int firstPlayerId, Color opponentShirt, boolean awayKit) {
        Color[] kit = entry.kitAgainst(opponentShirt, side == CourtSide.HOME, awayKit);
        Team team = new Team(entry.name(), entry.shortName(), side, new Color(kit[0]), new Color(kit[1]));
        team.chooseLiberoShirt(opponentShirt);
        team.setCoach(entry.coach(), entry.style());
        fill(team, firstPlayerId, entry.starters(), entry.numbers());
        addLibero(team, firstPlayerId + LIBERO_ID_OFFSET, entry.libero(), entry.liberoNumber());
        addSubstitutes(team, firstPlayerId, entry.subs(), entry.subNumbers());
        Random random = new Random(entry.id().hashCode() * 31L + 7L);
        for (VolleyballPlayer p : team.squad()) {
            strengthen(p.attributes, entry.strength());
            giveTraits(p, entry.focus(), random);
        }
        // Left-handers: drawn separately so the traits stay what they were.
        Random hands = new Random(entry.id().hashCode() * 17L + 3L);
        for (VolleyballPlayer p : team.squad()) {
            if (p.role == PlayerRole.LIBERO) continue;
            float chance = p.role == PlayerRole.OPPOSITE ? 0.35f : 0.1f;
            p.setLeftHanded(hands.nextFloat() < chance);
        }
        return team;
    }

    private static void strengthen(PlayerAttributes a, int strength) {
        if (strength == 0) return;
        a.speed += strength / 2;
        a.acceleration += strength / 2;
        a.jump += strength;
        a.attackPower += strength;
        a.attackAccuracy += strength;
        a.receiving += strength;
        a.setting += strength;
        a.reaction += strength;
        a.blocking += strength;
        a.defense += strength;
        a.serving += strength;
        a.tactics += strength;
        a.composure += strength;
        a.sanitize();
    }

    /**
     * One trait from the role's list (the club's own traits more often), and for about half of the
     * players a second one that does not contradict the first.
     */
    static void giveTraits(VolleyballPlayer p, List<PlayerTrait> focus, Random random) {
        List<PlayerTrait> options = PlayerTrait.forRole(p.role);
        List<PlayerTrait> preferred = new ArrayList<>();
        for (PlayerTrait t : focus) if (options.contains(t)) preferred.add(t);
        PlayerTrait first = !preferred.isEmpty() && random.nextFloat() < 0.55f
                ? preferred.get(random.nextInt(preferred.size()))
                : options.get(random.nextInt(options.size()));
        p.addTrait(first);
        if (random.nextFloat() < 0.5f) {
            List<PlayerTrait> second = new ArrayList<>();
            for (PlayerTrait t : PlayerTrait.GENERAL) if (!t.clashes(first) && allowed(p, t)) second.add(t);
            if (!second.isEmpty()) p.addTrait(second.get(random.nextInt(second.size())));
        }
    }

    /** A libero neither serves nor attacks: no serving traits and no hothead. */
    private static boolean allowed(VolleyballPlayer p, PlayerTrait t) {
        boolean serving = t == PlayerTrait.POWER_SERVER || t == PlayerTrait.FLOAT_SPECIALIST;
        return !(p.role == PlayerRole.LIBERO && (serving || t == PlayerTrait.HOTHEAD));
    }

    /**
     * Id of a team's libero relative to its first player: home(0) and away(6) get 12 and 18,
     * clear of both line-ups.
     */
    public static final int LIBERO_ID_OFFSET = 12;

    private static void addLibero(Team team, int id, String name, int number) {
        VolleyballPlayer libero = new VolleyballPlayer(id, name, number, PlayerRole.LIBERO, team.side,
                heightFor(PlayerRole.LIBERO, 0), attributesFor(PlayerRole.LIBERO, 0));
        Vector2 seat = CourtZones.benchPosition(team.side, 0, new Vector2());
        libero.sitOnBench(seat.x, seat.y);
        team.setLibero(libero);
    }

    /** Roles of the substitutes: a second setter (for 4-2 / 6-2), an outside hitter, a middle, an opposite. */
    static final PlayerRole[] SUBSTITUTE_ROLES = {
            PlayerRole.SETTER, PlayerRole.OUTSIDE_HITTER, PlayerRole.MIDDLE_BLOCKER, PlayerRole.OPPOSITE};
    /** Substitutes' ids follow the libero: home(0) gets 13..16, away(6) 19..22. */
    public static final int SUBSTITUTE_ID_OFFSET = LIBERO_ID_OFFSET + 1;

    private static void addSubstitutes(Team team, int firstId, String[] names, int[] numbers) {
        for (int i = 0; i < SUBSTITUTE_ROLES.length; i++) {
            PlayerRole role = SUBSTITUTE_ROLES[i];
            PlayerAttributes a = attributesFor(role, i + 1);
            // Bench players are a little weaker than the starters.
            a.receiving -= 4;
            a.attackPower -= 4;
            a.setting -= role == PlayerRole.SETTER ? 6 : 0;
            VolleyballPlayer p = new VolleyballPlayer(firstId + SUBSTITUTE_ID_OFFSET + i, names[i], numbers[i], role, team.side,
                    heightFor(role, i + 1), a.sanitize());
            Vector2 seat = CourtZones.benchPosition(team.side, i + 1, new Vector2());
            p.sitOnBench(seat.x, seat.y);
            team.addSubstitute(p);
        }
    }

    private static void fill(Team team, int firstId, String[] names, int[] numbers) {
        Vector2 spot = new Vector2();
        for (int i = 0; i < START_ROLES.length; i++) {
            PlayerRole role = START_ROLES[i];
            VolleyballPlayer player = new VolleyballPlayer(firstId + i, names[i], numbers[i], role, team.side,
                    heightFor(role, i), attributesFor(role, i));
            CourtZones.basePosition(START_ZONES[i], team.side, spot);
            player.placeAt(spot.x, spot.y);
            team.addPlayer(player);
        }
    }

    static float heightFor(PlayerRole role, int variation) {
        float base = switch (role) {
            case MIDDLE_BLOCKER -> 2.03f;
            case OPPOSITE -> 2.00f;
            case OUTSIDE_HITTER -> 1.96f;
            case SETTER -> 1.91f;
            case LIBERO -> 1.84f;
        };
        return base + (variation % 3 - 1) * 0.02f;
    }

    /** Role profiles following the role descriptions: middles jump and block, setters set, etc. */
    static PlayerAttributes attributesFor(PlayerRole role, int variation) {
        PlayerAttributes a = new PlayerAttributes();
        a.speed = 68;
        a.acceleration = 68;
        a.jump = 66;
        a.attackPower = 60;
        a.attackAccuracy = 60;
        a.receiving = 55;
        a.setting = 45;
        a.reaction = 62;
        a.blocking = 55;
        a.defense = 55;
        a.serving = 62;
        a.stamina = 70;
        a.tactics = 60;
        a.composure = 60;
        switch (role) {
            case SETTER -> {
                a.setting = 88;
                a.tactics = 82;
                a.speed = 74;
                a.acceleration = 76;
                a.attackPower = 45;
            }
            case OPPOSITE -> {
                a.attackPower = 88;
                a.attackAccuracy = 78;
                a.jump = 78;
                a.composure = 76;
                a.receiving = 40;
            }
            case MIDDLE_BLOCKER -> {
                a.jump = 84;
                a.blocking = 86;
                a.reaction = 74;
                a.acceleration = 76;
                a.receiving = 35;
            }
            case OUTSIDE_HITTER -> {
                a.receiving = 76;
                a.defense = 70;
                a.attackPower = 76;
                a.attackAccuracy = 72;
            }
            case LIBERO -> {
                a.receiving = 90;
                a.defense = 90;
                a.reaction = 88;
                a.speed = 80;
                a.attackPower = 20;
                a.blocking = 1;
            }
        }
        // Small individual differences so two players of the same role are not identical.
        a.speed += variation % 2 == 0 ? 3 : -3;
        a.jump += variation % 3 - 1;
        return a.sanitize();
    }
}
