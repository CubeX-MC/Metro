package org.cubexmc.metro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.cubexmc.metro.integration.VaultIntegration;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.service.TicketService.TicketChargeStatus;
import org.cubexmc.metro.service.TicketService.TicketCheckStatus;
import org.junit.jupiter.api.Test;

class TicketServiceTest {

    @Test
    void shouldAllowFreeAndEconomyDisabledRidesWithoutVault() {
        TicketService economyDisabled = new TicketService(() -> null, () -> false);
        Line paidLine = line(12.5);

        assertTrue(economyDisabled.checkCanBoard(player(), paidLine).canBoard());
        assertEquals(TicketChargeStatus.ECONOMY_DISABLED,
                economyDisabled.charge(economyDisabled.createTransaction(player(), paidLine)));

        TicketService freeRide = new TicketService(() -> null, () -> true);
        assertEquals(TicketCheckStatus.FREE, freeRide.checkCanBoard(player(), line(0.0)).getStatus());
        assertEquals(TicketChargeStatus.FREE, freeRide.charge(freeRide.createTransaction(player(), line(0.0))));
    }

    @Test
    void shouldRejectPaidRideWhenVaultUnavailableOrBalanceInsufficient() {
        TicketService withoutVault = new TicketService(() -> null, () -> true);
        assertEquals(TicketCheckStatus.VAULT_UNAVAILABLE,
                withoutVault.checkCanBoard(player(), line(5.0)).getStatus());

        VaultIntegration vault = enabledVault();
        Player player = player();
        when(vault.has(player, 5.0)).thenReturn(false);

        TicketService service = new TicketService(() -> vault, () -> true);

        assertFalse(service.checkCanBoard(player, line(5.0)).canBoard());
        assertEquals(TicketChargeStatus.INSUFFICIENT_FUNDS,
                service.charge(service.createTransaction(player, line(5.0))));
        verify(vault, never()).withdraw(player, 5.0);
    }

    @Test
    void shouldOnlyWithdrawWhenChargeIsCommitted() {
        VaultIntegration vault = enabledVault();
        Player player = player();
        UUID owner = UUID.randomUUID();
        Line line = line(7.0);
        line.setOwner(owner);

        when(vault.has(player, 7.0)).thenReturn(true);
        when(vault.withdraw(player, 7.0)).thenReturn(true);
        when(vault.deposit(owner, 7.0)).thenReturn(true);

        TicketService service = new TicketService(() -> vault, () -> true);

        assertTrue(service.checkCanBoard(player, line).canBoard());
        verify(vault, never()).withdraw(player, 7.0);

        TicketService.TicketTransaction transaction = service.createTransaction(player, line);
        assertEquals(TicketChargeStatus.CHARGED, service.charge(transaction));
        assertTrue(transaction.isCharged());
        verify(vault).withdraw(player, 7.0);
        verify(vault).deposit(owner, 7.0);
    }

    @Test
    void shouldReportTransactionFailureWhenWithdrawFails() {
        VaultIntegration vault = enabledVault();
        Player player = player();
        when(vault.has(player, 3.0)).thenReturn(true);
        when(vault.withdraw(player, 3.0)).thenReturn(false);

        TicketService service = new TicketService(() -> vault, () -> true);
        TicketService.TicketTransaction transaction = service.createTransaction(player, line(3.0));

        assertEquals(TicketChargeStatus.TRANSACTION_FAILED, service.charge(transaction));
        assertFalse(transaction.isCharged());
    }

    private VaultIntegration enabledVault() {
        VaultIntegration vault = mock(VaultIntegration.class);
        when(vault.isEnabled()).thenReturn(true);
        when(vault.format(5.0)).thenReturn("$5.00");
        when(vault.format(7.0)).thenReturn("$7.00");
        return vault;
    }

    private Player player() {
        return mock(Player.class);
    }

    private Line line(double price) {
        Line line = new Line("red", "Red");
        line.setTicketPrice(price);
        return line;
    }
}
