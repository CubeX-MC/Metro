package org.cubexmc.metro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommandDisplayServiceTest {

    private final CommandDisplayService service = new CommandDisplayService();

    @Test
    void shouldClampRequestedPageIntoValidRange() {
        List<String> items = List.of("a", "b", "c", "d", "e");

        CommandDisplayService.Page<String> firstPage = service.paginate(items, -3, 2);
        CommandDisplayService.Page<String> lastPage = service.paginate(items, 99, 2);

        assertEquals(1, firstPage.page());
        assertEquals(List.of("a", "b"), firstPage.items());
        assertEquals(3, lastPage.page());
        assertEquals(List.of("e"), lastPage.items());
    }

    @Test
    void shouldUseOneBasedPagesAndReportTotals() {
        CommandDisplayService.Page<String> page = service.paginate(List.of("a", "b", "c", "d", "e"), 2, 2);

        assertEquals(2, page.page());
        assertEquals(3, page.totalPages());
        assertEquals(5, page.totalItems());
        assertEquals(2, page.pageSize());
        assertEquals(List.of("c", "d"), page.items());
    }

    @Test
    void shouldTreatNullAndEmptyInputsAsSingleEmptyPage() {
        CommandDisplayService.Page<String> nullPage = service.paginate(null, null, 8);
        CommandDisplayService.Page<String> emptyPage = service.paginate(List.of(), 5, 8);

        assertEquals(1, nullPage.page());
        assertEquals(1, nullPage.totalPages());
        assertEquals(List.of(), nullPage.items());
        assertEquals(1, emptyPage.page());
        assertEquals(1, emptyPage.totalPages());
        assertEquals(List.of(), emptyPage.items());
    }

    @Test
    void shouldRejectInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> service.paginate(List.of("a"), 1, 0));
    }

    @Test
    void shouldBuildLocalizedHelpPageFromKeys() {
        List<String> keys = List.of("one", "two", "three", "four", "five", "six", "seven", "eight", "nine");

        CommandDisplayService.HelpPage page = service.helpPage(key -> "msg:" + key, "header", keys, 2);

        assertEquals("msg:header §e(2/2)", page.header());
        assertEquals(2, page.page());
        assertEquals(2, page.totalPages());
        assertEquals(List.of("msg:nine"), page.lines());
    }

    @Test
    void shouldBuildNonPagedHelpSectionFromKeys() {
        CommandDisplayService.HelpSection section = service.helpSection(key -> "msg:" + key,
                "header", List.of("one", "two"));

        assertEquals("msg:header", section.header());
        assertEquals(List.of("msg:one", "msg:two"), section.lines());
    }
}
