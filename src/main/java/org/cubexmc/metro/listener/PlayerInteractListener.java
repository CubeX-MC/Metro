package org.cubexmc.metro.listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.SelectionManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.TicketService;
import org.cubexmc.metro.train.TrainMovementTask;
import org.cubexmc.metro.util.MetroConstants;
import org.cubexmc.metro.util.OwnershipUtil;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.SoundUtil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
// import org.bukkit.util.Vector;

/**
 * 处理玩家交互事件
 */
public class PlayerInteractListener implements Listener {

    private final Metro plugin;
    private final SelectionManager selectionManager;

    // 用于防止短时间内多次点击触发多次调用
    private final Map<UUID, Long> lastInteractTime = new ConcurrentHashMap<>();

    // 用于跟踪站点的矿车生成状态，键为站点ID，值为时间戳
    private final Map<String, Long> pendingMinecarts = new ConcurrentHashMap<>();
    private final Object pendingMinecartCleanupTaskId;

    public PlayerInteractListener(Metro plugin) {
        this.plugin = plugin;
        this.selectionManager = plugin.getSelectionManager();

        // 定期清理过期的矿车等待记录
        this.pendingMinecartCleanupTaskId = SchedulerUtil.globalRun(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            long timeout = plugin.getConfigFacade().getMinecartPendingTimeout();
            pendingMinecarts.entrySet().removeIf(entry -> currentTime - entry.getValue() > timeout);
        }, 1200L, 1200L); // 每分钟清理一次
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();

        // 处理选区工具（可在config中配置，默认金锄头）
        // 只处理主手事件，避免主手和副手各触发一次导致消息重复
        // 允许拥有 metro.admin 或 metro.stop.create 权限的玩家使用选区工具
        Material selectionTool = plugin.getConfigFacade().getSelectionTool();
        if (OwnershipUtil.canCreateStop(player)
                && player.getInventory().getItemInMainHand().getType() == selectionTool
                && event.getHand() == EquipmentSlot.HAND) {
            if (action == Action.LEFT_CLICK_BLOCK) {
                selectionManager.setCorner1(player, clickedBlock.getLocation());
                player.sendMessage(plugin.getLanguageManager().getMessage("selection.corner1_set",
                        LanguageManager.put(LanguageManager.args(), "location",
                                clickedBlock.getLocation().getBlockX() + ", " + clickedBlock.getLocation().getBlockY()
                                        + ", " + clickedBlock.getLocation().getBlockZ())));
                event.setCancelled(true);
                return;
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                selectionManager.setCorner2(player, clickedBlock.getLocation());
                player.sendMessage(plugin.getLanguageManager().getMessage("selection.corner2_set",
                        LanguageManager.put(LanguageManager.args(), "location",
                                clickedBlock.getLocation().getBlockX() + ", " + clickedBlock.getLocation().getBlockY()
                                        + ", " + clickedBlock.getLocation().getBlockZ())));
                event.setCancelled(true);
                return;
            }
        }

        // 如果不是右键点击方块，不处理
        if (action != Action.RIGHT_CLICK_BLOCK || clickedBlock == null) {
            return;
        }

        // 检查点击的是否是铁轨
        if (!clickedBlock.getType().name().contains("RAIL")) {
            return;
        }

        // 防止短时间内多次点击
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long cooldown = plugin.getConfigFacade().getInteractCooldown();
        if (lastInteractTime.containsKey(playerId)) {
            long lastTime = lastInteractTime.get(playerId);
            if (currentTime - lastTime < cooldown) {
                // 如果冷却时间内再次点击，取消事件并返回
                event.setCancelled(true);
                return;
            }
        }

        // 发现玩家可能会点到多个相互重叠的 stop (比如复线和双向站), 此时选择最优方向的 Stop
        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getBestStopContainingLocation(clickedBlock.getLocation(), player.getLocation().getYaw());

        // 如果找到了停靠区，检查是否有待处理的矿车
        if (stop != null) {
            String stopId = stop.getId();
            if (pendingMinecarts.containsKey(stopId)) {
                // 该站点已有矿车在等待玩家上车
                long pendingTime = pendingMinecarts.get(stopId);
                long timeout = plugin.getConfigFacade().getMinecartPendingTimeout();
                if (currentTime - pendingTime < timeout) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_pending"));
                    event.setCancelled(true);
                    return;
                } else {
                    // 超时，清除记录
                    pendingMinecarts.remove(stopId);
                }
            }
        }

        // 检查是否是停靠点并处理
        boolean handled = checkAndHandleStopPoint(player, clickedBlock.getLocation());

        // 如果成功处理了停靠点，更新点击时间并取消事件
        if (handled) {
            lastInteractTime.put(playerId, currentTime);
            event.setCancelled(true);
            plugin.debug("interaction_flow", "Handled stop interaction for player=" + player.getName() + ", location="
                    + clickedBlock.getLocation());

            // 设置一个任务，在冷却时间后清除记录
            SchedulerUtil.entityRun(plugin, player, () -> lastInteractTime.remove(playerId), cooldown / 50,
                    -1);
        }
    }

    /**
     * 检查并处理停靠点交互
     * 
     * @return 是否成功处理了停靠点
     */
    private boolean checkAndHandleStopPoint(Player player, Location location) {
        if (!player.hasPermission("metro.use")) {
            return false;
        }

        StopManager stopManager = plugin.getStopManager();

        // 检查点击位置是否在任何停靠区内
        Stop stop = stopManager.getBestStopContainingLocation(location, player.getLocation().getYaw());

        // 如果找到了包含点击位置的停靠区
        if (stop != null) {
            // 确保停靠区已配置停靠点
            if (stop.getStopPointLocation() == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("interact.stop_no_point"));
                return false;
            }

            // 找到停靠区并且是铁轨，处理上车逻辑
            handleStopPoint(player, stop);
            return true;
        }

        return false;
    }

    /**
     * 处理停靠点交互
     */
    private void handleStopPoint(Player player, Stop stop) {
        List<Line> boardableLines = plugin.getLineSelectionService().getBoardableLines(stop);
        if (boardableLines.isEmpty()) {
            sendNoBoardableLineMessage(player, stop);
            return;
        }

        if (boardableLines.size() > 1 && plugin.getLineSelectionService().requiresChoice(player, stop)) {
            plugin.getGuiManager().openLineBoardingChoice(player, stop, 0);
            return;
        }

        Line line = plugin.getLineSelectionService().resolveDefaultLine(player, stop, stop.getStopPointLocation());
        if (line == null) {
            sendNoBoardableLineMessage(player, stop);
            return;
        }

        beginBoarding(player, stop, line);
    }

    /**
     * 从 GUI 选择线路后继续乘车流程。
     */
    public void boardSelectedLine(Player player, String stopId, String lineId) {
        if (!player.hasPermission("metro.use")) {
            return;
        }

        Stop stop = plugin.getStopManager().getStop(stopId);
        Line line = plugin.getLineManager().getLine(lineId);
        if (stop == null || line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.stop_no_line"));
            return;
        }
        if (stop.getStopPointLocation() == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.stop_no_point"));
            return;
        }
        if (plugin.getLineSelectionService().getBoardableLines(stop).stream()
                .noneMatch(boardableLine -> boardableLine.getId().equals(line.getId()))) {
            sendNoBoardableLineMessage(player, stop);
            return;
        }
        if (hasActivePendingMinecart(player, stop)) {
            return;
        }

        beginBoarding(player, stop, line);
    }

    private void beginBoarding(Player player, Stop stop, Line line) {
        plugin.getLineSelectionService().rememberChoice(player, stop.getId(), line.getId());

        TicketService.TicketCheck ticketCheck = plugin.getTicketService().checkCanBoard(player, line);
        if (!ticketCheck.canBoard()) {
            sendTicketCheckFailure(player, ticketCheck);
            return;
        }
        TicketService.TicketTransaction ticketTransaction = plugin.getTicketService().createTransaction(player, line);

        // 记录该站点有矿车正在处理中
        pendingMinecarts.put(stop.getId(), System.currentTimeMillis());
        plugin.debug("interaction_flow", "Preparing minecart for player=" + player.getName() + ", line=" + line.getId()
                + ", stop=" + stop.getId());

        // 显示线路信息
        showLineInfo(player, stop, line);

        // 播放车辆到站音乐 - 在右键点击后立即播放
        playStationArrivalSound(player);

        // 生成矿车
        spawnMinecart(player, stop, line, ticketTransaction);
    }

    private void sendNoBoardableLineMessage(Player player, Stop stop) {
        List<Line> servingLines = plugin.getLineManager().getLinesForStop(stop.getId());
        boolean onlyTerminalLines = !servingLines.isEmpty()
                && servingLines.stream().allMatch(servingLine -> servingLine.getNextStopId(stop.getId()) == null);
        if (onlyTerminalLines) {
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.terminal_stop"));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.stop_no_line"));
        }
        plugin.debug("interaction_flow", "No line found for stop=" + stop.getId());
    }

    private boolean hasActivePendingMinecart(Player player, Stop stop) {
        long currentTime = System.currentTimeMillis();
        Long pendingTime = pendingMinecarts.get(stop.getId());
        if (pendingTime == null) {
            return false;
        }
        long timeout = plugin.getConfigFacade().getMinecartPendingTimeout();
        if (currentTime - pendingTime < timeout) {
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_pending"));
            return true;
        }
        pendingMinecarts.remove(stop.getId());
        return false;
    }

    /**
     * 显示线路信息
     */
    private void showLineInfo(Player player, Stop stop, Line line) {
        // 获取下一停靠区信息
        String nextStopId = line.getNextStopId(stop.getId());

        if (nextStopId == null) {
            // 如果当前站已经是终点站，不需要显示信息
            return;
        }

        Stop nextStop = plugin.getStopManager().getStop(nextStopId);
        String nextStopName = nextStop != null ? nextStop.getName() : nextStopId;

        // 显示ActionBar信息
        String message = plugin.getLanguageManager().getMessage("interact.actionbar_line_info",
                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                        "line_name", line.getName()), "next_stop_name", nextStopName));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    /**
     * 播放车辆到站音乐
     */
    private void playStationArrivalSound(Player player) {
        if (plugin.getConfigFacade().isStationArrivalSoundEnabled()
                && !plugin.getConfigFacade().getStationArrivalNotes().isEmpty()) {
            SoundUtil.playNoteSequence(plugin, player, plugin.getConfigFacade().getStationArrivalNotes(),
                    plugin.getConfigFacade().getStationArrivalInitialDelay());
        }
    }

    /**
     * 生成矿车
     */
    private void spawnMinecart(Player player, Stop stop, Line line, TicketService.TicketTransaction ticketTransaction) {
        Location location = stop.getStopPointLocation();
        float yaw = stop.getLaunchYaw();

        if (location != null) {
            final String stopId = stop.getId();

            // 创建一个新位置，保留原来的坐标但使用停靠区的发车朝向
            Location spawnLocation = location.clone();
            // 反转Yaw值，使矿车外观朝向与移动方向一致
            spawnLocation.setYaw(yaw);

            // 获取矿车生成延迟
            long spawnDelay = plugin.getConfigFacade().getCartSpawnDelay();

            // 显示等待信息
            player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_coming"));

            // 延迟生成矿车，使用实体调度器以确保在正确的线程执行
            SchedulerUtil.regionRun(plugin, location, () -> {
                try {
                    // 生成矿车实体
                    Minecart minecart = (Minecart) location.getWorld().spawnEntity(spawnLocation, EntityType.MINECART);

                    // 设置矿车属性
                    minecart.getPersistentDataContainer().set(MetroConstants.getMinecartKey(),
                            org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    minecart.setCustomName(MetroConstants.METRO_MINECART_NAME);
                    minecart.setCustomNameVisible(false);
                    minecart.setPersistent(false);
                    minecart.setGravity(false); // 禁用重力temp 6-15
                    minecart.setSlowWhenEmpty(false); // 不因空车而减速
                    // minecart.setVelocity(new Vector(0, 0, 0));

                    double max_speed = line.getMaxSpeed();
                    if (max_speed == -1.0)
                        max_speed = plugin.getConfigFacade().getCartSpeed();

                    // 设置矿车的最大速度，只在创建时设置一次
                    minecart.setMaxSpeed(max_speed);

                    // 将玩家放入矿车
                    if (!minecart.addPassenger(player)) {
                        // 如果上车失败，可能需要处理，例如取消任务或通知玩家
                        minecart.remove(); // 移除矿车
                        pendingMinecarts.remove(stopId); // 清除等待状态
                        player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_error"));
                        return;
                    }

                    TicketService.TicketChargeStatus chargeStatus = plugin.getTicketService().charge(ticketTransaction);
                    if (!isSuccessfulCharge(chargeStatus)) {
                        minecart.remove();
                        pendingMinecarts.remove(stopId);
                        sendTicketChargeFailure(player, chargeStatus, ticketTransaction);
                        return;
                    }
                    if (chargeStatus == TicketService.TicketChargeStatus.CHARGED) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("economy.paid_boarding",
                                LanguageManager.put(LanguageManager.args(), "price",
                                        plugin.getTicketService().format(ticketTransaction.getPrice()))));
                    }

                    // 显示待乘车信息
                    player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_spawned",
                            LanguageManager.put(LanguageManager.args(), "departure_seconds",
                                    String.valueOf(plugin.getConfigFacade().getCartDepartureDelay() / 20))));

                    // 创建列车任务，使用TrainMovementTask处理等待发车和发车逻辑
                    // 这将触发handleArrivalAtStation方法，显示等待信息、播放等待音乐，然后延迟发车
                    TrainMovementTask.startTrainTask(plugin, minecart, player, line.getId(), stop.getId());
                    plugin.debug("interaction_flow", "Minecart spawned and task started for player=" + player.getName()
                            + ", line=" + line.getId() + ", stop=" + stop.getId());

                    // 清除该站点的矿车等待状态
                    pendingMinecarts.remove(stopId);
                } catch (Exception e) {
                    // 出现异常，清除该站点的矿车等待状态
                    pendingMinecarts.remove(stopId);
                    player.sendMessage(plugin.getLanguageManager().getMessage("interact.train_error"));
                    plugin.getLogger().log(Level.SEVERE, "Failed to spawn metro minecart for stop " + stopId, e);
                }
            }, spawnDelay, -1);
        }
    }

    private boolean isSuccessfulCharge(TicketService.TicketChargeStatus status) {
        return status == TicketService.TicketChargeStatus.CHARGED
                || status == TicketService.TicketChargeStatus.FREE
                || status == TicketService.TicketChargeStatus.ECONOMY_DISABLED;
    }

    private void sendTicketCheckFailure(Player player, TicketService.TicketCheck ticketCheck) {
        if (ticketCheck.getStatus() == TicketService.TicketCheckStatus.INSUFFICIENT_FUNDS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("economy.insufficient_funds",
                    LanguageManager.put(LanguageManager.args(), "price", ticketCheck.getFormattedPrice())));
            return;
        }
        if (ticketCheck.getStatus() == TicketService.TicketCheckStatus.VAULT_UNAVAILABLE) {
            player.sendMessage(plugin.getLanguageManager().getMessage("economy.vault_unavailable"));
            return;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("economy.transaction_failed"));
    }

    private void sendTicketChargeFailure(Player player, TicketService.TicketChargeStatus status,
            TicketService.TicketTransaction transaction) {
        if (status == TicketService.TicketChargeStatus.INSUFFICIENT_FUNDS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("economy.insufficient_funds",
                    LanguageManager.put(LanguageManager.args(), "price",
                            plugin.getTicketService().format(transaction.getPrice()))));
            return;
        }
        if (status == TicketService.TicketChargeStatus.VAULT_UNAVAILABLE) {
            player.sendMessage(plugin.getLanguageManager().getMessage("economy.vault_unavailable"));
            return;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("economy.transaction_failed"));
    }

    /**
     * 插件关闭时清理运行状态
     */
    public void shutdown() {
        SchedulerUtil.cancelTask(pendingMinecartCleanupTaskId);
        lastInteractTime.clear();
        pendingMinecarts.clear();
    }
}
