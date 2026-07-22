package org.cubexmc.metro.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.ServicePriority;
import org.cubexmc.metro.Metro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaultIntegrationTest {

    private Metro plugin;
    private Server server;
    private PluginManager pluginManager;
    private ServicesManager servicesManager;
    private Economy economy;
    private EconomyResponse successResponse;

    @BeforeEach
    void setUp() {
        plugin = mock(Metro.class);
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        servicesManager = mock(ServicesManager.class);
        economy = mock(Economy.class);
        successResponse = mock(EconomyResponse.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(successResponse.transactionSuccess()).thenReturn(true);
    }

    @Test
    void shouldBeDisabledWhenNoEconomyProvider() {
        when(servicesManager.getRegistration(eq(Economy.class))).thenReturn(null);

        VaultIntegration vault = new VaultIntegration(plugin);

        assertFalse(vault.isEnabled());
        assertNull(vault.economy);
    }

    @Test
    void shouldResolveProviderAtConstruction() {
        var registration = mock(net.milkbowl.vault.economy.Economy.class);
        when(servicesManager.getRegistration(eq(Economy.class)))
                .thenReturn(new org.bukkit.plugin.RegisteredService<>(
                        Economy.class, economy, plugin, ServicePriority.Normal));

        VaultIntegration vault = new VaultIntegration(plugin);

        assertTrue(vault.isEnabled());
        assertNotNull(vault.economy);
    }

    @Test
    void shouldRefreshProviderOnDemand() {
        when(servicesManager.getRegistration(eq(Economy.class))).thenReturn(null);
        VaultIntegration vault = new VaultIntegration(plugin);

        assertFalse(vault.isEnabled());

        // Economy provider appears later
        when(servicesManager.getRegistration(eq(Economy.class)))
                .thenReturn(new org.bukkit.plugin.RegisteredService<>(
                        Economy.class, economy, plugin, ServicePriority.Normal));
        vault.refreshProvider();

        assertTrue(vault.isEnabled());
    }

    @Test
    void shouldClearProviderOnRefreshWhenUnregistered() {
        when(servicesManager.getRegistration(eq(Economy.class)))
                .thenReturn(new org.bukkit.plugin.RegisteredService<>(
                        Economy.class, economy, plugin, ServicePriority.Normal));
        VaultIntegration vault = new VaultIntegration(plugin);

        assertTrue(vault.isEnabled());

        // Economy provider disappears
        when(servicesManager.getRegistration(eq(Economy.class))).thenReturn(null);
        vault.refreshProvider();

        assertFalse(vault.isEnabled());
        assertNull(vault.economy);
    }

    @Test
    void shouldDelegateHasCheckToProvider() {
        when(servicesManager.getRegistration(eq(Economy.class)))
                .thenReturn(new org.bukkit.plugin.RegisteredService<>(
                        Economy.class, economy, plugin, ServicePriority.Normal));
        VaultIntegration vault = new VaultIntegration(plugin);
        Player player = mock(Player.class);

        when(economy.has(player, 10.0)).thenReturn(true);
        assertTrue(vault.has(player, 10.0));

        when(economy.has(player, 99.0)).thenReturn(false);
        assertFalse(vault.has(player, 99.0));
    }

    @Test
    void shouldReturnFalseForAllOperationsWhenDisabled() {
        when(servicesManager.getRegistration(eq(Economy.class))).thenReturn(null);
        VaultIntegration vault = new VaultIntegration(plugin);
        Player player = mock(Player.class);

        assertFalse(vault.has(player, 5.0));
        assertFalse(vault.withdraw(player, 5.0));
        assertFalse(vault.deposit(null, 5.0));
    }

    @Test
    void shouldWithdrawFromPlayer() {
        when(servicesManager.getRegistration(eq(Economy.class)))
                .thenReturn(new org.bukkit.plugin.RegisteredService<>(
                        Economy.class, economy, plugin, ServicePriority.Normal));
        VaultIntegration vault = new VaultIntegration(plugin);
        Player player = mock(Player.class);

        when(economy.withdrawPlayer(player, 10.0)).thenReturn(successResponse);
        assertTrue(vault.withdraw(player, 10.0));
        verify(economy).withdrawPlayer(player, 10.0);
    }
}
