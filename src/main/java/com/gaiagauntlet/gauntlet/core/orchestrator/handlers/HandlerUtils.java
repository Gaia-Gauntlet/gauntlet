package com.gaiagauntlet.gauntlet.core.orchestrator.handlers;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.gaiagauntlet.gauntlet.core.GauntletUtils;
import com.gaiagauntlet.gauntlet.core.admin.GaiaLog;
import com.gaiagauntlet.gauntlet.core.components.PlayerComponent;
import com.gaiagauntlet.gauntlet.core.events.GauntletEvent;
import com.gaiagauntlet.gauntlet.core.events.events.GameEvent;
import com.gaiagauntlet.gauntlet.core.resources.UniverseGauntletResource;
import com.gaiagauntlet.gauntlet.core.session.components.GameSession;
import com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Bunch of utilities for the handlers so I don't have to repeat myself a
 * thousand times
 */
public class HandlerUtils {
    public static UniverseGauntletResource withResource() {
        return GauntletUtils.withResource();
    }

    @Nonnull
    public static Optional<GameSession> sessionFor(@Nonnull String id) {
        return GauntletUtils.sessionFor(id);
    }

    @Nonnull
    public static Optional<GameSession> sessionFor(@Nonnull PlayerRef player) {
        return GauntletUtils.sessionFor(player);
    }

    @Nonnull
    public static Optional<PlayerComponent> playerFor(@Nonnull PlayerRef player) {
        return GauntletUtils.playerFor(player);
    }

    public class Resolve {
        public static void error(GauntletEvent evt, GameSession session, Message mes) {
            evt.complete(
                    GaiaLog.atError().withSession(session)
                            .log(mes.param("sessionId", session.getId()).param("gameId", session.getCurrentGame())));
        }

        public static void error(GauntletEvent evt, Message mes) {
            evt.complete(
                    GaiaLog.atError()
                            .log(mes));
        }

        public static void error(GauntletEvent evt, GameSession session, String key) {
            error(evt, session, MessageUtils.msg(key));
        }

        public static void success(GauntletEvent evt, String key) {
            evt.complete(
                    GaiaLog.atError()
                            .log(key));
        }

        public static void success(GauntletEvent evt, GameSession session, Message mes) {
            evt.complete(
                    GaiaLog.atInfo().withSession(session)
                            .log(mes.param("sessionId", session.getId()).param("gameId", session.getCurrentGame())));
        }
        public static void log(GauntletEvent evt, GameSession session, Message mes) {
            evt.log(
                    GaiaLog.atInfo().withSession(session)
                            .log(mes.param("sessionId", session.getId()).param("gameId", session.getCurrentGame())));
        }
        public static void log(GauntletEvent evt, GameSession session, String key) {
            log(evt, session, MessageUtils.msg(key));
        }

        public static void success(GauntletEvent evt, GameSession session, String key) {
            success(evt, session, MessageUtils.msg(key));
        }
    }
}
