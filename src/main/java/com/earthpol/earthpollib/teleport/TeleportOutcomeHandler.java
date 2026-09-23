package com.earthpol.earthpollib.teleport;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.function.Consumer;

/**
 * Thread-safety: This class is not thread-safe. Register handlers only from the
 * main server thread during initialization, or ensure proper synchronization.
 * CODE INSIDE OF CONSUMERS SHOULD BE WRAPPED INSIDE OF THE APPROPRIATE FOLIA SCHEDULER!
 * A utility class for registering and executing custom logic based on the result
 * of a teleport attempt. Use this when you need to run more logic than just sending players
 * messages based on the status of their teleport (e.g You can't afford this teleport!) as
 * that functionality is already built-in to Teleporter itself.
 * <p>
 * This class maintains a mapping from {@link TeleportOutcome} values to
 * {@link Consumer} actions that will be executed when the corresponding
 * outcome occurs. It is designed to make teleport handling modular and
 * maintainable by centralizing outcome-specific logic.
 * <p>
 */
@SuppressWarnings("unused")
public final class TeleportOutcomeHandler {

    /** No-operation consumer to avoid null checks when an action is missing. */
    private static final Consumer<TeleportContext> NO_OP = ctx -> {};

    /** Mapping of teleport outcomes to their registered actions. */
    private final EnumMap<TeleportOutcome, Consumer<TeleportContext>> handlers =
            new EnumMap<>(TeleportOutcome.class);

    /**
     * Constructs a new {@code TeleportOutcomeHandler} with all outcomes
     * initialized to a no-op action.
     * <p>
     * This ensures that {@link #runAction(TeleportOutcome, TeleportContext)}
     * will never throw a {@link NullPointerException} due to a missing action.
     */
    public TeleportOutcomeHandler() {
        for (TeleportOutcome o : TeleportOutcome.values()) handlers.put(o, NO_OP);
    }

    public TeleportOutcomeHandler setActionOnOutcome(
            @NotNull TeleportOutcome outcome,
            Consumer<TeleportContext> action
    ) {
        handlers.put(outcome, action != null ? action : NO_OP);
        return this;
    }

    public TeleportOutcomeHandler setOnSuccess(Consumer<TeleportContext> action) {
        return setActionOnOutcome(TeleportOutcome.SUCCESS, action);
    }

    public TeleportOutcomeHandler setOnFailedDestUnsafe(Consumer<TeleportContext> action) {
        return setActionOnOutcome(TeleportOutcome.FAILED_DEST_UNSAFE, action);
    }

    public TeleportOutcomeHandler setOnFailedWarmupMoved(Consumer<TeleportContext> action) {
        return setActionOnOutcome(TeleportOutcome.FAILED_WARMUP_MOVED, action);
    }

    public TeleportOutcomeHandler setOnFailedUnaffordable(Consumer<TeleportContext> action) {
        return setActionOnOutcome(TeleportOutcome.FAILED_UNAFFORDABLE, action);
    }

    public TeleportOutcomeHandler setOnFailedError(Consumer<TeleportContext> action) {
        return setActionOnOutcome(TeleportOutcome.FAILED_ERROR, action);
    }

    @Contract(pure = true)
    public @NotNull Consumer<TeleportContext> getActionOnOutcome(
            @NotNull TeleportOutcome outcome
    ) {
        return handlers.getOrDefault(outcome, NO_OP);
    }

    void runAction(
            @NotNull TeleportOutcome outcome,
            @NotNull TeleportContext context
    ) {
        getActionOnOutcome(outcome).accept(context);
    }

}
