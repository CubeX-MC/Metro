package org.cubexmc.metro.service;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.model.Portal;

/**
 * Business operations used by portal commands.
 */
public class PortalCommandService {

    private static final int MAX_ID_LENGTH = 64;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final PortalManager portalManager;

    public PortalCommandService(PortalManager portalManager) {
        this.portalManager = portalManager;
    }

    public enum WriteStatus {
        SUCCESS,
        INVALID_ID,
        INVALID_LOCATION,
        EXISTS,
        NOT_FOUND,
        FAILED
    }

    public record PortalWriteResult(WriteStatus status, Portal portal, Location location) {
    }

    public record ReloadResult(WriteStatus status, int portalCount) {
    }

    public PortalWriteResult createPortal(String id, Location fallbackLocation, Block targetBlock) {
        if (!isValidId(id)) {
            return new PortalWriteResult(WriteStatus.INVALID_ID, null, null);
        }
        if (portalManager.getPortal(id) != null) {
            return new PortalWriteResult(WriteStatus.EXISTS, null, null);
        }
        Location entrance = resolveEntranceLocation(fallbackLocation, targetBlock);
        if (entrance == null || entrance.getWorld() == null) {
            return new PortalWriteResult(WriteStatus.INVALID_LOCATION, null, entrance);
        }
        Portal portal = portalManager.createPortal(id, entrance);
        return new PortalWriteResult(WriteStatus.SUCCESS, portal, entrance);
    }

    public PortalWriteResult setDestination(String id, Location destination) {
        if (destination == null || destination.getWorld() == null) {
            return new PortalWriteResult(WriteStatus.INVALID_LOCATION, null, destination);
        }
        if (!portalManager.setDestination(id, destination)) {
            return new PortalWriteResult(WriteStatus.NOT_FOUND, null, destination);
        }
        return new PortalWriteResult(WriteStatus.SUCCESS, portalManager.getPortal(id), destination);
    }

    public WriteStatus linkPortals(String id1, String id2) {
        if (id1 == null || id1.equals(id2)) {
            return WriteStatus.FAILED;
        }
        return portalManager.linkPortals(id1, id2) ? WriteStatus.SUCCESS : WriteStatus.NOT_FOUND;
    }

    public WriteStatus deletePortal(String id) {
        return portalManager.deletePortal(id) ? WriteStatus.SUCCESS : WriteStatus.NOT_FOUND;
    }

    public List<Portal> listPortals() {
        return portalManager.getAllPortals().stream()
                .sorted(Comparator.comparing(Portal::getId))
                .toList();
    }

    public ReloadResult reloadPortals(Runnable migration) {
        if (migration != null) {
            migration.run();
        }
        portalManager.load();
        return new ReloadResult(WriteStatus.SUCCESS, portalManager.getAllPortals().size());
    }

    public boolean isValidId(String id) {
        return id != null
                && !id.isBlank()
                && id.length() <= MAX_ID_LENGTH
                && ID_PATTERN.matcher(id).matches();
    }

    private Location resolveEntranceLocation(Location fallbackLocation, Block targetBlock) {
        if (targetBlock != null && isRail(targetBlock.getType())) {
            return targetBlock.getLocation();
        }
        return fallbackLocation;
    }

    private boolean isRail(Material material) {
        return material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }
}
