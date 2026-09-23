package com.earthpol.earthpollib.command;

import com.earthpol.earthpollib.messaging.MessageStyle;
import com.earthpol.earthpollib.messaging.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

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

    /** Returns the player, or sends the supplied feedback and returns null for console senders. */
    public static @Nullable Player requirePlayer(CommandSender sender, @Nullable Component error) {
        Objects.requireNonNull(sender, "sender");
        if (sender instanceof Player player) return player;
        if (error != null) sender.sendMessage(error);
        return null;
    }

    /** Uses the same operator bypass as the original combined check. */
    public static boolean hasPermission(CommandSender sender, String permission, @Nullable Component error) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(permission, "permission");
        if (sender.hasPermission(permission) || sender.isOp()) return true;
        if (error != null) sender.sendMessage(error);
        return false;
    }

    public static boolean isPlayerAndHasPermission(CommandSender sender, String permission,
                                                   Messages messages, String playerOnlyKey, String deniedKey) {
        if (requirePlayer(sender, messages.format(sender, MessageStyle.ERROR, playerOnlyKey, Map.of())) == null) {
            return false;
        }
        return hasPermission(sender, permission,
                messages.format(sender, MessageStyle.ERROR, deniedKey, Map.of("permission", permission)));
    }

    /** Validates a whole-number argument without throwing for invalid player input. */
    public static OptionalInt parseInteger(CommandSender sender, String input, int minimum, int maximum,
                                           @Nullable Component error) {
        Objects.requireNonNull(sender, "sender");
        if (minimum > maximum) throw new IllegalArgumentException("minimum exceeds maximum");
        if (input != null) {
            try {
                int value = Integer.parseInt(input);
                if (value >= minimum && value <= maximum) return OptionalInt.of(value);
            } catch (NumberFormatException ignored) {
                // Malformed and overflowing arguments receive the same command feedback.
            }
        }
        if (error != null) sender.sendMessage(error);
        return OptionalInt.empty();
    }

    public static OptionalInt parseInteger(CommandSender sender, String input, int minimum, int maximum,
                                           Messages messages, String errorKey) {
        return parseInteger(sender, input, minimum, maximum, messages.format(sender, MessageStyle.ERROR,
                errorKey, Map.of("input", Objects.toString(input, ""), "min", minimum, "max", maximum)));
    }

    public static void sendUsage(CommandSender sender, Messages messages, String usageKey, String usage) {
        messages.send(sender, MessageStyle.INFO, usageKey, Map.of("usage", usage));
    }
}
