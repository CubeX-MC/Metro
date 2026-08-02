package org.cubexmc.metro.gui

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.controller.AddStopController
import org.cubexmc.metro.gui.controller.ConfirmActionController
import org.cubexmc.metro.gui.controller.LineBoardingChoiceController
import org.cubexmc.metro.gui.controller.LineDetailController
import org.cubexmc.metro.gui.controller.LineListController
import org.cubexmc.metro.gui.controller.LineSettingsController
import org.cubexmc.metro.gui.controller.MainMenuController
import org.cubexmc.metro.gui.controller.StopListController
import org.cubexmc.metro.gui.controller.StopSettingsController
import org.cubexmc.metro.util.SchedulerUtil

/**
 * GUI 事件监听器
 */
class GuiListener(private val plugin: Metro) : Listener {

    private val addStopController = AddStopController(plugin)
    private val lineBoardingChoiceController = LineBoardingChoiceController(plugin)
    private val lineDetailController = LineDetailController(plugin)
    private val lineListController = LineListController(plugin)
    private val lineSettingsController = LineSettingsController(plugin)
    private val mainMenuController = MainMenuController(plugin)
    private val stopListController = StopListController(plugin)
    private val stopSettingsController = StopSettingsController(plugin)
    private val confirmActionController = ConfirmActionController(plugin)

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val inv = event.view.topInventory

        // 检查是否是我们的 GUI
        val holder = inv.holder as? GuiHolder ?: return

        // 取消事件，防止物品被拿走
        event.isCancelled = true
        event.result = Event.Result.DENY

        // 忽略非玩家点击
        val player = event.whoClicked as? Player ?: return

        // 被取消的点击在客户端仍会显示为“已移动”，下一 tick 重发一次背包
        // 内容，避免玩家看到 GUI 物品出现在自己背包里
        resyncInventory(player)

        val slot = event.rawSlot

        // 忽略点击 GUI 外部
        if (slot < 0 || slot >= inv.size) {
            return
        }

        // 根据 GUI 类型处理
        when (holder.getType()) {
            GuiHolder.GuiType.MAIN_MENU -> mainMenuController.handleClick(player, holder, slot)

            GuiHolder.GuiType.LINE_LIST ->
                lineListController.handleLineListClick(player, holder, slot, event.isRightClick)

            GuiHolder.GuiType.STOP_LIST ->
                stopListController.handleStopListClick(player, holder, slot, event.isRightClick)

            GuiHolder.GuiType.LINE_VARIANTS ->
                lineListController.handleLineVariantsClick(player, holder, slot, event.isRightClick)

            GuiHolder.GuiType.STOP_VARIANTS ->
                stopListController.handleStopVariantsClick(player, holder, slot, event.isRightClick)

            GuiHolder.GuiType.LINE_DETAIL ->
                lineDetailController.handleClick(player, holder, slot, event.isRightClick, event.isShiftClick)

            GuiHolder.GuiType.ADD_STOP_LIST -> addStopController.handleAddStopListClick(player, holder, slot)

            GuiHolder.GuiType.ADD_STOP_VARIANTS -> addStopController.handleAddStopVariantsClick(player, holder, slot)

            GuiHolder.GuiType.LINE_BOARDING_CHOICE ->
                lineBoardingChoiceController.handleClick(player, holder, slot, event.isRightClick)

            GuiHolder.GuiType.LINE_SETTINGS -> lineSettingsController.handleClick(player, holder, slot)

            GuiHolder.GuiType.STOP_SETTINGS -> stopSettingsController.handleClick(player, holder, slot)

            GuiHolder.GuiType.CONFIRM_ACTION -> confirmActionController.handleClick(player, holder, slot)

            GuiHolder.GuiType.STOP_DETAIL -> {
                // STOP_DETAIL is reserved for future expansion.
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val topInventory = event.view.topInventory
        if (topInventory.holder !is GuiHolder) {
            return
        }

        val topSize = topInventory.size
        val touchesMetroGui = event.rawSlots.any { slot -> slot in 0 until topSize }
        if (!touchesMetroGui) {
            // 只在玩家自己的背包里整理物品，放行
            return
        }

        event.isCancelled = true
        event.result = Event.Result.DENY
        val player = event.whoClicked as? Player ?: return
        resyncInventory(player)
    }

    /**
     * 关闭 Metro GUI 时清除任何逃逸到玩家身上的 GUI 物品
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.topInventory.holder !is GuiHolder) {
            return
        }
        val player = event.player as? Player ?: return
        purgeGuiItems(player)
    }

    private fun purgeGuiItems(player: Player) {
        var removed = false

        if (GuiItemMarker.isGuiItem(player.itemOnCursor)) {
            player.setItemOnCursor(null)
            removed = true
        }

        val inventory = player.inventory
        val contents = inventory.contents
        for (slot in contents.indices) {
            if (GuiItemMarker.isGuiItem(contents[slot])) {
                inventory.setItem(slot, null)
                removed = true
            }
        }

        if (removed) {
            plugin.debug("gui", "Removed leaked Metro GUI items from " + player.name)
            resyncInventory(player)
        }
    }

    @Suppress("DEPRECATION")
    private fun resyncInventory(player: Player) {
        SchedulerUtil.entityRun(plugin, player, { player.updateInventory() }, 1L, -1L)
    }
}
