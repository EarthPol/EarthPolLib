package com.earthpol.earthpollib.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// TODO: Eventually move YMLBuilder, Loader, PostProcessor to the ymlhandler package folder.


/**
 * Builds and updates YAML configuration files from {@link ReloadableConfigNode} definitions.
 *
 * <p>Responsible for creating the config file, writing defaults, and filling missing keys.
 * Uses {@link YMLPostProcessor} to keep formatting consistent.</p>
 */
@SuppressWarnings("unused")
final class YMLBuilder {
    private YMLBuilder() {}

    static File fileInitialSetup(Plugin plugin, String configFileName) throws IOException {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configFileName, "configFileName");

        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IOException("Unable to create plugin directory: " + dataDir.getAbsolutePath());
        }

        File file = new File(dataDir, configFileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create parent directories for: " + file.getAbsolutePath());
        }

        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Failed to create config file: " + file.getAbsolutePath());
        }
        return file;
    }

    static void buildNewConfiguration(File file,
                                      Collection<? extends ReloadableConfigNode<?>> nodes,
                                      List<String> headerLines) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(nodes, "nodes");

        YamlConfiguration yaml = new YamlConfiguration();
        applyHeader(yaml, headerLines);

        for (ReloadableConfigNode<?> n : nodes) {
            if (n instanceof SectionHeaderNode) {
                yaml.createSection(n.getYmlPath());
                if (n.getComment() != null && !n.getComment().isEmpty()) {
                    yaml.setComments(n.getYmlPath(), n.getComment());
                }
                continue;
            }

            yaml.set(n.getYmlPath(), toYamlValue(n));
            if (n.getComment() != null && !n.getComment().isEmpty()) {
                yaml.setComments(n.getYmlPath(), n.getComment());
            }
        }

        savePretty(yaml, file, nodes);
    }

    static boolean fillMissingDefaults(File file,
                                       Collection<? extends ReloadableConfigNode<?>> nodes,
                                       List<String> headerIfNewKeys) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(nodes, "nodes");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        for (ReloadableConfigNode<?> n : nodes) {
            if (!yaml.contains(n.getYmlPath())) {
                if (n instanceof SectionHeaderNode) {
                    yaml.createSection(n.getYmlPath());
                    if (n.getComment() != null && !n.getComment().isEmpty()) {
                        yaml.setComments(n.getYmlPath(), n.getComment());
                    }
                } else {
                    yaml.set(n.getYmlPath(), toYamlValue(n));
                    if (n.getComment() != null && !n.getComment().isEmpty()) {
                        yaml.setComments(n.getYmlPath(), n.getComment());
                    }
                }
                changed = true;
            }
        }
        if (changed) {
            applyHeader(yaml, headerIfNewKeys);
            savePretty(yaml, file, nodes);
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static Object toYamlValue(ReloadableConfigNode<?> node) {
        if (node instanceof ReloadableListNode<?> listNode) {
            List<?> list = listNode.getDefaultValue();
            if (list == null || list.isEmpty()) return List.of();
            Class<?> elemType = listNode.getElementType();
            if (!ExtendedParser.isYamlStringType(elemType)) {
                return list;
            }
            List<String> out = new java.util.ArrayList<>(list.size());
            for (Object el : list) {
                if (el == null) continue;
                out.add(ExtendedParser.write((Class<Object>) elemType, el));
            }
            return out;
        }

        Object value = node.getDefaultValue();
        Class<?> type = node.getDataType();
        if (value != null && ExtendedParser.isYamlStringType(type)) {
            return ExtendedParser.write((Class<Object>) type, value);
        }
        return value;
    }

    private static void applyHeader(YamlConfiguration yaml, List<String> headerLines) {
        if (headerLines == null || headerLines.isEmpty()) return;
        yaml.options().setHeader(List.copyOf(headerLines));
        yaml.options().parseComments(true);
    }

    static void savePretty(YamlConfiguration yaml, File file) throws IOException {
        savePretty(yaml, file, List.of());
    }

    static void savePretty(YamlConfiguration yaml,
                           File file,
                           Collection<? extends ReloadableConfigNode<?>> nodes) throws IOException {
        String raw = yaml.saveToString();
        QuotePaths quotePaths = computeQuotePaths(nodes);
        String pretty = YMLPostProcessor.formatRootEntries(raw, quotePaths.scalarPaths, quotePaths.listPaths);
        java.nio.file.Files.writeString(file.toPath(), pretty, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static QuotePaths computeQuotePaths(Collection<? extends ReloadableConfigNode<?>> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new QuotePaths(Set.of(), Set.of());
        }

        Set<String> scalarPaths = new HashSet<>();
        Set<String> listPaths = new HashSet<>();

        for (ReloadableConfigNode<?> node : nodes) {
            if (node instanceof ReloadableListNode<?> listNode) {
                Class<?> elemType = listNode.getElementType();
                if (shouldQuoteType(elemType)) {
                    listPaths.add(listNode.getYmlPath());
                }
            } else if (shouldQuoteType(node.getDataType())) {
                scalarPaths.add(node.getYmlPath());
            }
        }

        return new QuotePaths(scalarPaths, listPaths);
    }

    private static boolean shouldQuoteType(Class<?> type) {
        return type == String.class || ExtendedParser.isYamlStringType(type);
    }

    private record QuotePaths(Set<String> scalarPaths, Set<String> listPaths) {}
}
