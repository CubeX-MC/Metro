package org.cubexmc.metro.train;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Minecart;
import org.cubexmc.metro.Metro;

/**
 * Tracks active train movement tasks by minecart UUID.
 */
final class TrainTaskRegistry {

    private static final Map<UUID, TrainMovementTask> ACTIVE_TASKS = new ConcurrentHashMap<>();

    private TrainTaskRegistry() {
    }

    static TrainMovementTask get(Minecart cart) {
        return cart == null ? null : ACTIVE_TASKS.get(cart.getUniqueId());
    }

    static void register(Minecart cart, TrainMovementTask task) {
        if (cart != null && task != null) {
            ACTIVE_TASKS.put(cart.getUniqueId(), task);
        }
    }

    static void unregister(Minecart cart) {
        if (cart != null) {
            ACTIVE_TASKS.remove(cart.getUniqueId());
        }
    }

    static void transfer(Minecart previousCart, Minecart newCart, TrainMovementTask task) {
        unregister(previousCart);
        register(newCart, task);
    }

    static int shutdownActiveTasks() {
        return shutdownActiveTasks(null, false);
    }

    static int shutdownActiveTasks(Metro plugin, boolean folia) {
        List<TrainMovementTask> tasks = new ArrayList<>(new LinkedHashSet<>(ACTIVE_TASKS.values()));
        for (TrainMovementTask task : tasks) {
            if (folia && plugin != null) {
                task.removeMinecartAndCancelOnEntityScheduler();
            } else {
                task.removeMinecartAndCancel();
            }
        }
        ACTIVE_TASKS.clear();
        return tasks.size();
    }
}
