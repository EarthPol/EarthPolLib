package com.earthpol.earthpollib.cooldown;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>PlayerCooldownManager</h1>
 *
 * <p>A lightweight, thread-safe manager for player cooldowns.
 * Each player maps to an absolute {@code readyAt} timestamp (epoch milliseconds)
 * indicating when they may perform the gated action again.</p>
 *
 * <h2>Key characteristics</h2>
 * <ul>
 *   <li><b>Time base:</b> Internally stores absolute epoch <em>milliseconds</em>.</li>
 *   <li><b>Public API units:</b>
 *     <ul>
 *       <li>Second-based helpers: {@link #setCooldown(Player, Long)} and {@link #getCooldown(Player)}.</li>
 *       <li>Millisecond-precision helpers: {@link #setCooldownMilliseconds(Player, Long)} and
 *           {@link #getCooldownMilliseconds(Player)}.</li>
 *     </ul>
 *   </li>
 *   <li><b>Delegation:</b> The second-based methods <em>delegate</em> to the millisecond methods:
 *       {@link #setCooldown(Player, Long)} → {@link #setCooldownMilliseconds(Player, Long)},
 *       and {@link #getCooldown(Player)} ← {@link #getCooldownMilliseconds(Player)} (with ceil conversion).</li>
 *   <li><b>Rounding:</b> {@link #getCooldown(Player)} reports remaining time rounded <b>up</b> to the next whole second.
 *       {@link #getCooldownMilliseconds(Player)} returns raw milliseconds with <b>no</b> rounding.</li>
 *   <li><b>Thread-safety:</b> Backed by a {@link java.util.concurrent.ConcurrentHashMap}; safe for use
 *       from multiple threads (e.g., Folia entity threads) without external locking.</li>
 *   <li><b>Lazy cleanup:</b> Expired entries are removed on-demand per player via {@link #lazyUpdate(UUID)}.
 *       A best-effort full sweep is available via {@link #lazyUpdateAll()} if you want occasional housekeeping,
 *       but it is not required for correctness.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * PlayerCooldownManager mgr = new PlayerCooldownManager();
 *
 * // Start/reset a 5-second cooldown (seconds API delegates to milliseconds API)
 * mgr.setCooldown(player, 5L);
 *
 * // Gate an action (seconds, rounded up)
 * if (mgr.hasCooldown(player)) {
 *     Long secsLeft = mgr.getCooldown(player); // e.g., 3 (rounded up)
 *     player.sendMessage("Please wait " + secsLeft + "s.");
 *     return;
 * }
 *
 * // Or use millisecond precision
 * mgr.setCooldownMilliseconds(player, 4500L);
 * Long msLeft = mgr.getCooldownMilliseconds(player); // raw ms (e.g., 1723), or null if none
 * }</pre>
 *
 * <h2>Concurrency notes</h2>
 * <ul>
 *   <li>{@link #lazyUpdate(UUID)} uses a per-key atomic operation to drop expired entries.</li>
 *   <li>{@link #getCooldownMilliseconds(Player)} / {@link #hasCooldown(Player)} remove expired entries
 *       with {@code remove(id, expectedValue)} to avoid racing another thread that might extend the cooldown.</li>
 *   <li>{@link #lazyUpdateAll()} is weakly consistent (as per {@link java.util.concurrent.ConcurrentHashMap})
 *       and intended only for optional cleanup passes.</li>
 * </ul>
 *
 * <h2>Edge cases</h2>
 * <ul>
 *   <li>Non-positive durations (≤ 0) create an immediately-expiring cooldown.</li>
 *   <li>{@link #getCooldown(Player)} and {@link #getCooldownMilliseconds(Player)} return {@code null}
 *       when no cooldown is active or when the cooldown has just expired (they also remove the entry).</li>
 * </ul>
 */

@SuppressWarnings("unused")
public class PlayerCooldownManager {

    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public void setCooldown(@NotNull Player p, @NotNull Long seconds) {
        Objects.requireNonNull(seconds, "setCooldown() seconds is null");
        setCooldownMilliseconds(p, toMilliseconds(seconds));
    }

    public @Nullable Long getCooldown(@NotNull Player p) {
        final Long remainingMs = getCooldownMilliseconds(p);
        return (remainingMs == null) ? null : toSecondsCeil(remainingMs);
    }

    public void setCooldownMilliseconds(@NotNull Player p, @NotNull Long milliseconds) {
        Objects.requireNonNull(p, "setCooldownMilliseconds() player is null");
        Objects.requireNonNull(milliseconds, "setCooldownMilliseconds() milliseconds is null");
        if (milliseconds <= 0L) {return;}
        final UUID id = p.getUniqueId();
        final long readyAt = System.currentTimeMillis() + Math.max(0L, milliseconds);
        cooldowns.put(id, readyAt);
    }

    public @Nullable Long getCooldownMilliseconds(@NotNull Player p) {
        Objects.requireNonNull(p, "getCooldownMilliseconds() player is null");
        final UUID id = p.getUniqueId();
        lazyUpdate(id);

        final Long readyAt = cooldowns.getOrDefault(id,null);
        if (readyAt == null) return null;

        final long remainingMs = readyAt - System.currentTimeMillis();
        if (remainingMs <= 0L) {
            cooldowns.remove(id, readyAt);
            return null;
        }
        return remainingMs;
    }


    public boolean hasCooldown(@NotNull Player p) {
        Objects.requireNonNull(p, "hasCooldown() player is null");
        final UUID id = p.getUniqueId();
        lazyUpdate(id);

        final Long readyAt = cooldowns.getOrDefault(id,null);
        if (readyAt == null) return false;

        if (System.currentTimeMillis() >= readyAt) {
            cooldowns.remove(id, readyAt);
            return false;
        }
        return true;
    }

    public void clearCooldown(@NotNull Player p) {
        Objects.requireNonNull(p, "clearCooldown() player is null");
        cooldowns.remove(p.getUniqueId());
    }

    private void lazyUpdate(@NotNull UUID id) {
        Objects.requireNonNull(id, "lazyUpdate(uuid) uuid is null");
        final long now = System.currentTimeMillis();
        cooldowns.computeIfPresent(
                id,
                (k, readyAt) -> (readyAt == null || now >= readyAt) ? null : readyAt
        );
    }

    @SuppressWarnings("unused")
    private void lazyUpdateAll() {
        if (cooldowns.isEmpty()) return;
        final long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(e -> {
            Long readyAt = e.getValue();
            return readyAt == null || now >= readyAt;
        });
    }

    @Contract(pure = true)
    private static @NotNull Long toMilliseconds(@NotNull Long seconds) {
        Objects.requireNonNull(seconds, "toMilliseconds() seconds is null");
        return seconds * 1000L;
    }

    private static @NotNull Long toSecondsCeil(@NotNull Long milliseconds) {
        Objects.requireNonNull(milliseconds, "toSecondsCeil() milliseconds is null");
        return Math.max(0L, (milliseconds + 999L) / 1000L);
    }
}
