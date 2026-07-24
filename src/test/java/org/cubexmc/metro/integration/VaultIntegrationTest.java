package org.cubexmc.metro.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.cubexmc.metro.Metro;
import org.junit.jupiter.api.Test;

class VaultIntegrationTest {

    @Test
    void shouldRefreshProviderWhenEconomyServiceRegistersAndUnregisters() {
        Metro plugin = mock(Metro.class);
        Server server = mock(Server.class);
        ServicesManager services = mock(ServicesManager.class);
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        Economy economy = mock(Economy.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(services);
        when(registration.getService()).thenReturn(Economy.class);
        when(registration.getProvider()).thenReturn(economy);
        when(services.getRegistration(Economy.class)).thenReturn(null);

        VaultIntegration integration = new VaultIntegration(plugin);
        assertFalse(integration.isEnabled());

        when(services.getRegistration(Economy.class)).thenReturn(registration);
        ServiceRegisterEvent registerEvent = mock(ServiceRegisterEvent.class);
        doReturn(registration).when(registerEvent).getProvider();
        integration.onServiceRegister(registerEvent);

        assertTrue(integration.isEnabled());
        assertSame(economy, integration.getEconomy());

        when(services.getRegistration(Economy.class)).thenReturn(null);
        ServiceUnregisterEvent unregisterEvent = mock(ServiceUnregisterEvent.class);
        doReturn(registration).when(unregisterEvent).getProvider();
        integration.onServiceUnregister(unregisterEvent);

        assertFalse(integration.isEnabled());
    }
}
