package com.gaiagauntlet.gauntlet.games.EliminationZone.managers.lobby;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import com.gaiagauntlet.gauntlet.plugins.lobbycontroller.interfaces.LobbyManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

public class EZLobbyManager implements LobbyManager {

    @Override
    public CompletableFuture<World> setupWorld() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setupWorld'");
    }

    @Override
    public CompletableFuture<Void> playerTo(World lobbyWorld, PlayerRef player) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'playerTo'");
    }

    @Override
    public CompletableFuture<Void> playersTo(World lobbyWorld, Collection<PlayerRef> players) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'playersTo'");
    }
}
