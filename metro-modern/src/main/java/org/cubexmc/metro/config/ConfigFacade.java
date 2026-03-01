package org.cubexmc.metro.config;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.cubexmc.metro.Metro;

/**
 * Centralized read access and memory cache for plugin configuration values.
 */
public class ConfigFacade {

    private final Metro plugin;

    // Enter Stop Titles
    private boolean enterStopTitleEnabled;
    private String enterStopTitle;
    private String enterStopSubtitle;
    private int enterStopFadeIn;
    private int enterStopStay;
    private int enterStopFadeOut;

    // Arrive Stop Titles
    private boolean arriveStopTitleEnabled;
    private String arriveStopTitle;
    private String arriveStopSubtitle;
    private int arriveStopFadeIn;
    private int arriveStopStay;
    private int arriveStopFadeOut;

    // Terminal Stop Titles
    private boolean terminalStopTitleEnabled;
    private String terminalStopTitle;
    private String terminalStopSubtitle;
    private int terminalStopFadeIn;
    private int terminalStopStay;
    private int terminalStopFadeOut;

    // Departure Titles
    private boolean departureTitleEnabled;
    private String departureTitle;
    private String departureSubtitle;
    private String departureActionbar;
    private int departureFadeIn;
    private int departureStay;
    private int departureFadeOut;

    // Waiting Titles
    private boolean waitingTitleEnabled;
    private String waitingTitle;
    private String waitingSubtitle;
    private String waitingActionbar;

    // Sounds
    private boolean departureSoundEnabled;
    private List<String> departureNotes;
    private int departureInitialDelay;

    private boolean arrivalSoundEnabled;
    private List<String> arrivalNotes;
    private int arrivalInitialDelay;
    private boolean enableParticles;
    private boolean isScoreboardEnabled;

    private boolean stationArrivalSoundEnabled;
    private List<String> stationArrivalNotes;
    private int stationArrivalInitialDelay;

    private boolean waitingSoundEnabled;
    private List<String> waitingNotes;
    private int waitingInitialDelay;
    private int waitingSoundInterval;

    // Settings
    private double cartSpeed;
    private long cartSpawnDelay;
    private long cartDepartureDelay;
    private boolean debugEnabled;
    private boolean safeModeEnabled;
    private boolean safeModeEntityPushProtection;
    private boolean safeModeDamageProtection;

    private Material selectionTool;
    private String selectionToolName;

    public ConfigFacade(Metro plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // Enter Stop
        enterStopTitleEnabled = plugin.getConfig().getBoolean("titles.enter_stop.enabled", true);
        enterStopTitle = colorize(plugin.getConfig().getString("titles.enter_stop.title", "&6{line}"));
        enterStopSubtitle = colorize(plugin.getConfig().getString("titles.enter_stop.subtitle", "&a{stop_name}"));
        enterStopFadeIn = plugin.getConfig().getInt("titles.enter_stop.fade_in", 10);
        enterStopStay = plugin.getConfig().getInt("titles.enter_stop.stay", 40);
        enterStopFadeOut = plugin.getConfig().getInt("titles.enter_stop.fade_out", 10);

        // Arrive Stop
        arriveStopTitleEnabled = plugin.getConfig().getBoolean("titles.arrive_stop.enabled", true);
        arriveStopTitle = colorize(plugin.getConfig().getString("titles.arrive_stop.title", "&a已到站"));
        arriveStopSubtitle = colorize(plugin.getConfig().getString("titles.arrive_stop.subtitle", "&6{stop_name}"));
        arriveStopFadeIn = plugin.getConfig().getInt("titles.arrive_stop.fade_in", 10);
        arriveStopStay = plugin.getConfig().getInt("titles.arrive_stop.stay", 40);
        arriveStopFadeOut = plugin.getConfig().getInt("titles.arrive_stop.fade_out", 10);

        // Terminal Stop
        terminalStopTitleEnabled = plugin.getConfig().getBoolean("titles.terminal_stop.enabled", true);
        terminalStopTitle = colorize(plugin.getConfig().getString("titles.terminal_stop.title", "&c终点站"));
        terminalStopSubtitle = colorize(plugin.getConfig().getString("titles.terminal_stop.subtitle", "&6请下车"));
        terminalStopFadeIn = plugin.getConfig().getInt("titles.terminal_stop.fade_in", 10);
        terminalStopStay = plugin.getConfig().getInt("titles.terminal_stop.stay", 60);
        terminalStopFadeOut = plugin.getConfig().getInt("titles.terminal_stop.fade_out", 10);

        // Departure
        departureTitleEnabled = plugin.getConfig().getBoolean("titles.departure.enabled", true);
        departureTitle = plugin.getConfig().getString("titles.departure.title", "");
        departureSubtitle = plugin.getConfig().getString("titles.departure.subtitle", "");
        departureActionbar = plugin.getConfig().getString("titles.departure.actionbar", "列车已启动，请扶好站稳，注意安全");
        departureFadeIn = plugin.getConfig().getInt("titles.departure.fade_in", 5);
        departureStay = plugin.getConfig().getInt("titles.departure.stay", 40);
        departureFadeOut = plugin.getConfig().getInt("titles.departure.fade_out", 5);

        // Waiting
        waitingTitleEnabled = plugin.getConfig().getBoolean("titles.waiting.enabled", true);
        waitingTitle = plugin.getConfig().getString("titles.waiting.title", "列车即将发车");
        waitingSubtitle = plugin.getConfig().getString("titles.waiting.subtitle",
                "当前站点: &a{stop_name} | 下一站: &e{next_stop_name}");
        waitingActionbar = plugin.getConfig().getString("titles.waiting.actionbar", "列车将在 &c{countdown} &f秒后发车");

        // Sounds
        departureSoundEnabled = plugin.getConfig().getBoolean("sounds.departure.enabled", true);
        departureNotes = plugin.getConfig().getStringList("sounds.departure.notes");
        departureInitialDelay = plugin.getConfig().getInt("sounds.departure.initial_delay", 0);

        arrivalSoundEnabled = plugin.getConfig().getBoolean("sounds.arrival.enabled", true);
        arrivalNotes = plugin.getConfig().getStringList("sounds.arrival.notes");
        arrivalInitialDelay = plugin.getConfig().getInt("sounds.arrival.initial_delay", 0);
        enableParticles = plugin.getConfig().getBoolean("particles.enabled", true);
        isScoreboardEnabled = plugin.getConfig().getBoolean("scoreboard.enabled", true);

        stationArrivalSoundEnabled = plugin.getConfig().getBoolean("sounds.station_arrival.enabled", true);
        stationArrivalNotes = plugin.getConfig().getStringList("sounds.station_arrival.notes");
        stationArrivalInitialDelay = plugin.getConfig().getInt("sounds.station_arrival.initial_delay", 0);

        waitingSoundEnabled = plugin.getConfig().getBoolean("sounds.waiting.enabled", true);
        waitingNotes = plugin.getConfig().getStringList("sounds.waiting.notes");
        waitingInitialDelay = plugin.getConfig().getInt("sounds.waiting.initial_delay", 0);
        waitingSoundInterval = plugin.getConfig().getInt("sounds.waiting.interval", 20);

        // Settings
        cartSpeed = plugin.getConfig().getDouble("settings.cart_speed", 0.3);
        cartSpawnDelay = plugin.getConfig().getLong("settings.cart_spawn_delay", 60L);
        cartDepartureDelay = plugin.getConfig().getLong("settings.cart_departure_delay", 100L);
        debugEnabled = plugin.getConfig().getBoolean("settings.debug.enabled", false);
        safeModeEnabled = plugin.getConfig().getBoolean("settings.safe_mode.enabled", true);
        safeModeEntityPushProtection = plugin.getConfig().getBoolean("settings.safe_mode.entity_push_protection", true);
        safeModeDamageProtection = plugin.getConfig().getBoolean("settings.safe_mode.damage_protection", true);

        String toolName = plugin.getConfig().getString("settings.selection_tool", "GOLDEN_SHOVEL");
        try {
            selectionTool = Material.valueOf(toolName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger()
                    .warning("Invalid selection tool in config: " + toolName + ", using default GOLDEN_SHOVEL");
            selectionTool = Material.GOLDEN_SHOVEL;
        }

        String name = selectionTool.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        selectionToolName = result.toString().trim();
    }

    public boolean isEnterStopTitleEnabled() {
        return enterStopTitleEnabled;
    }

    public String getEnterStopTitle() {
        return enterStopTitle;
    }

    public String getEnterStopSubtitle() {
        return enterStopSubtitle;
    }

    public int getEnterStopFadeIn() {
        return enterStopFadeIn;
    }

    public int getEnterStopStay() {
        return enterStopStay;
    }

    public int getEnterStopFadeOut() {
        return enterStopFadeOut;
    }

    public boolean isArriveStopTitleEnabled() {
        return arriveStopTitleEnabled;
    }

    public String getArriveStopTitle() {
        return arriveStopTitle;
    }

    public String getArriveStopSubtitle() {
        return arriveStopSubtitle;
    }

    public int getArriveStopFadeIn() {
        return arriveStopFadeIn;
    }

    public int getArriveStopStay() {
        return arriveStopStay;
    }

    public int getArriveStopFadeOut() {
        return arriveStopFadeOut;
    }

    public boolean isTerminalStopTitleEnabled() {
        return terminalStopTitleEnabled;
    }

    public String getTerminalStopTitle() {
        return terminalStopTitle;
    }

    public String getTerminalStopSubtitle() {
        return terminalStopSubtitle;
    }

    public int getTerminalStopFadeIn() {
        return terminalStopFadeIn;
    }

    public int getTerminalStopStay() {
        return terminalStopStay;
    }

    public int getTerminalStopFadeOut() {
        return terminalStopFadeOut;
    }

    public boolean isDepartureTitleEnabled() {
        return departureTitleEnabled;
    }

    public String getDepartureTitle() {
        return departureTitle;
    }

    public String getDepartureSubtitle() {
        return departureSubtitle;
    }

    public String getDepartureActionbar() {
        return departureActionbar;
    }

    public int getDepartureFadeIn() {
        return departureFadeIn;
    }

    public int getDepartureStay() {
        return departureStay;
    }

    public int getDepartureFadeOut() {
        return departureFadeOut;
    }

    public boolean isWaitingTitleEnabled() {
        return waitingTitleEnabled;
    }

    public String getWaitingTitle() {
        return waitingTitle;
    }

    public String getWaitingSubtitle() {
        return waitingSubtitle;
    }

    public String getWaitingActionbar() {
        return waitingActionbar;
    }

    public boolean isDepartureSoundEnabled() {
        return departureSoundEnabled;
    }

    public List<String> getDepartureNotes() {
        return departureNotes;
    }

    public int getDepartureInitialDelay() {
        return departureInitialDelay;
    }

    public boolean isArrivalSoundEnabled() {
        return arrivalSoundEnabled;
    }

    public List<String> getArrivalNotes() {
        return arrivalNotes;
    }

    public int getArrivalInitialDelay() {
        return arrivalInitialDelay;
    }

    public boolean isEnableParticles() {
        return enableParticles;
    }

    public boolean isScoreboardEnabled() {
        return isScoreboardEnabled;
    }

    public boolean isStationArrivalSoundEnabled() {
        return stationArrivalSoundEnabled;
    }

    public List<String> getStationArrivalNotes() {
        return stationArrivalNotes;
    }

    public int getStationArrivalInitialDelay() {
        return stationArrivalInitialDelay;
    }

    public boolean isWaitingSoundEnabled() {
        return waitingSoundEnabled;
    }

    public List<String> getWaitingNotes() {
        return waitingNotes;
    }

    public int getWaitingInitialDelay() {
        return waitingInitialDelay;
    }

    public int getWaitingSoundInterval() {
        return waitingSoundInterval;
    }

    public double getCartSpeed() {
        return cartSpeed;
    }

    public long getCartSpawnDelay() {
        return cartSpawnDelay;
    }

    public long getCartDepartureDelay() {
        return cartDepartureDelay;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isSafeModeEnabled() {
        return safeModeEnabled;
    }

    public boolean isSafeModeEntityPushProtection() {
        return safeModeEnabled && safeModeEntityPushProtection;
    }

    public boolean isSafeModeDamageProtection() {
        return safeModeEnabled && safeModeDamageProtection;
    }

    public boolean isDebugCategoryEnabled(String category) {
        if (!isDebugEnabled() || category == null || category.isEmpty()) {
            return false;
        }
        return plugin.getConfig().getBoolean("settings.debug." + category, true);
    }

    public Material getSelectionTool() {
        return selectionTool;
    }

    public String getSelectionToolName() {
        return selectionToolName;
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
