package com.earthpol.earthpollib.teleport;

/**
 * This enum is used to describe how permissions from the Set<String> requiredPermissions field of
 * Teleporter are validated.
 * ALL - All permissions in the permissions list are required.
 * ANY - Only one of the permissions in the permissions list is required.
 */
public enum TeleportPermissionMode {
    ALL,
    ANY;

    @Override
    public String toString() { return name(); }
}
