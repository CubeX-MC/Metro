package org.cubexmc.metro.gui

import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.cubexmc.metro.util.MetroConstants

/**
 * Metro GUI 物品标记的读写入口。
 *
 * [ItemBuilder] 在构建时打标，本对象负责判定，任何逃逸到玩家背包里的按钮都能被识别并清除。
 */
object GuiItemMarker {

    /**
     * 判断物品是否由 Metro GUI 生成
     */
    @JvmStatic
    fun isGuiItem(stack: ItemStack?): Boolean {
        val guiItemKey = MetroConstants.getGuiItemKey()
        if (guiItemKey == null || stack == null || !stack.hasItemMeta()) {
            return false
        }
        val itemMeta = stack.itemMeta
        return itemMeta != null &&
            itemMeta.persistentDataContainer.has(guiItemKey, PersistentDataType.BYTE)
    }
}
