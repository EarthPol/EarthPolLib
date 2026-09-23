package com.earthpol.earthpollib.command;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationManagerTest {
    @Test
    void replacementAndPlayerIdentityProtectNewPendingAction() {
        ConfirmationManager manager = new ConfirmationManager();
        UUID player = UUID.randomUUID();
        UUID old = manager.request(player, Duration.ofMinutes(1), () -> true, () -> fail("Old action"));
        AtomicInteger calls = new AtomicInteger();
        UUID current = manager.request(player, Duration.ofMinutes(1), () -> true, calls::incrementAndGet);
        assertEquals(ConfirmationManager.Result.NOT_FOUND, manager.confirm(player, old));
        assertEquals(ConfirmationManager.Result.NOT_FOUND, manager.confirm(UUID.randomUUID(), current));
        assertEquals(ConfirmationManager.Result.CONFIRMED, manager.confirm(player, current));
        assertEquals(ConfirmationManager.Result.NOT_FOUND, manager.confirm(player, current));
        assertEquals(1, calls.get());
    }

    @Test
    void expiresAtDeadlineAndRechecksPermission() {
        AtomicLong time = new AtomicLong();
        ConfirmationManager manager = new ConfirmationManager(time::get);
        UUID player = UUID.randomUUID();
        UUID token = manager.request(player, Duration.ofSeconds(1), () -> true, () -> fail("Expired action"));
        time.set(Duration.ofSeconds(1).toNanos());
        assertEquals(ConfirmationManager.Result.EXPIRED, manager.confirm(player, token));
        AtomicBoolean allowed = new AtomicBoolean(true);
        token = manager.request(player, Duration.ofSeconds(1), allowed::get, () -> fail("Denied action"));
        allowed.set(false);
        assertEquals(ConfirmationManager.Result.DENIED, manager.confirm(player, token));
    }

    @Test
    void concurrentConfirmationRunsOnce() throws Exception {
        ConfirmationManager manager = new ConfirmationManager();
        UUID player = UUID.randomUUID();
        AtomicInteger count = new AtomicInteger();
        UUID token = manager.request(player, Duration.ofMinutes(1), () -> true, count::incrementAndGet);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> manager.confirm(player, token));
            var second = executor.submit(() -> manager.confirm(player, token));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, count.get());
    }

    @Test
    void cancellationAndShutdownClearActions() {
        ConfirmationManager manager = new ConfirmationManager();
        UUID player = UUID.randomUUID();
        UUID token = manager.request(player, Duration.ofMinutes(1), () -> true, () -> fail("Cancelled action"));
        assertTrue(manager.cancel(player));
        assertEquals(ConfirmationManager.Result.NOT_FOUND, manager.confirm(player, token));
        token = manager.request(player, Duration.ofMinutes(1), () -> true, () -> fail("Closed action"));
        manager.close();
        assertEquals(ConfirmationManager.Result.NOT_FOUND, manager.confirm(player, token));
        assertThrows(IllegalStateException.class, () -> manager.request(player, Duration.ofMinutes(1), () -> true, () -> {}));
    }
}
