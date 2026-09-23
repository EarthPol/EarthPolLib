package com.earthpol.earthpollib.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for use within command classes.
 */
@SuppressWarnings("unused")
public final class CommandUtil {
    private CommandUtil() {}

    private static final Component mustBeAPlayer =
            Component.text("You must be a player to use this command.", NamedTextColor.RED);

    /**
     * Server Operators will be considered as having permission.
     * @param sender
     * @param permission
     * @param noPermissionMessage
     * @return
     */
    public static boolean isPlayerAndHasPermission(CommandSender sender, String permission, @Nullable Component noPermissionMessage) {

        if(!(sender instanceof Player player)) {
            sender.sendMessage(mustBeAPlayer);
            return false;
        }

        if(player.hasPermission(permission) || player.isOp()) {return true;}
        else {
            if(noPermissionMessage != null) {player.sendMessage(noPermissionMessage);}
            return false;
        }
    }

    public static boolean isPlayerAndHasPermission(CommandSender sender, String permission) {
        return isPlayerAndHasPermission(sender, permission, null);
    }
}
