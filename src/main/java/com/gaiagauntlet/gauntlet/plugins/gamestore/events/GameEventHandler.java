package com.gaiagauntlet.gauntlet.plugins.gamestore.events;

import java.util.ArrayList;

import com.gaiagauntlet.gauntlet.core.GauntletUtils;
import com.gaiagauntlet.gauntlet.core.admin.GaiaLog;
import com.gaiagauntlet.gauntlet.core.events.events.GameEndEvent;
import com.gaiagauntlet.gauntlet.core.games.interfaces.GameController;
import com.gaiagauntlet.gauntlet.core.games.registries.GameRegistry;
import com.gaiagauntlet.gauntlet.core.session.components.GameSession;
import com.gaiagauntlet.gauntlet.plugins.gamestore.GameStorePlugin;
import com.gaiagauntlet.gauntlet.plugins.gamestore.interfaces.PersistentGamePlugin;
import com.gaiagauntlet.gauntlet.plugins.gamestore.interfaces.SessionWriter;
import com.gaiagauntlet.gauntlet.plugins.gamestore.utils.GameStore;
import com.hypixel.hytale.logger.HytaleLogger;

public class GameEventHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void GameEndHandler(GameEndEvent evt) {
        var gameId = evt.getGameId();
        if (!(GameRegistry.getGame(gameId).orElse(null) instanceof GameController gameController)) {
            return;
        }
        var requiredPlugins = gameController.getRequiredPlugins();
        var requred = requiredPlugins.contains(GameStorePlugin.ID);
        if (!requred)
            return; // not required for this game, do nothing

        var sessionId = evt.getSessionId();

        if (!(GauntletUtils.sessionFor(sessionId).orElse(null) instanceof GameSession session))
            return; // session does not exist - will be handled later

        var persistentPlugins = GameRegistry.getPlugins(requiredPlugins, PersistentGamePlugin.class);

        var gameWorld = evt.getGameWorld();

        evt.defer(GauntletUtils.runAsync(gameWorld, () -> {
            var writes = new ArrayList<SessionWriter>();
            var gameEcs = GameStore.ensureStore(gameWorld, sessionId);
            for (var plugin : persistentPlugins) {
                try {
                    writes.add(plugin.capture(gameWorld, gameEcs, sessionId));
                } catch (Exception e) {
                    evt.log(GaiaLog.atWarning().withSession(session).withCause(e)
                            .log(plugin.getId() + " plugin failed to read state from " + gameId + " with error "
                                    + e.getLocalizedMessage()));
                }
            }
            return writes;
        }).thenCompose(sessionWrites -> GauntletUtils.runAsync(GauntletUtils.withHubWorld(), () -> {
            for (var writer : sessionWrites) {
                try {
                    writer.apply(session);
                } catch (Exception e) {
                    evt.log(GaiaLog.atWarning().withSession(session).withCause(e)
                            .log("Failed to write session from " + gameId + " with error "
                                    + e.getLocalizedMessage()));
                }
            }
        })));
        evt.complete(GaiaLog.atInfo().withSession(session).log("Finished writing games to session"));
    }
}
