package org.cubexmc.metro.gui

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.view.AddStopView
import org.cubexmc.metro.gui.view.ConfirmActionView
import org.cubexmc.metro.gui.view.LineBoardingChoiceView
import org.cubexmc.metro.gui.view.LineDetailView
import org.cubexmc.metro.gui.view.LineListView
import org.cubexmc.metro.gui.view.LineSettingsView
import org.cubexmc.metro.gui.view.MainMenuView
import org.cubexmc.metro.gui.view.StopListView
import org.cubexmc.metro.gui.view.StopSettingsView
import org.cubexmc.metro.model.Stop

/**
 * GUI 管理器，负责创建和打开各种 GUI
 */
class GuiManager(private val plugin: Metro) {

    private val mainMenuView = MainMenuView(plugin)
    private val addStopView = AddStopView(plugin)
    private val lineListView = LineListView(plugin)
    private val stopListView = StopListView(plugin)
    private val lineDetailView = LineDetailView(plugin)
    private val lineSettingsView = LineSettingsView(plugin)
    private val stopSettingsView = StopSettingsView(plugin)
    private val lineBoardingChoiceView = LineBoardingChoiceView(plugin)
    private val confirmActionView = ConfirmActionView(plugin)

    fun openPreviousView(player: Player, holder: GuiHolder?, fallback: Runnable) {
        if (holder != null && openView(player, holder.getPreviousView())) {
            return
        }
        fallback.run()
    }

    fun openView(player: Player, view: GuiHolder.GuiView?): Boolean {
        if (view == null) {
            return false
        }
        val previous = view.getPreviousView()
        when (view.getType()) {
            GuiHolder.GuiType.MAIN_MENU -> openMainMenu(player)

            GuiHolder.GuiType.LINE_LIST ->
                openLineList(player, view.getData("page", 0), view.getData("showOnlyMine", false), previous)

            GuiHolder.GuiType.STOP_LIST ->
                openStopList(player, view.getData("page", 0), view.getData("showOnlyMine", false), previous)

            GuiHolder.GuiType.LINE_VARIANTS ->
                openLineVariants(player, view.getData("lineName"), view.getData("page", 0), previous)

            GuiHolder.GuiType.STOP_VARIANTS ->
                openStopVariants(player, view.getData("stopName"), view.getData("page", 0), previous)

            GuiHolder.GuiType.LINE_DETAIL ->
                openLineDetail(player, view.getData("lineId"), view.getData("page", 0), previous)

            GuiHolder.GuiType.ADD_STOP_LIST ->
                openAddStopList(
                    player,
                    view.getData("lineId"),
                    view.getData("page", 0),
                    view.getData("showOnlyMine", false),
                    previous,
                )

            GuiHolder.GuiType.ADD_STOP_VARIANTS ->
                openAddStopVariants(
                    player,
                    view.getData("lineId"),
                    view.getData("stopName"),
                    view.getData("page", 0),
                    previous,
                )

            GuiHolder.GuiType.LINE_SETTINGS -> openLineSettings(player, view.getData("lineId"), previous)

            GuiHolder.GuiType.STOP_SETTINGS ->
                openStopSettings(player, view.getData("stopId"), view.getData("fromLineId"), previous)

            GuiHolder.GuiType.LINE_BOARDING_CHOICE -> {
                val stopId = view.getData<String>("stopId")
                val stop = if (stopId == null) null else plugin.stopManager.getStop(stopId)
                if (stop == null) {
                    return false
                }
                openLineBoardingChoice(player, stop, view.getData("page", 0), previous)
            }

            GuiHolder.GuiType.CONFIRM_ACTION -> return false

            GuiHolder.GuiType.STOP_DETAIL -> {
                // STOP_DETAIL is reserved for future expansion.
            }
        }
        return true
    }

    /**
     * 打开主菜单
     */
    fun openMainMenu(player: Player) {
        mainMenuView.open(player)
    }

    /**
     * 打开乘车线路选择界面。
     */
    @JvmOverloads
    fun openLineBoardingChoice(
        player: Player,
        stop: Stop,
        page: Int,
        previousView: GuiHolder.GuiView? = null,
    ) {
        lineBoardingChoiceView.open(player, stop, page, previousView)
    }

    /**
     * 打开线路列表
     * @param player 玩家
     * @param page 页码（从0开始）
     * @param showOnlyMine 是否只显示自己管理的
     */
    @JvmOverloads
    fun openLineList(
        player: Player,
        page: Int,
        showOnlyMine: Boolean,
        previousView: GuiHolder.GuiView? = null,
    ) {
        lineListView.openLineList(player, page, showOnlyMine, previousView)
    }

    /**
     * 打开线路变体列表
     */
    @JvmOverloads
    fun openLineVariants(
        player: Player,
        lineName: String?,
        page: Int,
        previousView: GuiHolder.GuiView? = null,
    ) {
        lineListView.openLineVariants(player, lineName, page, previousView)
    }

    /**
     * 打开站点列表
     * @param player 玩家
     * @param page 页码（从0开始）
     * @param showOnlyMine 是否只显示自己管理的
     */
    @JvmOverloads
    fun openStopList(
        player: Player,
        page: Int,
        showOnlyMine: Boolean,
        previousView: GuiHolder.GuiView? = null,
    ) {
        stopListView.openStopList(player, page, showOnlyMine, previousView)
    }

    /**
     * 打开站点变体列表
     */
    @JvmOverloads
    fun openStopVariants(
        player: Player,
        stopName: String?,
        page: Int,
        previousView: GuiHolder.GuiView? = null,
    ) {
        stopListView.openStopVariants(player, stopName, page, previousView)
    }

    /**
     * 打开添加站点列表
     */
    @JvmOverloads
    fun openAddStopList(
        player: Player,
        lineId: String?,
        page: Int,
        showOnlyMine: Boolean,
        previousView: GuiHolder.GuiView? = null,
    ) {
        addStopView.openAddStopList(player, lineId, page, showOnlyMine, previousView)
    }

    /**
     * 打开添加站点变体列表
     */
    @JvmOverloads
    fun openAddStopVariants(
        player: Player,
        lineId: String?,
        stopName: String?,
        page: Int,
        previousView: GuiHolder.GuiView? = null,
    ) {
        addStopView.openAddStopVariants(player, lineId, stopName, page, previousView)
    }

    /**
     * 打开线路详情（站点列表）
     */
    @JvmOverloads
    fun openLineDetail(
        player: Player,
        lineId: String?,
        page: Int,
        previousView: GuiHolder.GuiView? = null,
    ) {
        lineDetailView.open(player, lineId, page, previousView)
    }

    /**
     * 打开线路设置
     */
    @JvmOverloads
    fun openLineSettings(player: Player, lineId: String?, previousView: GuiHolder.GuiView? = null) {
        lineSettingsView.open(player, lineId, previousView)
    }

    /**
     * 打开站点设置
     */
    @JvmOverloads
    fun openStopSettings(
        player: Player,
        stopId: String?,
        fromLineId: String? = null,
        previousView: GuiHolder.GuiView? = null,
    ) {
        stopSettingsView.open(player, stopId, fromLineId, previousView)
    }

    /**
     * 打开危险操作确认界面。
     */
    @JvmOverloads
    fun openConfirmAction(
        player: Player,
        action: String,
        targetId: String?,
        targetName: String?,
        lineId: String?,
        returnPage: Int,
        previousView: GuiHolder.GuiView? = null,
    ) {
        confirmActionView.open(player, action, targetId, targetName, lineId, returnPage, previousView)
    }
}
