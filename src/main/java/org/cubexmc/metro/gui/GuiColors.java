package org.cubexmc.metro.gui;

import org.bukkit.Material;

public final class GuiColors {
    private GuiColors() {
    }

    public static Material getWoolByColor(String colorCode) {
        if (colorCode == null) {
            return Material.WHITE_WOOL;
        }

        String code = colorCode.replace("&", "").toLowerCase();

        return switch (code) {
            case "0" -> Material.BLACK_WOOL;
            case "1" -> Material.BLUE_WOOL;
            case "2" -> Material.GREEN_WOOL;
            case "3" -> Material.CYAN_WOOL;
            case "4" -> Material.RED_WOOL;
            case "5" -> Material.PURPLE_WOOL;
            case "6" -> Material.ORANGE_WOOL;
            case "7" -> Material.LIGHT_GRAY_WOOL;
            case "8" -> Material.GRAY_WOOL;
            case "9" -> Material.LIGHT_BLUE_WOOL;
            case "a" -> Material.LIME_WOOL;
            case "b" -> Material.LIGHT_BLUE_WOOL;
            case "c" -> Material.RED_WOOL;
            case "d" -> Material.PINK_WOOL;
            case "e" -> Material.YELLOW_WOOL;
            case "f" -> Material.WHITE_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }
}
