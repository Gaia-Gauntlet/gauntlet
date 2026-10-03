package com.gaiagauntlet.gauntlet.core.admin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import com.gaiagauntlet.gauntlet.core.session.components.GameSession;
import com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;

import lombok.Getter;

/**
 * The last few hundred things worth telling an admin, kept in memory for the
 * dashboard's Log tab.
 */
public final class GaiaLog {
    public static HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String INFO = "server.logging.info";
    public static final String DEBUG = "server.logging.debug";
    public static final String ERROR = "server.logging.error";
    public static final String SEVERE = "server.logging.severe";
    public static final String WARNING = "server.logging.warning";

    // global logs
    public static final String GLOBAL = "Global";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Getter
    Level level;
    @Getter
    long millis;
    @Getter
    String sessionId;
    @Getter
    String gameId;
    @Getter
    Message text;
    @Getter
    Throwable e;

    public static GaiaLog log(GaiaLog line) {
        LOGGER.at(line.level).withCause(line.e).log(line.toString());
        AdminLog.add(line);
        return line;
    }

    public GaiaLog log(String text) {
        return log(Message.raw(text));
    }

    public GaiaLog log(Message message) {
        this.text = message;
        log(this);
        return this;
    }

    public GaiaLog(Level level) {
        this.level = level;
        this.millis = System.currentTimeMillis();
        gameId = GLOBAL;
        sessionId = GLOBAL;
    }

    public GaiaLog withSession(String id) {
        this.sessionId = id;
        return this;
    }
    public GaiaLog withGameId(String id) {
        this.gameId = id;
        return this;
    }

    public GaiaLog withSession(GameSession session) {
        if (session.getCurrentGame() != null) {
            this.gameId = session.getCurrentGame();
        }
        this.gameId = GLOBAL;
        this.sessionId = session.getId();
        return this;
    }

    public GaiaLog withLevel(Level level) {
        this.level = level;
        return this;
    }

    public GaiaLog withCause(Throwable e) {
        this.e = e;
        return this;
    }

    public static GaiaLog atInfo() {
        return new GaiaLog(Level.INFO);
    }

    public static GaiaLog atDebug() {
        return new GaiaLog(Level.FINE);
    }

    public static GaiaLog atWarning() {
        return new GaiaLog(Level.WARNING);
    }

    public static GaiaLog atError(Throwable e) {
        return new GaiaLog(Level.SEVERE).withCause(e);
    }

    public static GaiaLog atError() {
        return new GaiaLog(Level.SEVERE);
    }

    public GaiaLog(
            Level level, long millis, @Nonnull String gameId, @Nonnull Message text, Throwable e) {
        this.level = level;
        this.millis = millis;
        this.gameId = gameId;
        this.text = text;
        this.e = e;
    }

    @Override 
    public String toString() {
        return "[" + level.getName() + "] " + TIME.format(Instant.ofEpochMilli(millis))
                + " [" + gameId + "] " + text.getAnsiMessage();
    }

    public Message toMessage() {
        var trans = switch (level.getName()) {
            case "INFO" -> INFO;
            case "DEBUG" -> DEBUG;
            case "ERROR" -> ERROR;
            case "SEVERE" -> SEVERE;
            case "WARNING" -> WARNING;
            default -> INFO;
        };
        return MessageUtils.msg(trans).param("message", text);
    }
}
