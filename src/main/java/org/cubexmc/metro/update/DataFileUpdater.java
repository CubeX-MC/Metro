package org.cubexmc.metro.update;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Migrates persistent Metro data files across schema changes.
 */
public final class DataFileUpdater {

    public static final String SCHEMA_VERSION_KEY = "schema_version";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private DataFileUpdater() {
    }

    public static void migrateAll(JavaPlugin plugin) {
        migrateLines(plugin);
        migrateStops(plugin);
        migratePortals(plugin);
    }

    public static void migrateLines(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "lines.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        boolean hasDataSections = false;

        for (String lineId : config.getKeys(false)) {
            if (SCHEMA_VERSION_KEY.equals(lineId)) {
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(lineId);
            if (section == null) {
                continue;
            }
            hasDataSections = true;

            if (!section.contains("ordered_stop_ids") && section.contains("ordered_platform_ids")) {
                section.set("ordered_stop_ids", section.getStringList("ordered_platform_ids"));
                section.set("ordered_platform_ids", null);
                changed = true;
            }
            if (!section.contains("route_points")) {
                section.set("route_points", Collections.emptyList());
                changed = true;
            }
            if (!section.contains("rail_protected")) {
                section.set("rail_protected", false);
                changed = true;
            }
        }

        if (hasDataSections && config.getInt(SCHEMA_VERSION_KEY, 0) < CURRENT_SCHEMA_VERSION) {
            config.set(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
            changed = true;
        }
        saveIfChanged(plugin, file, config, changed, "lines.yml");
    }

    public static void migrateStops(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "stops.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        boolean hasDataSections = false;

        for (String stopId : config.getKeys(false)) {
            if (SCHEMA_VERSION_KEY.equals(stopId)) {
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(stopId);
            if (section == null) {
                continue;
            }
            hasDataSections = true;

            changed |= renameIfMissing(section, "name", "display_name");
            changed |= renameIfMissing(section, "corner1", "corner1_location");
            changed |= renameIfMissing(section, "corner2", "corner2_location");
            changed |= renameIfMissing(section, "stopPoint", "stoppoint_location");
            changed |= renameIfMissing(section, "stop_point", "stoppoint_location");
            changed |= renameIfMissing(section, "launchYaw", "launch_yaw");
            if (!section.contains("transferable_lines")) {
                section.set("transferable_lines", Collections.emptyList());
                changed = true;
            }
        }

        if (hasDataSections && config.getInt(SCHEMA_VERSION_KEY, 0) < CURRENT_SCHEMA_VERSION) {
            config.set(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
            changed = true;
        }
        saveIfChanged(plugin, file, config, changed, "stops.yml");
    }

    public static void migratePortals(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "portals.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        if (!config.isConfigurationSection("portals")) {
            changed = moveRootPortalSections(config);
        }

        ConfigurationSection portalsSection = config.getConfigurationSection("portals");
        if (portalsSection != null) {
            for (String portalId : portalsSection.getKeys(false)) {
                ConfigurationSection section = portalsSection.getConfigurationSection(portalId);
                if (section == null) {
                    continue;
                }
                changed |= migratePortalLocation(section, "entrance", false);
                changed |= migratePortalLocation(section, "destination", true);
                changed |= renameIfMissing(section, "destWorld", "dest_world");
                changed |= renameIfMissing(section, "destX", "dest_x");
                changed |= renameIfMissing(section, "destY", "dest_y");
                changed |= renameIfMissing(section, "destZ", "dest_z");
                changed |= renameIfMissing(section, "destYaw", "dest_yaw");
                changed |= renameIfMissing(section, "linkedPortalId", "linked");
            }
        }

        if (portalsSection != null && config.getInt(SCHEMA_VERSION_KEY, 0) < CURRENT_SCHEMA_VERSION) {
            config.set(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
            changed = true;
        }
        saveIfChanged(plugin, file, config, changed, "portals.yml");
    }

    private static boolean moveRootPortalSections(YamlConfiguration config) {
        Set<String> rootKeys = new HashSet<>(config.getKeys(false));
        boolean changed = false;
        for (String portalId : rootKeys) {
            if (SCHEMA_VERSION_KEY.equals(portalId)) {
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(portalId);
            if (section == null || !looksLikePortal(section)) {
                continue;
            }
            ConfigurationSection target = config.createSection("portals." + portalId);
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                target.set(entry.getKey(), entry.getValue());
            }
            config.set(portalId, null);
            changed = true;
        }
        return changed;
    }

    private static boolean looksLikePortal(ConfigurationSection section) {
        return section.contains("world") || section.contains("entrance") || section.contains("dest_world")
                || section.contains("destination") || section.contains("destX");
    }

    private static boolean migratePortalLocation(ConfigurationSection section, String key, boolean destination) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            return false;
        }
        List<String> parts = List.of(value.split(","));
        if ((!destination && parts.size() != 4) || (destination && parts.size() < 4)) {
            return false;
        }

        if (destination) {
            if (!section.contains("dest_world")) section.set("dest_world", parts.get(0));
            if (!section.contains("dest_x")) section.set("dest_x", parseDouble(parts.get(1), 0.0));
            if (!section.contains("dest_y")) section.set("dest_y", parseDouble(parts.get(2), 0.0));
            if (!section.contains("dest_z")) section.set("dest_z", parseDouble(parts.get(3), 0.0));
            if (parts.size() >= 5 && !section.contains("dest_yaw")) {
                section.set("dest_yaw", parseDouble(parts.get(4), 0.0));
            }
        } else {
            if (!section.contains("world")) section.set("world", parts.get(0));
            if (!section.contains("x")) section.set("x", parseInt(parts.get(1), 0));
            if (!section.contains("y")) section.set("y", parseInt(parts.get(2), 0));
            if (!section.contains("z")) section.set("z", parseInt(parts.get(3), 0));
        }
        section.set(key, null);
        return true;
    }

    private static boolean renameIfMissing(ConfigurationSection section, String oldKey, String newKey) {
        if (!section.contains(oldKey) || section.contains(newKey)) {
            return false;
        }
        section.set(newKey, section.get(oldKey));
        section.set(oldKey, null);
        return true;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void saveIfChanged(JavaPlugin plugin, File file, YamlConfiguration config, boolean changed,
                                      String fileName) {
        if (!changed) {
            return;
        }
        try {
            config.save(file);
            plugin.getLogger().info("Migrated Metro data file: " + fileName);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to migrate " + fileName + ": " + ex.getMessage());
        }
    }
}
