package com.earthpol.earthpollib.command;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * One pending confirmation per player. Tokens prevent an old prompt from confirming a replacement.
 * Authorization is checked again on confirmation; actions run at most once on the confirming thread.
 * Call confirm on the player's owning thread, cancel on quit, and close on plugin disable.
 */
public final class ConfirmationManager implements AutoCloseable {
    public enum Result { CONFIRMED, NOT_FOUND, EXPIRED, DENIED }

    private record Pending(UUID token, long deadline, BooleanSupplier authorized, Runnable action) {}

    private final Map<UUID, Pending> pending = new HashMap<>();
    private final LongSupplier nanoTime;
    private boolean closed;

    public ConfirmationManager() {
        this(System::nanoTime);
    }

    ConfirmationManager(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public synchronized UUID request(UUID playerId, Duration lifetime, BooleanSupplier authorized, Runnable action) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lifetime, "lifetime");
        Objects.requireNonNull(authorized, "authorized");
        Objects.requireNonNull(action, "action");
        if (closed) throw new IllegalStateException("Confirmation manager is closed");
        long nanos = lifetime.toNanos();
        if (nanos <= 0) throw new IllegalArgumentException("Confirmation lifetime must be positive");
        purgeExpired();
        UUID token = UUID.randomUUID();
        pending.put(playerId, new Pending(token, nanoTime.getAsLong() + nanos, authorized, action));
        return token;
    }

    public Result confirm(UUID playerId, UUID token) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        Pending confirmation;
        synchronized (this) {
            confirmation = pending.get(playerId);
            if (closed || confirmation == null || !confirmation.token.equals(token)) return Result.NOT_FOUND;
            pending.remove(playerId);
            if (expired(confirmation)) return Result.EXPIRED;
        }
        if (!confirmation.authorized.getAsBoolean()) return Result.DENIED;
        confirmation.action.run();
        return Result.CONFIRMED;
    }

    public synchronized boolean cancel(UUID playerId) {
        return pending.remove(Objects.requireNonNull(playerId, "playerId")) != null;
    }

    public synchronized void purgeExpired() {
        pending.values().removeIf(this::expired);
    }

    @Override
    public synchronized void close() {
        closed = true;
        pending.clear();
    }

    private boolean expired(Pending confirmation) {
        return nanoTime.getAsLong() - confirmation.deadline >= 0;
    }
}
