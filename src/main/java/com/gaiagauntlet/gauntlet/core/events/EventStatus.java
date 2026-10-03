package com.gaiagauntlet.gauntlet.core.events;

import com.hypixel.hytale.server.core.Message;

public record EventStatus (
    Message message,
    boolean success,
    String error
) {}
