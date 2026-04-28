package org.cubexmc.metro.gui;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiHolder.GuiType;
import org.cubexmc.metro.gui.view.AddStopView;
import org.cubexmc.metro.gui.view.ConfirmActionView;
import org.cubexmc.metro.gui.view.LineDetailView;
import org.cubexmc.metro.gui.view.LineListView;
import org.cubexmc.metro.gui.view.LineBoardingChoiceView;
import org.cubexmc.metro.gui.view.StopListView;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

/**
 * GUI 管理器，负责创建和打开各种 GUI
 */
public class GuiManager {
    
    private final Metro plugin;
    private final AddStopView addStopView;
    private final LineListView lineListView;
    private final StopListView stopListView;
    private final LineDetailView lineDetailView;
    private final LineBoardingChoiceView lineBoardingChoiceView;
    private final ConfirmActionView confirmActionView;
    
    public GuiManager(Metro plugin) {
        this.plugin = plugin;
        this.addStopView = new AddStopView(plugin);
        this.lineListView = new LineListView(plugin);
        this.stopListView = new StopListView(plugin);
        this.lineDetailView = new LineDetailView(plugin);
        this.lineBoardingChoiceView = new LineBoardingChoiceView(plugin);
        this.confirmActionView = new ConfirmActionView(plugin);
    }

    private GuiHolder createHolder(GuiType type, GuiHolder.GuiView previousView) {
        GuiHolder holder = new GuiHolder(type);
        holder.setPreviousView(previousView);
        return holder;
    }

    public void openPreviousView(Player player, GuiHolder holder, Runnable fallback) {
        if (holder != null && openView(player, holder.getPreviousView())) {
            return;
        }
        fallback.run();
    }

    public boolean openView(Player player, GuiHolder.GuiView view) {
        if (view == null) {
            return false;
        }
        GuiHolder.GuiView previous = view.getPreviousView();
        switch (view.getType()) {
            case MAIN_MENU -> openMainMenu(player);
            case LINE_LIST -> openLineList(player, view.getData("page", 0), view.getData("showOnlyMine", false), previous);
            case STOP_LIST -> openStopList(player, view.getData("page", 0), view.getData("showOnlyMine", false), previous);
            case LINE_VARIANTS -> openLineVariants(player, view.getData("lineName"), view.getData("page", 0), previous);
            case STOP_VARIANTS -> openStopVariants(player, view.getData("stopName"), view.getData("page", 0), previous);
            case LINE_DETAIL -> openLineDetail(player, view.getData("lineId"), view.getData("page", 0), previous);
            case ADD_STOP_LIST -> openAddStopList(player, view.getData("lineId"), view.getData("page", 0),
                    view.getData("showOnlyMine", false), previous);
            case ADD_STOP_VARIANTS -> openAddStopVariants(player, view.getData("lineId"), view.getData("stopName"),
                    view.getData("page", 0), previous);
            case LINE_SETTINGS -> openLineSettings(player, view.getData("lineId"), previous);
            case STOP_SETTINGS -> openStopSettings(player, view.getData("stopId"), view.getData("fromLineId"), previous);
            case LINE_BOARDING_CHOICE -> {
                String stopId = view.getData("stopId");
                Stop stop = stopId == null ? null : plugin.getStopManager().getStop(stopId);
                if (stop == null) {
                    return false;
                }
                openLineBoardingChoice(player, stop, view.getData("page", 0), previous);
            }
            case CONFIRM_ACTION -> {
                return false;
            }
        }
        return true;
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
        inv.setItem(11, new ItemBuilder(Material.RAIL)
                .name(msg("gui.main_menu.line_manage"))
                .lore(msg("gui.main_menu.line_manage_lore1"), 
                      msg("gui.main_menu.line_manage_lore2"))
                .build());
        
        // 站点管理按钮
        inv.setItem(15, new ItemBuilder(Material.MINECART)
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
     * 打开乘车线路选择界面。
     */
    public void openLineBoardingChoice(Player player, Stop stop, int page) {
        openLineBoardingChoice(player, stop, page, null);
    }

    public void openLineBoardingChoice(Player player, Stop stop, int page, GuiHolder.GuiView previousView) {
        lineBoardingChoiceView.open(player, stop, page, previousView);
    }
    
    /**
     * 打开线路列表
     * @param player 玩家
     * @param page 页码（从0开始）
     * @param showOnlyMine 是否只显示自己管理的
     */
    public void openLineList(Player player, int page, boolean showOnlyMine) {
        openLineList(player, page, showOnlyMine, null);
    }

    public void openLineList(Player player, int page, boolean showOnlyMine, GuiHolder.GuiView previousView) {
        lineListView.openLineList(player, page, showOnlyMine, previousView);
    }
    
    /**
     * 打开线路变体列表
     */
    public void openLineVariants(Player player, String lineName, int page) {
        openLineVariants(player, lineName, page, null);
    }

    public void openLineVariants(Player player, String lineName, int page, GuiHolder.GuiView previousView) {
        lineListView.openLineVariants(player, lineName, page, previousView);
    }

    /**
     * 打开站点列表
     * @param player 玩家
     * @param page 页码（从0开始）
     * @param showOnlyMine 是否只显示自己管理的
     */
    public void openStopList(Player player, int page, boolean showOnlyMine) {
        openStopList(player, page, showOnlyMine, null);
    }

    public void openStopList(Player player, int page, boolean showOnlyMine, GuiHolder.GuiView previousView) {
        stopListView.openStopList(player, page, showOnlyMine, previousView);
    }

    /**
     * 打开站点变体列表
     */
    public void openStopVariants(Player player, String stopName, int page) {
        openStopVariants(player, stopName, page, null);
    }

    public void openStopVariants(Player player, String stopName, int page, GuiHolder.GuiView previousView) {
        stopListView.openStopVariants(player, stopName, page, previousView);
    }
    
    /**
     * 打开添加站点列表
     */
    public void openAddStopList(Player player, String lineId, int page, boolean showOnlyMine) {
        openAddStopList(player, lineId, page, showOnlyMine, null);
    }

    public void openAddStopList(Player player, String lineId, int page, boolean showOnlyMine,
                                GuiHolder.GuiView previousView) {
        addStopView.openAddStopList(player, lineId, page, showOnlyMine, previousView);
    }
    
    /**
     * 打开添加站点变体列表
     */
    public void openAddStopVariants(Player player, String lineId, String stopName, int page) {
        openAddStopVariants(player, lineId, stopName, page, null);
    }

    public void openAddStopVariants(Player player, String lineId, String stopName, int page,
                                    GuiHolder.GuiView previousView) {
        addStopView.openAddStopVariants(player, lineId, stopName, page, previousView);
    }
    
    /**
     * 打开线路详情（站点列表）
     */
    public void openLineDetail(Player player, String lineId, int page) {
        openLineDetail(player, lineId, page, null);
    }

    public void openLineDetail(Player player, String lineId, int page, GuiHolder.GuiView previousView) {
        lineDetailView.open(player, lineId, page, previousView);
    }
    
    /**
     * 打开线路设置
     */
    public void openLineSettings(Player player, String lineId) {
        openLineSettings(player, lineId, null);
    }

    public void openLineSettings(Player player, String lineId, GuiHolder.GuiView previousView) {
        Line line = plugin.getLineManager().getLine(lineId);
        if (line == null) return;
        
        GuiHolder holder = createHolder(GuiType.LINE_SETTINGS, previousView);
        holder.setData("lineId", lineId);
        
        Inventory inv = Bukkit.createInventory(holder, 36,
                ChatColor.translateAlternateColorCodes('&', msg("gui.line_settings.title") + line.getName()));
        holder.setInventory(inv);
        
        // 填充背景
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);
        
        // 重命名按钮
        inv.setItem(9, new ItemBuilder(Material.NAME_TAG)
                .name(msg("gui.line_settings.rename"))
                .lore(msg("gui.line_settings.rename_lore"))
                .build());

        boolean recording = plugin.getRouteRecorder().isRecording(lineId);
        inv.setItem(1, new ItemBuilder(recording ? Material.REDSTONE_TORCH : Material.MINECART)
                .name(msg(recording ? "gui.line_settings.record_stop" : "gui.line_settings.record_start"))
                .lore(msg(recording ? "gui.line_settings.record_stop_lore" : "gui.line_settings.record_start_lore"))
                .build());

        inv.setItem(3, new ItemBuilder(Material.COMPASS)
                .name(msg("gui.line_settings.route_info"))
                .lore(msg("gui.line_settings.route_info_lore",
                        "point_count", String.valueOf(line.getRoutePoints().size())))
                .build());

        inv.setItem(5, new ItemBuilder(Material.TNT)
                .name(msg("gui.line_settings.clear_route"))
                .lore(msg("gui.line_settings.clear_route_lore"))
                .build());

        inv.setItem(7, new ItemBuilder(line.isRailProtected() ? Material.IRON_BARS : Material.RAIL)
                .name(msg(line.isRailProtected()
                        ? "gui.line_settings.protection_on"
                        : "gui.line_settings.protection_off"))
                .lore(msg("gui.line_settings.protection_lore"))
                .build());
                
        // 修改最高速度按钮
        inv.setItem(11, new ItemBuilder(Material.MINECART)
                .name(msg("gui.line_settings.set_speed"))
                .lore(msg("gui.line_settings.set_speed_lore"))
                .build());
                
        // 设置票价按钮
        inv.setItem(13, new ItemBuilder(Material.EMERALD)
                .name(msg("gui.line_settings.set_price"))
                .lore(msg("gui.line_settings.set_price_lore"))
                .build());
                
        // 生成对向线路按钮
        inv.setItem(15, new ItemBuilder(Material.COMPARATOR)
                .name(msg("gui.line_settings.clone_reverse"))
                .lore(msg("gui.line_settings.clone_reverse_lore"))
                .build());
                
        // 删除线路按钮
        inv.setItem(17, new ItemBuilder(Material.BARRIER)
                .name(msg("gui.line_settings.delete"))
                .lore(msg("gui.line_settings.delete_lore"))
                .build());

        inv.setItem(19, new ItemBuilder(getWoolByColor(line.getColor()))
                .name(msg("gui.line_settings.set_color"))
                .lore(msg("gui.line_settings.current_color", "color", line.getColor()),
                        msg("gui.line_settings.set_color_lore"))
                .build());

        String terminusName = line.getTerminusName() == null || line.getTerminusName().isBlank()
                ? msg("line.info_default")
                : line.getTerminusName();
        inv.setItem(21, new ItemBuilder(Material.OAK_SIGN)
                .name(msg("gui.line_settings.set_terminus"))
                .lore(msg("gui.line_settings.current_terminus", "terminus_name", terminusName),
                        msg("gui.line_settings.set_terminus_lore"))
                .build());
                
        // 返回按钮
        inv.setItem(31, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_line_list"))
                .build());
                
        player.openInventory(inv);
    }

    /**
     * 打开站点设置
     */
    public void openStopSettings(Player player, String stopId) {
        openStopSettings(player, stopId, null, null);
    }

    /**
     * 打开站点设置
     */
    public void openStopSettings(Player player, String stopId, String fromLineId) {
        openStopSettings(player, stopId, fromLineId, null);
    }

    public void openStopSettings(Player player, String stopId, String fromLineId, GuiHolder.GuiView previousView) {
        Stop stop = plugin.getStopManager().getStop(stopId);
        if (stop == null) return;
        
        GuiHolder holder = createHolder(GuiType.STOP_SETTINGS, previousView);
        holder.setData("stopId", stopId);
        if (fromLineId != null) {
            holder.setData("fromLineId", fromLineId);
        }
        
        Inventory inv = Bukkit.createInventory(holder, 27, 
                ChatColor.translateAlternateColorCodes('&', msg("gui.stop_settings.title") + stop.getName()));
        holder.setInventory(inv);
        
        // 填充背景
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        
        // 重命名按钮
        inv.setItem(11, new ItemBuilder(Material.NAME_TAG)
                .name(msg("gui.stop_settings.rename"))
                .lore(msg("gui.stop_settings.rename_lore"))
                .build());

        String stopPointText = stop.getStopPointLocation() == null
                ? msg("gui.stop_settings.stoppoint_not_set")
                : formatLocation(stop.getStopPointLocation());
        inv.setItem(13, new ItemBuilder(Material.RAIL)
                .name(msg("gui.stop_settings.set_point"))
                .lore(msg("gui.stop_settings.current_stoppoint", "stoppoint", stopPointText),
                        msg("gui.stop_settings.set_point_lore"))
                .build());
                
        // 删除站点按钮
        inv.setItem(15, new ItemBuilder(Material.BARRIER)
                .name(msg("gui.stop_settings.delete"))
                .lore(msg("gui.stop_settings.delete_lore"))
                .build());
                
        // 返回按钮
        inv.setItem(22, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name(msg("gui.control.back_main"))
                .build());
                
        player.openInventory(inv);
    }

    /**
     * 打开危险操作确认界面。
     */
    public void openConfirmAction(Player player, String action, String targetId, String targetName,
                                  String lineId, int returnPage) {
        openConfirmAction(player, action, targetId, targetName, lineId, returnPage, null);
    }

    public void openConfirmAction(Player player, String action, String targetId, String targetName,
                                  String lineId, int returnPage, GuiHolder.GuiView previousView) {
        confirmActionView.open(player, action, targetId, targetName, lineId, returnPage, previousView);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return msg("gui.stop_settings.stoppoint_not_set");
        }
        return location.getWorld().getName() + " "
                + location.getBlockX() + ", "
                + location.getBlockY() + ", "
                + location.getBlockZ();
    }

    /**
     * 根据颜色代码获取羊毛材质
     */
    private Material getWoolByColor(String colorCode) {
        return GuiColors.getWoolByColor(colorCode);
    }

}
