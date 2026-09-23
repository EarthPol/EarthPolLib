package com.earthpol.earthpollib.teleport;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleTeleporterTest {

    @Test
    void failedVehicleTeleportDoesNotAttemptReboard() throws Exception {
        Plugin plugin = plugin();
        FakeEntity player = fakePlayer("player", CompletableFuture.completedFuture(true));
        FakeEntity passenger = fakeEntity("passenger", CompletableFuture.completedFuture(true));
        FakeEntity vehicle = fakeEntity("vehicle", CompletableFuture.completedFuture(false));
        vehicle.passengers.add(passenger.proxy);

        invokeVehicleTeleport(plugin, (Player) player.proxy, vehicle.proxy,
                new Location(null, 0, 0, 0),
                new Location(null, 0, 0, 0),
                true);

        assertFalse(vehicle.passengers.contains(player.proxy));
        assertFalse(vehicle.passengers.contains(passenger.proxy));
    }

    @Test
    void successfulVehicleTeleportReboardsEligiblePassengers() throws Exception {
        Plugin plugin = plugin();
        FakeEntity player = fakePlayer("player", CompletableFuture.completedFuture(true));
        FakeEntity passenger = fakeEntity("passenger", CompletableFuture.completedFuture(true));
        FakeEntity vehicle = fakeEntity("vehicle", CompletableFuture.completedFuture(true));
        vehicle.passengers.add(passenger.proxy);

        invokeVehicleTeleport(plugin, (Player) player.proxy, vehicle.proxy,
                new Location(null, 0, 0, 0),
                new Location(null, 0, 0, 0),
                true);

        assertTrue(vehicle.passengers.contains(player.proxy));
        assertTrue(vehicle.passengers.contains(passenger.proxy));
    }

    private static void invokeVehicleTeleport(Plugin plugin,
                                              Player player,
                                              Entity vehicle,
                                              Location playerLocation,
                                              Location vehicleLocation,
                                              boolean bringPassengers) throws Exception {
        Method method = VehicleTeleporter.class.getDeclaredMethod(
                "vehicleTeleport",
                Plugin.class,
                Player.class,
                Entity.class,
                Location.class,
                Location.class,
                boolean.class
        );
        method.setAccessible(true);
        method.invoke(null, plugin, player, vehicle, playerLocation, vehicleLocation, bringPassengers);
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Plugin[TestPlugin]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static FakeEntity fakePlayer(String name, CompletableFuture<Boolean>... teleportResults) {
        return new FakeEntity(name, true, teleportResults);
    }

    private static FakeEntity fakeEntity(String name, CompletableFuture<Boolean>... teleportResults) {
        return new FakeEntity(name, false, teleportResults);
    }

    private static final class FakeEntity {
        private final String name;
        private final List<Entity> passengers = new ArrayList<>();
        private final Deque<CompletableFuture<Boolean>> teleportResults = new ArrayDeque<>();
        private final EntityScheduler scheduler;
        private final Entity proxy;
        private boolean valid = true;

        private FakeEntity(String name, boolean playerType, CompletableFuture<Boolean>... teleportResults) {
            this.name = name;
            this.scheduler = (EntityScheduler) Proxy.newProxyInstance(
                    EntityScheduler.class.getClassLoader(),
                    new Class<?>[]{EntityScheduler.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "execute" -> {
                            Runnable task = (Runnable) args[1];
                            if (task != null) {
                                task.run();
                            }
                            yield true;
                        }
                        default -> defaultValue(method.getReturnType());
                    }
            );
            for (CompletableFuture<Boolean> teleportResult : teleportResults) {
                this.teleportResults.addLast(teleportResult);
            }
            Class<?>[] interfaces = playerType ? new Class<?>[]{Player.class} : new Class<?>[]{Entity.class};
            this.proxy = (Entity) Proxy.newProxyInstance(
                    Entity.class.getClassLoader(),
                    interfaces,
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getScheduler" -> scheduler;
                        case "getPassengers" -> passengers;
                        case "removePassenger" -> passengers.remove(args[0]);
                        case "addPassenger" -> {
                            Entity passenger = (Entity) args[0];
                            if (!passengers.contains(passenger)) {
                                passengers.add(passenger);
                            }
                            yield true;
                        }
                        case "teleportAsync" -> teleportResults();
                        case "isValid" -> valid;
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> name;
                        case "getName" -> name;
                        case "isOnline" -> true;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private CompletableFuture<Boolean> teleportResults() {
            return teleportResults.isEmpty() ? CompletableFuture.completedFuture(true) : teleportResults.removeFirst();
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
