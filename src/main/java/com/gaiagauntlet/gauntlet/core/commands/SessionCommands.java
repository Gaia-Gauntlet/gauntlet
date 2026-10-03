package com.gaiagauntlet.gauntlet.core.commands;

import javax.annotation.Nonnull;

import com.gaiagauntlet.gauntlet.core.GauntletUtils;
import com.gaiagauntlet.gauntlet.core.events.GauntletEventRegistry;
import com.gaiagauntlet.gauntlet.core.events.events.GameEvent;
import com.gaiagauntlet.gauntlet.core.events.events.NewSessionEvent;
import com.gaiagauntlet.gauntlet.core.events.events.SessionEvent;
import com.gaiagauntlet.gauntlet.core.events.events.SessionQueueEvent;
import com.gaiagauntlet.gauntlet.core.events.events.SessionEvent.SessionOperation;
import com.gaiagauntlet.gauntlet.core.events.events.SessionQueueEvent.SessionQueueOp;
import com.gaiagauntlet.gauntlet.core.games.registries.GameRegistry;
import com.gaiagauntlet.gauntlet.core.orchestrator.GauntletOrchestrator;
import com.gaiagauntlet.gauntlet.core.session.components.GameSession;
import com.gaiagauntlet.gauntlet.core.session.constants.SessionState;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import static com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils.*;

import java.util.List;

/**
 * All of these are, currently, debug and a stop-gap until the full eventing
 * pipeline is finished
 * 
 * 
 * Once the eventing pipeline is done, the orchestrator will be managing all of
 * this stuff and these commands will just
 * emit intents
 */
public class SessionCommands extends AbstractCommandCollection {

    private static final SingleArgumentType<String> SESSION_ID = new SingleArgumentType<>(
            "server.commands.parsing.argtype.string.name", "server.commands.parsing.argtype.string.usage") {
        @Override
        public String parse(String input, ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(@Nonnull CommandSender sender, @Nonnull String textAlreadyEntered,
                int numParametersTyped, @Nonnull SuggestionResult result) {
            SuggestionUtil.suggestFiltered(GauntletUtils.withResource().getSessions().keySet(), textAlreadyEntered,
                    result);

        }

        @Override
        public int getSuggestionValueCount() {
            return 1;
        }
    };
    private static final SingleArgumentType<String> GAME_ID = new SingleArgumentType<>(
            "server.commands.parsing.argtype.string.name", "server.commands.parsing.argtype.string.usage") {
        @Override
        public String parse(String input, ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(@Nonnull CommandSender sender, @Nonnull String textAlreadyEntered,
                int numParametersTyped, @Nonnull SuggestionResult result) {
            SuggestionUtil.suggestFiltered(GameRegistry.getGameIds(), textAlreadyEntered, result);
        }

        @Override
        public int getSuggestionValueCount() {
            return 1;
        }
    };

    public SessionCommands() {
        super("session", "Gaia Gauntlet controls");
        // requirePermission(Permissions.ADMIN);
        addAliases("s");
        addSubCommand(new CreateSession());
        addSubCommand(new ListSessions());
        addSubCommand(new DestroySession());
        addSubCommand(new AddGameToSession());
        addSubCommand(new RemoveGameFromSession());
        addSubCommand(new SetupSession());
        addSubCommand(new CleanSession());
    }

    private static class CreateSession extends AbstractWorldCommand {
        private final RequiredArg<String> sessionId;
        private final OptionalArg<String> games;

        public CreateSession() {
            super("create", "Create a new session");
            addAliases("c");
            sessionId = withRequiredArg("sessionId", "The id of the session", ArgTypes.STRING);
            games = withOptionalArg("games", "the list of games to play as a csv", ArgTypes.STRING);
        }

        @Override
        protected void execute(CommandContext ctx, World arg1, Store<EntityStore> arg2) {
            var selectedGamesList = games.provided(ctx) ? games.get(ctx) : null;
            var session = sessionId.get(ctx);

            var gameSession = new GameSession(session);
            if (selectedGamesList != null) {

                var games = selectedGamesList.split(",");

                for (var game : games) {
                    if (!GameRegistry.hasGame(game)) {
                        ctx.sendMessage(error("Game " + game + " is not registered!"));
                        continue;
                    }
                    gameSession.addGame(game);
                }
            }

            ctx.sendMessage(msg("server.gg.commands.session.create.pending")
                    .param("sessionId", session));
            GauntletEventRegistry.dispatch(
                    new NewSessionEvent(gameSession)
                            .onMessage(msg -> ctx.sendMessage(msg.toMessage()))
                            .onComplete(message -> {
                                ctx.sendMessage(message.toMessage());
                            }));
        }
    }

    private static class DestroySession extends AbstractWorldCommand {
        private final RequiredArg<String> sessionId;

        public DestroySession() {
            super("destroy", "Destroys a session");
            addAliases("d");
            sessionId = withRequiredArg("sessionId", "The session to destroy", SESSION_ID);
        }

        @Override
        protected void execute(CommandContext ctx, World arg1, Store<EntityStore> arg2) {
            var targetSession = sessionId.get(ctx);

            ctx.sendMessage(msg("server.gg.commands.session.destroy.pending")
                    .param("sessionId", targetSession));
            GauntletEventRegistry.dispatch(
                    new SessionEvent(SessionOperation.DELETE, targetSession)
                            .onMessage(msg -> ctx.sendMessage(msg.toMessage()))
                            .onComplete(message -> {
                                ctx.sendMessage(message.toMessage());
                            }));

        }
    }

    private static class ListSessions extends AbstractWorldCommand {
        public ListSessions() {
            super("list", "List all sessions");
            addAliases("ls");
        }

        @Override
        protected void execute(CommandContext ctx, World arg1, Store<EntityStore> arg2) {
            var sessions = GauntletUtils.withResource().getSessions();

            if (sessions.isEmpty()) {
                ctx.sendMessage(error("No active sessions"));
                return;
            }

            for (var sessionEntry : sessions.entrySet()) {
                var games = sessionEntry.getValue().getGameSequence();
                var session = sessionEntry.getValue();
                ctx.sendMessage(
                        markup(Message.translation("server.gg.commands.session.list.line")
                                .param("sessionId", sessionEntry.getKey())
                                .param("game", session.getCurrentGame())
                                .param("gamesList",
                                        games != null && games.size() >= 1
                                                ? String.join(", ", session.getGameSequence())
                                                : "No games queued")
                                .param("state", session.getSessionState().toString() + " " + session.getErrorReason() )));
            }
        }
    }

    private static class AddGameToSession extends AbstractWorldCommand {
        private final RequiredArg<String> sessionId;
        private final RequiredArg<String> gameId;

        public AddGameToSession() {
            super("add", "Add a game to a session");
            addAliases("a");
            sessionId = withRequiredArg("sessionId", "The id of the session", SESSION_ID);
            gameId = withRequiredArg("game", "The game to add", GAME_ID);
        }

        @Override
        protected void execute(CommandContext ctx, World arg1, Store<EntityStore> arg2) {
            var game = gameId.get(ctx);
            var session = sessionId.get(ctx);
            GauntletEventRegistry.dispatch(
                    new SessionQueueEvent(SessionQueueOp.APPEND, session, List.of(game))
                            .onMessage(msg -> ctx.sendMessage(msg.toMessage()))
                            .onComplete(message -> {
                                ctx.sendMessage(message.toMessage());
                            }));

        }
    }

    private static class RemoveGameFromSession extends AbstractWorldCommand {
        private final RequiredArg<String> sessionId;
        private final RequiredArg<String> gameId;

        public RemoveGameFromSession() {
            super("remove", "Remove a game from the session");
            addAliases("r");
            sessionId = withRequiredArg("sessionId", "The id of the session", SESSION_ID);
            gameId = withRequiredArg("game", "the game to remove", GAME_ID);
        }

        @Override
        protected void execute(CommandContext ctx, World arg1, Store<EntityStore> arg2) {
            var game = gameId.get(ctx);
            var session = sessionId.get(ctx);
            GauntletEventRegistry.dispatch(new SessionQueueEvent(SessionQueueOp.REMOVE, session, List.of(game))
                    .onMessage(msg -> ctx.sendMessage(msg.toMessage())).onComplete(message -> {
                        ctx.sendMessage(message.toMessage());
                    }));

        }
    }

    private static class SetupSession extends AbstractWorldCommand {
        private final RequiredArg<String> sessionId;

        public SetupSession() {
            super("setup", "Sets up the next game for a session");
            sessionId = withRequiredArg("sessionId", "The session", SESSION_ID);
        }

        @Override
        protected void execute(CommandContext ctx, World arg1, Store<EntityStore> accessor) {
            var targetSession = sessionId.get(ctx);
            ctx.sendMessage(msg("server.gg.commands.session.setup.pending")
                    .param("sessionId", targetSession));

            GauntletEventRegistry.dispatch(new SessionEvent(SessionOperation.SETUP, targetSession)
                    .onMessage(msg -> ctx.sendMessage(msg.toMessage())).onComplete(message -> {
                        ctx.sendMessage(message.toMessage());
                    }));
        }
    }

    private static class CleanSession extends AbstractWorldCommand {
        private final RequiredArg<String> sessionId;

        public CleanSession() {
            super("stop", "Starts the next game for a session");
            sessionId = withRequiredArg("sessionId", "The session", SESSION_ID);
        }

        @Override
        protected void execute(CommandContext ctx, World arg1, Store<EntityStore> accessor) {
            var targetSession = sessionId.get(ctx);
            ctx.sendMessage(msg("server.gg.commands.session.cleanup.pending")
                    .param("sessionId", targetSession));

            GauntletEventRegistry.dispatch(new SessionEvent(SessionOperation.CLEAN, targetSession)
                    .onMessage(msg -> ctx.sendMessage(msg.toMessage())).onComplete(message -> {
                        ctx.sendMessage(message.toMessage());
                    }));
        }
    }
}
