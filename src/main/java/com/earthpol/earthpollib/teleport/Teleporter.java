package com.earthpol.earthpollib.teleport;

import com.earthpol.earthpollib.entity.EntitySchedulerUtil;
import com.earthpol.earthpollib.entity.vehicle.VehicleUtil;
import com.earthpol.earthpollib.location.LocationUtil;
import com.earthpol.earthpollib.logging.EnhancedLogger;
import com.palmergames.bukkit.towny.object.economy.Account;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable Teleporter configuration + behavior holder.
 * Build with {@link Teleporter#builder(Plugin)}.
 */
@SuppressWarnings("unused")
public final class Teleporter {

    // ---- config fields (immutable) ----

    //Plugin
    private final Plugin plugin;

    // Numeric values
    private final long warmupTicks;
    private final int protectionTicks; // TODO: Remove protectionTicks. Unused

    // Booleans
    private final boolean useWarmup;
    private final boolean useDestinationSafety;
    private final boolean useTeleportProtection;
    private final boolean allowPreTeleportMovement;

    // TextComponents
    private final @Nullable TextComponent preTeleportMessage;
    private final @Nullable TextComponent postTeleportMessage;

    // Sounds
    private final @Nullable Sound preTeleportSound;
    private final @Nullable Sound postTeleportSound;

    //Teleport cause
    private final @Nullable PlayerTeleportEvent.TeleportCause teleportCause;

    // EnumSets (Teleport flags)
    private final EnumSet<TeleportFlag.Relative> relativeTeleportFlags; // Passing teleport flags hasn't been tested yet
    private final EnumSet<TeleportFlag.EntityState> entityStateFlags;

    //TeleportOutcomeHandler
    private final TeleportOutcomeHandler outcomeHandler;

    // Post teleport effects
    private final Set<PotionEffect> postTeleportEffects;

    // Permissions check
    private final Set<String> requiredPermissions;
    private final TeleportPermissionMode teleportPermissionMode;

    // Logger
    private final @Nullable EnhancedLogger logger;

    // Vehicle Teleporter
    private final @Nullable VehicleTeleporter vehicleTeleporter;

    @Contract(pure = true)
    private Teleporter(@NotNull Builder b) {
        this.plugin = b.plugin;

        this.warmupTicks = b.warmupTicks;
        this.protectionTicks = b.protectionTicks;

        this.useWarmup = b.useWarmup;
        this.useDestinationSafety = b.useDestinationSafety;
        this.useTeleportProtection = b.useTeleportProtection;
        this.allowPreTeleportMovement = b.allowPreTeleportMovement;

        this.preTeleportMessage = b.preTeleportMessage;
        this.postTeleportMessage = b.postTeleportMessage;

        this.preTeleportSound = b.preTeleportSound;
        this.postTeleportSound = b.postTeleportSound;

        this.teleportCause = b.teleportCause;

        this.relativeTeleportFlags = EnumSet.copyOf(b.relativeTeleportFlags);
        this.entityStateFlags = EnumSet.copyOf(b.entityStateFlags);

        this.postTeleportEffects = Collections.unmodifiableSet(Set.copyOf(b.postTeleportEffects));

        this.outcomeHandler = b.outcomeHandler;

        this.requiredPermissions = Collections.unmodifiableSet(Set.copyOf(b.requiredPermissions));
        this.teleportPermissionMode = b.teleportPermissionMode;

        this.logger = b.logger;

        this.vehicleTeleporter = b.vehicleTeleporter;
    }

    @Contract(value = "_ -> new", pure = true)
    public static @NotNull Builder builder(@NotNull Plugin plugin) {
        return new Builder(Objects.requireNonNull(plugin, "plugin"));
    }

    @Contract(value = " -> new", pure = true)
    public @NotNull Builder toBuilder() { return new Builder(this); }

    // ---- getters ----

    /** @return the owning Bukkit plugin used for scheduling/logging (never null) */
    @Contract(pure = true)
    public @NotNull Plugin getPlugin() { return plugin; }

    /** @return the number of ticks to wait before teleportation begins */
    @Contract(pure = true)
    public long getWarmupTicks() { return warmupTicks; }

    /** @return the number of ticks the player is protected after teleportation */
    @Contract(pure = true)
    public int getProtectionTicks() { return protectionTicks; }

    /** @return whether warmup is enabled for teleportation */
    @Contract(pure = true)
    public boolean isUseWarmup() { return useWarmup; }

    /** @return whether destination safety checks are enabled */
    @Contract(pure = true)
    public boolean isUseDestinationSafety() { return useDestinationSafety; }

    /** @return whether post-teleport protection is enabled */
    @Contract(pure = true)
    public boolean isUseTeleportProtection() { return useTeleportProtection; }

    /** @return whether movement during the teleport warmup period is allowed */
    @Contract(pure = true)
    public boolean isAllowPreTeleportMovement() { return allowPreTeleportMovement; }

    /** @return the message shown before teleport begins, or null if none */
    @Contract(pure = true)
    public @Nullable TextComponent getPreTeleportMessage() { return preTeleportMessage; }

    /** @return the message shown after teleport completes, or null if none */
    @Contract(pure = true)
    public @Nullable TextComponent getPostTeleportMessage() { return postTeleportMessage; }

    @Contract(pure = true)
    public @Nullable Sound getPreTeleportSound() { return preTeleportSound; }

    @Contract(pure = true)
    public @Nullable Sound getPostTeleportSound() { return postTeleportSound; }

    @Contract(pure = true)
    public @Nullable PlayerTeleportEvent.TeleportCause getTeleportCause() { return teleportCause; }

    @Contract(pure = true)
    public EnumSet<TeleportFlag.Relative> getRelativeTeleportFlags() { return EnumSet.copyOf(relativeTeleportFlags); }

    @Contract(pure = true)
    public EnumSet<TeleportFlag.EntityState> getEntityStateFlags() { return EnumSet.copyOf(entityStateFlags); }

    @Contract(pure = true)
    public @Unmodifiable @NotNull Set<PotionEffect> getPostTeleportEffects() {return postTeleportEffects;}

    @Contract(pure = true)
    public @NotNull TeleportOutcomeHandler getOutcomeHandler() { return outcomeHandler; }

    @Contract(pure = true)
    public @Unmodifiable @NotNull Set<String> getRequiredPermissions() { return requiredPermissions; }

    @Contract(pure = true)
    public @NotNull TeleportPermissionMode getTeleportPermissionMode() { return teleportPermissionMode; }

    @Contract(pure = true)
    public @Nullable EnhancedLogger getLogger() { return logger; }

    @Contract(pure = true)
    public @Nullable VehicleTeleporter getVehicleTeleporter() { return vehicleTeleporter; }

    @Override
    public String toString() {
        return "Teleporter{" +
                "plugin=" + (plugin != null ? plugin.getName() : "null") +
                ", warmupTicks=" + warmupTicks +
                ", protectionTicks=" + protectionTicks +
                ", useWarmup=" + useWarmup +
                ", useDestinationSafety=" + useDestinationSafety +
                ", useTeleportProtection=" + useTeleportProtection +
                ", allowPreTeleportMovement=" + allowPreTeleportMovement +
                ", preTeleportMessage=" + preTeleportMessage +
                ", postTeleportMessage=" + postTeleportMessage +
                ", preTeleportSound=" + preTeleportSound +
                ", postTeleportSound=" + postTeleportSound +
                ", teleportCause=" + teleportCause +
                ", relativeTeleportFlags=" + relativeTeleportFlags +
                ", entityStateFlags=" + entityStateFlags +
                ", postTeleportEffects=" + postTeleportEffects +
                ", requiredPermissions=" + requiredPermissions +
                ", teleportPermissionMode=" + teleportPermissionMode +
                '}';
    }

    @Override
    @Contract(value = "null -> false", pure = true)
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Teleporter)) return false;
        Teleporter that = (Teleporter) o;
        return warmupTicks == that.warmupTicks
                && protectionTicks == that.protectionTicks
                && useWarmup == that.useWarmup
                && useDestinationSafety == that.useDestinationSafety
                && useTeleportProtection == that.useTeleportProtection
                && allowPreTeleportMovement == that.allowPreTeleportMovement
                && Objects.equals(preTeleportMessage, that.preTeleportMessage)
                && Objects.equals(postTeleportMessage, that.postTeleportMessage)
                && Objects.equals(preTeleportSound, that.preTeleportSound)
                && Objects.equals(postTeleportSound, that.postTeleportSound)
                && teleportCause == that.teleportCause
                && Objects.equals(relativeTeleportFlags, that.relativeTeleportFlags)
                && Objects.equals(entityStateFlags, that.entityStateFlags)
                && Objects.equals(postTeleportEffects, that.postTeleportEffects)
                && Objects.equals(requiredPermissions, that.requiredPermissions)
                && Objects.equals(teleportPermissionMode, that.teleportPermissionMode);


                // If adding more dont forget to bring down that semicolon lol
    }

    @Override
    @Contract(pure = true)
    public int hashCode() {
        return Objects.hash(
                warmupTicks,
                protectionTicks,
                useWarmup,
                useDestinationSafety,
                useTeleportProtection,
                allowPreTeleportMovement,
                preTeleportMessage,
                postTeleportMessage,
                preTeleportSound,
                postTeleportSound,
                teleportCause,
                relativeTeleportFlags,
                entityStateFlags,
                postTeleportEffects,
                requiredPermissions,
                teleportPermissionMode
        );
    }

    // Public Methods

    // Teleport convenience methods
    public void teleport(Player player, Location destination){
        teleport(player, destination,0,null,null,null);
    }

    public void teleport(Player player,
                         Location destination,
                         double teleportCost,
                         Account paymentTarget,
                         String paymentReason){
        teleport(player, destination, teleportCost, paymentTarget, paymentReason,null);
    }

    public void teleport(Player player, Location destination, Object customData){
        teleport(player, destination,0,null,null,customData);
    }


    @Contract("null, _, _, _, _,_ -> fail; _, null, _, _, _,_ -> fail")
    public void teleport(Player player,
                         Location destination,
                         double teleportCost,
                         @Nullable Account paymentTarget,
                         @Nullable String paymentReason,
                         @Nullable Object customData) {

        // Null checks
        if(player == null) throw new IllegalArgumentException("Null player passed to teleport()");

        if(destination == null) throw new IllegalArgumentException("Null destination passed to teleport()");
        destination = LocationUtil.centerOnBlock(destination);

        // TeleportContext is supposed to be an ephemeral object made on a per-teleport basis.
        // Using it as custom data is not intended and behavior is undefined.
        if(customData instanceof TeleportContext){
            throw new IllegalArgumentException(
            "TeleportContext itself cannot be used as custom data: This behavior is undefined."
            );
        }

        TeleportContext teleportContext = new TeleportContext(
                player,
                destination,
                this,
                teleportCost,
                paymentTarget,
                paymentReason,
                customData
        );

        // Permission check
        //TODO: TeleportOutcome.FAILED_NO_PERMISSION
        if(!hasRequiredPermissions(player)){
            player.sendMessage(Component.text("You do not have the permission(s) to use this teleport.",
                    NamedTextColor.RED));
            outcomeHandler.runAction(TeleportOutcome.FAILED_NO_PERMISSION, teleportContext);
            return;
        }

        // pre UX (entity thread is safe for Audience APIs)
        if (preTeleportMessage != null) player.sendMessage(preTeleportMessage);
        if (preTeleportSound != null)   player.playSound(preTeleportSound);

        if ((useWarmup) && warmupTicks > 0) {startWarmup(player, destination,teleportContext);}
        else {checkDestinationSafetyThenTeleport(player, destination, teleportContext);}

    }

    // ============================================================
    // Helpers
    // ============================================================

    // Teleport Helpers

    /* -----------------------------
       Warmup phase (entity thread)
       ----------------------------- */
    private void startWarmup(@NotNull Player player, @NotNull Location destination, TeleportContext teleportContext) {
        final Location start = player.getLocation().clone();

        if (allowPreTeleportMovement) {
            EntitySchedulerUtil.runDelayed(
                    plugin,
                    player,
                    warmupTicks,
                    () -> checkDestinationSafetyThenTeleport(player, destination, teleportContext),
                    () -> outcomeHandler.runAction(TeleportOutcome.FAILED_ERROR, teleportContext)
            );
            return;
        }

        final long[] elapsedTicks = {0L};
        EntitySchedulerUtil.runAtFixedRate(
                plugin,
                player,
                1L,
                1L,
                scheduledTask -> {
                    if (!LocationUtil.compareLocationXYZ(start, player.getLocation())) {
                        scheduledTask.cancel();
                        player.sendMessage(Component.text("Teleport canceled due to movement.", NamedTextColor.RED));
                        // TODO: TESTING: TeleportOutcome.FAILED_WARMUP_MOVED
                        outcomeHandler.runAction(TeleportOutcome.FAILED_WARMUP_MOVED, teleportContext);
                        return;
                    }

                    elapsedTicks[0]++;
                    if (elapsedTicks[0] < warmupTicks) {
                        return;
                    }

                    scheduledTask.cancel();
                    checkDestinationSafetyThenTeleport(player, destination, teleportContext);
                },
                () -> outcomeHandler.runAction(TeleportOutcome.FAILED_ERROR, teleportContext)
        );
    }

    /* -----------------------------------------
   Safety check on the DESTINATION region
   ----------------------------------------- */
    private void checkDestinationSafetyThenTeleport(@NotNull Player player,
                                                    @NotNull Location destination,
                                                    TeleportContext teleportContext) {
        if (!useDestinationSafety) {
            doTeleportOnEntityThread(player, destination, teleportContext);
            return;
        }

        // Hop to the destination region thread to read blocks safely
        Bukkit.getRegionScheduler().execute(plugin, destination, () -> {
            boolean safe = LocationUtil.isSafeLocation(destination);
            // Switch back to the player entity scheduler to continue
            EntitySchedulerUtil.run(
                    plugin,
                    player,
                    () -> {
                        if (safe) {
                            doTeleportOnEntityThread(player, destination, teleportContext);
                        }
                        else {
                            // choose: either fail or findNearestSafeSpot() (region work) then teleport
                            player.sendMessage(Component.text("Destination location is not safe for teleport. " +
                                    "It may be on a non-solid block or obstructed.",NamedTextColor.RED));
                            // TODO: TESTING: TeleportOutcome.FAILED_DEST_UNSAFE
                            outcomeHandler.runAction(TeleportOutcome.FAILED_DEST_UNSAFE, teleportContext);
                        }
                    },
                    () -> outcomeHandler.runAction(TeleportOutcome.FAILED_ERROR, teleportContext)
            );
        });
    }

    /* -----------------------------------------
   Actual teleport + post actions (entity thread)
   ----------------------------------------- */
    private void doTeleportOnEntityThread(@NotNull Player player,
                                          @NotNull Location destination,
                                          @NotNull TeleportContext teleportContext) {

        // Build flags array (Relative + EntityState both implement TeleportFlag)
        TeleportFlag[] flags = buildTeleportFlagsArray();

        PlayerTeleportEvent.TeleportCause cause =
                teleportCause != null ? teleportCause : PlayerTeleportEvent.TeleportCause.PLUGIN;

        // Pre-teleport affordability check.
        boolean paymentRequired = teleportContext.paymentRequired();
        double teleportCost = teleportContext.getTeleportCost();
        if(paymentRequired){
            if(!teleportContext.canAffordTeleport()){
                player.sendMessage(Component.text("You can't afford this teleport. Cost: " + teleportCost,
                        NamedTextColor.RED));
                outcomeHandler.runAction(TeleportOutcome.FAILED_UNAFFORDABLE, teleportContext);
                return;
            }
        }

        /* Prep the player for teleporting with their vehicle, if set.
         This dismounts the player from their vehicle, since calling teleportAsync while a
         player is riding a vehicle results in the teleport failing. (Outcome: FAILED_ERROR) (This has been tested)

         To complete a vehicle teleport:
         - This Teleporter's vehicleTeleporter must be set (not null)
         - Player must have been riding a vehicle at the time the teleport was initiated
          (initiatedWhileRidingVehicle is true)
         - Player must be riding that same vehicle by the time the warmup period ends.
         */
        Entity playerVehicle = teleportContext.getPlayerVehicle();
        boolean usingVehicleTeleport = this.vehicleTeleporter != null;
        boolean initiatedWhileRidingVehicle = playerVehicle != null;
        boolean isCurrentlyRidingSameVehicle = VehicleUtil.isOnboardVehicle(player, playerVehicle);

        boolean eligibleForVehicleTeleport;
        if(usingVehicleTeleport && initiatedWhileRidingVehicle && isCurrentlyRidingSameVehicle) {

            if(vehicleTeleporter.mustBeTheDriver() && !VehicleUtil.isDriverOfVehicle(player, playerVehicle)) {
                player.sendMessage(
                        Component.text("You must be the driver to initiate this teleport.",NamedTextColor.RED)
                );
                return;
            }
            playerVehicle.removePassenger(player); // Dismount initiating to avoid issues
            eligibleForVehicleTeleport = true;
        }
        else eligibleForVehicleTeleport = false;

        player.teleportAsync(destination, cause, flags).whenComplete(
                (success, throwable) -> {
                    EntitySchedulerUtil.run(
                            plugin,
                            player,
                            () -> {
                                if (throwable != null || !Boolean.TRUE.equals(success)) {
                                    restoreInitiatingPlayerMount(playerVehicle, player, eligibleForVehicleTeleport);
                                    player.sendMessage(Component.text("Teleport failed.", NamedTextColor.RED));
                                    if (throwable != null) {
                                        logTeleportFailure("teleportAsync() threw an exception for " + player.getName(), throwable);
                                    }
                                    // TODO: TESTING: TeleportOutcome.FAILED_ERROR
                                    outcomeHandler.runAction(TeleportOutcome.FAILED_ERROR, teleportContext);
                                    return;
                                }

                                if (postTeleportMessage != null) player.sendMessage(postTeleportMessage);
                                if (postTeleportSound != null)   player.playSound(postTeleportSound);

                                // Try the teleport payment, if its required.
                                if(paymentRequired) {
                                    Account playerAccount = teleportContext.getPlayerResidentAccount();
                                    if(playerAccount == null)
                                        throw new IllegalStateException("A payment was attempted while the player's resident economy account is null.");
                                    boolean paymentSuccess = playerAccount.payTo(
                                            teleportCost,
                                            teleportContext.getTargetPaymentAccount(),
                                            teleportContext.getPaymentReason()
                                    );

                                    // Payment failure SILENT/RESILLIENT FAILURE: If payments fail, don't stop executing any of the other logic
                                    // This branch will run if the player can afford the teleport, but the payment process failed.
                                    if(!paymentSuccess) {
                                        player.sendMessage(Component.text("Payment failed. Report to developer: " + teleportContext.getTeleportContextPaymentInfo()
                                                ,NamedTextColor.RED));
                                        plugin.getLogger().severe("Teleport Payment Failed.  " + teleportContext);
                                    }

                                }
                                applyPostTeleportEffects(player);

                                // Execute vehicle teleport if conditions are met
                                if(eligibleForVehicleTeleport){
                                    this.vehicleTeleporter.run(teleportContext);
                                }

                                // TeleportOutcome.SUCCESS
                                outcomeHandler.runAction(TeleportOutcome.SUCCESS, teleportContext);
                            },
                            () -> outcomeHandler.runAction(TeleportOutcome.FAILED_ERROR, teleportContext)
                    );
                }
        );
    }

    private void restoreInitiatingPlayerMount(@Nullable Entity playerVehicle,
                                              @NotNull Player player,
                                              boolean eligibleForVehicleTeleport) {
        if (!eligibleForVehicleTeleport || playerVehicle == null) {
            return;
        }

        playerVehicle.getScheduler().execute(
                plugin,
                () -> {
                    if (!playerVehicle.isValid() || !player.isValid()) {
                        return;
                    }
                    if (!playerVehicle.getPassengers().contains(player)) {
                        playerVehicle.addPassenger(player);
                    }
                },
                null,
                0
        );
    }

    private void logTeleportFailure(String message, Throwable throwable) {
        if (logger != null) {
            logger.severe(message, throwable);
        } else {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, message, throwable);
        }
    }

    // Runs on the entity thread
    private void applyPostTeleportEffects(@NotNull Player player) {
        if (postTeleportEffects.isEmpty()) return;
        player.addPotionEffects(postTeleportEffects);
    }

    private boolean hasRequiredPermissions(@NotNull Player player) {
        if(!player.isOnline()) // Make sure the player is online before checking permissions.
            throw new IllegalStateException("Can't check permissions for offline player!" +
                    "PlayerName: " + player.getName() + "UUID: " + player.getUniqueId());

        if (requiredPermissions.isEmpty()) return true;
        if (teleportPermissionMode == TeleportPermissionMode.ALL) {
            for (String p : requiredPermissions) {
                if (p == null || p.isBlank()) continue;
                if (!player.hasPermission(p)) return false;
            }
            return true;
        } else { // ANY
            for (String p : requiredPermissions) {
                if (p == null || p.isBlank()) continue;
                if (player.hasPermission(p)) return true;
            }
            return false;
        }
    }

    // Parser Helpers

    private TeleportFlag @NotNull [] buildTeleportFlagsArray() {
        TeleportFlag[] out = new TeleportFlag[relativeTeleportFlags.size() + entityStateFlags.size()];
        int i = 0;
        for (var f : relativeTeleportFlags)  out[i++] = f;
        for (var f : entityStateFlags)       out[i++] = f;
        return out;
    }




    // ============================================================
    /**Builder -- Teleporter creation static class*/
    public static final class Builder {
        // defaults
        private final Plugin plugin;

        private long warmupTicks = 0L;
        private int protectionTicks = 0;

        private boolean useWarmup = false;
        private boolean useDestinationSafety = false;
        private boolean useTeleportProtection = false;
        private boolean allowPreTeleportMovement = false;

        private TextComponent preTeleportMessage = null;
        private TextComponent postTeleportMessage = null;

        private @Nullable Sound preTeleportSound = null;
        private @Nullable Sound postTeleportSound = null;

        private @Nullable PlayerTeleportEvent.TeleportCause teleportCause = null;

        private EnumSet<TeleportFlag.EntityState> entityStateFlags = EnumSet.noneOf(TeleportFlag.EntityState.class);
        private EnumSet<TeleportFlag.Relative> relativeTeleportFlags = EnumSet.noneOf(TeleportFlag.Relative.class);

        private Set<PotionEffect> postTeleportEffects = new HashSet<PotionEffect>();

        private TeleportOutcomeHandler outcomeHandler = new TeleportOutcomeHandler();

        private Set<String> requiredPermissions = new HashSet<String>();
        private TeleportPermissionMode teleportPermissionMode = TeleportPermissionMode.ALL;

        private EnhancedLogger logger;

        private @Nullable VehicleTeleporter vehicleTeleporter = null;

        private Builder(@NotNull Plugin plugin) {
            this.plugin = plugin;
        }

        // Used for "copying" an existing Teleporter and overriding certain settings on it. Called by toBuilder()
        @Contract(pure = true)
        private Builder(@NotNull Teleporter originalTeleporter) {
            this.plugin = originalTeleporter.plugin;

            this.warmupTicks = originalTeleporter.warmupTicks;
            this.protectionTicks = originalTeleporter.protectionTicks;

            this.useWarmup = originalTeleporter.useWarmup;
            this.useDestinationSafety = originalTeleporter.useDestinationSafety;
            this.useTeleportProtection = originalTeleporter.useTeleportProtection;
            this.allowPreTeleportMovement = originalTeleporter.allowPreTeleportMovement;

            this.preTeleportMessage = originalTeleporter.preTeleportMessage;
            this.postTeleportMessage = originalTeleporter.postTeleportMessage;

            this.preTeleportSound = originalTeleporter.preTeleportSound;
            this.postTeleportSound = originalTeleporter.postTeleportSound;

            this.teleportCause = originalTeleporter.teleportCause;

            this.entityStateFlags = EnumSet.copyOf(originalTeleporter.entityStateFlags);

            this.relativeTeleportFlags = EnumSet.copyOf(originalTeleporter.relativeTeleportFlags);

            this.postTeleportEffects = new HashSet<>(originalTeleporter.postTeleportEffects);

            this.outcomeHandler = originalTeleporter.outcomeHandler;

            this.requiredPermissions = new HashSet<>(originalTeleporter.requiredPermissions);
            this.teleportPermissionMode = originalTeleporter.teleportPermissionMode;

            this.logger = originalTeleporter.logger;

            this.vehicleTeleporter = originalTeleporter.vehicleTeleporter;
        }

        // ---- fluent setters ----

        public Builder enableWarmup(long ticks) {
            if (ticks < 0) throw new IllegalArgumentException("enableWarmup() requires ticks >= 0");
            else if(ticks == 0){
                this.warmupTicks = 0L;
                this.useWarmup = false;
                return this;
            }
            this.warmupTicks = ticks;
            this.useWarmup = true;
            return this;
        }

        public Builder enablePostTeleportProtection(int ticks) {
            if (ticks < 0) throw new IllegalArgumentException("enableProtection() requires ticks >= 0");
            else if(ticks == 0){
                this.protectionTicks = 0;
                this.useTeleportProtection = false;
                return this;
            }
            this.protectionTicks = ticks;
            this.useTeleportProtection = true;
            return this;
        }

        public Builder enableDestinationSafety() {this.useDestinationSafety = true; return this;}

        public Builder disablePreTeleportMovement() {this.allowPreTeleportMovement = false; return this;}

        public Builder preTeleportMessage(TextComponent message) {this.preTeleportMessage = message; return this;}

        public Builder postTeleportMessage(TextComponent message) {this.postTeleportMessage = message; return this;}

        public Builder preTeleportSound(@Nullable Sound sound) { this.preTeleportSound = sound; return this; }

        public Builder postTeleportSound(@Nullable Sound sound) { this.postTeleportSound = sound; return this; }

        public Builder cause(@NotNull PlayerTeleportEvent.TeleportCause cause) {this.teleportCause = cause; return this;}

        public Builder setRelativeFlags(@NotNull Set<TeleportFlag.Relative> flags) {
            this.relativeTeleportFlags.clear();
            this.relativeTeleportFlags.addAll(flags);
            return this;
        }

        public Builder setEntityStateFlags(@NotNull Set<TeleportFlag.EntityState> flags) {
            this.entityStateFlags.clear();
            this.entityStateFlags.addAll(flags);
            return this;
        }


        public Builder addRelativeFlags(TeleportFlag.Relative... flags) {
            if (flags != null) for (var f : flags) if (f != null) this.relativeTeleportFlags.add(f);
            return this;
        }

        public Builder addEntityStateFlags(TeleportFlag.EntityState... flags) {
            if (flags != null) for (var f : flags) if (f != null) this.entityStateFlags.add(f);
            return this;
        }

        public Builder setPostTeleportEffects(@NotNull Set<PotionEffect> effects) {
            Objects.requireNonNull(effects, "effects");
            for (PotionEffect e : effects) {
                if (e == null) {
                    throw new NullPointerException("effects set cannot contain null elements");
                }
            }
            this.postTeleportEffects = new HashSet<>(effects);
            return this;
        }


        public Builder addPostTeleportEffects(@NotNull Set<PotionEffect> effects) {
            Objects.requireNonNull(effects, "effects");
            for (PotionEffect e : effects){
                if (e == null){throw new IllegalArgumentException("effects set cannot contain null elements");}
                else this.postTeleportEffects.add(e);
            }
            return this;
        }

        public Builder addPostTeleportEffect(@NotNull PotionEffect effect) {
            Objects.requireNonNull(effect, "effect");
            this.postTeleportEffects.add(effect);
            return this;
        }

        public Builder setOutcomeHandler(@NotNull TeleportOutcomeHandler outcomeHandler) {
            this.outcomeHandler = Objects.requireNonNull(outcomeHandler, "outcomeHandler");
            return this;
        }

        public Builder setRequiredPermissions(@NotNull Set<String> requiredPermissions,
                                              TeleportPermissionMode teleportPermissionMode) {
            Objects.requireNonNull(requiredPermissions, "requiredPermissions");
            this.requiredPermissions = new HashSet<>(requiredPermissions); // defensive, mutable
            this.teleportPermissionMode = Objects.requireNonNull(teleportPermissionMode, "teleportPermissionMode");
            return this;
        }

        public Builder setLogger(@NotNull EnhancedLogger logger) {
            this.logger = Objects.requireNonNull(logger, "logger");
            return this;
        }

        public Builder setVehicleTeleporter(@Nullable VehicleTeleporter vehicleTeleporter) {
            this.vehicleTeleporter = vehicleTeleporter;
            return this;
        }

        @Contract(" -> new")
        public @NotNull Teleporter build() {
            // Plugin null check
            // Require plugin
            if (plugin == null) throw new IllegalStateException("plugin must be set");
            // Ensure it’s enabled (useful if called during shutdown/reload)
            if (!plugin.isEnabled()) throw new IllegalStateException("plugin is not enabled: " + plugin.getName());

            // Domain checks
            if (warmupTicks < 0)     throw new IllegalArgumentException("warmupTicks < 0");
            if (protectionTicks < 0) throw new IllegalArgumentException("protectionTicks < 0");

            // Consistency: flags must match their values
            if (useWarmup && warmupTicks == 0) throw new IllegalStateException("useWarmup=true but warmupTicks==0");
            if (!useWarmup && warmupTicks > 0) throw new IllegalStateException("warmupTicks>0 but useWarmup=false");

            if (useTeleportProtection && protectionTicks == 0)
                throw new IllegalStateException("useTeleportProtection=true but protectionTicks==0");
            if (!useTeleportProtection && protectionTicks > 0)
                throw new IllegalStateException("protectionTicks>0 but useTeleportProtection=false");

            return new Teleporter(this);
        }
    }
}
