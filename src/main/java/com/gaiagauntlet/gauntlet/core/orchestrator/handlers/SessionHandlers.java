package com.gaiagauntlet.gauntlet.core.orchestrator.handlers;

import static com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils.msg;

import com.gaiagauntlet.gauntlet.core.GauntletUtils;
import com.gaiagauntlet.gauntlet.core.admin.AdminLog;
import com.gaiagauntlet.gauntlet.core.admin.GaiaLog;
import com.gaiagauntlet.gauntlet.core.events.GauntletEvent;
import com.gaiagauntlet.gauntlet.core.events.events.NewSessionEvent;
import com.gaiagauntlet.gauntlet.core.events.events.SessionEvent;
import com.gaiagauntlet.gauntlet.core.events.events.SessionQueueEvent;
import com.gaiagauntlet.gauntlet.core.events.events.SessionQueueEvent.SessionQueueOp;
import com.gaiagauntlet.gauntlet.core.games.interfaces.GameController;
import com.gaiagauntlet.gauntlet.core.games.registries.GameRegistry;
import com.gaiagauntlet.gauntlet.core.session.components.GameSession;
import com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;

public class SessionHandlers extends HandlerUtils {
    public static void handleNewSession(World hub, NewSessionEvent sessionEvt) {

        var gameSession = sessionEvt.getNewSession();
        // validate loaded games

        if (gameSession.getGameSequence() != null) {

            for (var game : gameSession.getGameSequence()) {
                if (!GameRegistry.hasGame(game)) {
                    Resolve.error(sessionEvt, gameSession, "session.generic.missing.game");
                    continue;
                }
                gameSession.addGame(game);
            }
        }

        var success = withResource().addSession(gameSession);
        if (success) {
            sessionEvt.complete(GaiaLog.atInfo().withSession(gameSession).log(
                    msg("server.gg.commands.session.create.success").param("sessionId", gameSession.getId())));
        } else {
            sessionEvt.complete(GaiaLog.atWarning().log("Unable to add session! It already exists"));
        }
    }

    public static void handleSession(World hub, SessionEvent sessionEvt) {
        var sessionOp = sessionFor(sessionEvt.getSessionId());

        if (!sessionOp.isPresent()) {
            sessionEvt.complete(GaiaLog.atError().withSession(sessionEvt.getSessionId())
                    .log("Session " + sessionEvt.getSessionId() + " is not present"));
            return;
        }
        var session = sessionOp.get();

        switch (sessionEvt.getOp()) {
            case SETUP -> {
                setupGame(hub, sessionEvt, session);
                return;
            }
            case CLEAN -> {
                cleanGame(hub, sessionEvt, session);
                return;
            }
            case DELETE -> {
                deleteSession(hub, sessionEvt, session);
                return;
            }
        }
    }

    private static void setupGame(World hub, SessionEvent sessionEvt, GameSession session) {
        if (!session.available()) {
            sessionEvt.complete(GaiaLog.atError().withSession(session)
                    .log(MessageUtils.msg("server.gg.events.session.error.unavailable")
                            .param("sessionState", session.getSessionState().toString())
                            .param("sessionId", session.getId())
                            .param("action", "being set up")));
            return;
        }

        var nextGameId = session.getNext();

        if (!(GameRegistry.getGame(nextGameId).orElse(null) instanceof GameController game)) {
            sessionEvt.complete(GaiaLog.atError().withSession(session)
                    .log(MessageUtils.msg("server.gg.events.session.error.unavailable")
                            .param("sessionId", session.getId())));
            return;
        }

        // pass stuff to the game
        // mark the next game as running
        session.startNext();
        var future = game.setupGame(hub.getEntityStore().getStore(), session);
        future.whenComplete((value, error) -> {
            if (error != null) {
                // errored
                GaiaLog.atError().withSession(session).withCause(error)
                        .log(MessageUtils.msg("server.gg.events.session.setup.error")
                                .param("sessionId", session.getId())
                                .param("gameId", nextGameId)
                                .param("reason", error.getLocalizedMessage()));

                session.setErrored("Error thrown during setup");
                // cancel the game immediately - run on the hub thread
                GauntletUtils.run(hub, () -> cleanGame(hub, sessionEvt, session));
                return;
            }

            var check = session.setRunning(nextGameId);
            if (!check) {
                // something has gone horribly wrong
                GaiaLog.atError().withSession(session)
                        .log(MessageUtils.msg("server.gg.events.session.setup.invalid")
                                .param("sessionId", session.getId())
                                .param("gameId", nextGameId));

                session.setErrored("Game was in a weird state when starting (session state mismatch)");
                // cancel the game immediately - run on the hub thread
                GauntletUtils.run(hub, () -> cleanGame(hub, sessionEvt, session));
                return;
            }
            sessionEvt.complete(GaiaLog.atInfo().withSession(session)
                    .log(MessageUtils.msg("server.gg.events.session.setup.success")
                            .param("sessionId", session.getId())
                            .param("gameId", nextGameId)));
        });
    }

    private static void cleanGame(World hub, SessionEvent sessionEvt, GameSession session) {
        var currentGame = session.getCurrentGame();
        var isCleaning = session.setCleaning(currentGame);

        if (!isCleaning) {
            sessionEvt.complete(GaiaLog.atError().withSession(session)
                    .log(MessageUtils.msg("server.gg.events.session.error.unavailable")
                            .param("sessionState", session.getSessionState().toString())
                            .param("sessionId", session.getId())
                            .param("action", "cleaning. Already getting cleaned up!")));
            return;
        }

        if (!(GameRegistry.getGame(currentGame).orElse(null) instanceof GameController game)) {
            session.setErrored("No current game is available to cancel");
            sessionEvt.complete(GaiaLog.atError().withSession(session)
                    .log(MessageUtils.msg("server.gg.events.session.error.unavailable")
                            .param("sessionId", session.getId())));
            return;
        }

        // pass stuff to the game
        var future = game.cleanGame(hub, session);
        future.whenComplete((value, error) -> {
            if (error != null) {
                GaiaLog.atError().withSession(session).withCause(error)
                        .log(MessageUtils.msg("server.gg.events.session.setup.error")
                                .param("sessionId", session.getId())
                                .param("gameId", currentGame)
                                .param("reason", error.getLocalizedMessage()));
                session.setErrored("Error thrown when cancelling");
            } else {
                var success = session.setComplete(currentGame);
                if (!success) {
                    sessionEvt.complete(GaiaLog.atError().withSession(session)
                            .log(MessageUtils.msg("server.gg.events.session.error.unavailable")
                                    .param("sessionState", session.getSessionState().toString())
                                    .param("sessionId", session.getId())
                                    .param("action", "completing")));
                    return;
                }
            }
            Resolve.success(sessionEvt, session, "server.gg.events.session.clean.success");
        });
    }

    private static void deleteSession(World hub, SessionEvent sessionEvt, GameSession session) {
        // if the game is running, cancel it
        if (!session.available()) {
            cleanGame(hub, sessionEvt, session);
            return;
        }

        // clean players
        // for (var player : session.getParticipants())

        var resource = withResource();
        resource.removeSession(session);
        Resolve.success(sessionEvt, session, "server.gg.events.session.destroy.success");
    }

    public static void handleSessionQueue(World hub, SessionQueueEvent sessionEvt) {
        if (!(sessionFor(sessionEvt.getSessionId()).orElse(null) instanceof GameSession session)) {
            Resolve.error(sessionEvt, MessageUtils.msg("server.gg.events.session.missing").param("sessionId",
                    sessionEvt.getSessionId()));

            return;
        }

        var op = sessionEvt.getOp();
        var gameQueue = sessionEvt.getNewQueue();
        // validate games
        if (op != SessionQueueOp.REMOVE) {

            for (var game : gameQueue) {
                if (!GameRegistry.hasGame(game)) {
                    // validation failed
                    Resolve.error(sessionEvt, session, "server.gg.events.session.generic.missing.game");
                    return;
                }
            }
        }

        if (gameQueue.size() == 0) {
            Resolve.error(sessionEvt, session, "server.gg.events.game.queue.missing");
            return;
        }

        switch (op) {
            case SET -> {
                session.setGames(gameQueue);
                Resolve.success(sessionEvt, session, MessageUtils.msg("server.gg.events.session.game.success")
                        .param("action", "set the games list"));
                return;
            }
            case REMOVE -> {
                var removed = 0;
                for (var game : gameQueue) {
                    var success = session.removeGame(game);
                    if (success) removed++;
                }
                Resolve.success(sessionEvt, session, MessageUtils.msg("server.gg.events.session.game.removal.success")
                        .param("games", removed)
                    );
                return;
            }
            case APPEND -> {
                session.addGames(gameQueue);
                Resolve.success(sessionEvt, session, MessageUtils.msg("server.gg.events.session.game.success")
                        .param("action", "appended " + gameQueue.size() + " game(s)"));
            }
        }
    }
}
