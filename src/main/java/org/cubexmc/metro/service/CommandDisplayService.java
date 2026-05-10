package org.cubexmc.metro.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared display helpers for command views.
 */
public class CommandDisplayService {

    public static final int DEFAULT_PAGE_SIZE = 8;

    public static final class Page<T> {
        private final List<T> items;
        private final int page;
        private final int totalPages;
        private final int totalItems;
        private final int pageSize;

        public Page(List<T> items, int page, int totalPages, int totalItems, int pageSize) {
            this.items = items;
            this.page = page;
            this.totalPages = totalPages;
            this.totalItems = totalItems;
            this.pageSize = pageSize;
        }

        public List<T> items() {
            return items;
        }

        public int page() {
            return page;
        }

        public int totalPages() {
            return totalPages;
        }

        public int totalItems() {
            return totalItems;
        }

        public int pageSize() {
            return pageSize;
        }
    }

    public static final class HelpPage {
        private final String header;
        private final List<String> lines;
        private final int page;
        private final int totalPages;

        public HelpPage(String header, List<String> lines, int page, int totalPages) {
            this.header = header;
            this.lines = lines;
            this.page = page;
            this.totalPages = totalPages;
        }

        public String header() {
            return header;
        }

        public List<String> lines() {
            return lines;
        }

        public int page() {
            return page;
        }

        public int totalPages() {
            return totalPages;
        }
    }

    public static final class HelpSection {
        private final String header;
        private final List<String> lines;

        public HelpSection(String header, List<String> lines) {
            this.header = header;
            this.lines = lines;
        }

        public String header() {
            return header;
        }

        public List<String> lines() {
            return lines;
        }
    }

    public <T> Page<T> paginate(List<T> items, Integer requestedPage) {
        return paginate(items, requestedPage, DEFAULT_PAGE_SIZE);
    }

    public <T> Page<T> paginate(List<T> items, Integer requestedPage, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than zero");
        }

        List<T> safeItems = items == null ? new ArrayList<T>() : items;
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
        List<T> pageItems = totalItems == 0 ? new ArrayList<T>() : Collections.unmodifiableList(new ArrayList<T>(safeItems.subList(start, end)));
        return new Page<>(pageItems, page, totalPages, totalItems, pageSize);
    }

    public HelpPage helpPage(Function<String, String> messageResolver,
                             String headerKey,
                             List<String> lineKeys,
                             Integer requestedPage) {
        Page<String> keyPage = paginate(lineKeys, requestedPage);
        List<String> lines = keyPage.items().stream()
                .map(messageResolver)
                .collect(Collectors.toList());
        String header = pageHeader(messageResolver.apply(headerKey), keyPage);
        return new HelpPage(header, lines, keyPage.page(), keyPage.totalPages());
    }

    public String pageHeader(String header, Page<?> page) {
        return header + " §e(" + page.page() + "/" + page.totalPages() + ")";
    }

    public HelpSection helpSection(Function<String, String> messageResolver,
                                   String headerKey,
                                   List<String> lineKeys) {
        List<String> safeKeys = (lineKeys == null ? new ArrayList<String>() : lineKeys);
        List<String> lines = safeKeys.stream()
                .map(messageResolver)
                .collect(Collectors.toList());
        return new HelpSection(messageResolver.apply(headerKey), lines);
    }
}
