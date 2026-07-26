package org.cubexmc.metro.gui;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataType;
import org.cubexmc.metro.util.ColorUtil;
import org.cubexmc.metro.util.MetroConstants;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 简化物品创建的工具类
 */
public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
        }
        return this;
    }

    public ItemBuilder lore(String... lore) {
        if (meta != null) {
            List<String> loreList = Arrays.stream(lore)
                    .map(ColorUtil::colorize)
                    .collect(Collectors.toList());
            meta.setLore(loreList);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (meta != null) {
            List<String> loreList = lore.stream()
                    .map(ColorUtil::colorize)
                    .collect(Collectors.toList());
            meta.setLore(loreList);
        }
        return this;
    }

    public ItemBuilder glow() {
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder hideAttributes() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            // 打上 GUI 标记，任何逃逸到玩家背包里的按钮都能被识别并清除
            NamespacedKey guiItemKey = MetroConstants.getGuiItemKey();
            if (guiItemKey != null) {
                meta.getPersistentDataContainer().set(guiItemKey, PersistentDataType.BYTE, (byte) 1);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 判断物品是否由 Metro GUI 生成
     */
    public static boolean isGuiItem(ItemStack stack) {
        NamespacedKey guiItemKey = MetroConstants.getGuiItemKey();
        if (guiItemKey == null || stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta itemMeta = stack.getItemMeta();
        return itemMeta != null
                && itemMeta.getPersistentDataContainer().has(guiItemKey, PersistentDataType.BYTE);
    }
}

