package org.cubexmc.metro.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Line {
    private String id;
    private String name;
    private final List<String> orderedStopIds;
    private final List<String> portalIds;
    private List<RoutePoint> routePoints;
    private String color;
    private String terminusName;
    private Double maxSpeed;
    private double ticketPrice;
    private boolean railProtected;
    private Long routeRecordedAtEpochMillis;
    private UUID routeRecordedBy;
    private UUID routeRecordedCartId;
    private UUID owner;
    private final Set<UUID> admins;
    private String worldName;

    // --- New fields for advanced pricing and line management ---
    private FareRule fareRule;
    private LineStatus lineStatus = LineStatus.NORMAL;
    private final List<String> alternativeRouteIds = new ArrayList<>();
    private String suspensionMessage;

    public Line(String id, String name) {
        this.id = id;
        this.name = name;
        this.orderedStopIds = new ArrayList<String>();
        this.portalIds = new ArrayList<String>();
        this.routePoints = new ArrayList<RoutePoint>();
        this.color = "&f";
        this.terminusName = "";
        this.maxSpeed = null;
        this.ticketPrice = 0.0;
        this.railProtected = false;
        this.admins = new HashSet<UUID>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getTerminusName() {
        return terminusName;
    }

    public void setTerminusName(String terminusName) {
        this.terminusName = terminusName;
    }

    public Double getMaxSpeed() {
        if (maxSpeed == null)
            return -1.0;
        return maxSpeed;
    }

    public void setMaxSpeed(Double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public double getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(double ticketPrice) {
        this.ticketPrice = Math.max(0.0, ticketPrice);
    }

    public boolean isRailProtected() {
        return railProtected;
    }

    public void setRailProtected(boolean railProtected) {
        this.railProtected = railProtected;
    }

    public List<String> getOrderedStopIds() {
        return new ArrayList<String>(orderedStopIds);
    }

    public List<String> getPortalIds() {
        return new ArrayList<String>(portalIds);
    }

    public List<RoutePoint> getRoutePoints() {
        return new ArrayList<RoutePoint>(routePoints);
    }

    public void setRoutePoints(Collection<RoutePoint> routePoints) {
        this.routePoints = new ArrayList<RoutePoint>();
        if (routePoints != null) {
            for (RoutePoint point : routePoints) {
                if (point != null) {
                    this.routePoints.add(point);
                }
            }
        }
    }

    public void clearRoutePoints() {
        routePoints.clear();
        clearRouteRecordingMetadata();
    }

    public Long getRouteRecordedAtEpochMillis() {
        return routeRecordedAtEpochMillis;
    }

    public void setRouteRecordedAtEpochMillis(Long routeRecordedAtEpochMillis) {
        this.routeRecordedAtEpochMillis = routeRecordedAtEpochMillis != null && routeRecordedAtEpochMillis > 0
                ? routeRecordedAtEpochMillis
                : null;
    }

    public UUID getRouteRecordedBy() {
        return routeRecordedBy;
    }

    public void setRouteRecordedBy(UUID routeRecordedBy) {
        this.routeRecordedBy = routeRecordedBy;
    }

    public UUID getRouteRecordedCartId() {
        return routeRecordedCartId;
    }

    public void setRouteRecordedCartId(UUID routeRecordedCartId) {
        this.routeRecordedCartId = routeRecordedCartId;
    }

    public void setRouteRecordingMetadata(Long recordedAtEpochMillis, UUID recordedBy, UUID recordedCartId) {
        setRouteRecordedAtEpochMillis(recordedAtEpochMillis);
        this.routeRecordedBy = recordedBy;
        this.routeRecordedCartId = recordedCartId;
    }

    public void clearRouteRecordingMetadata() {
        this.routeRecordedAtEpochMillis = null;
        this.routeRecordedBy = null;
        this.routeRecordedCartId = null;
    }

    public boolean isCircular() {
        if (orderedStopIds.isEmpty() || orderedStopIds.size() < 2) {
            return false;
        }
        return orderedStopIds.get(0).equals(orderedStopIds.get(orderedStopIds.size() - 1));
    }

    public void addStop(String stopId, int index) {
        boolean isMakingCircular = !isCircular() &&
                !orderedStopIds.isEmpty() &&
                orderedStopIds.get(0).equals(stopId) &&
                (index == -1 || index == orderedStopIds.size());

        if (orderedStopIds.contains(stopId) && !isMakingCircular) {
            orderedStopIds.remove(stopId);
        }

        if (isCircular() && index == -1) {
            orderedStopIds.add(orderedStopIds.size() - 1, stopId);
        } else if (index >= 0 && index < orderedStopIds.size()) {
            orderedStopIds.add(index, stopId);
        } else {
            orderedStopIds.add(stopId);
        }
    }

    public void delStop(String stopId) {
        orderedStopIds.remove(stopId);
    }

    public boolean containsStop(String stopId) {
        return orderedStopIds.contains(stopId);
    }

    public boolean addPortal(String portalId) {
        if (portalId == null || portalId.trim().isEmpty() || portalIds.contains(portalId)) {
            return false;
        }
        return portalIds.add(portalId);
    }

    public boolean delPortal(String portalId) {
        return portalIds.remove(portalId);
    }

    public boolean containsPortal(String portalId) {
        return portalIds.contains(portalId);
    }

    public String getNextStopId(String currentStopId) {
        int index = orderedStopIds.indexOf(currentStopId);
        if (index == -1) {
            return null;
        }

        if (index == orderedStopIds.size() - 1) {
            if (isCircular()) {
                if (orderedStopIds.size() > 1) {
                    return orderedStopIds.get(1);
                } else {
                    return orderedStopIds.get(0);
                }
            } else {
                return null;
            }
        }
        return orderedStopIds.get(index + 1);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        if (owner != null) {
            admins.add(owner);
        }
        admins.remove(null);
    }

    public Set<UUID> getAdmins() {
        return new HashSet<UUID>(admins);
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public void setAdmins(Collection<UUID> adminIds) {
        admins.clear();
        if (adminIds != null) {
            admins.addAll(adminIds);
        }
        if (owner != null) {
            admins.add(owner);
        }
        admins.remove(null);
    }

    public boolean addAdmin(UUID adminId) {
        if (adminId == null) {
            return false;
        }
        admins.remove(null);
        return admins.add(adminId);
    }

    public boolean removeAdmin(UUID adminId) {
        if (adminId == null) {
            return false;
        }
        if (owner != null && owner.equals(adminId)) {
            return false;
        }
        boolean removed = admins.remove(adminId);
        admins.remove(null);
        return removed;
    }

    public String getPreviousStopId(String currentStopId) {
        int index = orderedStopIds.indexOf(currentStopId);
        if (index <= 0) {
            if (isCircular()) {
                if (orderedStopIds.size() > 2) {
                    return orderedStopIds.get(orderedStopIds.size() - 2);
                } else if (orderedStopIds.size() == 2) {
                    return orderedStopIds.get(0);
                }
            }
            return null;
        }
        return orderedStopIds.get(index - 1);
    }

    // =============================================================
    // Advanced Pricing (FareRule)
    // =============================================================

    /**
     * Get the fare rule for this line. If null, the legacy ticketPrice is used.
     */
    public FareRule getFareRule() {
        return fareRule;
    }

    /**
     * Set the fare rule for this line.
     */
    public void setFareRule(FareRule fareRule) {
        this.fareRule = fareRule;
    }

    // =============================================================
    // Line Status (Suspension/Maintenance)
    // =============================================================

    /**
     * Get the current operational status of this line.
     */
    public LineStatus getLineStatus() {
        return lineStatus != null ? lineStatus : LineStatus.NORMAL;
    }

    /**
     * Set the operational status of this line.
     */
    public void setLineStatus(LineStatus lineStatus) {
        this.lineStatus = lineStatus != null ? lineStatus : LineStatus.NORMAL;
    }

    // =============================================================
    // Alternative Routes
    // =============================================================

    /**
     * Get the list of alternative route line IDs suggested when this line is suspended.
     */
    public List<String> getAlternativeRouteIds() {
        return new ArrayList<>(alternativeRouteIds);
    }

    /**
     * Set the list of alternative route line IDs.
     */
    public void setAlternativeRouteIds(Collection<String> alternativeRouteIds) {
        this.alternativeRouteIds.clear();
        if (alternativeRouteIds != null) {
            for (String id : alternativeRouteIds) {
                if (id != null && !id.trim().isEmpty()) {
                    this.alternativeRouteIds.add(id.trim());
                }
            }
        }
    }

    /**
     * Add an alternative route by line ID.
     */
    public boolean addAlternativeRoute(String lineId) {
        if (lineId == null || lineId.trim().isEmpty() || alternativeRouteIds.contains(lineId.trim())) {
            return false;
        }
        return alternativeRouteIds.add(lineId.trim());
    }

    /**
     * Remove an alternative route by line ID.
     */
    public boolean removeAlternativeRoute(String lineId) {
        return alternativeRouteIds.remove(lineId);
    }

    // =============================================================
    // Suspension Message
    // =============================================================

    /**
     * Get the message displayed to players when trying to board a suspended line.
     */
    public String getSuspensionMessage() {
        return suspensionMessage;
    }

    /**
     * Set the suspension message for this line.
     */
    public void setSuspensionMessage(String suspensionMessage) {
        this.suspensionMessage = suspensionMessage;
    }
}
