package org.cubexmc.metro.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.cubexmc.metro.Metro;

import java.util.UUID;

public class VaultIntegration {

    private final Metro plugin;
    private Economy economy;
    private boolean enabled;

    public VaultIntegration(Metro plugin) {
        this.plugin = plugin;
        this.enabled = setupEconomy();
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean has(Player player, double amount) {
        if (!enabled) return false;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!enabled) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(UUID uuid, double amount) {
        if (!enabled || uuid == null) return false;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return economy.depositPlayer(offlinePlayer, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (!enabled) return String.valueOf(amount);
        return economy.format(amount);
    }
}
