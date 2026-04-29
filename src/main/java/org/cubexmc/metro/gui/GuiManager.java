package org.cubexmc.metro.gui;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.view.AddStopView;
import org.cubexmc.metro.gui.view.ConfirmActionView;
import org.cubexmc.metro.gui.view.LineDetailView;
import org.cubexmc.metro.gui.view.LineListView;
import org.cubexmc.metro.gui.view.LineBoardingChoiceView;
import org.cubexmc.metro.gui.view.LineSettingsView;
import org.cubexmc.metro.gui.view.MainMenuView;
import org.cubexmc.metro.gui.view.StopListView;
import org.cubexmc.metro.gui.view.StopSettingsView;
import org.cubexmc.metro.model.Stop;

/**
 * GUI 管理器，负责创建和打开各种 GUI
 */
public class GuiManager {
    
    private final Metro plugin;
    private final MainMenuView mainMenuView;
    private final AddStopView addStopView;
    private final LineListView lineListView;
    private final StopListView stopListView;
    private final LineDetailView lineDetailView;
    private final LineSettingsView lineSettingsView;
    private final StopSettingsView stopSettingsView;
    private final LineBoardingChoiceView lineBoardingChoiceView;
    private final ConfirmActionView confirmActionView;
    
    public GuiManager(Metro plugin) {
        this.plugin = plugin;
        this.mainMenuView = new MainMenuView(plugin);
        this.addStopView = new AddStopView(plugin);
        this.lineListView = new LineListView(plugin);
        this.stopListView = new StopListView(plugin);
        this.lineDetailView = new LineDetailView(plugin);
        this.lineSettingsView = new LineSettingsView(plugin);
        this.stopSettingsView = new StopSettingsView(plugin);
        this.lineBoardingChoiceView = new LineBoardingChoiceView(plugin);
        this.confirmActionView = new ConfirmActionView(plugin);
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
    
    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        mainMenuView.open(player);
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
        lineSettingsView.open(player, lineId, previousView);
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
        stopSettingsView.open(player, stopId, fromLineId, previousView);
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

}
