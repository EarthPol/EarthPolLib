package com.earthpol.earthpollib.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandUtilTest {
    @Test
    void playerAndPermissionChecksPreserveLegacyOperatorBehavior() {
        List<Component> messages = new ArrayList<>();
        CommandSender console = sender(false, true, true, messages);
        assertFalse(CommandUtil.isPlayerAndHasPermission(console, "example.use"));
        assertEquals(1, messages.size());
        assertTrue(CommandUtil.isPlayerAndHasPermission(sender(true, false, true, messages), "example.use"));
        assertFalse(CommandUtil.isPlayerAndHasPermission(sender(true, false, false, messages), "example.use"));
        assertEquals(1, messages.size());
        Component denied = Component.text("Denied");
        assertFalse(CommandUtil.hasPermission(sender(true, false, false, messages), "example.use", denied));
        assertEquals(denied, messages.getLast());
        assertNull(CommandUtil.requirePlayer(console, null));
    }

    @Test
    void validatesNumbersAndReportsMalformedOverflowingAndOutOfRangeInput() {
        List<Component> errors = new ArrayList<>();
        CommandSender sender = sender(false, true, false, errors);
        Component error = Component.text("Enter 1 through 10");
        assertEquals(1, CommandUtil.parseInteger(sender, "1", 1, 10, error).orElseThrow());
        assertEquals(10, CommandUtil.parseInteger(sender, "10", 1, 10, error).orElseThrow());
        for (String input : List.of("0", "11", "word", "2147483648", "1.5")) {
            assertTrue(CommandUtil.parseInteger(sender, input, 1, 10, error).isEmpty());
        }
        assertEquals(5, errors.size());
        assertThrows(IllegalArgumentException.class, () -> CommandUtil.parseInteger(sender, "1", 10, 1, error));
    }

    private static CommandSender sender(boolean player, boolean allowed, boolean op, List<Component> messages) {
        Class<?> type = player ? Player.class : CommandSender.class;
        return (CommandSender) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> switch (m.getName()) {
            case "hasPermission" -> allowed;
            case "isOp" -> op;
            case "sendMessage" -> { messages.add((Component) args[0]); yield null; }
            default -> null;
        });
    }
}
