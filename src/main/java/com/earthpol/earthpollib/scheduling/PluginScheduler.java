package com.earthpol.earthpollib.scheduling;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Plugin-owned Paper/Folia tasks with explicit execution contexts. Close from onDisable.
 * Tick delays must be positive; async delays use elapsed time and may be zero.
 * Closing prevents new tasks and cancels pending/repeating work without waiting for running work.
 */
public final class PluginScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final Set<TaskHandle> tasks = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public PluginScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public TaskHandle runEntity(Entity entity, Runnable action) {
        Objects.requireNonNull(entity, "entity");
        return schedule(action, false, (run, retired) -> entity.getScheduler().run(plugin, run, retired));
    }

    public TaskHandle runEntityDelayed(Entity entity, long delayTicks, Runnable action) {
        Objects.requireNonNull(entity, "entity");
        positive(delayTicks, "delayTicks");
        return schedule(action, false, (run, retired) -> entity.getScheduler().runDelayed(plugin, run, retired, delayTicks));
    }

    public TaskHandle runEntityAtFixedRate(Entity entity, long delayTicks, long periodTicks, Runnable action) {
        Objects.requireNonNull(entity, "entity");
        positive(delayTicks, "delayTicks");
        positive(periodTicks, "periodTicks");
        return schedule(action, true, (run, retired) -> entity.getScheduler().runAtFixedRate(plugin, run, retired, delayTicks, periodTicks));
    }

    public TaskHandle runRegion(Location location, Runnable action) {
        Location target = target(location);
        return schedule(action, false, (run, retired) -> plugin.getServer().getRegionScheduler().run(plugin, target, run));
    }

    public TaskHandle runRegionDelayed(Location location, long delayTicks, Runnable action) {
        Location target = target(location);
        positive(delayTicks, "delayTicks");
        return schedule(action, false, (run, retired) -> plugin.getServer().getRegionScheduler().runDelayed(plugin, target, run, delayTicks));
    }

    public TaskHandle runRegionAtFixedRate(Location location, long delayTicks, long periodTicks, Runnable action) {
        Location target = target(location);
        positive(delayTicks, "delayTicks");
        positive(periodTicks, "periodTicks");
        return schedule(action, true, (run, retired) -> plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, target, run, delayTicks, periodTicks));
    }

    public TaskHandle runGlobal(Runnable action) {
        return schedule(action, false, (run, retired) -> plugin.getServer().getGlobalRegionScheduler().run(plugin, run));
    }

    public TaskHandle runGlobalDelayed(long delayTicks, Runnable action) {
        positive(delayTicks, "delayTicks");
        return schedule(action, false, (run, retired) -> plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, run, delayTicks));
    }

    public TaskHandle runGlobalAtFixedRate(long delayTicks, long periodTicks, Runnable action) {
        positive(delayTicks, "delayTicks");
        positive(periodTicks, "periodTicks");
        return schedule(action, true, (run, retired) -> plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, run, delayTicks, periodTicks));
    }

    public TaskHandle runAsync(Runnable action) {
        return schedule(action, false, (run, retired) -> plugin.getServer().getAsyncScheduler().runNow(plugin, run));
    }

    public TaskHandle runAsyncDelayed(Duration delay, Runnable action) {
        long nanos = nanos(delay, false);
        return schedule(action, false, (run, retired) -> plugin.getServer().getAsyncScheduler().runDelayed(plugin, run, nanos, TimeUnit.NANOSECONDS));
    }

    public TaskHandle runAsyncAtFixedRate(Duration delay, Duration period, Runnable action) {
        long delayNanos = nanos(delay, false);
        long periodNanos = nanos(period, true);
        return schedule(action, true, (run, retired) -> plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, run, delayNanos, periodNanos, TimeUnit.NANOSECONDS));
    }

    @Override
    public void close() {
        TaskHandle[] pending;
        synchronized (this) {
            if (closed) return;
            closed = true;
            pending = tasks.toArray(TaskHandle[]::new);
        }
        for (TaskHandle task : pending) task.cancel();
    }

    private TaskHandle schedule(Runnable action, boolean repeating,
                                BiFunction<Consumer<ScheduledTask>, Runnable, ScheduledTask> submit) {
        Objects.requireNonNull(action, "action");
        TaskHandle task = new TaskHandle();
        synchronized (this) {
            if (closed || !plugin.isEnabled()) throw new IllegalStateException("Plugin scheduler is closed or disabled");
            tasks.add(task);
        }
        task.completion().whenComplete((unused, failure) -> tasks.remove(task));
        try {
            if (!task.isDone()) {
                task.bind(submit.apply(nativeTask -> {
                    if (!plugin.isEnabled()) task.cancel();
                    task.execute(action, repeating);
                }, task::retire));
            }
        } catch (RuntimeException | Error ex) {
            task.fail(ex);
            throw ex;
        }
        return task;
    }

    private static Location target(Location location) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        return location.clone();
    }

    private static void positive(long ticks, String name) {
        if (ticks < 1) throw new IllegalArgumentException(name + " must be positive");
    }

    private static long nanos(Duration duration, boolean positive) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || (positive && duration.isZero())) {
            throw new IllegalArgumentException(positive ? "Period must be positive" : "Delay must not be negative");
        }
        return duration.toNanos();
    }
}
