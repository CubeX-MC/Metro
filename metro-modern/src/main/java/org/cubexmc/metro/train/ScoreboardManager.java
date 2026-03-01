package org.cubexmc.metro.train;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;

/**
 * 计分板管理器，用于在玩家乘坐矿车时显示线路信息。
 * 现已重构为基于 scoreboard-library 的数据包虚拟侧边栏实现，兼容 Folia 并极大优化并发性能与兼容性。
 */
public class ScoreboardManager {

    // 独立玩家侧边栏实例映射
    private final Map<UUID, Sidebar> playerSidebars = new ConcurrentHashMap<>();

    // 记录玩家当前查看的线路和状态，减少不必要重绘
    private final Map<UUID, String> playerCurrentLine = new ConcurrentHashMap<>();

    private final Metro plugin;
    private final ScoreboardLibrary library;

    public ScoreboardManager(Metro plugin) {
        this.plugin = plugin;
        this.library = plugin.getGlobalScoreboardLibrary();
    }

    /**
     * 为进入站点区域的乘客更新计分板
     */
    public void updateEnteringStopScoreboard(Player player, Line line, String currentStopId) {
        if (!shouldUseScoreboard(player, line))
            return;
        String nextStopId = line.getNextStopId(currentStopId);
        updateScoreboardInternal(player, line, currentStopId, nextStopId);
    }

    /**
     * 为行驶中的乘客更新计分板（离开站点后）
     */
    public void updateTravelingScoreboard(Player player, Line line, String targetStopId) {
        if (!shouldUseScoreboard(player, line))
            return;
        updateScoreboardInternal(player, line, null, targetStopId);
    }

    /**
     * 为到达终点站的乘客更新计分板
     */
    public void updateTerminalScoreboard(Player player, Line line, String currentStopId) {
        if (!shouldUseScoreboard(player, line))
            return;
        updateScoreboardInternal(player, line, currentStopId, null);
    }

    private boolean shouldUseScoreboard(Player player, Line line) {
        if (player == null || !player.isOnline()) {
            plugin.debug("scoreboard", "shouldUseScoreboard: player null or offline");
            return false;
        }
        if (line == null) {
            plugin.debug("scoreboard", "shouldUseScoreboard: line is null");
            return false;
        }
        if (library == null) {
            plugin.debug("scoreboard", "shouldUseScoreboard: ScoreboardLibrary is null (failed to load)");
            return false;
        }
        boolean enabled = plugin.getConfigFacade().isScoreboardEnabled();
        if (!enabled) {
            plugin.debug("scoreboard", "shouldUseScoreboard: scoreboard disabled in config");
        }
        return enabled;
    }

    /**
     * 更新侧边栏核心逻辑
     */
    private void updateScoreboardInternal(Player player, Line line, String currentStopId, String nextStopId) {
        UUID playerId = player.getUniqueId();
        Sidebar sidebar = playerSidebars.get(playerId);

        // 如果该玩家尚未初始化侧边栏实例
        if (sidebar == null) {
            plugin.debug("scoreboard", "Creating new sidebar for player=" + player.getName());
            sidebar = library.createSidebar();
            sidebar.addPlayer(player);
            playerSidebars.put(playerId, sidebar);
        }

        plugin.debug("scoreboard", "Updating sidebar for player=" + player.getName()
                + " line=" + line.getId() + " current=" + currentStopId + " next=" + nextStopId);
        updateLines(sidebar, line, currentStopId, nextStopId);
        playerCurrentLine.put(playerId, line.getId());
    }

    /**
     * 构建或更新 Sidebar 组件线
     */
    private void updateLines(Sidebar sidebar, Line line, String currentStopId, String nextStopId) {
        List<String> stopIds = line.getOrderedStopIds();
        StopManager stopManager = plugin.getStopManager();
        LineManager lineManager = plugin.getLineManager();

        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
        String currentStopStyle = plugin.getConfig().getString("scoreboard.styles.current_stop", "&f");
        String nextStopStyle = plugin.getConfig().getString("scoreboard.styles.next_stop", "&a");
        String otherStopsStyle = plugin.getConfig().getString("scoreboard.styles.other_stops", "&7");
        String lineSymbol = plugin.getConfig().getString("scoreboard.line_symbol", "■");

        int currentStopIndex = currentStopId != null ? stopIds.indexOf(currentStopId) : -1;
        int nextStopIndex = nextStopId != null ? stopIds.indexOf(nextStopId) : -1;

        SidebarComponent.Builder componentBuilder = SidebarComponent.builder();
        int maxLines = Math.min(stopIds.size(), 15); // Minecraft 限制侧边栏最多15行

        for (int i = 0; i < maxLines; i++) {
            String stopId = stopIds.get(i);
            Stop stop = stopManager.getStop(stopId);
            if (stop == null)
                continue;

            String displayName = stop.getName();

            // 构建换乘信息
            StringBuilder transferInfo = new StringBuilder();
            List<String> transferableLines = stop.getTransferableLines();
            if (transferableLines != null && !transferableLines.isEmpty()) {
                for (String transferLineId : transferableLines) {
                    if (transferLineId.equals(line.getId()))
                        continue;
                    Line transferLine = lineManager.getLine(transferLineId);
                    if (transferLine != null) {
                        transferInfo.append(transferLine.getColor()).append(lineSymbol).append(" ");
                    }
                }
            }

            // 决定行样式前缀
            String prefix;
            if (i == currentStopIndex) {
                prefix = currentStopStyle;
            } else if (i == nextStopIndex) {
                prefix = nextStopStyle;
            } else {
                prefix = otherStopsStyle;
            }

            // 整合整行内容并反序列化为 Adventure Component
            String rawLine = prefix + displayName + (transferInfo.length() > 0 ? " " + transferInfo : "");
            Component lineComponent = serializer.deserialize(rawLine)
                    // 防止 italic 被全局覆盖
                    .decoration(TextDecoration.ITALIC, false);

            componentBuilder.addComponent(SidebarComponent.staticLine(lineComponent));
        }

        Component titleComponent = Component.text(line.getName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD);

        // 生成组件布局并应用
        ComponentSidebarLayout layout = new ComponentSidebarLayout(
                SidebarComponent.staticLine(titleComponent),
                componentBuilder.build());
        layout.apply(sidebar);
    }

    /**
     * 恢复玩家原有的计分板（如今由于不污染服务端Scoreboard，只需直接关闭并解绑 Sidebar 即可）
     */
    public void clearScoreboard(Player player) {
        if (player == null || !player.isOnline())
            return;

        UUID playerId = player.getUniqueId();
        Sidebar sidebar = playerSidebars.remove(playerId);
        if (sidebar != null) {
            sidebar.removePlayer(player);
            sidebar.close();
        }
        playerCurrentLine.remove(playerId);
    }

    /**
     * 清除玩家的地铁显示内容（包括侧边栏和title）
     */
    public void clearPlayerDisplay(Player player) {
        clearScoreboard(player);
        if (player != null && player.isOnline()) {
            player.sendTitle("", "", 0, 0, 0);
        }
    }

    /**
     * 插件关闭时销毁全部 Sidebar
     */
    public void shutdown() {
        for (Sidebar sidebar : playerSidebars.values()) {
            sidebar.close();
        }
        playerSidebars.clear();
        playerCurrentLine.clear();
    }
}