package org.cubexmc.metro.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private final Map<UUID, Location> corner1Selections = new HashMap<>();
    private final Map<UUID, Location> corner2Selections = new HashMap<>();

    public void setCorner1(Player player, Location location) {
        corner1Selections.put(player.getUniqueId(), location);
    }

    public void setCorner2(Player player, Location location) {
        corner2Selections.put(player.getUniqueId(), location);
    }

    public Location getCorner1(Player player) {
        return corner1Selections.get(player.getUniqueId());
    }

    public Location getCorner2(Player player) {
        return corner2Selections.get(player.getUniqueId());
    }

    public boolean isSelectionComplete(Player player) {
        return corner1Selections.containsKey(player.getUniqueId()) && corner2Selections.containsKey(player.getUniqueId());
    }
}
