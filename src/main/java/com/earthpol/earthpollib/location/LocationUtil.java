package com.earthpol.earthpollib.location;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Helper methods for working with locations
 */
@SuppressWarnings("unused")
public final class LocationUtil {

    /*
    Check if a location would be safe to teleport to.
    - Block below must be solid, buildable block.
    - Block at the feet position must be passable and not liquid.
    - Block at the head must be passable and not liquid.
     */
    public static boolean isSafeLocation(@NotNull Location loc) {
        Block below  = loc.getBlock().getRelative(0, -1, 0);
        Block feet   = loc.getBlock();
        Block head   = loc.getBlock().getRelative(0,1,0);

        // The block below must be solid (buildable) and NOT liquid
        if (!below.isBuildable() || below.isLiquid()) {return false;}

        // The feet position must be passable (no collision) and NOT liquid
        if (!feet.isPassable() || feet.isLiquid()) {return false;}

        // The head position must also be passable and NOT liquid
        if (!head.isPassable() || head.isLiquid()) {return false;}

        // All checks passed. Safe to teleport here.
        return true;
    }

    // Compare XYZ coordinates and the world
    public static boolean compareLocationXYZ(@NotNull Location a, @NotNull Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    // Returns the exact same location object, but centered in the middle of the block.
    public static @NotNull Location centerOnBlock(@NotNull Location location) {
        Objects.requireNonNull(location, "location");
        return new Location(
                location.getWorld(),
                location.getBlockX() + 0.5,
                location.getY(),
                location.getBlockZ() + 0.5,
                location.getYaw(),
                location.getPitch()
        );
    }
}
