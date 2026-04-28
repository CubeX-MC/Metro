package org.cubexmc.metro.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.MetroConstants;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.TextUtil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * 监听玩家移动事件，用于检测玩家进入停靠区
 */
public class PlayerMoveListener implements Listener {

    private final Metro plugin;
    private final Map<UUID, String> playerInStopMap = new ConcurrentHashMap<>(); // 记录玩家当前所在的停靠区ID
    private final Map<UUID, Object> continuousInfoTasks = new ConcurrentHashMap<>(); // 记录持续显示信息的任务ID
    private final Map<UUID, Object> actionBarTasks = new ConcurrentHashMap<>(); // 记录专门的ActionBar显示任务ID

    public PlayerMoveListener(Metro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只有玩家从一个方块移动到另一个方块时才检查，优化性能
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("metro.use")) {
            return;
        }

        // 检查玩家是否在矿车内，如果在矿车内则不显示站台信息
        if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
            org.bukkit.entity.Minecart minecart = (org.bukkit.entity.Minecart) player.getVehicle();
            // 检查是否是Metro的矿车
            if (minecart.getPersistentDataContainer().has(MetroConstants.getMinecartKey(),
                    org.bukkit.persistence.PersistentDataType.BYTE)) {
                // 如果玩家在Metro矿车内，取消站台信息显示
                UUID playerId = player.getUniqueId();
                String currentStopId = playerInStopMap.remove(playerId);
                if (currentStopId != null) {
                    cancelContinuousInfoTask(playerId);
                    cancelActionBarTask(playerId);
                }
                return;
            }
        }

        // 玩家不在矿车内，正常处理站台信息
        Location location = player.getLocation();
        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getStopContainingLocation(location);

        UUID playerId = player.getUniqueId();
        String currentStopId = playerInStopMap.get(playerId);

        // 检查玩家是否进入了新的停靠区
        if (stop != null) {
            String stopId = stop.getId();
            if (!stopId.equals(currentStopId)) {
                // 玩家进入了新的停靠区
                playerInStopMap.put(playerId, stopId);

                // 取消原来的持续显示任务
                cancelContinuousInfoTask(playerId);

                // 启动新的持续显示任务
                if (plugin.getConfigFacade().isStopContinuousTitleEnabled()) {
                    startContinuousInfoTask(player, stop);
                }
            }
        } else if (currentStopId != null) {
            // 玩家离开了停靠区
            playerInStopMap.remove(playerId);
            cancelContinuousInfoTask(playerId);
            cancelActionBarTask(playerId); // 取消ActionBar任务

            // 立即清除title和actionbar显示
            player.resetTitle();
            // 发送空的ActionBar来清除显示
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));

            // 清除首次运行标记
            String lastStopId = currentStopId;
            if (lastStopId != null) {
                player.removeMetadata("metro_first_run_" + lastStopId, plugin);
            }
        }
    }

    /**
     * 检查两个位置是否在同一方块内
     */
    private boolean isSameBlock(Location from, Location to) {
        if (from == null || to == null) {
            return true;
        }

        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

    /**
     * 启动持续显示停靠区信息的任务
     */
    private void startContinuousInfoTask(Player player, Stop stop) {
        UUID playerId = player.getUniqueId();

        // 取消已有的ActionBar任务
        cancelActionBarTask(playerId);

        LineManager lineManager = plugin.getLineManager();
        List<Line> boardableLines = plugin.getLineSelectionService().getBoardableLines(stop);

        if (boardableLines.isEmpty()) {
            return;
        }
        if (boardableLines.size() > 1) {
            startMultiLineInfoTask(player, stop, boardableLines);
            return;
        }

        final Line line = boardableLines.get(0);

        ConfigFacade config = plugin.getConfigFacade();
        int interval = config.getStopContinuousInterval();
        boolean alwaysShow = config.isStopContinuousAlways();
        int configuredFadeIn = config.getStopContinuousFadeIn();
        int configuredStay = config.getStopContinuousStay();
        int configuredFadeOut = config.getStopContinuousFadeOut();

        int continuousFadeIn = configuredFadeIn;
        int continuousStay = configuredStay;
        int continuousFadeOut = configuredFadeOut;

        if (alwaysShow) {
            // 连续显示时禁用淡入淡出，避免标题每次重绘出现闪烁
            continuousFadeIn = 0;
            continuousFadeOut = 0;
            // 确保持续时间覆盖刷新间隔，避免出现可见空窗
            continuousStay = Math.max(configuredStay, interval + 1);
        }

        final int effectiveContinuousFadeIn = continuousFadeIn;
        final int effectiveContinuousStay = continuousStay;
        final int effectiveContinuousFadeOut = continuousFadeOut;
        final int singleFadeIn = configuredFadeIn;
        final int singleStay = configuredStay;
        final int singleFadeOut = configuredFadeOut;

        // 准备信息内容（在任务外提前准备，以便ActionBar任务可以使用）
        // 获取前一站和下一站信息
        String lastStopId = line.getPreviousStopId(stop.getId());
        String nextStopId = line.getNextStopId(stop.getId());

        StopManager stopManager = plugin.getStopManager();
        Stop lastStop = lastStopId != null ? stopManager.getStop(lastStopId) : null;
        Stop nextStop = nextStopId != null ? stopManager.getStop(nextStopId) : null;

        // 获取终点站信息
        List<String> stopIds = line.getOrderedStopIds();
        Stop terminalStop = null;
        if (!stopIds.isEmpty()) {
            String terminalStopId = stopIds.get(stopIds.size() - 1);
            terminalStop = stopManager.getStop(terminalStopId);
        }

        // 确定站点类型并获取对应配置
        boolean isStartStop = (lastStop == null); // 没有上一站，是始发站
        boolean isEndStop = (nextStop == null); // 没有下一站，是终点站

        String title = config.getStopContinuousTitle(isStartStop, isEndStop);
        String subtitle = config.getStopContinuousSubtitle(isStartStop, isEndStop);
        String actionbar = config.getStopContinuousActionbar(isStartStop, isEndStop);

        Map<String, String> customTitle = stop.getCustomTitle("stop_continuous");
        if (customTitle != null) {
            if (customTitle.containsKey("title")) {
                title = customTitle.get("title");
            }
            if (customTitle.containsKey("subtitle")) {
                subtitle = customTitle.get("subtitle");
            }
            if (customTitle.containsKey("actionbar")) {
                actionbar = customTitle.get("actionbar");
            }
        }

        final String finalTitle = TextUtil.replacePlaceholders(title, line, stop, lastStop, nextStop, terminalStop,
                lineManager);
        final String finalSubtitle = TextUtil.replacePlaceholders(subtitle, line, stop, lastStop, nextStop,
                terminalStop, lineManager);
        final String finalActionbar = TextUtil.replacePlaceholders(actionbar, line, stop, lastStop, nextStop,
                terminalStop, lineManager);
        final String translatedTitle = ChatColor.translateAlternateColorCodes('&', finalTitle);
        final String translatedSubtitle = ChatColor.translateAlternateColorCodes('&', finalSubtitle);
        final net.md_5.bungee.api.chat.BaseComponent[] actionbarComponent = TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&', finalActionbar));

        if (alwaysShow) {
            Object actionBarTaskId = SchedulerUtil.entityRun(plugin, player, new Runnable() {
                @Override
                public void run() {
                    // 检查任务是否仍然存在于Map中，如果不存在说明已被外部取消
                    if (!actionBarTasks.containsKey(playerId)) {
                        return;
                    }

                    if (!player.isOnline() || !stop.isInStop(player.getLocation())) {
                        // 不要在这里取消任务，让外部的PlayerMoveEvent来处理
                        // cancelActionBarTask(playerId);
                        return;
                    }
                    if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
                        org.bukkit.entity.Minecart minecart = (org.bukkit.entity.Minecart) player.getVehicle();
                        if (minecart.getPersistentDataContainer().has(MetroConstants.getMinecartKey(),
                                org.bukkit.persistence.PersistentDataType.BYTE)) {
                            return;
                        }
                    }
                    player.spigot().sendMessage(
                            ChatMessageType.ACTION_BAR,
                            actionbarComponent);
                }
            }, 0L, 20L);
            actionBarTasks.put(playerId, actionBarTaskId);

            Object titleTaskId = SchedulerUtil.entityRun(plugin, player, new Runnable() {
                @Override
                public void run() {
                    // 检查任务是否仍然存在于Map中，如果不存在说明已被外部取消
                    if (!continuousInfoTasks.containsKey(playerId)) {
                        return;
                    }

                    if (!player.isOnline() || !stop.isInStop(player.getLocation())) {
                        // 不要在这里取消任务，让外部的PlayerMoveEvent来处理
                        // cancelContinuousInfoTask(playerId);
                        return;
                    }
                    if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
                        org.bukkit.entity.Minecart minecart = (org.bukkit.entity.Minecart) player.getVehicle();
                        if (minecart.getPersistentDataContainer().has(MetroConstants.getMinecartKey(),
                                org.bukkit.persistence.PersistentDataType.BYTE)) {
                            return;
                        }
                    }
                    player.sendTitle(
                            translatedTitle,
                            translatedSubtitle,
                            effectiveContinuousFadeIn,
                            effectiveContinuousStay,
                            effectiveContinuousFadeOut);
                    // Title title = new Title(ChatColor.translateAlternateColorCodes('&',
                    // finalTitle), ChatColor.translateAlternateColorCodes('&', finalSubtitle), 0,
                    // 40, interval);
                    // player.showTitle(title);
                }
            }, 0L, interval);
            continuousInfoTasks.put(playerId, titleTaskId);
        } else {
            String metaKey = "metro_first_run_" + stop.getId();
            List<MetadataValue> metaList = player.getMetadata(metaKey);

            if (metaList.isEmpty()) {
                player.setMetadata(metaKey, new FixedMetadataValue(plugin, true));

                boolean inMetroMinecart = false;
                if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
                    org.bukkit.entity.Minecart mc_vehicle = (org.bukkit.entity.Minecart) player.getVehicle();
                    if (mc_vehicle.getPersistentDataContainer().has(MetroConstants.getMinecartKey(),
                            org.bukkit.persistence.PersistentDataType.BYTE)) {
                        inMetroMinecart = true;
                    }
                }

                if (!inMetroMinecart) {
                    player.sendTitle(
                            translatedTitle,
                            translatedSubtitle,
                            singleFadeIn, singleStay, singleFadeOut);

                    final int totalDisplayTime = singleStay + singleFadeOut;
                    Object actionBarTaskId = SchedulerUtil.entityRun(plugin, player, new Runnable() {
                        private int count = 0;
                        private final int maxCount = totalDisplayTime / 20 + 1;

                        @Override
                        public void run() {
                            if (!player.isOnline() || count >= maxCount || !stop.isInStop(player.getLocation())) {
                                cancelActionBarTask(playerId);
                                return;
                            }
                            if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart) {
                                org.bukkit.entity.Minecart minecart = (org.bukkit.entity.Minecart) player.getVehicle();
                                if (minecart.getPersistentDataContainer().has(MetroConstants.getMinecartKey(),
                                        org.bukkit.persistence.PersistentDataType.BYTE)) {
                                    count++;
                                    return;
                                }
                            }
                            player.spigot().sendMessage(
                                    ChatMessageType.ACTION_BAR,
                                    actionbarComponent);
                            count++;
                        }
                    }, 0L, 20L);
                    actionBarTasks.put(playerId, actionBarTaskId);
                }
            }
        }
    }

    private void startMultiLineInfoTask(Player player, Stop stop, List<Line> boardableLines) {
        UUID playerId = player.getUniqueId();
        ConfigFacade config = plugin.getConfigFacade();
        int interval = config.getStopContinuousInterval();
        boolean alwaysShow = config.isStopContinuousAlways();
        int configuredFadeIn = config.getStopContinuousFadeIn();
        int configuredStay = config.getStopContinuousStay();
        int configuredFadeOut = config.getStopContinuousFadeOut();

        int continuousFadeIn = configuredFadeIn;
        int continuousStay = configuredStay;
        int continuousFadeOut = configuredFadeOut;

        if (alwaysShow) {
            continuousFadeIn = 0;
            continuousFadeOut = 0;
            continuousStay = Math.max(configuredStay, interval + 1);
        }

        final String translatedTitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getMessage("interact.multi_line_title",
                        Map.of("stop_name", stop.getName())));
        final String translatedSubtitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getLanguageManager().getMessage("interact.multi_line_subtitle",
                        Map.of("count", String.valueOf(boardableLines.size()))));
        final net.md_5.bungee.api.chat.BaseComponent[] actionbarComponent = TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&',
                        plugin.getLanguageManager().getMessage("interact.multi_line_actionbar",
                                Map.of("routes", buildBoardableRouteSummary(stop, boardableLines)))));

        final int effectiveContinuousFadeIn = continuousFadeIn;
        final int effectiveContinuousStay = continuousStay;
        final int effectiveContinuousFadeOut = continuousFadeOut;
        final int singleFadeIn = configuredFadeIn;
        final int singleStay = configuredStay;
        final int singleFadeOut = configuredFadeOut;

        if (alwaysShow) {
            Object actionBarTaskId = SchedulerUtil.entityRun(plugin, player, new Runnable() {
                @Override
                public void run() {
                    if (!actionBarTasks.containsKey(playerId)) {
                        return;
                    }
                    if (!player.isOnline() || !stop.isInStop(player.getLocation()) || isInMetroMinecart(player)) {
                        return;
                    }
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, actionbarComponent);
                }
            }, 0L, 20L);
            actionBarTasks.put(playerId, actionBarTaskId);

            Object titleTaskId = SchedulerUtil.entityRun(plugin, player, new Runnable() {
                @Override
                public void run() {
                    if (!continuousInfoTasks.containsKey(playerId)) {
                        return;
                    }
                    if (!player.isOnline() || !stop.isInStop(player.getLocation()) || isInMetroMinecart(player)) {
                        return;
                    }
                    player.sendTitle(translatedTitle, translatedSubtitle, effectiveContinuousFadeIn,
                            effectiveContinuousStay, effectiveContinuousFadeOut);
                }
            }, 0L, interval);
            continuousInfoTasks.put(playerId, titleTaskId);
        } else {
            String metaKey = "metro_first_run_" + stop.getId();
            List<MetadataValue> metaList = player.getMetadata(metaKey);

            if (metaList.isEmpty()) {
                player.setMetadata(metaKey, new FixedMetadataValue(plugin, true));

                if (!isInMetroMinecart(player)) {
                    player.sendTitle(translatedTitle, translatedSubtitle, singleFadeIn, singleStay, singleFadeOut);

                    final int totalDisplayTime = singleStay + singleFadeOut;
                    Object actionBarTaskId = SchedulerUtil.entityRun(plugin, player, new Runnable() {
                        private int count = 0;
                        private final int maxCount = totalDisplayTime / 20 + 1;

                        @Override
                        public void run() {
                            if (!player.isOnline() || count >= maxCount || !stop.isInStop(player.getLocation())) {
                                cancelActionBarTask(playerId);
                                return;
                            }
                            if (!isInMetroMinecart(player)) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, actionbarComponent);
                            }
                            count++;
                        }
                    }, 0L, 20L);
                    actionBarTasks.put(playerId, actionBarTaskId);
                }
            }
        }
    }

    private String buildBoardableRouteSummary(Stop stop, List<Line> boardableLines) {
        List<String> routes = new ArrayList<>();
        for (Line line : boardableLines) {
            Stop nextStop = plugin.getStopManager().getStop(line.getNextStopId(stop.getId()));
            String nextStopName = nextStop != null ? nextStop.getName() : line.getNextStopId(stop.getId());
            String lineName = (line.getColor() != null ? line.getColor() : "&f") + line.getName();
            routes.add(plugin.getLanguageManager().getMessage("interact.multi_line_route",
                    Map.of("line_name", lineName, "next_stop_name", nextStopName)));
        }
        return String.join("&7 | ", routes);
    }

    private boolean isInMetroMinecart(Player player) {
        if (player.isInsideVehicle() && player.getVehicle() instanceof org.bukkit.entity.Minecart minecart) {
            return minecart.getPersistentDataContainer().has(MetroConstants.getMinecartKey(),
                    org.bukkit.persistence.PersistentDataType.BYTE);
        }
        return false;
    }

    /**
     * 取消显示持续信息的任务
     */
    private void cancelContinuousInfoTask(UUID playerId) {
        Object taskId = continuousInfoTasks.remove(playerId);
        if (taskId != null) {
            SchedulerUtil.cancelTask(taskId);
        }
    }

    /**
     * 取消ActionBar显示任务
     */
    private void cancelActionBarTask(UUID playerId) {
        Object taskId = actionBarTasks.remove(playerId);
        if (taskId != null) {
            SchedulerUtil.cancelTask(taskId);
        }
    }

    /**
     * 插件关闭时主动清理所有显示任务和缓存状态
     */
    public void shutdown() {
        for (Object taskId : continuousInfoTasks.values()) {
            SchedulerUtil.cancelTask(taskId);
        }
        for (Object taskId : actionBarTasks.values()) {
            SchedulerUtil.cancelTask(taskId);
        }
        continuousInfoTasks.clear();
        actionBarTasks.clear();
        playerInStopMap.clear();
    }
}
