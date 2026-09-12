package org.cubexmc.metro.integration

import java.math.BigDecimal
import java.util.UUID
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent
import org.cubexmc.economy.EconomyAccount
import org.cubexmc.economy.VaultEconomy
import org.cubexmc.metro.Metro

class VaultIntegration(private val plugin: Metro) : Listener {
    @Volatile
    var economy: Economy? = null
        private set

    /**
     * The shared `cubex-economy` wrapper around the same provider, used for the
     * one thing raw Vault cannot express: routing a fare to `economy.account`
     * when the line has no owner to pay.
     *
     * Rebuilt by [refresh] because it holds the resolved account, and a new
     * provider has to resolve it again.
     */
    @Volatile
    private var routed: VaultEconomy? = null

    @Volatile
    private var account: EconomyAccount = EconomyAccount.None

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
        val provider = plugin.server.servicesManager.getRegistration(Economy::class.java)?.provider
        economy = provider
        routed = provider?.let { VaultEconomy(it, plugin.log()).apply { useAccount(account) } }
        return isEnabled
    }

    /**
     * Sets where fares go when the line has no owner. Resolving a name can hit
     * the profile cache, so callers do this on enable and on reload - never per
     * fare (the resolved target lives in [VaultEconomy]).
     */
    @Synchronized
    fun useAccount(spec: EconomyAccount) {
        account = spec
        routed?.useAccount(spec)
    }

    /** Human readable description of the current fare destination, for logs. */
    fun accountDescription(): String = routed?.accountDescription() ?: account.label()

    /**
     * Withdraws the fare and routes it to `economy.account`.
     *
     * Used for lines with no owner: before this the money was simply destroyed,
     * which is what an empty `economy.account` still does - only now it is a
     * configured choice instead of the only option.
     *
     * The returned flag is the *withdrawal* result. A failed deposit into the
     * server account is not rolled back (the player already rode the train);
     * `VaultEconomy` logs it so the owner can reconcile.
     */
    fun chargeToAccount(player: Player, amount: Double): Boolean =
        routed?.charge(player, BigDecimal.valueOf(amount))?.success() ?: false

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
