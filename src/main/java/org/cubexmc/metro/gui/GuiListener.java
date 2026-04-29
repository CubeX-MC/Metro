package org.cubexmc.metro.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.cubexmc.metro.Metro;
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
        // 防止在 GUI 中拖拽物品
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }
}

