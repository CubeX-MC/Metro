package org.cubexmc.metro.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.gui.controller.AddStopController;
import org.cubexmc.metro.gui.controller.ConfirmActionController;
import org.cubexmc.metro.gui.controller.LineDetailController;
import org.cubexmc.metro.gui.controller.LineBoardingChoiceController;
import org.cubexmc.metro.gui.controller.LineListController;
import org.cubexmc.metro.gui.controller.LineSettingsController;
import org.cubexmc.metro.gui.controller.MainMenuController;
import org.cubexmc.metro.gui.controller.StopListController;
import org.cubexmc.metro.gui.controller.StopSettingsController;

/**
 * GUI 事件监听器
 */
public class GuiListener implements Listener {

    private final Metro plugin;
    private final AddStopController addStopController;
    private final LineBoardingChoiceController lineBoardingChoiceController;
    private final LineDetailController lineDetailController;
    private final LineListController lineListController;
    private final LineSettingsController lineSettingsController;
    private final MainMenuController mainMenuController;
    private final StopListController stopListController;
    private final StopSettingsController stopSettingsController;
    private final ConfirmActionController confirmActionController;
    
    public GuiListener(Metro plugin) {
        this.plugin = plugin;
        this.addStopController = new AddStopController(plugin);
        this.lineBoardingChoiceController = new LineBoardingChoiceController(plugin);
        this.lineDetailController = new LineDetailController(plugin);
        this.lineListController = new LineListController(plugin);
        this.lineSettingsController = new LineSettingsController(plugin);
        this.mainMenuController = new MainMenuController(plugin);
        this.stopListController = new StopListController(plugin);
        this.stopSettingsController = new StopSettingsController(plugin);
        this.confirmActionController = new ConfirmActionController(plugin);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Inventory inv = view.getTopInventory();
        
        // 检查是否是我们的 GUI
        if (!(inv.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        
        // 取消事件，防止物品被拿走
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        // 忽略非玩家点击
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 被取消的点击在客户端仍会显示为“已移动”，下一 tick 重发一次背包
        // 内容，避免玩家看到 GUI 物品出现在自己背包里
        resyncInventory(player);

        int slot = event.getRawSlot();
        
        // 忽略点击 GUI 外部
        if (slot < 0 || slot >= inv.getSize()) {
            return;
        }
        
        // 根据 GUI 类型处理
        switch (holder.getType()) {
            case MAIN_MENU -> mainMenuController.handleClick(player, holder, slot);
            case LINE_LIST -> lineListController.handleLineListClick(player, holder, slot, event.isRightClick());
            case STOP_LIST -> stopListController.handleStopListClick(player, holder, slot, event.isRightClick());
            case LINE_VARIANTS -> lineListController.handleLineVariantsClick(player, holder, slot,
                    event.isRightClick());
            case STOP_VARIANTS -> stopListController.handleStopVariantsClick(player, holder, slot,
                    event.isRightClick());
            case LINE_DETAIL -> lineDetailController.handleClick(player, holder, slot, event.isRightClick(),
                    event.isShiftClick());
            case ADD_STOP_LIST -> addStopController.handleAddStopListClick(player, holder, slot);
            case ADD_STOP_VARIANTS -> addStopController.handleAddStopVariantsClick(player, holder, slot);
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
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof GuiHolder)) {
            return;
        }

        int topSize = topInventory.getSize();
        boolean touchesMetroGui = event.getRawSlots().stream()
                .anyMatch(slot -> slot >= 0 && slot < topSize);
        if (!touchesMetroGui) {
            // 只在玩家自己的背包里整理物品，放行
            return;
        }

        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        if (event.getWhoClicked() instanceof Player player) {
            resyncInventory(player);
        }
    }

    /**
     * 关闭 Metro GUI 时清除任何逃逸到玩家身上的 GUI 物品
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        purgeGuiItems(player);
    }

    private void purgeGuiItems(Player player) {
        boolean removed = false;

        ItemStack cursor = player.getItemOnCursor();
        if (ItemBuilder.isGuiItem(cursor)) {
            player.setItemOnCursor(null);
            removed = true;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (ItemBuilder.isGuiItem(contents[slot])) {
                inventory.setItem(slot, null);
                removed = true;
            }
        }

        if (removed) {
            plugin.debug("gui", "Removed leaked Metro GUI items from " + player.getName());
            resyncInventory(player);
        }
    }

    @SuppressWarnings("deprecation")
    private void resyncInventory(Player player) {
        SchedulerUtil.entityRun(plugin, player, player::updateInventory, 1L, -1L);
    }
}

