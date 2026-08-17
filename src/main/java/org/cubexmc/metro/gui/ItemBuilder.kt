package org.cubexmc.metro.gui

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.cubexmc.gui.ItemBuilder as SharedItemBuilder
import org.cubexmc.gui.TextStyler
import org.cubexmc.metro.util.ColorUtil
import org.cubexmc.metro.util.MetroConstants

/**
 * 简化物品创建的工具类。
 *
 * 构造逻辑本身在共享模块 `cubex-gui` 的 [SharedItemBuilder]；这里只把它绑定到本插件的颜色管线
 * （legacy `&` + `&#RRGGBB`），让 GUI 的 90 多个调用点保持原样。
 */
class ItemBuilder @JvmOverloads constructor(material: Material, amount: Int = 1) {
    private val delegate = SharedItemBuilder(material, amount, STYLER)

    fun name(name: String): ItemBuilder = apply { delegate.name(name) }

    fun lore(vararg lore: String): ItemBuilder = apply { delegate.lore(*lore) }

    fun lore(lore: List<String>): ItemBuilder = apply { delegate.lore(lore) }

    fun glow(): ItemBuilder = apply { delegate.glow() }

    fun hideAttributes(): ItemBuilder = apply { delegate.hideAttributes() }

    fun build(): ItemStack {
        // 打上 GUI 标记，任何逃逸到玩家背包里的按钮都能被识别并清除
        delegate.guiMarker(MetroConstants.getGuiItemKey())
        return delegate.build()
    }

    private companion object {
        // colorize 的返回值可空；退回原串比抛 NPE 更符合"按钮至少画得出来"的预期。
        private val STYLER = TextStyler { input -> ColorUtil.colorize(input) ?: input }
    }
}
