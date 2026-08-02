package org.cubexmc.metro.gui

import org.bukkit.inventory.Inventory
import java.util.Collections

/**
 * GUI 持有者，用于标识和存储 GUI 数据
 */
class GuiHolder(private val type: GuiType) : NullableInventoryHolder() {

    enum class GuiType {
        MAIN_MENU, // 主菜单
        LINE_LIST, // 线路列表
        STOP_LIST, // 站点列表
        LINE_VARIANTS, // 线路变体列表
        STOP_VARIANTS, // 站点变体列表
        LINE_DETAIL, // 线路详情
        STOP_DETAIL, // 站点详情
        ADD_STOP_LIST, // 添加站点列表
        ADD_STOP_VARIANTS, // 添加站点变体列表
        LINE_BOARDING_CHOICE, // 乘车线路选择
        LINE_SETTINGS, // 线路设置
        STOP_SETTINGS, // 站点设置
        CONFIRM_ACTION, // 操作确认
    }

    private val data: MutableMap<String, Any?> = HashMap()
    private var previousView: GuiView? = null
    private var inventory: Inventory? = null

    fun getType(): GuiType = type

    fun setData(key: String, value: Any?) {
        data[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: String): T? = data[key] as T?

    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: String, defaultValue: T): T {
        val value = data[key] ?: return defaultValue
        return value as T
    }

    fun getPreviousView(): GuiView? = previousView

    fun setPreviousView(previousView: GuiView?) {
        this.previousView = previousView
    }

    fun snapshot(): GuiView = GuiView(type, data, previousView)

    fun setInventory(inventory: Inventory?) {
        this.inventory = inventory
    }

    override fun currentInventory(): Inventory? = inventory

    class GuiView internal constructor(
        private val type: GuiType,
        data: Map<String, Any?>,
        private val previousView: GuiView?,
    ) {
        private val data: Map<String, Any?> = Collections.unmodifiableMap(HashMap(data))

        fun getType(): GuiType = type

        @Suppress("UNCHECKED_CAST")
        fun <T> getData(key: String): T? = data[key] as T?

        @Suppress("UNCHECKED_CAST")
        fun <T> getData(key: String, defaultValue: T): T {
            val value = data[key] ?: return defaultValue
            return value as T
        }

        fun getPreviousView(): GuiView? = previousView
    }
}
