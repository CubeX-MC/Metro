package org.cubexmc.metro.service;

import java.util.List;
import java.util.function.Function;

/**
 * Shared display helpers for command views.
 */
public class CommandDisplayService {

    public static final int DEFAULT_PAGE_SIZE = 8;

    public record Page<T>(List<T> items, int page, int totalPages, int totalItems, int pageSize) {
    }

    public record HelpPage(String header, List<String> lines, int page, int totalPages) {
    }

    public <T> Page<T> paginate(List<T> items, Integer requestedPage) {
        return paginate(items, requestedPage, DEFAULT_PAGE_SIZE);
    }

    public <T> Page<T> paginate(List<T> items, Integer requestedPage, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than zero");
        }

        List<T> safeItems = items == null ? List.of() : items;
        int totalItems = safeItems.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
        int page = requestedPage == null ? 1 : requestedPage;
        if (page < 1) {
            page = 1;
        } else if (page > totalPages) {
            page = totalPages;
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, totalItems);
        List<T> pageItems = totalItems == 0 ? List.of() : List.copyOf(safeItems.subList(start, end));
        return new Page<>(pageItems, page, totalPages, totalItems, pageSize);
    }

    public HelpPage helpPage(Function<String, String> messageResolver,
                             String headerKey,
                             List<String> lineKeys,
                             Integer requestedPage) {
        Page<String> keyPage = paginate(lineKeys, requestedPage);
        List<String> lines = keyPage.items().stream()
                .map(messageResolver)
                .toList();
        String header = messageResolver.apply(headerKey) + " §e(" + keyPage.page() + "/" + keyPage.totalPages() + ")";
        return new HelpPage(header, lines, keyPage.page(), keyPage.totalPages());
    }
}
