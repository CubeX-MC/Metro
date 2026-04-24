package org.cubexmc.metro.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.cubexmc.metro.Metro;
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
            case STOP_LIST -> handleStopListClick(player, holder, slot, event.isRightClick());
            case LINE_VARIANTS -> handleLineVariantsClick(player, holder, slot, event.isRightClick());
            case STOP_VARIANTS -> handleStopVariantsClick(player, holder, slot, event.isRightClick());
            case LINE_DETAIL -> handleLineDetailClick(player, holder, slot, event.isRightClick(), event.isShiftClick());
            case ADD_STOP_LIST -> handleAddStopListClick(player, holder, slot);
            case ADD_STOP_VARIANTS -> handleAddStopVariantsClick(player, holder, slot);
            case LINE_SETTINGS -> handleLineSettingsClick(player, holder, slot);
            case STOP_SETTINGS -> handleStopSettingsClick(player, holder, slot);
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
                if (variants == null || variants.isEmpty()) {
                    return;
                }
                
                if (variants.size() > 1) {
                    plugin.getGuiManager().openLineVariants(player, name, 0);
                } else {
                    Line line = variants.get(0);
                    if (isRightClick && OwnershipUtil.canManageLine(player, line)) {
                        plugin.getGuiManager().openLineSettings(player, line.getId());
                    } else {
                        plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
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
                if (isRightClick && OwnershipUtil.canManageLine(player, line)) {
                    plugin.getGuiManager().openLineSettings(player, line.getId());
                } else {
                    plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
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
                if (variants == null || variants.isEmpty()) {
                    return;
                }
                
                if (variants.size() > 1) {
                    plugin.getGuiManager().openStopVariants(player, name, 0);
                } else {
                    Stop stop = variants.get(0);
                    if (isRightClick && OwnershipUtil.canManageStop(player, stop)) {
                        plugin.getGuiManager().openStopSettings(player, stop.getId());
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
                if (isRightClick && OwnershipUtil.canManageStop(player, stop)) {
                    plugin.getGuiManager().openStopSettings(player, stop.getId());
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
            case SLOT_FILTER -> {
                if (OwnershipUtil.canManageLine(player, line)) {
                    plugin.getGuiManager().openAddStopList(player, lineId, 0, false);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openLineList(player, 0, false);
                return;
            }
            case 50 -> {
                if (OwnershipUtil.canManageLine(player, line)) {
                    plugin.getGuiManager().openLineSettings(player, lineId);
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
                        if (plugin.getLineManager().delStopFromLine(lineId, stopId)) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_success",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                            "stop_id", stopId), "line_id", lineId)));
                            // 刷新界面
                            plugin.getGuiManager().openLineDetail(player, lineId, page);
                        }
                    }
                } else if (isRightClick) {
                    // 右键打开该站点的设置页面
                    if (OwnershipUtil.canManageLine(player, line)) {
                        plugin.getGuiManager().openStopSettings(player, stopId, lineId);
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
                    plugin.getGuiManager().openAddStopList(player, lineId, page - 1, showOnlyMine);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openAddStopList(player, lineId, page + 1, showOnlyMine);
                }
                return;
            }
            case SLOT_FILTER -> {
                plugin.getGuiManager().openAddStopList(player, lineId, 0, !showOnlyMine);
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openLineDetail(player, lineId, 0);
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
                    plugin.getGuiManager().openAddStopVariants(player, lineId, name, 0);
                } else {
                    Stop stop = variants.get(0);
                    handleAddStopClick(player, line, stop);
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
                    plugin.getGuiManager().openAddStopVariants(player, lineId, stopName, page - 1);
                }
                return;
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages - 1) {
                    plugin.getGuiManager().openAddStopVariants(player, lineId, stopName, page + 1);
                }
                return;
            }
            case SLOT_BACK -> {
                plugin.getGuiManager().openAddStopList(player, lineId, 0, false);
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
                handleAddStopClick(player, line, stop);
            }
        }
    }

    private void handleAddStopClick(Player player, Line line, Stop stop) {
        if (!OwnershipUtil.canManageLine(player, line)) {
            return;
        }
        
        // Add the stop to the line
        if (plugin.getLineManager().addStopToLine(line.getId(), stop.getId(), -1)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stop.getId()), "line_id", line.getId())));
            // 刷新界面回到线路详情
            plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_fail"));
            plugin.getGuiManager().openLineDetail(player, line.getId(), 0);
        }
    }

    private void handleLineSettingsClick(Player player, GuiHolder holder, int slot) {
        String lineId = holder.getData("lineId");
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 9 -> { // Rename
                plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_name"), new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        if (plugin.getLineManager().setLineName(lineId, input)) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_success",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                            "old_name", line.getName()), "new_name", input)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_fail"));
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId);
                    }
                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId);
                    }
                });
            }
            case 11 -> { // Max Speed
                plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_speed"), new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        try {
                            double speed = Double.parseDouble(input);
                            if (speed <= 0) throw new NumberFormatException();
                            if (plugin.getLineManager().setLineMaxSpeed(lineId, speed)) {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_success",
                                        LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                                "line_id", lineId), "max_speed", String.valueOf(speed))));
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_invalid"));
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId);
                    }
                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId);
                    }
                });
            }
            case 13 -> { // Set Price
                plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_price"), new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        try {
                            double price = Double.parseDouble(input);
                            if (price < 0) throw new NumberFormatException();
                            if (plugin.getLineManager().setLineTicketPrice(lineId, price)) {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_success",
                                        LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                                "line_name", line.getName()), "price", String.valueOf(price))));
                            } else {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_fail"));
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage(org.bukkit.ChatColor.RED + "Invalid price.");
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId);
                    }
                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId);
                    }
                });
            }
            case 15 -> { // Clone reverse
                plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_clone_info"), new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        String[] parts = input.split(" ");
                        if (parts.length >= 1 && !parts[0].isEmpty()) {
                            String newId = parts[0];
                            String stopIdSuffix = parts.length > 1 ? parts[1] : "_rev";
                            if (plugin.getLineManager().cloneReverseLine(lineId, newId, stopIdSuffix, player.getUniqueId())) {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.clone_success",
                                        LanguageManager.put(LanguageManager.args(), "new_line_id", newId)));
                            } else {
                                player.sendMessage(plugin.getLanguageManager().getMessage("line.clone_fail"));
                            }
                        }
                        plugin.getGuiManager().openLineSettings(player, lineId);
                    }
                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openLineSettings(player, lineId);
                    }
                });
            }
            case 17 -> { // Delete
                if (plugin.getLineManager().deleteLine(lineId)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_success",
                            LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_fail"));
                }
                plugin.getGuiManager().openLineList(player, 0, false);
            }
            case 22 -> { // Back
                plugin.getGuiManager().openLineList(player, 0, false);
            }
        }
    }

    private void handleStopSettingsClick(Player player, GuiHolder holder, int slot) {
        String stopId = holder.getData("stopId");
        String fromLineId = holder.getData("fromLineId");
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 11 -> { // Rename
                plugin.getChatInputManager().requestInput(player, plugin.getLanguageManager().getMessage("chat.enter_new_name"), new ChatInputManager.ChatInputCallback() {
                    @Override
                    public void onInput(String input) {
                        if (plugin.getStopManager().setStopName(stopId, input)) {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.rename_success",
                                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                                            "old_name", stop.getName()), "new_name", input)));
                        } else {
                            player.sendMessage(plugin.getLanguageManager().getMessage("stop.rename_fail"));
                        }
                        plugin.getGuiManager().openStopSettings(player, stopId, fromLineId);
                    }
                    @Override
                    public void onCancel() {
                        plugin.getGuiManager().openStopSettings(player, stopId, fromLineId);
                    }
                });
            }
            case 15 -> { // Delete
                if (plugin.getStopManager().deleteStop(stopId)) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_success",
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("stop.delete_not_found",
                            LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
                }
                if (fromLineId != null) {
                    plugin.getGuiManager().openLineDetail(player, fromLineId, 0);
                } else {
                    plugin.getGuiManager().openStopList(player, 0, false);
                }
            }
            case 22 -> { // Back
                if (fromLineId != null) {
                    plugin.getGuiManager().openLineDetail(player, fromLineId, 0);
                } else {
                    plugin.getGuiManager().openStopList(player, 0, false);
                }
            }
        }
    }
}

