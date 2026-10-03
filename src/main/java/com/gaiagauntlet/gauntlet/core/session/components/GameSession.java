package com.gaiagauntlet.gauntlet.core.session.components;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.gaiagauntlet.gauntlet.core.admin.AdminLog;
import com.gaiagauntlet.gauntlet.core.admin.GaiaLog;
import com.gaiagauntlet.gauntlet.core.admin.GaiaLog;
import com.gaiagauntlet.gauntlet.core.session.constants.SessionState;
import com.gaiagauntlet.gauntlet.core.session.registry.SessionRegistry;
import com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils;
import com.gaiagauntlet.gauntlet.utils.codec.StringRegistryCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.logger.HytaleLogger;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds the relevant information regarding an active session.
 * 
 * Does NOT hold specific information like players active, rather, only the
 * information necessary to identify a specific game
 */
public class GameSession {
    private final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static BuilderCodec<@NotNull GameSession> CODEC = BuilderCodec
            .builder(GameSession.class, GameSession::new)
            .append(new KeyedCodec<>("Id", Codec.STRING),
                    (holder, v) -> holder.id = v,
                    holder -> holder.id)
            .add()
            .append(new KeyedCodec<>("Components",
                    new StringRegistryCodec<>(new SessionRegistry(), ConcurrentHashMap::new)),
                    (holder, components) -> holder.sessionComponents = components,
                    holder -> holder.sessionComponents)
            .add()
            .append(new KeyedCodec<>("Sequence", Codec.STRING_ARRAY),
                    (holder, v) -> holder.setGames(Arrays.asList(v)),
                    holder -> holder.gameSequence.toArray(new String[0]))
            .add()
            .append(new KeyedCodec<>("CurrentGame", Codec.STRING),
                    (holder, v) -> holder.currentGame = v,
                    holder -> holder.currentGame)
            .add()
            .append(new KeyedCodec<>("State", new EnumCodec<>(SessionState.class)),
                    (holder, v) -> holder.sessionState = v,
                    holder -> holder.sessionState)
            .add()
            .build();

    @Getter
    private Map<String, SessionComponent> sessionComponents;

    @Getter
    private String id;

    public <T extends SessionComponent> void put(SessionComponentType<T> type, T component) {
        sessionComponents.put(type.getIndex(), component);
    }

    public <T extends SessionComponent> Optional<T> get(SessionComponentType<T> type) {
        var sesComp = sessionComponents.get(type.getIndex());
        if (sesComp == null)
            return Optional.empty();

        return Optional.of(type.getTypeClass().cast(sesComp));
    }

    @Getter
    private final ArrayDeque<String> gameSequence = new ArrayDeque<>();

    @Getter
    // design here may change. My head canon is that the currentGame will pop from
    // the array and the array of the sequence will shrink.
    // Alternatively we could store the index of the current game inside the
    // sequence and keep the sequence as-is
    // I'm good with either
    private String currentGame;

    @Getter
    @NotNull
    private SessionState sessionState = SessionState.IDLE;
    @Getter
    @Nullable
    private String errorReason;

    public GameSession() {
        sessionComponents = new ConcurrentHashMap<>();
    }

    public GameSession(String id) {
        this.id = id;
        this();
    }

    public boolean available() {
        return !sessionState.active();
    }

    /** gets the next available game */
    @Nullable
    public String getNext() {
        return gameSequence.peekFirst();
    }

    private boolean transitionBlocked(SessionState state, @Nullable String gameIdCheck) {
        if (gameIdCheck != null && !gameIdCheck.equals(currentGame)) {
            error().log("Game " + gameIdCheck + " in " + getId() + " failed to switch to " + state.toString() + "! Game "
                            + currentGame
                            + " was somehow registered instead");
            return true;
        }

        if (sessionState.to(state)) {
            logger().log(MessageUtils.msg("server.gauntlet.session.transition.success")
                .param("sessionId", this.id)
                .param("newState", state.toString())
                .param("oldState", sessionState.toString())
            );
            return false; // transition allowed, not blocked
        }

        error().log("Game failed to switch to " + state.toString() + "! State is " + sessionState.toString()
                        + " instead!");
        
        return true;
    }

    private GaiaLog logger() {
        return GaiaLog.atInfo().withSession(this);
    }
    private GaiaLog error() {
        return GaiaLog.atError().withSession(this);
    }

    /**
     * transitions to the next game, popping it from the list and setting it as
     * current
     */
    @Nullable
    public String startNext() {
        if (transitionBlocked(SessionState.SETTING_UP, null))
            return null;

        currentGame = gameSequence.pollFirst();
        
        if (currentGame == null) {
            return null;
        }
        
        errorReason = null;
        sessionState = SessionState.SETTING_UP;
        return currentGame;
    }

    /**
     * Checks if the current game is still the current game, and then sets it as
     * running
     * <br />
     * <br />
     * <b>MANAGED BY THE ORCHESTRATOR</b>
     */
    public boolean setRunning(String gameIdCheck) {
        if (transitionBlocked(SessionState.RUNNING, gameIdCheck))
            return false;

        errorReason = null;
        sessionState = SessionState.RUNNING;
        return true;
    }

    /**
     * RULE: Can switch to Complete if currently in Running state. Warns if already
     * finished/idle but returns true
     * <br />
     * <br />
     * Recovers from an errored state - but keeps the error reason
     * 
     * @param gameIdCheck
     * @return
     */
    public boolean setComplete(String gameIdCheck) {
        if (transitionBlocked(SessionState.FINISHED, gameIdCheck))
            return false;
        errorReason = null;
        sessionState = SessionState.FINISHED;
        return true;
    }

    /**
     * Returns TRUE if the transition happened. Returns FALSE if already in cleaning
     * or game is not running
     * 
     * @param gameIdCheck
     * @return
     */
    public boolean setCleaning(String gameIdCheck) {
        if (transitionBlocked(SessionState.CLEANING, gameIdCheck))
            return false;
        sessionState = SessionState.CLEANING;
        return true;
    }

    /** Set if the session resulted in an error of some kind */
    public void setErrored(String errorReason) {
        sessionState = SessionState.ERROR;
        this.errorReason = errorReason;
    }

    /** adds a game to the sequence */
    public void addGame(@Nonnull String gameId) {
        gameSequence.addLast(gameId);
    }

    public void addGames(@Nonnull Collection<String> games) {
        gameSequence.addAll(games);
    }

    public void setGames(Collection<String> games) {
        gameSequence.clear();
        if (games != null) {
            gameSequence.addAll(games);
        }
    }

    /** removes a game if it is present */
    public boolean removeGame(@Nonnull String gameId) {
        return gameSequence.removeFirstOccurrence(gameId);
    }
}
