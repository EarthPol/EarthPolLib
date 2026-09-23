package com.earthpol.earthpollib.messaging;

import com.earthpol.earthpollib.translation.TranslationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @TempDir Path directory;

    @Test
    void miniMessageTreatsDynamicTextLiterally() {
        Messages messages = messages(Messages.Format.MINI_MESSAGE);
        Component result = messages.component(Locale.US, "mini", Map.of("name", "<red>Town &cName"));
        assertEquals("Hello <red>Town &cName", plain(result));
        assertEquals(NamedTextColor.GREEN, result.color());
    }

    @Test
    void legacyPlaceholdersAreNotReparsedOrRecursivelyReplaced() {
        Messages messages = messages(Messages.Format.LEGACY);
        Component result = messages.component(Locale.US, "legacy", Map.of("name", "&c{other}", "other", "changed"));
        assertEquals("Hello &c{other}", plain(result));
        assertEquals("Bonjour Alice", plain(messages.component(Locale.FRANCE, "legacy", Map.of("name", "Alice"))));
    }

    @Test
    void componentPlaceholdersPreserveInteractions() {
        Component button = Component.text("Confirm").clickEvent(ClickEvent.runCommand("/confirm"));
        Component result = messages(Messages.Format.MINI_MESSAGE).component(Locale.US, "button", Map.of("button", button));
        assertEquals(button, result);
    }

    @Test
    void chatUsesPrefixAndStyleWhileActionBarsOmitPrefix() {
        List<Component> chat = new ArrayList<>();
        List<Component> bars = new ArrayList<>();
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (p, m, args) -> {
                    if (m.getName().equals("sendMessage")) chat.add((Component) args[0]);
                    if (m.getName().equals("sendActionBar")) bars.add((Component) args[0]);
                    return null;
                });
        Messages messages = messages(Messages.Format.LEGACY);
        messages.success(sender, "plain", Map.of("name", "Alice"));
        messages.actionBar(sender, MessageStyle.WARNING, "plain", Map.of("name", "Alice"));
        assertEquals("[EarthPol] Hello Alice", plain(chat.getFirst()));
        assertEquals(NamedTextColor.GREEN, chat.getFirst().children().getLast().color());
        assertEquals("Hello Alice", plain(bars.getFirst()));
        assertEquals(NamedTextColor.YELLOW, bars.getFirst().color());
    }

    private Messages messages(Messages.Format format) {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (p, m, args) -> switch (m.getName()) {
                    case "getName" -> "MessagesTest";
                    case "getDataFolder" -> directory.toFile();
                    case "getResource" -> MessagesTest.class.getClassLoader().getResourceAsStream((String) args[0]);
                    case "getLogger" -> Logger.getLogger("MessagesTest");
                    default -> null;
                });
        TranslationService service = new TranslationService(plugin, MessagesTest.class, "message-translations");
        service.load();
        return new Messages(service, format);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
