package com.earthpol.earthpollib.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtendedParserTest {

    @Test
    void parseBooleanIsStrict() {
        assertEquals(Boolean.TRUE, ExtendedParser.parseBoolean(" true "));
        assertEquals(Boolean.FALSE, ExtendedParser.parseBoolean("false"));
        assertThrows(IllegalArgumentException.class, () -> ExtendedParser.parseBoolean("yes"));
    }

    @Test
    void parseListParsesMixedRawValues() {
        assertEquals(List.of(1, 2, 3), ExtendedParser.parseList(Integer.class, List.of("1", 2, "3")));
    }

    @Test
    void ymlLoaderParsesScalarAndListValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("scalar", "42");
        yaml.set("list", List.of("a", "b"));

        assertEquals(42, YMLLoader.get(yaml, "scalar", Integer.class));
        assertEquals(List.of("a", "b"), YMLLoader.getList(yaml, "list", String.class));
    }
}
