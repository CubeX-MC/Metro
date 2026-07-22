package org.cubexmc.metro.integration

import java.util.UUID
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.ServiceRegisterEvent
import org.bukkit.plugin.ServiceUnregisterEvent
import org.cubexmc.metro.Metro

class VaultIntegration(private val plugin: Metro) : Listener {
    var economy: Economy? = null
        private set
    var isEnabled: Boolean = false
        private set

    init {
        refreshProvider()
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Re-queries ServicesManager for the current Economy provider.
     * Safe to call at any time; does not require the "Vault" plugin to be loaded.
     */
    fun refreshProvider() {
        val registration = plugin.server.servicesManager.getRegistration(Economy::class.java)
        economy = registration?.provider
        isEnabled = economy != null
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onServiceRegister(event: ServiceRegisterEvent) {
        if (event.provider is Economy) {
            refreshProvider()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onServiceUnregister(event: ServiceUnregisterEvent) {
        if (event.provider is Economy) {
            economy = null
            isEnabled = false
        }
    }

    fun has(player: Player, amount: Double): Boolean {
        if (!isEnabled) {
            return false
        }
        return economy?.has(player, amount) ?: false
    }

    fun withdraw(player: Player, amount: Double): Boolean {
        if (!isEnabled) {
            return false
        }
        return economy?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false
    }

    fun deposit(uuid: UUID?, amount: Double): Boolean {
        if (!isEnabled || uuid == null) {
            return false
        }
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        return economy?.depositPlayer(offlinePlayer, amount)?.transactionSuccess() ?: false
    }

    fun format(amount: Double): String {
        if (!isEnabled) {
            return amount.toString()
        }
        return economy?.format(amount) ?: amount.toString()
    }
}
