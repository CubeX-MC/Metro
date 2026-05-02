package org.cubexmc.metro.integration;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RGB color used by web map integrations.
 */
public record MapLineColor(int red, int green, int blue) {

    public static final MapLineColor WHITE = new MapLineColor(255, 255, 255);

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern BUKKIT_HEX_PATTERN = Pattern.compile(
            "(?i)\u00a7x\u00a7([0-9a-f])\u00a7([0-9a-f])\u00a7([0-9a-f])"
                    + "\u00a7([0-9a-f])\u00a7([0-9a-f])\u00a7([0-9a-f])");

    public static MapLineColor fromLineColor(String color) {
        if (color == null || color.isBlank()) {
            return WHITE;
        }

        Matcher hexMatcher = HEX_PATTERN.matcher(color);
        if (hexMatcher.find()) {
            return fromHex(hexMatcher.group(1));
        }

        Matcher bukkitHexMatcher = BUKKIT_HEX_PATTERN.matcher(color);
        if (bukkitHexMatcher.find()) {
            StringBuilder hex = new StringBuilder(6);
            for (int index = 1; index <= 6; index++) {
                hex.append(bukkitHexMatcher.group(index));
            }
            return fromHex(hex.toString());
        }

        String normalized = color.replace('\u00a7', '&').toLowerCase(Locale.ROOT);
        for (int index = normalized.length() - 2; index >= 0; index--) {
            if (normalized.charAt(index) == '&') {
                MapLineColor legacyColor = fromLegacyCode(normalized.charAt(index + 1));
                if (legacyColor != null) {
                    return legacyColor;
                }
            }
        }
        return WHITE;
    }

    public int asRgbInt() {
        return (red << 16) | (green << 8) | blue;
    }

    private static MapLineColor fromHex(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        return new MapLineColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static MapLineColor fromLegacyCode(char code) {
        return switch (code) {
            case '0' -> new MapLineColor(0, 0, 0);
            case '1' -> new MapLineColor(0, 0, 170);
            case '2' -> new MapLineColor(0, 170, 0);
            case '3' -> new MapLineColor(0, 170, 170);
            case '4' -> new MapLineColor(170, 0, 0);
            case '5' -> new MapLineColor(170, 0, 170);
            case '6' -> new MapLineColor(255, 170, 0);
            case '7' -> new MapLineColor(170, 170, 170);
            case '8' -> new MapLineColor(85, 85, 85);
            case '9' -> new MapLineColor(85, 85, 255);
            case 'a' -> new MapLineColor(85, 255, 85);
            case 'b' -> new MapLineColor(85, 255, 255);
            case 'c' -> new MapLineColor(255, 85, 85);
            case 'd' -> new MapLineColor(255, 85, 255);
            case 'e' -> new MapLineColor(255, 255, 85);
            case 'f' -> WHITE;
            default -> null;
        };
    }
}
