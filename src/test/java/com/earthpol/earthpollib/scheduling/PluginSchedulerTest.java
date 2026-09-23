package com.earthpol.earthpollib.scheduling;

import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class PluginSchedulerTest {
    @Test
    void routesEachContextAndCompletesOneShotTasks() {
        Environment env = new Environment();
        AtomicInteger calls = new AtomicInteger();
        TaskHandle entity = env.scheduler.runEntity(env.entity, calls::incrementAndGet);
        env.scheduler.runRegion(new Location(proxy(World.class, (p, m, a) -> null), 1, 2, 3), calls::incrementAndGet);
        env.scheduler.runGlobal(calls::incrementAndGet);
        env.scheduler.runAsync(calls::incrementAndGet);
        assertEquals(List.of("entity", "region", "global", "async"), env.calls.stream().map(c -> c.context).toList());
        env.calls.forEach(Call::run);
        assertEquals(4, calls.get());
        assertNull(entity.completion().toCompletableFuture().join());
    }

    @Test
    void handlesRetirementBeforeAndAfterSubmission() {
        Environment env = new Environment();
        env.rejectEntity = true;
        TaskHandle rejected = env.scheduler.runEntity(env.entity, () -> fail("Retired action executed"));
        assertTrue(rejected.isCancelled());
        env.rejectEntity = false;
        TaskHandle pending = env.scheduler.runEntity(env.entity, () -> fail("Retired action executed"));
        env.calls.getLast().retired.run();
        assertTrue(pending.isCancelled());
        env.calls.getLast().run();
    }

    @Test
    void closeCancelsPendingTasksAndRejectsNewWork() {
        Environment env = new Environment();
        TaskHandle pending = env.scheduler.runAsync(() -> fail("Cancelled action executed"));
        env.scheduler.close();
        env.scheduler.close();
        assertTrue(pending.isCancelled());
        assertTrue(env.calls.getFirst().cancelled);
        env.calls.getFirst().run();
        assertThrows(IllegalStateException.class, () -> env.scheduler.runGlobal(() -> {}));
    }

    @Test
    void closeDuringSubmissionCancelsTheLateNativeHandle() {
        Environment env = new Environment();
        env.beforeReturn = env.scheduler::close;
        TaskHandle handle = env.scheduler.runAsync(() -> fail("Closed action executed"));
        assertTrue(handle.isCancelled());
        assertTrue(env.calls.getFirst().cancelled);
    }

    @Test
    void immediateCompletionBeforeBindingIsHandled() {
        Environment env = new Environment();
        env.immediate = true;
        AtomicInteger count = new AtomicInteger();
        TaskHandle handle = env.scheduler.runAsync(count::incrementAndGet);
        assertTrue(handle.isDone());
        assertFalse(handle.isCancelled());
        assertEquals(1, count.get());
    }

    @Test
    void repeatingFailureStopsFurtherExecutionAndPreservesCause() {
        Environment env = new Environment();
        IllegalArgumentException failure = new IllegalArgumentException("bad task");
        TaskHandle handle = env.scheduler.runGlobalAtFixedRate(1, 20, () -> { throw failure; });
        assertSame(failure, assertThrows(IllegalArgumentException.class, env.calls.getFirst()::run));
        assertTrue(env.calls.getFirst().cancelled);
        CompletionException result = assertThrows(CompletionException.class, () -> handle.completion().toCompletableFuture().join());
        assertSame(failure, result.getCause());
        env.calls.getFirst().run();
    }

    @Test
    void durationsKeepTheirPrecisionAndRepeatingTasksRemainPending() {
        Environment env = new Environment();
        TaskHandle handle = env.scheduler.runAsyncAtFixedRate(Duration.ZERO, Duration.ofNanos(1234), () -> {});
        Call call = env.calls.getFirst();
        assertEquals(0L, call.args[2]);
        assertEquals(1234L, call.args[3]);
        assertEquals(TimeUnit.NANOSECONDS, call.args[4]);
        call.run();
        call.run();
        assertFalse(handle.isDone());
        handle.cancel();
        assertTrue(handle.isCancelled());
    }

    @Test
    void rejectsInvalidDelaysAndSkipsDisabledPluginWork() {
        Environment env = new Environment();
        assertThrows(IllegalArgumentException.class, () -> env.scheduler.runGlobalDelayed(0, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> env.scheduler.runAsyncDelayed(Duration.ofSeconds(-1), () -> {}));
        assertThrows(IllegalArgumentException.class, () -> env.scheduler.runAsyncAtFixedRate(Duration.ZERO, Duration.ZERO, () -> {}));
        TaskHandle handle = env.scheduler.runEntity(env.entity, () -> fail("Disabled plugin ran"));
        env.enabled = false;
        env.calls.getFirst().run();
        assertTrue(handle.isCancelled());
        assertThrows(IllegalStateException.class, () -> env.scheduler.runAsync(() -> {}));
    }

    private static final class Environment {
        final List<Call> calls = new ArrayList<>();
        boolean enabled = true;
        boolean rejectEntity;
        boolean immediate;
        Runnable beforeReturn = () -> {};
        final EntityScheduler entityScheduler = scheduler(EntityScheduler.class, "entity");
        final RegionScheduler regionScheduler = scheduler(RegionScheduler.class, "region");
        final GlobalRegionScheduler globalScheduler = scheduler(GlobalRegionScheduler.class, "global");
        final AsyncScheduler asyncScheduler = scheduler(AsyncScheduler.class, "async");
        final Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getRegionScheduler" -> regionScheduler;
            case "getGlobalRegionScheduler" -> globalScheduler;
            case "getAsyncScheduler" -> asyncScheduler;
            default -> null;
        });
        final Plugin plugin = proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "isEnabled" -> enabled;
            case "getServer" -> server;
            default -> null;
        });
        final Entity entity = proxy(Entity.class, (p, m, a) -> m.getName().equals("getScheduler") ? entityScheduler : null);
        final PluginScheduler scheduler = new PluginScheduler(plugin);

        private <T> T scheduler(Class<T> type, String context) {
            return proxy(type, (p, m, args) -> {
                if (context.equals("entity") && rejectEntity) return null;
                Call call = new Call(context, args);
                calls.add(call);
                if (immediate) call.run();
                beforeReturn.run();
                return call.nativeTask;
            });
        }
    }

    private static final class Call {
        final String context;
        final Object[] args;
        Consumer<ScheduledTask> action;
        Runnable retired;
        boolean cancelled;
        final ScheduledTask nativeTask = proxy(ScheduledTask.class, (p, m, a) -> {
            if (m.getName().equals("cancel")) cancelled = true;
            return null;
        });

        @SuppressWarnings("unchecked")
        Call(String context, Object[] args) {
            this.context = context;
            this.args = args;
            for (Object arg : args) {
                if (arg instanceof Consumer<?> consumer) action = (Consumer<ScheduledTask>) consumer;
                if (arg instanceof Runnable runnable) retired = runnable;
            }
        }

        void run() { action.accept(nativeTask); }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
