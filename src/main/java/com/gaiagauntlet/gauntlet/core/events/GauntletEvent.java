package com.gaiagauntlet.gauntlet.core.events;

import static com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils.error;

import java.util.function.Consumer;

import com.gaiagauntlet.gauntlet.core.admin.AdminLog;
import com.gaiagauntlet.gauntlet.core.admin.GaiaLog;
import com.gaiagauntlet.gauntlet.plugins.announcer.utils.MessageUtils;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;

import lombok.Getter;
import lombok.Setter;

/**
 * Wrapper for all events
 * 
 * For your sanity, it goes down in "scale"
 * Universe -> Session -> Game -> Player
 */
public abstract class GauntletEvent implements IEvent<Void> {
    private static HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private Consumer<GaiaLog> consumer = null;
    private Consumer<GaiaLog> onComplete = null;
    @Getter
    @Setter
    private boolean inProgress = true;

    // part of the event builder to add a callback
    public GauntletEvent onMessage(Consumer<GaiaLog> consumer) {
        this.consumer = consumer;
        return this;
    }

    /** Registers a callback that is run exactly once at the end of the operation */
    public GauntletEvent onComplete(Consumer<GaiaLog> runnable) {
        this.onComplete = runnable;
        return this;
    }

    public void complete(GaiaLog reason) {
        if (!inProgress)
            return;
        if (this.onComplete != null) {
            try {
                this.onComplete.accept(reason);
                this.onComplete = null; // clear the runner
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to run callback for event!");
            }
        }
    }

    public void log(GaiaLog log) {
        if (consumer == null)
            return;
        consumer.accept(log);
    }
}
