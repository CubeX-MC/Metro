package org.cubexmc.metro.integration;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RGB color used by web map integrations.
 */
public final class MapLineColor {

    private final int red;
    private final int green;
    private final int blue;

    public static final MapLineColor WHITE = new MapLineColor(255, 255, 255);

    public MapLineColor(int red, int green, int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public int red() { return red; }
    public int green() { return green; }
    public int blue() { return blue; }

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern BUKKIT_HEX_PATTERN = Pattern.compile(
            "(?i)\u00a7x\u00a7([0-9a-f])\u00a7([0-9a-f])\u00a7([0-9a-f])"
                    + "\u00a7([0-9a-f])\u00a7([0-9a-f])\u00a7([0-9a-f])");

    public static MapLineColor fromLineColor(String color) {
        if (color == null || color.trim().isEmpty()) {
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
        switch (code) {
            case '0':
                return new MapLineColor(0, 0, 0);
            case '1':
                return new MapLineColor(0, 0, 170);
            case '2':
                return new MapLineColor(0, 170, 0);
            case '3':
                return new MapLineColor(0, 170, 170);
            case '4':
                return new MapLineColor(170, 0, 0);
            case '5':
                return new MapLineColor(170, 0, 170);
            case '6':
                return new MapLineColor(255, 170, 0);
            case '7':
                return new MapLineColor(170, 170, 170);
            case '8':
                return new MapLineColor(85, 85, 85);
            case '9':
                return new MapLineColor(85, 85, 255);
            case 'a':
                return new MapLineColor(85, 255, 85);
            case 'b':
                return new MapLineColor(85, 255, 255);
            case 'c':
                return new MapLineColor(255, 85, 85);
            case 'd':
                return new MapLineColor(255, 85, 255);
            case 'e':
                return new MapLineColor(255, 255, 85);
            case 'f':
                return WHITE;
            default:
                return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapLineColor)) return false;
        MapLineColor that = (MapLineColor) o;
        return red == that.red && green == that.green && blue == that.blue;
    }

    @Override
    public int hashCode() {
        return (red << 16) | (green << 8) | blue;
    }

    @Override
    public String toString() {
        return "MapLineColor[red=" + red + ", green=" + green + ", blue=" + blue + "]";
    }
}
