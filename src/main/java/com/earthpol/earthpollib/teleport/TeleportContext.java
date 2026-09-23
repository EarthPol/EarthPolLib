package com.earthpol.earthpollib.teleport;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.economy.Account;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Object representing information about a player's teleport. Used for wrapping information needed for
 * Consumers contained in TeleportOutcomeHandler.
 */
@SuppressWarnings("unused")
public final class TeleportContext {

    private final @NotNull Player player;
    private final @NotNull UUID playerUUID;
    private final @NotNull Location destination;
    private final @NotNull Teleporter teleporter;
    private final @Nullable Object customData;
    private final double teleportCost;
    private final @Nullable Account targetPaymentAccount;
    private final @NotNull String paymentReason;

    // Derived information
    private final @Nullable Resident playerResident;
    private final @Nullable Account playerResidentAccount;
    private final @Nullable Entity playerVehicle;

    /**
     * Package-private: constructed only by
     * {@link Teleporter#teleport(Player, Location, double, Account, String, Object)}.
     *
     * <p>Thread-safety: This object is immutable, but any Consumers that observe it must not assume
     * thread-safety of the wider server state. Use the appropriate Folia scheduler when acting on it.</p>
     */
     TeleportContext(
            Player player,
            Location destination,
            Teleporter teleporter,
            double teleportCost,
            @Nullable Account targetPaymentAccount,
            String paymentReason,
            @Nullable Object customData
     ) {

        // Validation checks
        if(player == null) throw new IllegalArgumentException("player cannot be null");
        if(destination == null) throw new IllegalArgumentException("destination cannot be null");
        if(teleporter == null) throw new IllegalArgumentException("teleporter cannot be null");
        if(teleportCost < 0)
             throw new IllegalArgumentException("Invalid teleport cost: " + teleportCost);
        if (targetPaymentAccount == null && teleportCost > 0)
            throw new IllegalArgumentException("Can't pay for a teleport to a null economy account!");

        this.player = player;
        this.destination = destination.clone();
        this.teleporter = teleporter;
        this.playerUUID = player.getUniqueId();
        this.customData = customData;
        this.teleportCost = teleportCost;
        this.targetPaymentAccount = targetPaymentAccount;
        this.paymentReason =  Objects.requireNonNullElse(paymentReason, "No paymentReason provided. NULL");

        Resident resident = null;
        Account residentAccount = null;
        if (teleportCost > 0D) {
            resident = TownyAPI.getInstance().getResident(player);
            if (resident == null) {
                throw new IllegalStateException("Payment required but player's resident is null: " + player.getName());
            }

            try {
                residentAccount = resident.getAccount();
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                    "Payment required but player's resident account could not be resolved: " + player.getName(),
                    exception
                );
            }

            if (residentAccount == null) {
                throw new IllegalStateException("Payment required but player's resident account is null: " + player.getName());
            }
        }
        this.playerResident = resident;
        this.playerResidentAccount = residentAccount;

        this.playerVehicle = player.getVehicle();

    }

    // Getters

    @Contract(pure = true)
    public @NotNull Player getPlayer() { return player; }

    @Contract(pure = true)
    public @NotNull UUID getPlayerUUID() { return playerUUID; }

    @Contract(pure = true)
    public @NotNull Location getDestination() { return destination.clone(); }

    @Contract(pure = true)
    public @NotNull Teleporter getTeleporter() { return teleporter; }

    @Contract(pure = true)
    public @Nullable Object getCustomData() { return customData; }

    @Contract(pure = true)
    public double getTeleportCost() { return teleportCost; }

    @Contract(pure = true)
    public @Nullable Account getTargetPaymentAccount() { return targetPaymentAccount; }

    @Contract(pure = true)
    public @NotNull String getPaymentReason() { return paymentReason; }

    @Contract(pure = true)
    public @Nullable Account getPlayerResidentAccount() { return playerResidentAccount; }

    @Contract(pure = true)
    public @Nullable Resident getPlayerResident() { return playerResident; }

    @Contract(pure = true)
    public boolean paymentRequired() { return teleportCost > 0; }

    public boolean canAffordTeleport(){
        if (!paymentRequired()) return true; // Players can always afford the teleport if it's free.
        if(this.playerResident == null)
            throw new IllegalStateException(
                    "Attempt to check canAffordTeleport(), but this TeleportContext has a NULL resident!" + this );
        if(this.playerResidentAccount == null)
            throw new IllegalStateException("Attempt to check canAffordTeleport(), but this TeleportContext has a NULL resident Account!" + this);

        return playerResidentAccount.canPayFromHoldings(teleportCost);
    }

    public @Nullable Entity getPlayerVehicle() { return playerVehicle; }

    @Override
    @Contract(pure = true)
    public String toString() {
        return "TeleportContext{" +
                "player=" + (player != null ? player.getName() : "null") +
                ", playerUUID=" + playerUUID +
                ", destination=" + formatLocation(destination) +
                ", teleporter=" + teleporter.getClass().getSimpleName() +
                ", teleportCost=" + teleportCost +
                ", targetPaymentAccount=" + (targetPaymentAccount != null ? targetPaymentAccount.getName() : "null") +
                ", paymentReason='" + paymentReason +
                ", customData=" + (customData != null ? customData.getClass().getSimpleName() : "null") +
                '}';
    }

    @Contract(pure = true)
    public @NotNull String getTeleportContextPaymentInfo() {
        return "teleportCost=" + teleportCost
                + ", targetPaymentAccount=" + (targetPaymentAccount != null ? targetPaymentAccount.getName() : "null")
                + ", destination=" + formatLocation(destination)
                + ", player=" + (player != null ? player.getName() : "null")
                + ", playerUUID=" + playerUUID
                + ", residentAccountUUID=" + (this.playerResidentAccount != null ? this.playerResidentAccount.getUUID().toString() : "null");
    }

    @Contract(pure = true)
    private static @NotNull String formatLocation(@Nullable Location loc) {
        if (loc == null) return "null";
        String world = (loc.getWorld() != null ? loc.getWorld().getName() : "null");
        return world + ":" +
                loc.getBlockX() + "," +
                loc.getBlockY() + "," +
                loc.getBlockZ() +
                " (yaw=" + loc.getYaw() + ", pitch=" + loc.getPitch() + ")";
    }
}
