package org.cubexmc.metro.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * GUI 持有者，用于标识和存储 GUI 数据
 */
public class GuiHolder implements InventoryHolder {
    
    public enum GuiType {
        MAIN_MENU,      // 主菜单
        LINE_LIST,      // 线路列表
        STOP_LIST,      // 站点列表
        LINE_VARIANTS,  // 线路变体列表
        STOP_VARIANTS,  // 站点变体列表
        LINE_DETAIL,    // 线路详情
        STOP_DETAIL,    // 站点详情
        ADD_STOP_LIST,  // 添加站点列表
        ADD_STOP_VARIANTS, // 添加站点变体列表
        LINE_BOARDING_CHOICE, // 乘车线路选择
        LINE_SETTINGS,  // 线路设置
        STOP_SETTINGS,  // 站点设置
        CONFIRM_ACTION  // 操作确认
    }
    
    private final GuiType type;
    private final Map<String, Object> data;
    private Inventory inventory;
    
    public GuiHolder(GuiType type) {
        this.type = type;
        this.data = new HashMap<>();
    }
    
    public GuiType getType() {
        return type;
    }
    
    public void setData(String key, Object value) {
        data.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }
    
    public <T> T getData(String key, T defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }
    
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

