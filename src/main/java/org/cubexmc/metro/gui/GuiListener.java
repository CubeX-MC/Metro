package org.cubexmc.metro.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.controller.ConfirmActionController;
import org.cubexmc.metro.gui.controller.LineBoardingChoiceController;
import org.cubexmc.metro.gui.controller.LineSettingsController;
import org.cubexmc.metro.gui.controller.StopSettingsController;
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

    private final LineBoardingChoiceController lineBoardingChoiceController;
    private final LineSettingsController lineSettingsController;
    private final StopSettingsController stopSettingsController;
    private final ConfirmActionController confirmActionController;
    
    public GuiListener(Metro plugin) {
        this.plugin = plugin;
        this.lineBoardingChoiceController = new LineBoardingChoiceController(plugin);
        this.lineSettingsController = new LineSettingsController(plugin);
        this.stopSettingsController = new StopSettingsController(plugin);
        this.confirmActionController = new ConfirmActionController(plugin);
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
            case MAIN_MENU -> handleMainMenuClick(player, holder, slot);
            case LINE_LIST -> handleLineListClick(player, holder, slot, event.isRightClick());
            case STOP_LIST -> handleStopListClick(player, holder, slot, event.isRightClick());
            case LINE_VARIANTS -> handleLineVariantsClick(player, holder, slot, event.isRightClick());
            case STOP_VARIANTS -> handleStopVariantsClick(player, holder, slot, event.isRightClick());
            case LINE_DETAIL -> handleLineDetailClick(player, holder, slot, event.isRightClick(), event.isShiftClick());
            case ADD_STOP_LIST -> handleAddStopListClick(player, holder, slot);
            case ADD_STOP_VARIANTS -> handleAddStopVariantsClick(player, holder, slot);
            case LINE_BOARDING_CHOICE -> lineBoardingChoiceController.handleClick(player, holder, slot,
                    event.isRightClick());
            case LINE_SETTINGS -> lineSettingsController.handleClick(player, holder, slot);
            case STOP_SETTINGS -> stopSettingsController.handleClick(player, holder, slot);
            case CONFIRM_ACTION -> confirmActionController.handleClick(player, holder, slot);
            case STOP_DETAIL -> {
                // STOP_DETAIL is reserved for future expansion.
            }
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
    private void handleMainMenuClick(Player player, GuiHolder holder, int slot) {
        switch (slot) {
            case SLOT_LINE_MANAGE -> plugin.getGuiManager().openLineList(player, 0, false, holder.snapshot());
            case SLOT_STOP_MANAGE -> plugin.getGuiManager().openStopList(player, 0, false, holder.snapshot());
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
                    plugin.getGuiManager().openLineList(player, page - 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineList(player, page + 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case SLOT_FILTER -> {
                plugin.getGuiManager().openLineList(player, 0, !showOnlyMine, holder.getPreviousView());
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder, () -> plugin.getGuiManager().openMainMenu(player));
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
                if (variants == null || variants.isEmpty()) {
                    return;
                }
                
                if (variants.size() > 1) {
                    plugin.getGuiManager().openLineVariants(player, name, 0, holder.snapshot());
                } else {
                    Line line = variants.get(0);
                    if (isRightClick && OwnershipUtil.canManageLine(player, line)) {
                        plugin.getGuiManager().openLineSettings(player, line.getId(), holder.snapshot());
                    } else {
                        plugin.getGuiManager().openLineDetail(player, line.getId(), 0, holder.snapshot());
                    }
                }
            }
        }
    }
    
    /**
     * 处理线路变体列表点击
     */
    private void handleLineVariantsClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        String lineName = holder.getData("lineName");
        int totalPages = holder.getData("totalPages", 1);
        
        // 处理控制栏
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openLineVariants(player, lineName, page - 1, holder.getPreviousView());
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineVariants(player, lineName, page + 1, holder.getPreviousView());
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder, () -> plugin.getGuiManager().openLineList(player, 0, false));
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
                if (isRightClick && OwnershipUtil.canManageLine(player, line)) {
                    plugin.getGuiManager().openLineSettings(player, line.getId(), holder.snapshot());
                } else {
                    plugin.getGuiManager().openLineDetail(player, line.getId(), 0, holder.snapshot());
                }
            }
        }
    }

    /**
     * 处理站点列表点击
     */
    private void handleStopListClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
        int totalPages = holder.getData("totalPages", 1);
        
        // 处理控制栏
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openStopList(player, page - 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openStopList(player, page + 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case SLOT_FILTER -> {
                plugin.getGuiManager().openStopList(player, 0, !showOnlyMine, holder.getPreviousView());
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder, () -> plugin.getGuiManager().openMainMenu(player));
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
                if (variants == null || variants.isEmpty()) {
                    return;
                }
                
                if (variants.size() > 1) {
                    plugin.getGuiManager().openStopVariants(player, name, 0, holder.snapshot());
                } else {
                    Stop stop = variants.get(0);
                    if (isRightClick && OwnershipUtil.canManageStop(player, stop)) {
                        plugin.getGuiManager().openStopSettings(player, stop.getId(), null, holder.snapshot());
                    } else {
                        handleStopClick(player, stop);
                    }
                }
            }
        }
    }

    /**
     * 处理站点变体列表点击
     */
    private void handleStopVariantsClick(Player player, GuiHolder holder, int slot, boolean isRightClick) {
        int page = holder.getData("page", 0);
        String stopName = holder.getData("stopName");
        int totalPages = holder.getData("totalPages", 1);
        
        // 处理控制栏
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (page > 0) {
                    plugin.getGuiManager().openStopVariants(player, stopName, page - 1, holder.getPreviousView());
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openStopVariants(player, stopName, page + 1, holder.getPreviousView());
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder, () -> plugin.getGuiManager().openStopList(player, 0, false));
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
                if (isRightClick && OwnershipUtil.canManageStop(player, stop)) {
                    plugin.getGuiManager().openStopSettings(player, stop.getId(), null, holder.snapshot());
                } else {
                    handleStopClick(player, stop);
                }
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
    private void handleLineDetailClick(Player player, GuiHolder holder, int slot, boolean isRightClick, boolean isShiftClick) {
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
                    plugin.getGuiManager().openLineDetail(player, lineId, page - 1, holder.getPreviousView());
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openLineDetail(player, lineId, page + 1, holder.getPreviousView());
                }
                return;
            }
            case SLOT_FILTER -> {
                if (OwnershipUtil.canManageLine(player, line)) {
                    plugin.getGuiManager().openAddStopList(player, lineId, 0, false, holder.snapshot());
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder, () -> plugin.getGuiManager().openLineList(player, 0, false));
                return;
            }
            case 50 -> {
                if (OwnershipUtil.canManageLine(player, line)) {
                    plugin.getGuiManager().openLineSettings(player, lineId, holder.snapshot());
                }
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
                
                if (isShiftClick) {
                    // Shift+点击从线路移除站点
                    if (OwnershipUtil.canManageLine(player, line)) {
                        plugin.getGuiManager().openConfirmAction(player, "REMOVE_STOP_FROM_LINE",
                                stopId, stop.getName(), lineId, page, holder.snapshot());
                    }
                } else if (isRightClick) {
                    // 右键打开该站点的设置页面
                    if (OwnershipUtil.canManageLine(player, line)) {
                        plugin.getGuiManager().openStopSettings(player, stopId, lineId, holder.snapshot());
                    }
                } else if (player.hasPermission("metro.tp") && stop.getStopPointLocation() != null) {
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

    /**
     * 处理添加站点列表点击
     */
    private void handleAddStopListClick(Player player, GuiHolder holder, int slot) {
        String lineId = holder.getData("lineId");
        int page = holder.getData("page", 0);
        boolean showOnlyMine = holder.getData("showOnlyMine", false);
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
                    plugin.getGuiManager().openAddStopList(player, lineId, page - 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openAddStopList(player, lineId, page + 1, showOnlyMine, holder.getPreviousView());
                }
                return;
            }
            case SLOT_FILTER -> {
                plugin.getGuiManager().openAddStopList(player, lineId, 0, !showOnlyMine, holder.getPreviousView());
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder, () -> plugin.getGuiManager().openLineDetail(player, lineId, 0));
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
                if (variants == null || variants.isEmpty()) {
                    return;
                }
                
                if (variants.size() > 1) {
                    plugin.getGuiManager().openAddStopVariants(player, lineId, name, 0, holder.snapshot());
                } else {
                    Stop stop = variants.get(0);
                    handleAddStopClick(player, line, stop, holder.getPreviousView());
                }
            }
        }
    }

    /**
     * 处理添加站点变体列表点击
     */
    private void handleAddStopVariantsClick(Player player, GuiHolder holder, int slot) {
        String lineId = holder.getData("lineId");
        int page = holder.getData("page", 0);
        String stopName = holder.getData("stopName");
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
                    plugin.getGuiManager().openAddStopVariants(player, lineId, stopName, page - 1, holder.getPreviousView());
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openAddStopVariants(player, lineId, stopName, page + 1, holder.getPreviousView());
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openPreviousView(player, holder, () -> plugin.getGuiManager().openAddStopList(player, lineId, 0, false));
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
                handleAddStopClick(player, line, stop, holder.getPreviousView());
            }
        }
    }

    private void handleAddStopClick(Player player, Line line, Stop stop, GuiHolder.GuiView returnView) {
        if (!OwnershipUtil.canManageLine(player, line)) {
            return;
        }
        
        // Add the stop to the line
        if (plugin.getLineManager().addStopToLine(line.getId(), stop.getId(), -1)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stop.getId()), "line_id", line.getId())));
            reopenAfterAddStop(player, line, returnView);
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_fail"));
            reopenAfterAddStop(player, line, returnView);
        }
    }

    private void reopenAfterAddStop(Player player, Line line, GuiHolder.GuiView returnView) {
        GuiHolder.GuiView view = returnView;
        while (view != null && (view.getType() == GuiHolder.GuiType.ADD_STOP_LIST
                || view.getType() == GuiHolder.GuiType.ADD_STOP_VARIANTS)) {
            view = view.getPreviousView();
        }
        if (!plugin.getGuiManager().openView(player, view)) {
            plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
        }
    }
}

