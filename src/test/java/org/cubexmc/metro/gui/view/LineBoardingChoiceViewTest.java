package org.cubexmc.metro.gui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.service.TicketService;
import org.junit.jupiter.api.Test;

class LineBoardingChoiceViewTest {

    @Test
    void shouldBlockBoardingBeforeTicketChecksWhenPlayerCannotUseMetro() throws Exception {
        Metro plugin = mock(Metro.class);
        LanguageManager languageManager = mock(LanguageManager.class);
        TicketService ticketService = mock(TicketService.class);
        Player player = mock(Player.class);
        Line line = new Line("red", "Red");

        when(plugin.getLanguageManager()).thenReturn(languageManager);
        when(plugin.getTicketService()).thenReturn(ticketService);
        when(player.hasPermission("metro.use")).thenReturn(false);
        when(languageManager.getMessage("gui.line_boarding.no_permission")).thenReturn("No permission");

        String reason = invokeBlockReason(new LineBoardingChoiceView(plugin), player, line);

        assertEquals("No permission", reason);
        verify(ticketService, never()).checkCanBoard(player, line);
    }

    private String invokeBlockReason(LineBoardingChoiceView view, Player player, Line line) throws Exception {
        Method method = LineBoardingChoiceView.class.getDeclaredMethod("getBoardingBlockReason", Player.class, Line.class);
        method.setAccessible(true);
        return (String) method.invoke(view, player, line);
    }
}
