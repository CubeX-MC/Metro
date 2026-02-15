package org.cubexmc.metro.config;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.cubexmc.metro.Metro;

/**
 * Centralized read access for plugin configuration values.
 */
public class ConfigFacade {

    private final Metro plugin;

    public ConfigFacade(Metro plugin) {
        this.plugin = plugin;
    }

    public boolean isEnterStopTitleEnabled() {
        return plugin.getConfig().getBoolean("titles.enter_stop.enabled", true);
    }

    public String getEnterStopTitle() {
        return colorize(plugin.getConfig().getString("titles.enter_stop.title", "§6{line}"));
    }

    public String getEnterStopSubtitle() {
        return colorize(plugin.getConfig().getString("titles.enter_stop.subtitle", "§a{stop_name}"));
    }

    public int getEnterStopFadeIn() {
        return plugin.getConfig().getInt("titles.enter_stop.fade_in", 10);
    }

    public int getEnterStopStay() {
        return plugin.getConfig().getInt("titles.enter_stop.stay", 40);
    }

    public int getEnterStopFadeOut() {
        return plugin.getConfig().getInt("titles.enter_stop.fade_out", 10);
    }

    public boolean isArriveStopTitleEnabled() {
        return plugin.getConfig().getBoolean("titles.arrive_stop.enabled", true);
    }

    public String getArriveStopTitle() {
        return colorize(plugin.getConfig().getString("titles.arrive_stop.title", "§a已到站"));
    }

    public String getArriveStopSubtitle() {
        return colorize(plugin.getConfig().getString("titles.arrive_stop.subtitle", "§6{stop_name}"));
    }

    public int getArriveStopFadeIn() {
        return plugin.getConfig().getInt("titles.arrive_stop.fade_in", 10);
    }

    public int getArriveStopStay() {
        return plugin.getConfig().getInt("titles.arrive_stop.stay", 40);
    }

    public int getArriveStopFadeOut() {
        return plugin.getConfig().getInt("titles.arrive_stop.fade_out", 10);
    }

    public boolean isTerminalStopTitleEnabled() {
        return plugin.getConfig().getBoolean("titles.terminal_stop.enabled", true);
    }

    public String getTerminalStopTitle() {
        return colorize(plugin.getConfig().getString("titles.terminal_stop.title", "§c终点站"));
    }

    public String getTerminalStopSubtitle() {
        return colorize(plugin.getConfig().getString("titles.terminal_stop.subtitle", "§6请下车"));
    }

    public int getTerminalStopFadeIn() {
        return plugin.getConfig().getInt("titles.terminal_stop.fade_in", 10);
    }

    public int getTerminalStopStay() {
        return plugin.getConfig().getInt("titles.terminal_stop.stay", 60);
    }

    public int getTerminalStopFadeOut() {
        return plugin.getConfig().getInt("titles.terminal_stop.fade_out", 10);
    }

    public boolean isDepartureSoundEnabled() {
        return plugin.getConfig().getBoolean("sounds.departure.enabled", true);
    }

    public List<String> getDepartureNotes() {
        return plugin.getConfig().getStringList("sounds.departure.notes");
    }

    public int getDepartureInitialDelay() {
        return plugin.getConfig().getInt("sounds.departure.initial_delay", 0);
    }

    public boolean isArrivalSoundEnabled() {
        return plugin.getConfig().getBoolean("sounds.arrival.enabled", true);
    }

    public List<String> getArrivalNotes() {
        return plugin.getConfig().getStringList("sounds.arrival.notes");
    }

    public int getArrivalInitialDelay() {
        return plugin.getConfig().getInt("sounds.arrival.initial_delay", 0);
    }

    public boolean isStationArrivalSoundEnabled() {
        return plugin.getConfig().getBoolean("sounds.station_arrival.enabled", true);
    }

    public List<String> getStationArrivalNotes() {
        return plugin.getConfig().getStringList("sounds.station_arrival.notes");
    }

    public int getStationArrivalInitialDelay() {
        return plugin.getConfig().getInt("sounds.station_arrival.initial_delay", 0);
    }

    public boolean isWaitingSoundEnabled() {
        return plugin.getConfig().getBoolean("sounds.waiting.enabled", true);
    }

    public List<String> getWaitingNotes() {
        return plugin.getConfig().getStringList("sounds.waiting.notes");
    }

    public int getWaitingInitialDelay() {
        return plugin.getConfig().getInt("sounds.waiting.initial_delay", 0);
    }

    public int getWaitingSoundInterval() {
        return plugin.getConfig().getInt("sounds.waiting.interval", 20);
    }

    public double getCartSpeed() {
        return plugin.getConfig().getDouble("settings.cart_speed", 0.3);
    }

    public long getCartSpawnDelay() {
        return plugin.getConfig().getLong("settings.cart_spawn_delay", 60L);
    }

    public long getCartDepartureDelay() {
        return plugin.getConfig().getLong("settings.cart_departure_delay", 100L);
    }

    public boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("settings.debug.enabled", false);
    }

    public boolean isDebugCategoryEnabled(String category) {
        if (!isDebugEnabled() || category == null || category.isEmpty()) {
            return false;
        }
        return plugin.getConfig().getBoolean("settings.debug." + category, true);
    }

    public Material getSelectionTool() {
        String toolName = plugin.getConfig().getString("settings.selection_tool", "GOLDEN_SHOVEL");
        try {
            return Material.valueOf(toolName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid selection tool in config: " + toolName + ", using default GOLDEN_SHOVEL");
            return Material.GOLDEN_SHOVEL;
        }
    }

    public String getSelectionToolName() {
        Material tool = getSelectionTool();
        String name = tool.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
