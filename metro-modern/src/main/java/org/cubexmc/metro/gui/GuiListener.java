package org.cubexmc.metro.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder.GuiType;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;
import org.cubexmc.metro.util.SchedulerUtil;

import java.util.List;

/**
 * GUI 事件监听器
 */
public class GuiListener implements Listener {
    
    private final Metro plugin;
    
    // 槽位常量
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_FILTER = 49;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_BACK = 46;
    
    // 主菜单槽位
    private static final int SLOT_LINE_MANAGE = 11;
    private static final int SLOT_STOP_MANAGE = 15;
    
    public GuiListener(Metro plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        
        // 检查是否是我们的 GUI
        if (!(inv.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        
        // 取消事件，防止物品被拿走
        event.setCancelled(true);
        
        // 忽略非玩家点击
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        int slot = event.getRawSlot();
        
        // 忽略点击 GUI 外部
        if (slot < 0 || slot >= inv.getSize()) {
            return;
        }
        
        // 根据 GUI 类型处理
        switch (holder.getType()) {
            case MAIN_MENU -> handleMainMenuClick(player, slot);
            case LINE_LIST -> handleLineListClick(player, holder, slot, event.isRightClick());
            case STOP_LIST -> handleStopListClick(player, holder, slot);
            case LINE_VARIANTS -> handleLineVariantsClick(player, holder, slot);
            case STOP_VARIANTS -> handleStopVariantsClick(player, holder, slot);
            case LINE_DETAIL -> handleLineDetailClick(player, holder, slot, event.isRightClick());
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // 防止在 GUI 中拖拽物品
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }
    
    /**
     * 处理主菜单点击
     */
    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case SLOT_LINE_MANAGE -> plugin.getGuiManager().openLineList(player, 0, false);
            case SLOT_STOP_MANAGE -> plugin.getGuiManager().openStopList(player, 0, false);
        }
    }
    
    /**
     * 处理线路列表点击
     */
    private void handleLineListClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
        int totalPages = holder.getData("totalPages", 1);
        
        // 处理控制栏
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineList(player, page - 1, showOnlyMine);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineList(player, page + 1, showOnlyMine);
                }
                return;
            }
            case SLOT_FILTER -> {
                plugin.getGuiManager().openLineList(player, 0, !showOnlyMine);
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openMainMenu(player);
                return;
            }
        }
        
        // 处理线路物品点击（前36格）
        if (slot < 36) {
            List<String> lineNames = holder.getData("lineNames");
            java.util.Map<String, List<Line>> groupedLines = holder.getData("groupedLines");
            if (lineNames == null || groupedLines == null) return;
            
            int index = page * 36 + slot;
            if (index >= 0 && index < lineNames.size()) {
                String name = lineNames.get(index);
                List<Line> variants = groupedLines.get(name);
                
                if (variants.size() > 1) {
                    plugin.getGuiManager().openLineVariants(player, name, 0);
                } else {
                    plugin.getGuiManager().openLineDetail(player, variants.get(0).getId(), 0);
                }
            }
        }
    }
    
    /**
     * 处理线路变体列表点击
     */
    private void handleLineVariantsClick(Player player, GuiHolder holder, int slot) {
        int page = holder.getData("page", 0);
        String lineName = holder.getData("lineName");
        int totalPages = holder.getData("totalPages", 1);
        
        // 处理控制栏
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineVariants(player, lineName, page - 1);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineVariants(player, lineName, page + 1);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openLineList(player, 0, false);
                return;
            }
        }
        
        // 处理线路物品点击（前36格）
        if (slot < 36) {
            List<Line> lines = holder.getData("lines");
            if (lines == null) return;
            
            int index = page * 36 + slot;
            if (index >= 0 && index < lines.size()) {
                Line line = lines.get(index);
                plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
            }
        }
    }

    /**
     * 处理站点列表点击
     */
    private void handleStopListClick(Player player, GuiHolder holder, int slot) {
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
        int totalPages = holder.getData("totalPages", 1);
        
        // 处理控制栏
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openStopList(player, page - 1, showOnlyMine);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openStopList(player, page + 1, showOnlyMine);
                }
                return;
            }
            case SLOT_FILTER -> {
                plugin.getGuiManager().openStopList(player, 0, !showOnlyMine);
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openMainMenu(player);
                return;
            }
        }
        
        // 处理站点物品点击（前36格）
        if (slot < 36) {
            List<String> stopNames = holder.getData("stopNames");
            java.util.Map<String, List<Stop>> groupedStops = holder.getData("groupedStops");
            if (stopNames == null || groupedStops == null) return;
            
            int index = page * 36 + slot;
            if (index >= 0 && index < stopNames.size()) {
                String name = stopNames.get(index);
                List<Stop> variants = groupedStops.get(name);
                
                if (variants.size() > 1) {
                    plugin.getGuiManager().openStopVariants(player, name, 0);
                } else {
                    Stop stop = variants.get(0);
                    handleStopClick(player, stop);
                }
            }
        }
    }

    /**
     * 处理站点变体列表点击
     */
    private void handleStopVariantsClick(Player player, GuiHolder holder, int slot) {
        int page = holder.getData("page", 0);
        String stopName = holder.getData("stopName");
        int totalPages = holder.getData("totalPages", 1);
        
        // 处理控制栏
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openStopVariants(player, stopName, page - 1);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openStopVariants(player, stopName, page + 1);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openStopList(player, 0, false);
                return;
            }
        }
        
        // 处理站点物品点击（前36格）
        if (slot < 36) {
            List<Stop> stops = holder.getData("stops");
            if (stops == null) return;
            
            int index = page * 36 + slot;
            if (index >= 0 && index < stops.size()) {
                Stop stop = stops.get(index);
                handleStopClick(player, stop);
            }
        }
    }

    private void handleStopClick(Player player, Stop stop) {
        // 检查传送权限
        if (!player.hasPermission("metro.tp")) {
            return;
        }
        
        // 传送到站点
        if (stop.getStopPointLocation() != null) {
            player.closeInventory();
            SchedulerUtil.teleportEntity(player, stop.getStopPointLocation()).thenAccept(success -> {
                if (success) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success",
                            LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                }
            });
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("stop.stoppoint_not_set",
                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
        }
    }
    
    /**
     * 处理线路详情点击
     */
    private void handleLineDetailClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        String lineId = holder.getData("lineId");
        int page = holder.getData("page", 0);
        int totalPages = holder.getData("totalPages", 1);
        
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.closeInventory();
            return;
        }
        
        // 处理控制栏
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineDetail(player, lineId, page - 1);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineDetail(player, lineId, page + 1);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openLineList(player, 0, false);
                return;
            }
        }
        
        // 处理站点物品点击（前36格）
        if (slot < 36) {
            List<String> stopIds = line.getOrderedStopIds();
            int index = page * 36 + slot;
            if (index >= 0 && index < stopIds.size()) {
                String stopId = stopIds.get(index);
                Stop stop = plugin.getStopManager().getStop(stopId);
                
                if (stop == null) return;
                
                if (isRightClick && OwnershipUtil.canManageLine(player, line)) {
                    // 右键从线路移除站点
                    if (plugin.getLineManager().delStopFromLine(lineId, stopId)) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_success",
                                LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                        "stop_id", stopId), "line_id", lineId)));
                        // 刷新界面
                        plugin.getGuiManager().openLineDetail(player, lineId, page);
                    }
                } else if (!isRightClick && player.hasPermission("metro.tp") && stop.getStopPointLocation() != null) {
                    // 左键传送到站点（需要 metro.tp 权限）
                    player.closeInventory();
                    SchedulerUtil.teleportEntity(player, stop.getStopPointLocation()).thenAccept(success -> {
                        if (success) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.tp_success",
                                    LanguageManager.put(LanguageManager.args(), "stop_name", stop.getName())));
                        }
                    });
                }
            }
        }
    }
}

