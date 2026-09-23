package com.earthpol.earthpollib.teleport;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

class TeleportOutcomeHandlerTest {

    @Test
    void unsetOutcomesDefaultToNoOpConsumer() {
        TeleportOutcomeHandler handler = new TeleportOutcomeHandler();

        assertDoesNotThrow(() -> handler.getActionOnOutcome(TeleportOutcome.SUCCESS).accept(null));
    }

    @Test
    void setActionOnOutcomeRegistersProvidedConsumer() {
        TeleportOutcomeHandler handler = new TeleportOutcomeHandler();
        AtomicBoolean called = new AtomicBoolean(false);

        handler.setActionOnOutcome(TeleportOutcome.SUCCESS, ctx -> called.set(true));
        handler.getActionOnOutcome(TeleportOutcome.SUCCESS).accept(null);

        assertSame(true, called.get());
    }

    @Test
    void nullActionResetsOutcomeToNoOp() {
        TeleportOutcomeHandler handler = new TeleportOutcomeHandler();
        handler.setOnSuccess(ctx -> { throw new AssertionError("should not run"); });

        handler.setActionOnOutcome(TeleportOutcome.SUCCESS, null);

        assertDoesNotThrow(() -> handler.getActionOnOutcome(TeleportOutcome.SUCCESS).accept(null));
    }
}
