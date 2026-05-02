package org.cubexmc.metro.lifecycle;

import org.bukkit.Bukkit;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.gui.GuiListener;
import org.cubexmc.metro.listener.PlayerInteractListener;
import org.cubexmc.metro.listener.PlayerMoveListener;
import org.cubexmc.metro.listener.VehicleListener;
import org.cubexmc.metro.manager.RailProtectionManager;
import org.cubexmc.metro.train.TrainDisplayController;

/**
 * Creates and registers Bukkit event listeners.
 */
public class ListenerRegistration {

    private final Metro plugin;
    private final RailProtectionManager railProtectionManager;

    public ListenerRegistration(Metro plugin, RailProtectionManager railProtectionManager) {
        this.plugin = plugin;
        this.railProtectionManager = railProtectionManager;
    }

    public Result register() {
        PlayerInteractListener playerInteractListener = new PlayerInteractListener(plugin);
        VehicleListener vehicleListener = new VehicleListener(plugin);
        PlayerMoveListener playerMoveListener = new PlayerMoveListener(plugin);
        GuiListener guiListener = new GuiListener(plugin);
        TrainDisplayController trainDisplayController = new TrainDisplayController(plugin);

        Bukkit.getPluginManager().registerEvents(playerInteractListener, plugin);
        Bukkit.getPluginManager().registerEvents(vehicleListener, plugin);
        Bukkit.getPluginManager().registerEvents(playerMoveListener, plugin);
        Bukkit.getPluginManager().registerEvents(guiListener, plugin);
        Bukkit.getPluginManager().registerEvents(trainDisplayController, plugin);
        Bukkit.getPluginManager().registerEvents(railProtectionManager, plugin);

        return new Result(playerInteractListener, vehicleListener, playerMoveListener,
                guiListener, trainDisplayController);
    }

    public record Result(PlayerInteractListener playerInteractListener,
                         VehicleListener vehicleListener,
                         PlayerMoveListener playerMoveListener,
                         GuiListener guiListener,
                         TrainDisplayController trainDisplayController) {
    }
}
