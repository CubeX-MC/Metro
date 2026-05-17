package org.cubexmc.metro.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.config.ConfigFacade;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.LineStatus;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.model.PriceRule;
import org.cubexmc.metro.model.RoutePoint;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.LineStatusService;
import org.cubexmc.metro.service.PortalCommandService;
import org.cubexmc.metro.service.PriceService;
import org.cubexmc.metro.service.TicketService;
import org.cubexmc.metro.util.OwnershipUtil;
import org.cubexmc.metro.util.VersionUtil;

/**
 * Public API for other plugins to integrate with Metro.
 * Access via {@code MetroAPI.getInstance()}.
 * <p>
 * Read queries return live model objects for convenience; snapshot queries
 * return immutable API records. Mutations route through managers/services to
 * preserve save, refresh, event, and permission consistency.
 * <p>
 * The raw managers ({@link #getLineManager()}, {@link #getStopManager()},
 * {@link #getPortalManager()}, {@link #getPlugin()}) are exposed for advanced
 * use but are not the recommended integration surface.
 *
 * @since 1.1.6
 */
public final class MetroAPI {

    private static MetroAPI instance;
    private final Metro plugin;

    private MetroAPI(Metro plugin) {
        this.plugin = plugin;
    }

    // =============================================================
    // Result/status records
    // =============================================================

    public enum PortalWriteStatus {
        SUCCESS,
        NOT_FOUND,
        EXISTS,
        INVALID_ID,
        INVALID_LOCATION,
        SAME_PORTAL,
        FAILED
    }

    public enum OwnershipWriteStatus {
        SUCCESS,
        NOT_FOUND,
        EXISTS,
        NOT_ADMIN,
        INVALID_PLAYER,
        OWNER_PROTECTED,
        FAILED
    }

    public record PortalWriteResult(PortalWriteStatus status, Portal portal, Location location) {
    }

    public record LocationSnapshot(String worldName, double x, double y, double z, float yaw) {
        static LocationSnapshot from(Location location) {
            if (location == null || location.getWorld() == null) {
                return null;
            }
            return new LocationSnapshot(
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw());
        }
    }

    public record TimeDiscountSnapshot(int startTick, int endTick, double discountMultiplier) {
        static TimeDiscountSnapshot from(PriceRule.TimeDiscount discount) {
            return new TimeDiscountSnapshot(
                    discount.getStartTick(),
                    discount.getEndTick(),
                    discount.getDiscountMultiplier());
        }
    }

    public record PriceRuleSnapshot(
            PriceRule.PricingMode mode,
            double basePrice,
            double perBlockRate,
            double perIntervalRate,
            double maxPrice,
            List<TimeDiscountSnapshot> timeDiscounts) {
        static PriceRuleSnapshot from(PriceRule rule) {
            if (rule == null) {
                return null;
            }
            return new PriceRuleSnapshot(
                    rule.getMode(),
                    rule.getBasePrice(),
                    rule.getPerBlockRate(),
                    rule.getPerIntervalRate(),
                    rule.getMaxPrice(),
                    rule.getTimeDiscounts().stream()
                            .map(TimeDiscountSnapshot::from)
                            .toList());
        }
    }

    public record LineSnapshot(
            String id,
            String name,
            List<String> orderedStopIds,
            List<String> portalIds,
            List<RoutePoint> routePoints,
            String color,
            String terminusName,
            double maxSpeed,
            double ticketPrice,
            boolean railProtected,
            Long routeRecordedAtEpochMillis,
            UUID routeRecordedBy,
            UUID routeRecordedCartId,
            UUID owner,
            Set<UUID> admins,
            String worldName,
            PriceRuleSnapshot priceRule,
            LineStatus lineStatus,
            List<String> alternativeRouteIds,
            String suspensionMessage) {
        static LineSnapshot from(Line line) {
            if (line == null) {
                return null;
            }
            return new LineSnapshot(
                    line.getId(),
                    line.getName(),
                    List.copyOf(line.getOrderedStopIds()),
                    List.copyOf(line.getPortalIds()),
                    List.copyOf(line.getRoutePoints()),
                    line.getColor(),
                    line.getTerminusName(),
                    line.getMaxSpeed(),
                    line.getTicketPrice(),
                    line.isRailProtected(),
                    line.getRouteRecordedAtEpochMillis(),
                    line.getRouteRecordedBy(),
                    line.getRouteRecordedCartId(),
                    line.getOwner(),
                    Set.copyOf(line.getAdmins()),
                    line.getWorldName(),
                    PriceRuleSnapshot.from(line.getPriceRule()),
                    line.getLineStatus(),
                    List.copyOf(line.getAlternativeRouteIds()),
                    line.getSuspensionMessage());
        }
    }

    public record StopSnapshot(
            String id,
            String name,
            LocationSnapshot corner1,
            LocationSnapshot corner2,
            LocationSnapshot stopPoint,
            float launchYaw,
            List<String> transferableLines,
            UUID owner,
            Set<UUID> admins,
            Set<String> linkedLineIds,
            String worldName) {
        static StopSnapshot from(Stop stop) {
            if (stop == null) {
                return null;
            }
            return new StopSnapshot(
                    stop.getId(),
                    stop.getName(),
                    LocationSnapshot.from(stop.getCorner1()),
                    LocationSnapshot.from(stop.getCorner2()),
                    LocationSnapshot.from(stop.getStopPointLocation()),
                    stop.getLaunchYaw(),
                    List.copyOf(stop.getTransferableLines()),
                    stop.getOwner(),
                    Set.copyOf(stop.getAdmins()),
                    Set.copyOf(stop.getLinkedLineIds()),
                    stop.getWorldName());
        }
    }

    public record PortalSnapshot(
            String id,
            String worldName,
            int x,
            int y,
            int z,
            String destinationWorldName,
            double destinationX,
            double destinationY,
            double destinationZ,
            float destinationYaw,
            String linkedPortalId,
            UUID owner,
            Set<UUID> admins) {
        static PortalSnapshot from(Portal portal) {
            if (portal == null) {
                return null;
            }
            return new PortalSnapshot(
                    portal.getId(),
                    portal.getWorldName(),
                    portal.getX(),
                    portal.getY(),
                    portal.getZ(),
                    portal.getDestWorldName(),
                    portal.getDestX(),
                    portal.getDestY(),
                    portal.getDestZ(),
                    portal.getDestYaw(),
                    portal.getLinkedPortalId(),
                    portal.getOwner(),
                    Set.copyOf(portal.getAdmins()));
        }
    }

    // =============================================================
    // Lifecycle
    // =============================================================

    /**
     * @param plugin the Metro plugin instance
     * @since 1.1.6
     */
    public static void initialize(Metro plugin) {
        if (instance == null) {
            instance = new MetroAPI(plugin);
        }
    }

    static void resetForTests() {
        instance = null;
    }

    public static MetroAPI getInstance() {
        return instance;
    }

    // =============================================================
    // Read-only settings
    // =============================================================

    public boolean isEconomyEnabled() {
        return config().isEconomyEnabled();
    }

    public boolean isVaultEconomyAvailable() {
        return plugin.getVaultIntegration() != null && plugin.getVaultIntegration().isEnabled();
    }

    public boolean isPortalsEnabled() {
        return config().isPortalsEnabled();
    }

    public String getPortalTriggerBlock() {
        return config().getPortalTriggerBlock();
    }

    public double getDefaultCartSpeed() {
        return config().getCartSpeed();
    }

    public long getCartDepartureDelay() {
        return config().getCartDepartureDelay();
    }

    public int getPortalTeleportDelay() {
        return config().getPortalTeleportDelay();
    }

    public boolean isPassengerRailBreakProtectionEnabled() {
        return config().isSafeModePassengerRailBreakProtection();
    }

    public boolean isFoliaRuntime() {
        return VersionUtil.isFolia();
    }

    // =============================================================
    // Line queries
    // =============================================================

    public Line getLine(String lineId) {
        return plugin.getLineManager().getLine(lineId);
    }

    public List<Line> getAllLines() {
        return plugin.getLineManager().getAllLines();
    }

    public List<Line> getLinesForStop(String stopId) {
        return plugin.getLineManager().getLinesForStop(stopId);
    }

    public LineSnapshot getLineSnapshot(String lineId) {
        return LineSnapshot.from(getLine(lineId));
    }

    public List<LineSnapshot> getLineSnapshots() {
        return getAllLines().stream()
                .map(LineSnapshot::from)
                .toList();
    }

    // =============================================================
    // Stop queries
    // =============================================================

    public Stop getStop(String stopId) {
        return plugin.getStopManager().getStop(stopId);
    }

    public List<Stop> getAllStops() {
        return plugin.getStopManager().getAllStops();
    }

    public StopSnapshot getStopSnapshot(String stopId) {
        return StopSnapshot.from(getStop(stopId));
    }

    public List<StopSnapshot> getStopSnapshots() {
        return getAllStops().stream()
                .map(StopSnapshot::from)
                .toList();
    }

    // =============================================================
    // Portal queries and mutations
    // =============================================================

    public Portal getPortal(String portalId) {
        PortalManager portalManager = plugin.getPortalManager();
        return portalManager != null ? portalManager.getPortal(portalId) : null;
    }

    public List<Portal> getAllPortals() {
        PortalManager portalManager = plugin.getPortalManager();
        return portalManager != null ? portalManager.getAllPortals() : List.of();
    }

    public Portal getPortalAt(Location location) {
        PortalManager portalManager = plugin.getPortalManager();
        return portalManager != null ? portalManager.getPortalAt(location) : null;
    }

    public PortalSnapshot getPortalSnapshot(String portalId) {
        return PortalSnapshot.from(getPortal(portalId));
    }

    public List<PortalSnapshot> getPortalSnapshots() {
        return getAllPortals().stream()
                .map(PortalSnapshot::from)
                .toList();
    }

    public PortalWriteResult createPortal(String portalId, Location entrance, UUID ownerId) {
        PortalCommandService service = portalService();
        if (service == null) {
            return new PortalWriteResult(PortalWriteStatus.FAILED, null, entrance);
        }
        PortalCommandService.PortalWriteResult result = service.createPortal(portalId, entrance, null, ownerId);
        return new PortalWriteResult(toPortalWriteStatus(result.status()), result.portal(), result.location());
    }

    public PortalWriteStatus setPortalDestination(String portalId, Location destination) {
        PortalCommandService service = portalService();
        if (service == null) {
            return PortalWriteStatus.FAILED;
        }
        return toPortalWriteStatus(service.setDestination(portalId, destination).status());
    }

    public PortalWriteStatus linkPortals(String firstPortalId, String secondPortalId) {
        if (firstPortalId != null && firstPortalId.equals(secondPortalId)) {
            return PortalWriteStatus.SAME_PORTAL;
        }
        PortalCommandService service = portalService();
        if (service == null) {
            return PortalWriteStatus.FAILED;
        }
        return toPortalWriteStatus(service.linkPortals(firstPortalId, secondPortalId));
    }

    public PortalWriteStatus deletePortal(String portalId) {
        PortalCommandService service = portalService();
        if (service == null) {
            return PortalWriteStatus.FAILED;
        }
        return toPortalWriteStatus(service.deletePortal(portalId));
    }

    // =============================================================
    // Line status
    // =============================================================

    public LineStatus getLineStatus(String lineId) {
        Line line = getLine(lineId);
        return line != null ? line.getLineStatus() : LineStatus.NORMAL;
    }

    public boolean setLineStatus(String lineId, LineStatus status) {
        Line line = getLine(lineId);
        if (line == null) return false;
        LineStatusService statusService = plugin.getLineStatusService();
        if (statusService == null) return false;
        return statusService.setStatus(line, status);
    }

    public boolean isLineSuspended(String lineId) {
        return getLineStatus(lineId) == LineStatus.SUSPENDED;
    }

    public boolean isLineMaintenance(String lineId) {
        return getLineStatus(lineId) == LineStatus.MAINTENANCE;
    }

    public void setSuspensionMessage(String lineId, String message) {
        Line line = getLine(lineId);
        if (line != null) {
            line.setSuspensionMessage(message);
            plugin.getLineManager().saveConfig();
        }
    }

    // =============================================================
    // Pricing
    // =============================================================

    public PriceRule getPriceRule(String lineId) {
        Line line = getLine(lineId);
        return line != null ? line.getPriceRule() : null;
    }

    public void setPriceRule(String lineId, PriceRule rule) {
        Line line = getLine(lineId);
        if (line != null) {
            line.setPriceRule(rule);
            plugin.getLineManager().saveConfig();
        }
    }

    public double calculatePrice(String lineId, String entryStopId, String exitStopId,
                                  double distanceBlocks, int intervals) {
        Line line = getLine(lineId);
        Stop entryStop = getStop(entryStopId);
        Stop exitStop = getStop(exitStopId);
        if (line == null || entryStop == null || exitStop == null) return 0.0;

        PriceService priceService = plugin.getPriceService();
        if (priceService == null) {
            return Math.max(0.0, line.getTicketPrice());
        }

        return priceService.calculatePrice(line, entryStop, exitStop, distanceBlocks, intervals,
                entryStop.getStopPointLocation() != null ? entryStop.getStopPointLocation().getWorld() : null);
    }

    public double getEstimatedPrice(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return 0.0;
        PriceService priceService = plugin.getPriceService();
        if (priceService == null) {
            return Math.max(0.0, line.getTicketPrice());
        }
        return priceService.getEstimatedPrice(line);
    }

    public String getPriceDescription(String lineId) {
        Line line = getLine(lineId);
        if (line == null) return "Free";
        PriceService priceService = plugin.getPriceService();
        if (priceService == null) return String.valueOf(Math.max(0.0, line.getTicketPrice()));
        return priceService.getPriceDescription(line);
    }

    // =============================================================
    // Ticketing
    // =============================================================

    public TicketService.TicketCheck checkCanBoard(Player player, String lineId) {
        Line line = getLine(lineId);
        if (line == null) {
            return new TicketService.TicketCheck(
                    TicketService.TicketCheckStatus.INSUFFICIENT_FUNDS, 0, "0");
        }
        return plugin.getTicketService().checkCanBoard(player, line);
    }

    // =============================================================
    // Ownership queries
    // =============================================================

    public UUID getLineOwner(String lineId) {
        Line line = getLine(lineId);
        return line != null ? line.getOwner() : null;
    }

    public UUID getStopOwner(String stopId) {
        Stop stop = getStop(stopId);
        return stop != null ? stop.getOwner() : null;
    }

    public UUID getPortalOwner(String portalId) {
        Portal portal = getPortal(portalId);
        return portal != null ? portal.getOwner() : null;
    }

    public Set<UUID> getLineAdmins(String lineId) {
        Line line = getLine(lineId);
        return line != null ? Set.copyOf(line.getAdmins()) : Set.of();
    }

    public Set<UUID> getStopAdmins(String stopId) {
        Stop stop = getStop(stopId);
        return stop != null ? Set.copyOf(stop.getAdmins()) : Set.of();
    }

    public Set<UUID> getPortalAdmins(String portalId) {
        Portal portal = getPortal(portalId);
        return portal != null ? Set.copyOf(portal.getAdmins()) : Set.of();
    }

    public boolean isLineServerOwned(String lineId) {
        return OwnershipUtil.isServerOwned(getLine(lineId));
    }

    public boolean isStopServerOwned(String stopId) {
        return OwnershipUtil.isServerOwned(getStop(stopId));
    }

    public boolean isPortalServerOwned(String portalId) {
        return OwnershipUtil.isServerOwned(getPortal(portalId));
    }

    public boolean isLineAdmin(String lineId, UUID playerId) {
        return OwnershipUtil.isLineAdmin(playerId, getLine(lineId));
    }

    public boolean isStopAdmin(String stopId, UUID playerId) {
        return OwnershipUtil.isStopAdmin(playerId, getStop(stopId));
    }

    public boolean isPortalAdmin(String portalId, UUID playerId) {
        return OwnershipUtil.isPortalAdmin(playerId, getPortal(portalId));
    }

    // =============================================================
    // Permission checks
    // =============================================================

    public boolean canManageLine(CommandSender sender, String lineId) {
        return OwnershipUtil.canManageLine(sender, getLine(lineId));
    }

    public boolean canManageStop(CommandSender sender, String stopId) {
        return OwnershipUtil.canManageStop(sender, getStop(stopId));
    }

    public boolean canManagePortal(CommandSender sender, String portalId) {
        return OwnershipUtil.canManagePortal(sender, getPortal(portalId));
    }

    public boolean canModifyLineStops(CommandSender sender, String lineId, String stopId) {
        return OwnershipUtil.canModifyLineStops(sender, getLine(lineId), getStop(stopId));
    }

    public boolean canLinkStopToLine(CommandSender sender, String lineId, String stopId) {
        return OwnershipUtil.canLinkStopToLine(sender, getLine(lineId), getStop(stopId));
    }

    // =============================================================
    // Ownership mutations
    // =============================================================

    public OwnershipWriteStatus setLineOwner(String lineId, UUID ownerId) {
        Line line = getLine(lineId);
        if (line == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (ownerId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        return plugin.getLineManager().setLineOwner(lineId, ownerId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    public OwnershipWriteStatus addLineAdmin(String lineId, UUID adminId) {
        Line line = getLine(lineId);
        if (line == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        if (line.getAdmins().contains(adminId)) {
            return OwnershipWriteStatus.EXISTS;
        }
        return plugin.getLineManager().addLineAdmin(lineId, adminId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    public OwnershipWriteStatus removeLineAdmin(String lineId, UUID adminId) {
        Line line = getLine(lineId);
        if (line == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        if (adminId.equals(line.getOwner())) {
            return OwnershipWriteStatus.OWNER_PROTECTED;
        }
        if (!line.getAdmins().contains(adminId)) {
            return OwnershipWriteStatus.NOT_ADMIN;
        }
        return plugin.getLineManager().removeLineAdmin(lineId, adminId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    public OwnershipWriteStatus setStopOwner(String stopId, UUID ownerId) {
        Stop stop = getStop(stopId);
        if (stop == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (ownerId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        return plugin.getStopManager().setStopOwner(stopId, ownerId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    public OwnershipWriteStatus addStopAdmin(String stopId, UUID adminId) {
        Stop stop = getStop(stopId);
        if (stop == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        if (stop.getAdmins().contains(adminId)) {
            return OwnershipWriteStatus.EXISTS;
        }
        return plugin.getStopManager().addStopAdmin(stopId, adminId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    public OwnershipWriteStatus removeStopAdmin(String stopId, UUID adminId) {
        Stop stop = getStop(stopId);
        if (stop == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        if (adminId.equals(stop.getOwner())) {
            return OwnershipWriteStatus.OWNER_PROTECTED;
        }
        if (!stop.getAdmins().contains(adminId)) {
            return OwnershipWriteStatus.NOT_ADMIN;
        }
        return plugin.getStopManager().removeStopAdmin(stopId, adminId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    public OwnershipWriteStatus setPortalOwner(String portalId, UUID ownerId) {
        Portal portal = getPortal(portalId);
        if (portal == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (ownerId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        return plugin.getPortalManager().setPortalOwner(portalId, ownerId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    public OwnershipWriteStatus addPortalAdmin(String portalId, UUID adminId) {
        Portal portal = getPortal(portalId);
        if (portal == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        if (portal.getAdmins().contains(adminId)) {
            return OwnershipWriteStatus.EXISTS;
        }
        return plugin.getPortalManager().addPortalAdmin(portalId, adminId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    public OwnershipWriteStatus removePortalAdmin(String portalId, UUID adminId) {
        Portal portal = getPortal(portalId);
        if (portal == null) {
            return OwnershipWriteStatus.NOT_FOUND;
        }
        if (adminId == null) {
            return OwnershipWriteStatus.INVALID_PLAYER;
        }
        if (adminId.equals(portal.getOwner())) {
            return OwnershipWriteStatus.OWNER_PROTECTED;
        }
        if (!portal.getAdmins().contains(adminId)) {
            return OwnershipWriteStatus.NOT_ADMIN;
        }
        return plugin.getPortalManager().removePortalAdmin(portalId, adminId)
                ? OwnershipWriteStatus.SUCCESS
                : OwnershipWriteStatus.FAILED;
    }

    // =============================================================
    // Advanced / unstable access
    // =============================================================

    public LineManager getLineManager() {
        return plugin.getLineManager();
    }

    public StopManager getStopManager() {
        return plugin.getStopManager();
    }

    public PortalManager getPortalManager() {
        return plugin.getPortalManager();
    }

    public Metro getPlugin() {
        return plugin;
    }

    private ConfigFacade config() {
        return plugin.getConfigFacade();
    }

    private PortalCommandService portalService() {
        PortalManager portalManager = plugin.getPortalManager();
        return portalManager != null ? new PortalCommandService(portalManager) : null;
    }

    private PortalWriteStatus toPortalWriteStatus(PortalCommandService.WriteStatus status) {
        if (status == null) {
            return PortalWriteStatus.FAILED;
        }
        return switch (status) {
            case SUCCESS -> PortalWriteStatus.SUCCESS;
            case INVALID_ID -> PortalWriteStatus.INVALID_ID;
            case INVALID_LOCATION -> PortalWriteStatus.INVALID_LOCATION;
            case EXISTS -> PortalWriteStatus.EXISTS;
            case NOT_FOUND -> PortalWriteStatus.NOT_FOUND;
            case FAILED -> PortalWriteStatus.FAILED;
        };
    }
}
