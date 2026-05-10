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

        switch (code) {
            case "0":
                return Material.BLACK_WOOL;
            case "1":
                return Material.BLUE_WOOL;
            case "2":
                return Material.GREEN_WOOL;
            case "3":
                return Material.CYAN_WOOL;
            case "4":
                return Material.RED_WOOL;
            case "5":
                return Material.PURPLE_WOOL;
            case "6":
                return Material.ORANGE_WOOL;
            case "7":
                return Material.LIGHT_GRAY_WOOL;
            case "8":
                return Material.GRAY_WOOL;
            case "9":
                return Material.LIGHT_BLUE_WOOL;
            case "a":
                return Material.LIME_WOOL;
            case "b":
                return Material.LIGHT_BLUE_WOOL;
            case "c":
                return Material.RED_WOOL;
            case "d":
                return Material.PINK_WOOL;
            case "e":
                return Material.YELLOW_WOOL;
            case "f":
                return Material.WHITE_WOOL;
            default:
                return Material.WHITE_WOOL;
        }
    }
}
