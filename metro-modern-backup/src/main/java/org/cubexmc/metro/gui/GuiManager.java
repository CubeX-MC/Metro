package org.cubexmc.metro.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder.GuiType;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.OwnershipUtil;

/**
 * GUI 管理器，负责创建和打开各种 GUI
 */
public class GuiManager {
    
    private final Metro plugin;
    
    // 每页显示的物品数量（4行 x 9列 = 36，底部2行留给控制栏）
    private static final int ITEMS_PER_PAGE = 36;
    
    // 槽位常量
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_FILTER = 49;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_BACK = 46;
    private static final int SLOT_PAGE_INFO = 47;
    
    public GuiManager(Metro plugin) {
        this.plugin = plugin;
    }
    
    private String msg(String key) {
        return plugin.getLanguageManager().getMessage(key);
    }
    
    private String msg(String key, String... replacements) {
        Map<String, Object> args = LanguageManager.args();
        for (int i = 0; i < replacements.length - 1; i += 2) {
            LanguageManager.put(args, replacements[i], replacements[i + 1]);
        }
        return plugin.getLanguageManager().getMessage(key, args);
    }
    
    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        GuiHolder holder = new GuiHolder(GuiType.MAIN_MENU);
        Inventory inv = Bukkit.createInventory(holder, 27, 
                ChatColor.DARK_GRAY + ChatColor.stripColor(msg("gui.main_menu.title")));
        holder.setInventory(inv);
        
        // 线路管理按钮
        inv.setItem(11, new ItemBuilder(Material.MINECART)
                .name(msg("gui.main_menu.line_manage"))
                .lore(msg("gui.main_menu.line_manage_lore1"), 
                      msg("gui.main_menu.line_manage_lore2"))
                .build());
        
        // 站点管理按钮
        inv.setItem(15, new ItemBuilder(Material.RAIL)
                .name(msg("gui.main_menu.stop_manage"))
                .lore(msg("gui.main_menu.stop_manage_lore1"), 
                      msg("gui.main_menu.stop_manage_lore2"))
                .build());
        
        // 填充边框
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
        
        player.openInventory(inv);
    }
    
    /**
     * 打开线路列表
     * @param player 玩家
     * @param page 页码（从0开始）
     * @param showOnlyMine 是否只显示自己管理的
     */
    public void openLineList(Player player, int page, boolean showOnlyMine) {
        GuiHolder holder = new GuiHolder(GuiType.LINE_LIST);
        holder.setData("page", page);
        holder.setData("showOnlyMine", showOnlyMine);
        
        // 获取线路列表
        List<Line> allLines = new ArrayList<>(plugin.getLineManager().getAllLines());
        
        // 筛选
        List<Line> filteredLines;
        if (showOnlyMine && !OwnershipUtil.hasAdminBypass(player)) {
            filteredLines = allLines.stream()
                    .filter(line -> OwnershipUtil.canManageLine(player, line))
                    .collect(Collectors.toList());
        } else {
            filteredLines = allLines;
        }
        
        // 按名称分组
        Map<String, List<Line>> groupedLines = filteredLines.stream()
                .collect(Collectors.groupingBy(Line::getName));
        
        // 排序名称
        List<String> sortedNames = new ArrayList<>(groupedLines.keySet());
        sortedNames.sort(String::compareTo);
        
        holder.setData("lineNames", sortedNames);
        holder.setData("groupedLines", groupedLines);
        
        // 计算分页
        int totalPages = Math.max(1, (int) Math.ceil((double) sortedNames.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);
        
        String titleKey = showOnlyMine ? "gui.line_list.title_mine" : "gui.line_list.title_all";
        String title = ChatColor.translateAlternateColorCodes('&', msg(titleKey));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        
        // 填充线路物品
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, sortedNames.size());
        
        for (int i = start; i < end; i++) {
            String name = sortedNames.get(i);
            List<Line> variants = groupedLines.get(name);
            Line representative = variants.get(0);
            int slot = i - start;
            
            List<String> lore = new ArrayList<>();
            
            if (variants.size() > 1) {
                lore.add(msg("gui.common.variants", "count", String.valueOf(variants.size())));
                lore.add("");
                lore.add(msg("gui.line_list.click_view_variants"));
            } else {
                lore.add(msg("gui.common.id", "id", representative.getId()));
                lore.add(msg("gui.line_list.stop_count", "count", String.valueOf(representative.getOrderedStopIds().size())));
                if (representative.getColor() != null) {
                    lore.add(msg("gui.line_list.color") + representative.getColor() + "■■■■■");
                }
                lore.add("");
                if (OwnershipUtil.canManageLine(player, representative)) {
                    lore.add(msg("gui.line_list.can_manage"));
                } else {
                    lore.add(msg("gui.line_list.view_only"));
                }
                lore.add("");
                lore.add(msg("gui.line_list.click_view"));
            }
            
            // 根据线路颜色选择羊毛颜色
            Material material = getWoolByColor(representative.getColor());
            
            inv.setItem(slot, new ItemBuilder(material)
                    .name((representative.getColor() != null ? representative.getColor() : "&f") + name)
                    .lore(lore)
                    .build());
        }
        
        // 底部控制栏
        addControlBar(inv, page, totalPages, showOnlyMine);
        
        player.openInventory(inv);
    }
    
    /**
     * 打开线路变体列表
     */
    public void openLineVariants(Player player, String lineName, int page) {
        GuiHolder holder = new GuiHolder(GuiType.LINE_VARIANTS);
        holder.setData("page", page);
        holder.setData("lineName", lineName);
        
        // 获取该名称的所有线路
        List<Line> variants = plugin.getLineManager().getAllLines().stream()
                .filter(line -> line.getName().equals(lineName))
                .sorted(Comparator.comparing(Line::getId))
                .collect(Collectors.toList());
                
        holder.setData("lines", variants);
        
        int totalPages = Math.max(1, (int) Math.ceil((double) variants.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);
        
        String title = ChatColor.translateAlternateColorCodes('&', lineName + " - " + msg("gui.common.variants_title"));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, variants.size());
        
        for (int i = start; i < end; i++) {
            Line line = variants.get(i);
            int slot = i - start;
            
            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.common.id", "id", line.getId()));
            lore.add(msg("gui.line_list.stop_count", "count", String.valueOf(line.getOrderedStopIds().size())));
            if (line.getColor() != null) {
                lore.add(msg("gui.line_list.color") + line.getColor() + "■■■■■");
            }
            lore.add("");
            if (OwnershipUtil.canManageLine(player, line)) {
                lore.add(msg("gui.line_list.can_manage"));
            } else {
                lore.add(msg("gui.line_list.view_only"));
            }
            lore.add("");
            lore.add(msg("gui.line_list.click_view"));
            
            Material material = getWoolByColor(line.getColor());
            
            inv.setItem(slot, new ItemBuilder(material)
                    .name((line.getColor() != null ? line.getColor() : "&f") + line.getId()) // Use ID as name here to distinguish
                    .lore(lore)
                    .build());
        }
        
        // Add back button
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(msg("gui.common.back"))
                .build());
                
        // Add page controls if needed
        if (totalPages > 1) {
            if (page > 0) {
                inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW).name(msg("gui.common.prev_page")).build());
            }
            inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                    .name(msg("gui.common.page_info", "page", String.valueOf(page + 1), "total", String.valueOf(totalPages)))
                    .build());
            if (page < totalPages - 1) {
                inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW).name(msg("gui.common.next_page")).build());
            }
        }
        
        player.openInventory(inv);
    }

    /**
     * 打开站点列表
     * @param player 玩家
     * @param page 页码（从0开始）
     * @param showOnlyMine 是否只显示自己管理的
     */
    public void openStopList(Player player, int page, boolean showOnlyMine) {
        GuiHolder holder = new GuiHolder(GuiType.STOP_LIST);
        holder.setData("page", page);
        holder.setData("showOnlyMine", showOnlyMine);
        
        // 获取站点列表
        List<Stop> allStops = plugin.getStopManager().getAllStopIds().stream()
                .map(id -> plugin.getStopManager().getStop(id))
                .filter(stop -> stop != null)
                .collect(Collectors.toList());
        
        // 筛选
        List<Stop> filteredStops;
        if (showOnlyMine && !OwnershipUtil.hasAdminBypass(player)) {
            filteredStops = allStops.stream()
                    .filter(stop -> OwnershipUtil.canManageStop(player, stop))
                    .collect(Collectors.toList());
        } else {
            filteredStops = allStops;
        }
        
        // 按名称分组
        Map<String, List<Stop>> groupedStops = filteredStops.stream()
                .collect(Collectors.groupingBy(Stop::getName));
        
        // 排序名称
        List<String> sortedNames = new ArrayList<>(groupedStops.keySet());
        sortedNames.sort(String::compareTo);
        
        holder.setData("stopNames", sortedNames);
        holder.setData("groupedStops", groupedStops);
        
        // 计算分页
        int totalPages = Math.max(1, (int) Math.ceil((double) sortedNames.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);
        
        String titleKey = showOnlyMine ? "gui.stop_list.title_mine" : "gui.stop_list.title_all";
        String title = ChatColor.translateAlternateColorCodes('&', msg(titleKey));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        
        // 填充站点物品
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, sortedNames.size());
        
        for (int i = start; i < end; i++) {
            String name = sortedNames.get(i);
            List<Stop> variants = groupedStops.get(name);
            Stop representative = variants.get(0);
            int slot = i - start;
            
            List<String> lore = new ArrayList<>();
            
            if (variants.size() > 1) {
                lore.add(msg("gui.common.variants", "count", String.valueOf(variants.size())));
                lore.add("");
                lore.add(msg("gui.stop_list.click_view_variants"));
            } else {
                lore.add(msg("gui.common.id", "id", representative.getId()));
                
                // 显示所属线路
                List<String> lineNames = new ArrayList<>();
                for (Line line : plugin.getLineManager().getAllLines()) {
                    if (line.containsStop(representative.getId())) {
                        String coloredName = (line.getColor() != null ? line.getColor() : "&f") + line.getName();
                        lineNames.add(coloredName);
                    }
                }
                if (!lineNames.isEmpty()) {
                    lore.add(msg("gui.stop_list.lines") + String.join("&7, ", lineNames));
                } else {
                    lore.add(msg("gui.stop_list.no_lines"));
                }
                
                lore.add("");
                if (OwnershipUtil.canManageStop(player, representative)) {
                    lore.add(msg("gui.line_list.can_manage"));
                } else {
                    lore.add(msg("gui.line_list.view_only"));
                }
                lore.add("");
                if (player.hasPermission("metro.tp")) {
                    if (representative.getStopPointLocation() != null) {
                        lore.add(msg("gui.stop_list.click_tp"));
                    } else {
                        lore.add(msg("gui.stop_list.no_stoppoint"));
                    }
                }
            }
            
            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("&a" + name)
                    .lore(lore)
                    .build());
        }
        
        // 底部控制栏
        addControlBar(inv, page, totalPages, showOnlyMine);
        
        player.openInventory(inv);
    }

    /**
     * 打开站点变体列表
     */
    public void openStopVariants(Player player, String stopName, int page) {
        GuiHolder holder = new GuiHolder(GuiType.STOP_VARIANTS);
        holder.setData("page", page);
        holder.setData("stopName", stopName);
        
        // 获取该名称的所有站点
        List<Stop> variants = plugin.getStopManager().getAllStopIds().stream()
                .map(id -> plugin.getStopManager().getStop(id))
                .filter(stop -> stop != null && stop.getName().equals(stopName))
                .sorted(Comparator.comparing(Stop::getId))
                .collect(Collectors.toList());
                
        holder.setData("stops", variants);
        
        int totalPages = Math.max(1, (int) Math.ceil((double) variants.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);
        
        String title = ChatColor.translateAlternateColorCodes('&', stopName + " - " + msg("gui.common.variants_title"));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, variants.size());
        
        for (int i = start; i < end; i++) {
            Stop stop = variants.get(i);
            int slot = i - start;
            
            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.common.id", "id", stop.getId()));
            
            // 显示所属线路
            List<String> lineNames = new ArrayList<>();
            for (Line line : plugin.getLineManager().getAllLines()) {
                if (line.containsStop(stop.getId())) {
                    String coloredName = (line.getColor() != null ? line.getColor() : "&f") + line.getName();
                    lineNames.add(coloredName);
                }
            }
            if (!lineNames.isEmpty()) {
                lore.add(msg("gui.stop_list.lines") + String.join("&7, ", lineNames));
            } else {
                lore.add(msg("gui.stop_list.no_lines"));
            }
            
            lore.add("");
            if (OwnershipUtil.canManageStop(player, stop)) {
                lore.add(msg("gui.line_list.can_manage"));
            } else {
                lore.add(msg("gui.line_list.view_only"));
            }
            lore.add("");
            if (player.hasPermission("metro.tp")) {
                if (stop.getStopPointLocation() != null) {
                    lore.add(msg("gui.stop_list.click_tp"));
                } else {
                    lore.add(msg("gui.stop_list.no_stoppoint"));
                }
            }
            
            inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                    .name("&a" + stop.getId()) // Use ID as name
                    .lore(lore)
                    .build());
        }
        
        // Add back button
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(msg("gui.common.back"))
                .build());
                
        // Add page controls if needed
        if (totalPages > 1) {
            if (page > 0) {
                inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW).name(msg("gui.common.prev_page")).build());
            }
            inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                    .name(msg("gui.common.page_info", "page", String.valueOf(page + 1), "total", String.valueOf(totalPages)))
                    .build());
            if (page < totalPages - 1) {
                inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW).name(msg("gui.common.next_page")).build());
            }
        }
        
        player.openInventory(inv);
    }
    
    /**
     * 打开线路详情（站点列表）
     */
    public void openLineDetail(Player player, String lineId, int page) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.line_not_found",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId)));
            return;
        }
        
        GuiHolder holder = new GuiHolder(GuiType.LINE_DETAIL);
        holder.setData("lineId", lineId);
        holder.setData("page", page);
        
        List<String> stopIds = line.getOrderedStopIds();
        int totalPages = Math.max(1, (int) Math.ceil((double) stopIds.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages - 1);
        page = Math.max(0, page);
        holder.setData("page", page);
        holder.setData("totalPages", totalPages);
        
        String coloredName = (line.getColor() != null ? line.getColor() : "") + line.getName();
        String title = ChatColor.translateAlternateColorCodes('&', 
                msg("gui.line_detail.title") + coloredName);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        
        boolean canManage = OwnershipUtil.canManageLine(player, line);
        
        // 填充站点物品
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, stopIds.size());
        
        for (int i = start; i < end; i++) {
            String stopId = stopIds.get(i);
            Stop stop = plugin.getStopManager().getStop(stopId);
            int slot = i - start;
            
            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.line_detail.index", "index", String.valueOf(i + 1)));
            
            if (stop != null) {
                lore.add(msg("gui.common.id", "id", stop.getId()));
                if (i == 0) {
                    lore.add(msg("gui.line_detail.start_stop"));
                } else if (i == stopIds.size() - 1) {
                    lore.add(msg("gui.line_detail.end_stop"));
                }
                lore.add("");
                if (player.hasPermission("metro.tp") && stop.getStopPointLocation() != null) {
                    lore.add(msg("gui.line_detail.click_tp"));
                }
                if (canManage) {
                    lore.add(msg("gui.line_detail.click_remove"));
                }
                
                inv.setItem(slot, new ItemBuilder(Material.OAK_SIGN)
                        .name("&a" + stop.getName())
                        .lore(lore)
                        .build());
            } else {
                lore.add(msg("gui.line_detail.stop_not_exist"));
                inv.setItem(slot, new ItemBuilder(Material.BARRIER)
                        .name("&c" + stopId)
                        .lore(lore)
                        .build());
            }
        }
        
        // 底部控制栏（简化版）
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, filler);
        }
        
        // 上一页
        if (page > 0) {
            inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.prev_page"))
                    .build());
        }
        
        // 页码信息
        inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(msg("gui.control.page_info", "current", String.valueOf(page + 1), "total", String.valueOf(totalPages)))
                .lore(msg("gui.control.stop_count", "count", String.valueOf(stopIds.size())))
                .build());
        
        // 下一页
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.next_page"))
                    .build());
        }
        
        // 返回按钮
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_line_list"))
                .build());
        
        player.openInventory(inv);
    }
    
    /**
     * 添加控制栏
     */
    private void addControlBar(Inventory inv, int page, int totalPages, 
                               boolean showOnlyMine) {
        // 填充底部
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, filler);
        }
        
        // 上一页
        if (page > 0) {
            inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.prev_page"))
                    .build());
        }
        
        // 返回主菜单
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_main"))
                .build());
        
        // 页码信息
        inv.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(msg("gui.control.page_info", "current", String.valueOf(page + 1), "total", String.valueOf(totalPages)))
                .build());
        
        // 筛选按钮
        String filterName = showOnlyMine ? msg("gui.control.filter_mine") : msg("gui.control.filter_all");
        String filterLore = showOnlyMine ? msg("gui.control.filter_lore_mine") : msg("gui.control.filter_lore_all");
        inv.setItem(SLOT_FILTER, new ItemBuilder(Material.HOPPER)
                .name(filterName)
                .lore(filterLore)
                .build());
        
        // 下一页
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name(msg("gui.control.next_page"))
                    .build());
        }
    }
    
    /**
     * 根据颜色代码获取羊毛材质
     */
    private Material getWoolByColor(String colorCode) {
        if (colorCode == null) return Material.WHITE_WOOL;
        
        // 移除 & 前缀
        String code = colorCode.replace("&", "").toLowerCase();
        
        return switch (code) {
            case "0" -> Material.BLACK_WOOL;        // 黑色
            case "1" -> Material.BLUE_WOOL;         // 深蓝
            case "2" -> Material.GREEN_WOOL;        // 深绿
            case "3" -> Material.CYAN_WOOL;         // 深青
            case "4" -> Material.RED_WOOL;          // 深红
            case "5" -> Material.PURPLE_WOOL;       // 紫色
            case "6" -> Material.ORANGE_WOOL;       // 金色/橙色
            case "7" -> Material.LIGHT_GRAY_WOOL;   // 灰色
            case "8" -> Material.GRAY_WOOL;         // 深灰
            case "9" -> Material.LIGHT_BLUE_WOOL;   // 蓝色
            case "a" -> Material.LIME_WOOL;         // 绿色
            case "b" -> Material.LIGHT_BLUE_WOOL;   // 青色
            case "c" -> Material.RED_WOOL;          // 红色
            case "d" -> Material.PINK_WOOL;         // 粉色
            case "e" -> Material.YELLOW_WOOL;       // 黄色
            case "f" -> Material.WHITE_WOOL;        // 白色
            default -> Material.WHITE_WOOL;
        };
    }
}
