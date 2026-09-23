package com.earthpol.earthpollib.config;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static com.earthpol.earthpollib.string.StringUtil.parseCharStrict;
import static com.earthpol.earthpollib.string.StringUtil.stringify;
import static com.earthpol.earthpollib.string.StringUtil.trim;

/**
 * Functional, thread-safe parsers for config tokens (strings) into strongly-typed values.
 *
 * <h2>Scope</h2>
 * <ul>
 *   <li>Strict boolean: only {@code "true"} or {@code "false"} (case-insensitive).</li>
 *   <li>Numbers use Java parse methods (base-10, dot as decimal separator).</li>
 *   <li>{@code float}/{@code double} reject NaN/∞ &rarr; use {@link BigDecimal} for arbitrary precision.</li>
 *   <li>{@code char} requires exactly one (trimmed) character.</li>
 *   <li>Paper registries such as ({@link Material}, {@link EntityType}, {@link PotionEffectType}) resolve in
 *       the {@code minecraft:} namespace via {@link Registry} and {@code getOrThrow}.</li>
 *   <li>List parsing is provided as a convenience; each element is parsed individually.</li>
 * </ul>
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>Immutable: all maps are unmodifiable; there is no dynamic registration API.</li>
 *   <li>Errors are fail-fast and descriptive (e.g., index info for lists, type names in messages).</li>
 *   <li>Intended for configuration parsing, not general user-input (no locale-specific formats).</li>
 * </ul>
 *
 * <h2> Minecraft Types</h2>
 * <ul>
 *  <li>All Minecraft types (e.g {@link Material}, {@link EntityType}) are resolved via Paper's registry API. In practical
 *  terms, this means the name of the object after the Namespace key</li>
 *  <li> Example, to get the Material for "minecraft:stone", you would provide "stone" to the parser. </li>
 *  <li> We always assume the "minecraft" namespace for readability and ease of editing config files. </li>
 * <ul>
 */
@SuppressWarnings("unused")
public final class ExtendedParser {
    private ExtendedParser() {}

    private static final Map<Class<?>, Function<String, ?>> PARSERS =
            Map.ofEntries(
                    Map.entry(String.class,     Function.identity()),
                    Map.entry(Boolean.class,    ExtendedParser::parseBoolean),
                    Map.entry(Integer.class,    s -> Integer.parseInt(trim(s))),
                    Map.entry(Long.class,       s -> Long.parseLong(trim(s))),
                    Map.entry(Double.class,     ExtendedParser::parseDouble),
                    Map.entry(Float.class,      ExtendedParser::parseFloat),
                    Map.entry(Short.class,      s -> Short.parseShort(trim(s))),
                    Map.entry(Byte.class,       s -> Byte.parseByte(trim(s))),
                    Map.entry(Character.class,  ExtendedParser::parseChar),
                    Map.entry(BigInteger.class, s -> new BigInteger(trim(s))),
                    Map.entry(BigDecimal.class, s -> new BigDecimal(trim(s))),
                    Map.entry(UUID.class,       s -> UUID.fromString(trim(s))),
                    Map.entry(Material.class,         ExtendedParser::parseMaterial),
                    Map.entry(EntityType.class,       ExtendedParser::parseEntityType),
                    Map.entry(PotionEffectType.class, ExtendedParser::parsePotionEffectType),
                    Map.entry(org.bukkit.Sound.class, ExtendedParser::parseBukkitSound),
                    Map.entry(Villager.Profession.class, ExtendedParser::parseVillagerProfession),
                    Map.entry(Villager.Type.class,       ExtendedParser::parseVillagerType),
                    Map.entry(BlockType.class,          ExtendedParser::parseBlockType),
                    Map.entry(PotionType.class,         ExtendedParser::parsePotionType),
                    Map.entry(TrimPattern.class,        ExtendedParser::parseTrimPattern),
                    Map.entry(Enchantment.class,        ExtendedParser::parseEnchantment),
                    Map.entry(TrimMaterial.class,       ExtendedParser::parseTrimMaterial),
                    Map.entry(Biome.class,              ExtendedParser::parseBiome),
                    Map.entry(MusicInstrument.class,    ExtendedParser::parseInstrument),
                    Map.entry(Color.class,              ExtendedParser::parseBukkitColor)
            );

    private static final Map<Class<?>, Function<Object, String>> WRITERS =
            Map.ofEntries(
                    Map.entry(Material.class,         v -> writeMaterial((Material) v)),
                    Map.entry(EntityType.class,       v -> writeEntityType((EntityType) v)),
                    Map.entry(PotionEffectType.class, v -> writePotionEffectType((PotionEffectType) v)),
                    Map.entry(org.bukkit.Sound.class, v -> writeBukkitSound((org.bukkit.Sound) v)),
                    Map.entry(Villager.Profession.class, v -> writeVillagerProfession((Villager.Profession) v)),
                    Map.entry(Villager.Type.class,       v -> writeVillagerType((Villager.Type) v)),
                    Map.entry(BlockType.class,          v -> writeBlockType((BlockType) v)),
                    Map.entry(PotionType.class,         v -> writePotionType((PotionType) v)),
                    Map.entry(TrimPattern.class,        v -> writeTrimPattern((TrimPattern) v)),
                    Map.entry(Enchantment.class,        v -> writeEnchantment((Enchantment) v)),
                    Map.entry(TrimMaterial.class,       v -> writeTrimMaterial((TrimMaterial) v)),
                    Map.entry(Biome.class,              v -> writeBiome((Biome) v)),
                    Map.entry(MusicInstrument.class,    v -> writeInstrument((MusicInstrument) v)),
                    Map.entry(Color.class,              v -> writeBukkitColor((Color) v))
            );

    private static final Set<Class<?>> YAML_STRING_TYPES = Set.of(
            Material.class,
            EntityType.class,
            PotionEffectType.class,
            org.bukkit.Sound.class,
            Color.class,
            Villager.Profession.class,
            Villager.Type.class,
            BlockType.class,
            PotionType.class,
            TrimPattern.class,
            Enchantment.class,
            TrimMaterial.class,
            Biome.class,
            MusicInstrument.class
    );

    public static <T> T parse(Class<T> type, String raw) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(raw,  "raw");
        @SuppressWarnings("unchecked")
        final Function<String, ?> fn = PARSERS.get(type);
        if (fn == null) {
            throw new UnsupportedOperationException("Unsupported parse type: " + type.getName());
        }
        return type.cast(fn.apply(raw));
    }

    public static <T> Optional<T> tryParse(Class<T> type, String raw) {
        try {
            return Optional.of(parse(type, raw));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public static <T> @Nullable T tryParseOrNull(Class<T> type, String raw) {
        try {
            return parse(type, raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static <T> T parseFromObject(Class<T> type, Object raw) {
        String token = stringify(raw);
        if (token == null) throw new NoSuchElementException("Null value cannot be parsed.");
        return parse(type, token);
    }

    public static <T> List<T> parseList(Class<T> elementType, List<?> raw) {
        Objects.requireNonNull(elementType, "elementType");
        if (raw == null || raw.isEmpty()) return List.of();

        if (!isSupportedParser(elementType)) {
            throw new UnsupportedOperationException("Unsupported list element type: " + elementType.getName());
        }

        List<T> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Object o = raw.get(i);
            if (o == null) continue;
            try {
                out.add(parseFromObject(elementType, o));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(
                        "Failed to parse element at index " + i + " as " + elementType.getSimpleName() + ": " + o, ex
                );
            }
        }
        return Collections.unmodifiableList(out);
    }

    @Contract(pure = true)
    public static boolean isSupportedParser(Class<?> type) {
        return PARSERS.containsKey(type);
    }

    @Contract(pure = true)
    public static @NotNull Set<Class<?>> supportedParsers() {
        return PARSERS.keySet();
    }

    @Contract(pure = true)
    public static boolean isSupportedWriter(Class<?> type) {
        return WRITERS.containsKey(type);
    }

    @Contract(pure = true)
    public static Set<Class<?>> supportedWriters() {
        return WRITERS.keySet();
    }

    public static <T> String write(Class<T> type, T value) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        @SuppressWarnings("unchecked")
        final Function<Object, String> fn = WRITERS.get(type);
        if (fn == null) {
            throw new UnsupportedOperationException("Unsupported write type: " + type.getName());
        }
        return fn.apply(value);
    }

    @Contract(pure = true)
    public static boolean isWritable(Class<?> type) {
        return WRITERS.containsKey(type);
    }

    @Contract(pure = true)
    public static boolean isYamlStringType(Class<?> type) {
        return YAML_STRING_TYPES.contains(type);
    }

    public static @NotNull Character parseChar(String s) {
        return parseCharStrict(s);
    }

    public static @NotNull Boolean parseBoolean(String s) {
        String t = trim(s).toLowerCase(Locale.ROOT);
        if ("true".equals(t))  return Boolean.TRUE;
        if ("false".equals(t)) return Boolean.FALSE;
        throw new IllegalArgumentException("Only 'true' or 'false' allowed; got: " + s);
    }

    public static @NotNull Double parseDouble(String s) {
        double d = Double.parseDouble(trim(s));
        if (!Double.isFinite(d)) {
            throw new IllegalArgumentException("Double is NaN/Infinity. Consider BigDecimal: " + s);
        }
        return d;
    }

    public static @NotNull Float parseFloat(String s) {
        float f = Float.parseFloat(trim(s));
        if (!Float.isFinite(f)) {
            throw new IllegalArgumentException("Float is NaN/Infinity. Consider BigDecimal: " + s);
        }
        return f;
    }

    public static Material parseMaterial(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return Registry.MATERIAL.getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeMaterial(Material m) {
        return Registry.MATERIAL.getKey(m).getKey();
    }

    public static EntityType parseEntityType(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return Registry.ENTITY_TYPE.getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeEntityType(EntityType t) {
        return Registry.ENTITY_TYPE.getKey(t).getKey();
    }

    public static PotionEffectType parsePotionEffectType(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return Registry.POTION_EFFECT_TYPE.getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writePotionEffectType(PotionEffectType t) {
        return Registry.POTION_EFFECT_TYPE.getKey(t).getKey();
    }

    public static org.bukkit.Sound parseBukkitSound(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return Registry.SOUNDS.getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeBukkitSound(org.bukkit.Sound s) {
        return Registry.SOUNDS.getKey(s).getKey();
    }

    public static Color parseBukkitColor(String s) {
        s = trim(s).toLowerCase();
        switch (s) {
            case "aqua" -> {return Color.AQUA;}
            case "black" -> {return Color.BLACK;}
            case "blue" -> {return Color.BLUE;}
            case "fuchsia" -> {return Color.FUCHSIA;}
            case "gray" -> {return Color.GRAY;}
            case "green" -> {return Color.GREEN;}
            case "lime" -> {return Color.LIME;}
            case "maroon" -> {return Color.MAROON;}
            case "navy" -> {return Color.NAVY;}
            case "olive" -> {return Color.OLIVE;}
            case "orange" -> {return Color.ORANGE;}
            case "purple" -> {return Color.PURPLE;}
            case "red" -> {return Color.RED;}
            case "silver" -> {return Color.SILVER;}
            case "teal" -> {return Color.TEAL;}
            case "white" -> {return Color.WHITE;}
            case "yellow" -> {return Color.YELLOW;}
            default ->  {throw new NoSuchElementException("Unknown color: " + s);}
        }
    }

    public static String writeBukkitColor(Color c) {
        if (Color.AQUA.equals(c)) return "aqua";
        if (Color.BLACK.equals(c)) return "black";
        if (Color.BLUE.equals(c)) return "blue";
        if (Color.FUCHSIA.equals(c)) return "fuchsia";
        if (Color.GRAY.equals(c)) return "gray";
        if (Color.GREEN.equals(c)) return "green";
        if (Color.LIME.equals(c)) return "lime";
        if (Color.MAROON.equals(c)) return "maroon";
        if (Color.NAVY.equals(c)) return "navy";
        if (Color.OLIVE.equals(c)) return "olive";
        if (Color.ORANGE.equals(c)) return "orange";
        if (Color.PURPLE.equals(c)) return "purple";
        if (Color.RED.equals(c)) return "red";
        if (Color.SILVER.equals(c)) return "silver";
        if (Color.TEAL.equals(c)) return "teal";
        if (Color.WHITE.equals(c)) return "white";
        if (Color.YELLOW.equals(c)) return "yellow";
        throw new NoSuchElementException("Unknown color: " + c);
    }

    public static Villager.Profession parseVillagerProfession(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return Registry.VILLAGER_PROFESSION.getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeVillagerProfession(Villager.Profession p) {
        return Registry.VILLAGER_PROFESSION.getKey(p).getKey();
    }

    public static Villager.Type parseVillagerType(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return Registry.VILLAGER_TYPE.getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeVillagerType(Villager.Type t) {
        return Registry.VILLAGER_TYPE.getKey(t).getKey();
    }

    public static BlockType parseBlockType(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return Registry.BLOCK.getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeBlockType(BlockType t) {
        return Registry.BLOCK.getKey(t).getKey();
    }

    public static PotionType parsePotionType(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return Registry.POTION.getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writePotionType(PotionType t) {
        return Registry.POTION.getKey(t).getKey();
    }

    public static TrimMaterial parseTrimMaterial(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeTrimMaterial(TrimMaterial t) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).getKey(t).getKey();
    }

    public static TrimPattern parseTrimPattern(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeTrimPattern(TrimPattern p) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).getKey(p).getKey();
    }

    public static Enchantment parseEnchantment(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeEnchantment(Enchantment e) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).getKey(e).getKey();
    }

    public static Biome parseBiome(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeBiome(Biome b) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getKey(b).getKey();
    }

    public static MusicInstrument parseInstrument(String s) {
        String key = trim(s).toLowerCase(Locale.ROOT);
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT).getOrThrow(NamespacedKey.minecraft(key));
    }

    public static String writeInstrument(MusicInstrument i) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT).getKey(i).getKey();
    }

}
