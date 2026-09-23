package com.earthpol.earthpollib.teleport;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@SuppressWarnings("unused")
/**
 * ALL teleports done in this class will assume that the player who has initiated the teleport
 * has already successfully teleported to the destination.
 */
public final class VehicleTeleporter {

    private static final int MAX_REBOARD_ATTEMPTS = 10;

    private final boolean mustBeTheDriver;
    private final boolean bringPassengers;

    public VehicleTeleporter(boolean mustBeTheDriver, boolean bringPassengers) {
        this.mustBeTheDriver = mustBeTheDriver;
        this.bringPassengers = bringPassengers;
    }

    public boolean mustBeTheDriver() { return mustBeTheDriver; }
    public boolean bringPassengers() { return bringPassengers; }

    // Generic Vehicle teleport handling
    private void defaultVehicleTP(TeleportContext context) {
        Entity playerVehicle = Objects.requireNonNull(
                context.getPlayerVehicle(),"Null vehicle provided to defaultVehicleTP"
        );
        Location destination = context.getDestination();
        vehicleTeleport(
                context.getTeleporter().getPlugin(),
                context.getPlayer(),
                playerVehicle,
                destination,
                destination,
                this.bringPassengers
        );
    }

    // Happy ghast teleport handling
    // Teleport the players above the happy ghast
    private void happyGhastTP(TeleportContext context) {
        Entity playerVehicle = Objects.requireNonNull(
                context.getPlayerVehicle(),"Null vehicle provided to happyGhastTP"
        );
        Location destination = context.getDestination();
        Location aboveHappyGhast = destination.clone().add(0, 6, 0);
        vehicleTeleport(
                context.getTeleporter().getPlugin(),
                context.getPlayer(),
                playerVehicle,
                aboveHappyGhast,
                destination,
                this.bringPassengers
        );
    }

    private static void vehicleTeleport(
            Plugin plugin,
            @NotNull Player initiatingPlayer,
            @NotNull Entity vehicle,
            Location playerTeleportLocation,
            Location vehicleTeleportLocation,
            boolean bringPassengers
    ){
        vehicle.getScheduler().execute(
                plugin,
                () -> {
                    List<Entity> vehiclePassengers =  List.copyOf(vehicle.getPassengers());
                    // The initiating player is removed from the vehicle before this method is invoked,
                    // so vehiclePassengers cannot be used to decide if they should be reboarded.
                    boolean shouldReboardInitiatingPlayer =
                            playerTeleportLocation.equals(vehicleTeleportLocation);

                    // Always dismount all passengers
                    if (!vehiclePassengers.isEmpty()) {
                        for(Entity entity : vehiclePassengers) {
                            vehicle.removePassenger(entity);
                        }
                    }

                    /* Note: This teleport will result in the initiating player being teleported twice. Once in Teleporter,
                    and again for this call. To not overcomplicate the code, it was left this way
                    since it's relatively harmless. Separation of playerTeleportLocation is currently only
                    used so that a player can be teleported above their vehicle upon arrival if needed.
                    (such as with Happy Ghasts)
                     */
                    CompletableFuture<Boolean> initiatingPlayerTeleport =
                            initiatingPlayer.teleportAsync(playerTeleportLocation);

                    CompletableFuture<Boolean> vehicleTeleport = vehicle.teleportAsync(vehicleTeleportLocation);

                    List<Entity> passengersToReboard = new ArrayList<>();
                    List<CompletableFuture<Boolean>> teleportFutures = new ArrayList<>();
                    teleportFutures.add(vehicleTeleport);

                    if (shouldReboardInitiatingPlayer) {
                        teleportFutures.add(initiatingPlayerTeleport);
                        passengersToReboard.add(initiatingPlayer);
                    }

                    // If bring passengers is true, also teleport the passengers
                    if(bringPassengers) {
                        for(Entity entity : vehiclePassengers) {
                            if (entity.equals(initiatingPlayer) && shouldReboardInitiatingPlayer) continue;

                            CompletableFuture<Boolean> passengerTeleport =
                                    entity.teleportAsync(vehicleTeleportLocation);

                            teleportFutures.add(passengerTeleport);
                            passengersToReboard.add(entity);
                        }
                    }

                    CompletableFuture.allOf(teleportFutures.toArray(CompletableFuture[]::new))
                            .handle((ignored, throwable) -> {
                                if (throwable == null && allTeleportsSucceeded(teleportFutures)) {
                                    scheduleReboardAttempt(plugin, vehicle, passengersToReboard, 1);
                                }
                                return null;
                            });
                },
                null,
                0
        );
    }

    private static boolean allTeleportsSucceeded(List<CompletableFuture<Boolean>> teleportFutures) {
        for (CompletableFuture<Boolean> teleportFuture : teleportFutures) {
            try {
                if (!Boolean.TRUE.equals(teleportFuture.join())) {
                    return false;
                }
            } catch (CancellationException | CompletionException ex) {
                return false;
            }
        }
        return true;
    }

    private static void scheduleReboardAttempt(
            @NotNull Plugin plugin,
            @NotNull Entity vehicle,
            @NotNull List<Entity> desiredPassengers,
            int attempt
    ) {
        vehicle.getScheduler().execute(
                plugin,
                () -> tryReboardPassengers(plugin, vehicle, desiredPassengers, attempt),
                null,
                1
        );
    }

    private static void tryReboardPassengers(
            @NotNull Plugin plugin,
            @NotNull Entity vehicle,
            @NotNull List<Entity> desiredPassengers,
            int attempt
    ) {
        if (!vehicle.isValid() || desiredPassengers.isEmpty()) return;

        List<Entity> pendingPassengers = new ArrayList<>();

        for (Entity passenger : desiredPassengers) {
            if (!passenger.isValid() || passenger.equals(vehicle)) continue;

            if (vehicle.getPassengers().contains(passenger)) continue;

            boolean boarded = vehicle.addPassenger(passenger);
            if (!boarded) {
                pendingPassengers.add(passenger);
            }
        }

        if (!pendingPassengers.isEmpty() && attempt < MAX_REBOARD_ATTEMPTS) {
            scheduleReboardAttempt(plugin, vehicle, pendingPassengers, attempt + 1);
        }
    }

    /**
     *
     * @param teleportContext TeleportContext being used (provided by the Teleporter object)
     */
    void run(@NotNull TeleportContext teleportContext) {
        Entity playerVehicle = teleportContext.getPlayerVehicle();
        if(playerVehicle == null) return;

        switch (playerVehicle.getType()) {
            case HAPPY_GHAST -> happyGhastTP(teleportContext);
            default -> defaultVehicleTP(teleportContext);
        }
    }
}
