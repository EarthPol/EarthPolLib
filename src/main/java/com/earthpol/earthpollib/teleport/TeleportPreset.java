package com.earthpol.earthpollib.teleport;

import com.palmergames.bukkit.towny.object.economy.Account;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

// ================================
/**
 * Teleporter presets: Pre-configured, one-time-use Teleporter objects with no side effects.
 * */
@SuppressWarnings("unused")
public final class TeleportPreset{

    private TeleportPreset() {}
    /**
     * Performs a one-off teleport for the specified player using a temporary
     * {@link Teleporter} configuration with the following defaults:
     * <ul>
     *   <li>Warmup period of the specified duration</li>
     *   <li>Pre-teleport movement disabled</li>
     *   <li>Destination safety checks enabled</li>
     *   <li>Optional post-teleport potion effects</li>
     * </ul>
     * <p>
     * This method does not return the configured {@link Teleporter} instance —
     * it is created internally, used for the teleport, and discarded.
     * </p>
     */
    @Contract(pure = true)
    public static void safeTeleportWithWarmup(Player player,
                                              Location destination,
                                              long warmupTicks,
                                              @Nullable Set<PotionEffect> postTeleportEffects,
                                              Plugin plugin) {

        // Can't teleport to a null destination
        if (destination == null)
            throw new IllegalArgumentException("Null destination passed to safeTeleportWithWarmup()");

        // If no potion effects, give the teleporter an empty set.
        if (postTeleportEffects == null) postTeleportEffects = Collections.emptySet();

        Teleporter safeTeleportWithWarmup =
                Teleporter.builder(Objects.requireNonNull(plugin,"plugin"))
                        .enableWarmup(warmupTicks)
                        .disablePreTeleportMovement()
                        .enableDestinationSafety()
                        .setPostTeleportEffects(postTeleportEffects)
                        .build();

        safeTeleportWithWarmup.teleport(player, destination);
    }

    /**
     * Convenience preset for performing a safe teleport with a warmup period and a teleportation cost.
     */
    @Contract(pure = true)
    public static void safeTeleportWithWarmupAndCost(Player player,
                                                     Location destination,
                                                     long warmupTicks,
                                                     @Nullable Set<PotionEffect> postTeleportEffects,
                                                     double cost,
                                                     Account paymentTarget,
                                                     @Nullable String paymentReason,
                                                     Plugin plugin) {

        // Can't teleport to a null destination
        if (destination == null)
            throw new IllegalArgumentException("Null destination passed to safeTeleportWithWarmupAndCost()");

        // If no potion effects, give the teleporter an empty set.
        if (postTeleportEffects == null) postTeleportEffects = Collections.emptySet();

        // Can't pay to a null account
        if(paymentTarget == null)
            throw new IllegalArgumentException("Null paymentTarget passed to safeTeleportWithWarmupAndCost()");

        Teleporter safeTeleportWithWarmupAndCost =
                Teleporter.builder(Objects.requireNonNull(plugin,"plugin"))
                        .enableWarmup(warmupTicks)
                        .disablePreTeleportMovement()
                        .enableDestinationSafety()
                        .setPostTeleportEffects(postTeleportEffects)
                        .build();
        safeTeleportWithWarmupAndCost.teleport(player,destination,cost,paymentTarget,paymentReason);
    }

    /**
     * Convenience preset for performing a safe teleport with a warmup period and a teleportation cost while
     * integrating Towny's ConfirmationOnAccept confirmation chat text box function.
     */
    @Contract(pure = true)
    public static void townyconfirmation_safeTeleportWithWarmupAndCost(Player player,
                                                                       Location destination,
                                                                       long warmupTicks,
                                                                       @Nullable Set<PotionEffect> postTeleportEffects,
                                                                       double cost,
                                                                       Plugin plugin) {

        //Can't teleport to a null destination
        if (destination == null)
            throw new IllegalArgumentException("Null destination passed to townyconfirmation_safeTeleportWithWarmupAndCost()");

        // TODO: Run the towny Confirmation on accept here and wrap safeTeleportWithWarmupAndCost() in it.


    }

}
