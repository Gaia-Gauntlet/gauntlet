package com.gaiagauntlet.gauntlet.core.orchestrator.handlers;

import java.util.concurrent.TimeUnit;

import com.gaiagauntlet.gauntlet.core.admin.GaiaLog;
import com.gaiagauntlet.gauntlet.core.events.GauntletEventRegistry;
import com.gaiagauntlet.gauntlet.core.events.events.GameEndEvent;
import com.gaiagauntlet.gauntlet.core.events.events.GameEvent;
import com.gaiagauntlet.gauntlet.core.events.events.SessionEvent;
import com.gaiagauntlet.gauntlet.core.events.events.SessionEvent.SessionOperation;
import com.gaiagauntlet.gauntlet.core.session.components.GameSession;
import com.gaiagauntlet.gauntlet.core.session.constants.SessionState;
import com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;

public class GameHandlers extends HandlerUtils {
    public static void handleGame(World hub, GameEvent sessionEvt) {
        throw new IllegalAccessError("Not implemented!");
    }

    public static void handleGameEnd(World hub, GameEndEvent sessionEvt) {

        var sessionId = sessionEvt.getSessionId();
        if (!(sessionFor(sessionId).orElse(null) instanceof GameSession session)) {
            Resolve.error(sessionEvt,
                    MessageUtils.msg("server.gg.events.session.missing").param("sessionId", sessionEvt.getSessionId()));
            return;
        }

        if (session.getSessionState() != SessionState.RUNNING) {
            Resolve.success(sessionEvt, session, "server.gg.events.game.end.ended");
            return;
        }

        // wait for the timers to be done
        sessionEvt.settled().orTimeout(5, TimeUnit.SECONDS).whenComplete((a, error) -> {
            // log error, continue anyways
            if (error != null) {
                Resolve.log(sessionEvt, session, "server.gg.events.game.deferral.error");
            }

            // clean the game
            GauntletEventRegistry.dispatch(new SessionEvent(SessionOperation.CLEAN, sessionId));

            Resolve.success(sessionEvt, session, "server.gg.events.game.deferral.success");
        });
    }
}
