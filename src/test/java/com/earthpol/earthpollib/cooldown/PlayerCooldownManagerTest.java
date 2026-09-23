package com.earthpol.earthpollib.cooldown;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCooldownManagerTest {

    @Test
    void cooldownLifecycleWorksForMillisecondsApi() throws InterruptedException {
        PlayerCooldownManager manager = new PlayerCooldownManager();
        Player player = player(UUID.randomUUID());

        manager.setCooldownMilliseconds(player, 50L);

        assertTrue(manager.hasCooldown(player));
        assertNotNull(manager.getCooldownMilliseconds(player));

        Thread.sleep(75L);

        assertFalse(manager.hasCooldown(player));
        assertNull(manager.getCooldownMilliseconds(player));
    }

    @Test
    void secondApiRoundsUpRemainingTime() {
        PlayerCooldownManager manager = new PlayerCooldownManager();
        Player player = player(UUID.randomUUID());

        manager.setCooldownMilliseconds(player, 1L);

        Long remainingSeconds = manager.getCooldown(player);

        assertNotNull(remainingSeconds);
        assertTrue(remainingSeconds >= 1L);
    }

    @Test
    void clearCooldownRemovesActiveEntry() {
        PlayerCooldownManager manager = new PlayerCooldownManager();
        Player player = player(UUID.randomUUID());

        manager.setCooldown(player, 5L);
        manager.clearCooldown(player);

        assertFalse(manager.hasCooldown(player));
        assertNull(manager.getCooldown(player));
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "toString" -> "Player[" + id + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "isPermissionSet", "hasPermission", "isOp", "isOnline", "isValid" -> false;
                    default -> null;
                }
        );
    }
}
