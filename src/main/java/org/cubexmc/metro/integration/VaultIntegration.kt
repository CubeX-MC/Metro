package org.cubexmc.metro.integration

import java.util.UUID
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent
import org.cubexmc.metro.Metro

class VaultIntegration(private val plugin: Metro) : Listener {
    @Volatile
    var economy: Economy? = null
        private set

    val isEnabled: Boolean
        get() = economy != null

    init {
        refresh()
    }

    /**
     * Re-resolves the currently registered Vault economy provider.
     *
     * Economy bridges may register or replace their provider after Metro has
     * already enabled, so the provider must not be cached for the entire
     * plugin lifetime.
    */
    @Synchronized
    fun refresh(): Boolean {
        economy = plugin.server.servicesManager.getRegistration(Economy::class.java)?.provider
        return isEnabled
    }

    @EventHandler
    fun onServiceRegister(event: ServiceRegisterEvent) {
        if (event.provider.service == Economy::class.java) {
            refresh()
        }
    }

    @EventHandler
    fun onServiceUnregister(event: ServiceUnregisterEvent) {
        if (event.provider.service == Economy::class.java) {
            refresh()
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
