package com.earthpol.earthpollib.teleport;

/**
 * Defines potential outcomes of a teleport.
 * SUCCESS - Teleport proceeded normally
 * FAILED_DEST_UNSAFE - Destination was unsafe.
 * FAILED_WARMUP_MOVED - Player moved during the warmup period.
 * FAILED_UNAFFORDABLE - Player couldn't afford the cost to do the teleport.
 * FAILED_ERROR - Player.teleportAsync() was not successful.
 * FAILED_NO_PERMISSION - Player does not have permission as per Player.hasPermission()
 */
public enum TeleportOutcome {
    SUCCESS,
    FAILED_DEST_UNSAFE,
    FAILED_WARMUP_MOVED,
    FAILED_UNAFFORDABLE,
    FAILED_NO_PERMISSION,
    FAILED_ERROR;


    /**
     *
     * @return Name of this enum value in String form
     */
    @Override public String toString() { return name(); }

}
