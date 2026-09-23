package com.earthpol.earthpollib.entity;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class EntitySchedulerUtil {

    private EntitySchedulerUtil() {
    }

    public static void run(@NotNull Plugin plugin, @NotNull Entity entity, @NotNull Runnable task) {
        run(plugin, entity, task, null);
    }

    public static void run(
        @NotNull Plugin plugin,
        @NotNull Entity entity,
        @NotNull Runnable task,
        Runnable retiredTask
    ) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), retiredTask);
    }

    public static void runDelayed(@NotNull Plugin plugin, @NotNull Entity entity, long delayTicks,
                                  @NotNull Runnable task) {
        runDelayed(plugin, entity, delayTicks, task, null);
    }

    public static void runDelayed(
        @NotNull Plugin plugin,
        @NotNull Entity entity,
        long delayTicks,
        @NotNull Runnable task,
        Runnable retiredTask
    ) {
        entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), retiredTask, delayTicks);
    }

    public static @Nullable ScheduledTask runAtFixedRate(
        @NotNull Plugin plugin,
        @NotNull Entity entity,
        long initialDelayTicks,
        long periodTicks,
        @NotNull Consumer<ScheduledTask> task
    ) {
        return runAtFixedRate(plugin, entity, initialDelayTicks, periodTicks, task, null);
    }

    public static @Nullable ScheduledTask runAtFixedRate(
        @NotNull Plugin plugin,
        @NotNull Entity entity,
        long initialDelayTicks,
        long periodTicks,
        @NotNull Consumer<ScheduledTask> task,
        Runnable retiredTask
    ) {
        return entity.getScheduler().runAtFixedRate(plugin, task, retiredTask, initialDelayTicks, periodTicks);
    }
}
