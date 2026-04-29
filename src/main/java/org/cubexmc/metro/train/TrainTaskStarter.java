package org.cubexmc.metro.train;

import org.bukkit.Bukkit;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;

/**
 * Creates and registers train movement tasks after a player boards.
 */
final class TrainTaskStarter {

    private TrainTaskStarter() {
    }

    static void start(Metro plugin, Minecart minecart, Player passenger, String lineId, String currentStopId) {
        LineManager lineManager = plugin.getLineManager();
        Line line = lineManager.getLine(lineId);
        if (line == null) {
            return;
        }

        if (passenger == null || !passenger.isOnline() || passenger.getVehicle() != minecart) {
            if (minecart.isValid()) {
                minecart.remove();
            }
            return;
        }

        TrainMovementTask trainTask = new TrainMovementTask(plugin, minecart, passenger, lineId, currentStopId);
        Bukkit.getPluginManager().registerEvents(trainTask, plugin);
        TrainTaskRegistry.register(minecart, trainTask);

        minecart.setMaxSpeed(0);
        minecart.setVelocity(new Vector(0, 0, 0));
        trainTask.startAtStation();
    }
}
