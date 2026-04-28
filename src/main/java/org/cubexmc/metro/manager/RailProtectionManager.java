package org.cubexmc.metro.manager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.util.MetroConstants;
import org.cubexmc.metro.util.OwnershipUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds an in-memory rail protection index from recorded route points.
 */
public class RailProtectionManager implements Listener {

    private static final double INTERPOLATION_STEP = 0.5;
    private static final Set<Material> RAIL_MATERIALS = EnumSet.of(
            Material.RAIL,
            Material.POWERED_RAIL,
            Material.DETECTOR_RAIL,
            Material.ACTIVATOR_RAIL
    );

    private final Metro plugin;
    private final Map<String, Set<BlockKey>> lineToBlocks = new HashMap<>();
    private final Map<BlockKey, Set<String>> blockToLines = new HashMap<>();

    public RailProtectionManager(Metro plugin) {
        this.plugin = plugin;
    }

    public synchronized void rebuildAll() {
        lineToBlocks.clear();
        blockToLines.clear();
        for (Line line : plugin.getLineManager().getAllLines()) {
            rebuildLineLocked(line);
        }
    }

    public synchronized void rebuildLine(String lineId) {
        removeLineLocked(lineId);
        Line line = plugin.getLineManager().getLine(lineId);
        rebuildLineLocked(line);
    }

    public synchronized int getProtectedBlockCount(String lineId) {
        Set<BlockKey> blocks = lineToBlocks.get(lineId);
        return blocks == null ? 0 : blocks.size();
    }

    private void rebuildLineLocked(Line line) {
        if (line == null || !line.isRailProtected()) {
            return;
        }
        Set<BlockKey> blocks = collectRailBlocks(line.getRoutePoints());
        if (blocks.isEmpty()) {
            return;
        }
        lineToBlocks.put(line.getId(), blocks);
        for (BlockKey block : blocks) {
            blockToLines.computeIfAbsent(block, key -> new HashSet<>()).add(line.getId());
        }
    }

    private void removeLineLocked(String lineId) {
        Set<BlockKey> oldBlocks = lineToBlocks.remove(lineId);
        if (oldBlocks == null) {
            return;
        }
        for (BlockKey block : oldBlocks) {
            Set<String> lineIds = blockToLines.get(block);
            if (lineIds == null) {
                continue;
            }
            lineIds.remove(lineId);
            if (lineIds.isEmpty()) {
                blockToLines.remove(block);
            }
        }
    }

    private Set<BlockKey> collectRailBlocks(List<RoutePoint> routePoints) {
        if (routePoints == null || routePoints.isEmpty()) {
            return Collections.emptySet();
        }
        Set<BlockKey> blocks = new HashSet<>();
        List<RoutePoint> points = new ArrayList<>(routePoints);
        for (int i = 0; i < points.size(); i++) {
            addNearestRail(blocks, points.get(i));
            if (i + 1 < points.size()) {
                interpolateSegment(blocks, points.get(i), points.get(i + 1));
            }
        }
        return blocks;
    }

    private void interpolateSegment(Set<BlockKey> blocks, RoutePoint from, RoutePoint to) {
        if (from == null || to == null || !from.worldName().equals(to.worldName())) {
            return;
        }
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int samples = Math.max(1, (int) Math.ceil(distance / INTERPOLATION_STEP));
        for (int i = 1; i < samples; i++) {
            double ratio = (double) i / samples;
            addNearestRail(blocks, new RoutePoint(
                    from.worldName(),
                    from.x() + dx * ratio,
                    from.y() + dy * ratio,
                    from.z() + dz * ratio
            ));
        }
    }

    private void addNearestRail(Set<BlockKey> blocks, RoutePoint point) {
        BlockKey nearestRail = findNearestRail(point);
        if (nearestRail != null) {
            blocks.add(nearestRail);
        }
    }

    private BlockKey findNearestRail(RoutePoint point) {
        World world = Bukkit.getWorld(point.worldName());
        if (world == null) {
            return null;
        }

        int baseX = (int) Math.floor(point.x());
        int baseY = (int) Math.floor(point.y());
        int baseZ = (int) Math.floor(point.z());
        BlockKey best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dy : new int[] {0, -1}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = baseX + dx;
                    int y = baseY + dy;
                    int z = baseZ + dz;
                    Block block = world.getBlockAt(x, y, z);
                    if (!isRail(block)) {
                        continue;
                    }
                    double distance = distanceSquaredToBlockCenter(point, x, y, z);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new BlockKey(world.getName(), x, y, z);
                    }
                }
            }
        }
        return best;
    }

    private double distanceSquaredToBlockCenter(RoutePoint point, int x, int y, int z) {
        double dx = point.x() - (x + 0.5);
        double dy = point.y() - (y + 0.5);
        double dz = point.z() - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (isMetroPassengerBreakingRail(player, block)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getMessage("protection.passenger_break_denied"));
            return;
        }

        Set<String> protectedLineIds = getProtectedLines(block);
        if (protectedLineIds.isEmpty()) {
            return;
        }
        if (canBreakProtectedRail(player, protectedLineIds)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getLanguageManager().getMessage("protection.rail_break_denied"));
    }

    private boolean isMetroPassengerBreakingRail(Player player, Block block) {
        if (!plugin.getConfigFacade().isSafeModePassengerRailBreakProtection() || !isRail(block)) {
            return false;
        }
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Minecart minecart)) {
            return false;
        }
        return minecart.getPersistentDataContainer().has(MetroConstants.getMinecartKey(), PersistentDataType.BYTE);
    }

    private synchronized Set<String> getProtectedLines(Block block) {
        if (!isRail(block)) {
            return Collections.emptySet();
        }
        Set<String> lineIds = blockToLines.get(BlockKey.fromBlock(block));
        return lineIds == null ? Collections.emptySet() : new HashSet<>(lineIds);
    }

    private boolean canBreakProtectedRail(Player player, Set<String> protectedLineIds) {
        if (OwnershipUtil.hasAdminBypass(player)) {
            return true;
        }
        for (String lineId : protectedLineIds) {
            Line line = plugin.getLineManager().getLine(lineId);
            if (line != null && !OwnershipUtil.canManageLine(player, line)) {
                return false;
            }
        }
        return true;
    }

    private boolean isRail(Block block) {
        return block != null && RAIL_MATERIALS.contains(block.getType());
    }

    private record BlockKey(String worldName, int x, int y, int z) {
        private static BlockKey fromBlock(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }
}
