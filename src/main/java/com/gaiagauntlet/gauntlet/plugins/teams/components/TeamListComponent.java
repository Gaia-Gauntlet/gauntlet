package com.gaiagauntlet.gauntlet.plugins.teams.components;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import org.jetbrains.annotations.NotNull;
import com.gaiagauntlet.gauntlet.core.admin.AdminLog;
import com.gaiagauntlet.gauntlet.core.admin.GaiaLog;
import com.gaiagauntlet.gauntlet.core.session.components.SessionComponent;
import com.gaiagauntlet.gauntlet.core.session.components.SessionComponentType;
import com.gaiagauntlet.gauntlet.plugins.gamestore.components.GameComponent;
import com.gaiagauntlet.gauntlet.plugins.gamestore.components.GameComponentType;
import com.gaiagauntlet.gauntlet.utils.PlayerUtils;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import lombok.Getter;
import lombok.Setter;

public final class TeamListComponent implements SessionComponent, GameComponent {
    @Nonnull
    public static final String ID = "TeamListComponent";

    @Getter
    @Setter
    private static GameComponentType<TeamListComponent> gameComponentType;
    @Getter
    @Setter
    private static SessionComponentType<TeamListComponent> sessionComponentType;

    public static final BuilderCodec<@NotNull TeamListComponent> CODEC = AssetBuilderCodec
            .builder(
                    TeamListComponent.class,
                    TeamListComponent::new)
            .append(new KeyedCodec<>("TeamList", new MapCodec<>(TeamComponent.CODEC, ConcurrentHashMap::new)),
                    (team, v) -> team.teamList = v,
                    team -> team.teamList)
            .documentation("The full list of teams in this session.")
            .add()
            .append(new KeyedCodec<>("TeamSize", Codec.INTEGER),
                    TeamListComponent::setTeamSize, TeamListComponent::getTeamSize)
            .documentation("Whether team distribution should respect player defined parties.")
            .add()
            .append(new KeyedCodec<>("RespectParties", Codec.BOOLEAN),
                    TeamListComponent::setRespectParties, TeamListComponent::isRespectParties)
            .documentation("Whether team distribution should respect player defined parties.")
            .add()
            .afterDecode((teams) -> {

                // wipe the map before rebuilding it
                teams.playerToTeam.clear();

                // iterate over every player of every team
                for (var teamEntry : teams.getTeams().entrySet()) {
                    for (var player : teamEntry.getValue().getPlayers()) {
                        // Resynchronizes the teams
                        var prev = teams.playerToTeam.put(player, teamEntry.getKey());
                        if (prev != null) {
                            // the player is on two teams - whoops - not much to be done about that though
                            // other than cry
                            GaiaLog.atWarning()
                                    .log("Player " + PlayerUtils.resolveOnline(player) + " is on both team " + prev
                                            + " and team " + teamEntry.getKey());
                            teams.remove(player, prev);
                        }
                    }
                }
            })
            .build();

    @Nonnull
    private Map<String, TeamComponent> teamList = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToTeam = new ConcurrentHashMap<>();
    @Setter
    @Getter
    private int teamSize;
    @Setter
    @Getter
    private boolean respectParties;

    public Map<String, TeamComponent> getTeams() {
        return teamList;
    }

    public void addTeam(String teamId, TeamComponent team) {
        // puts the team
        var existing = teamList.put(teamId, team);
        // if there was an existing team with the same id, remove all the players from
        // the lookup table
        if (existing != null) {
            for (var player : existing.getPlayers()) {
                playerToTeam.remove(player);
            }
        }

        // add the players in the new team to the lookup table
        for (var player : team.getPlayers()) {
            var prev = playerToTeam.put(player, team.getId());
            if (prev != null) { // if a new player is already in a team, remove them from that team
                remove(player, prev);
            }
        }
    }

    @Nullable
    public TeamComponent get(String teamId) {
        return teamList.get(teamId);
    }

    @Nullable
    public TeamComponent get(UUID player) {
        var teamId = playerToTeam.get(player);
        if (teamId == null) {
            // ik we spend so much time making the map, but go ahead and manually search
            // just in case someone DIDN'T se the teamList to add a player to a team >.>
            for (var teamEntry : teamList.entrySet()) {
                if (teamEntry.getValue().contains(player)) {
                    put(player, teamEntry.getKey());
                    return teamEntry.getValue();
                }
            }
            return null; // player is for sure not here
        }
        return get(teamId);
    }

    /**
     * Puts a player on a team, returning the previous team
     */
    @Nullable
    public String put(UUID player, String teamId) {
        var team = get(teamId);
        if (team == null)
            return null; // new team does not exist
        var existing = playerToTeam.put(player, teamId);
        if (teamId.equals(existing))
            return null; // player already on the team
        if (existing != null) {
            remove(player, existing);
        }

        team.add(player);
        return existing;
    }

    public void remove(UUID player, String teamId) {
        var existingTeam = playerToTeam.get(player);
        if (existingTeam != null && existingTeam.equals(teamId)) {
            playerToTeam.remove(player);
        }

        var team = get(teamId);
        if (team == null)
            return;

        team.remove(player);
    }

    public Set<UUID> getPlayers() {
        return playerToTeam.keySet();
    }

    // add more here, since this is not enough
}
