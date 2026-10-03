package com.gaiagauntlet.gauntlet.core.admin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gaiagauntlet.gauntlet.core.session.components.GameSession;
import com.hypixel.hytale.logger.HytaleLogger;

/**
 * The last few hundred things worth telling an admin, kept in memory for the
 * dashboard's Log tab.
 */
public final class AdminLog {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // global logs
    public static final String GLOBAL = "Global";
    private static final int CAPACITY = 300;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final ArrayDeque<GaiaLog> LINES = new ArrayDeque<>();

    private AdminLog() {
    }

    public static void add(GaiaLog line) {
        // add to the lines for later reference
        synchronized (LINES) {
            LINES.addLast(line);
            while (LINES.size() > CAPACITY) {
                LINES.removeFirst();
            }
        }
    }

    /** The newest lines first: the game's own plus server-wide ones. */
    @Nonnull
    public static List<GaiaLog> recent(@Nullable GameSession session, int limit) {
        var out = new ArrayList<GaiaLog>();
        if (Objects.isNull(session))
            return out;

        synchronized (LINES) {
            var it = LINES.descendingIterator();
            while (it.hasNext() && out.size() < limit) {
                var line = it.next();
                if (line.getSessionId() == GLOBAL || line.getSessionId().isEmpty() || line.getSessionId().equals(session.getId())) {
                    out.add(line);
                }
            }
        }
        return out;
    }
}
