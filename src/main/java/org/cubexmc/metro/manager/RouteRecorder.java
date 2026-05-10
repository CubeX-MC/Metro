package org.cubexmc.metro.manager;

import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.RoutePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RouteRecorder {

    private static final double DEFAULT_MIN_SAMPLE_DISTANCE_BLOCKS = 1.0;
    private static final double DEFAULT_SIMPLIFY_EPSILON_BLOCKS = 0.15;
    private static final int MIN_SAVE_POINTS = 2;

    private final Metro plugin;
    private final Map<String, RecordingSession> sessions = new ConcurrentHashMap<>();
    private final RouteNormalizer routeNormalizer;

    public RouteRecorder(Metro plugin) {
        this.plugin = plugin;
        this.routeNormalizer = new RouteNormalizer();
    }

    public boolean start(String lineId) {
        return start(lineId, null);
    }

    public boolean start(String lineId, UUID recorderId) {
        return sessions.putIfAbsent(lineId, new RecordingSession(lineId, recorderId)) == null;
    }

    public FinishResult stopAndSave(String lineId) {
        RecordingSession session = sessions.remove(lineId);
        if (session == null) {
            return FinishResult.notRecording(lineId);
        }
        return saveSession(session);
    }

    public boolean clearActive(String lineId) {
        return sessions.remove(lineId) != null;
    }

    public boolean isRecording(String lineId) {
        return sessions.containsKey(lineId);
    }

    public int getActivePointCount(String lineId) {
        RecordingSession session = sessions.get(lineId);
        return session == null ? 0 : session.pointCount();
    }

    public UUID getRecordingCartId(String lineId) {
        RecordingSession session = sessions.get(lineId);
        return session == null ? null : session.cartId;
    }

    public UUID getRecordingPlayerId(String lineId) {
        RecordingSession session = sessions.get(lineId);
        return session == null ? null : session.recorderId;
    }

    public boolean transferCart(String lineId, Minecart previousCart, Minecart newCart) {
        RecordingSession session = sessions.get(lineId);
        if (session == null || previousCart == null || newCart == null) {
            return false;
        }
        return session.transferCart(previousCart.getUniqueId(), newCart.getUniqueId());
    }

    public void sample(String lineId, Minecart minecart, Location location) {
        RecordingSession session = sessions.get(lineId);
        if (session == null || minecart == null || location == null) {
            return;
        }
        RoutePoint routePoint = RoutePoint.fromLocation(location);
        if (routePoint == null) {
            return;
        }
        session.sample(minecart.getUniqueId(), routePoint, minSampleDistanceSquared());
    }

    public FinishResult finishIfRecording(String lineId, Minecart minecart) {
        RecordingSession session = sessions.get(lineId);
        if (session == null || minecart == null || !session.matchesCart(minecart.getUniqueId())) {
            return FinishResult.notRecording(lineId);
        }
        sessions.remove(lineId);
        return saveSession(session);
    }

    public void cancelAll() {
        sessions.clear();
    }

    private FinishResult saveSession(RecordingSession session) {
        List<RoutePoint> normalized = routeNormalizer.normalize(session.snapshot(), simplifyEpsilonBlocks());
        List<RoutePoint> points = normalized.size() >= MIN_SAVE_POINTS
                ? normalized
                : simplifyRoutePoints(session.snapshot());
        if (points.size() < MIN_SAVE_POINTS) {
            return FinishResult.tooFewPoints(session.lineId, points.size(), session.recorderId, session.cartId);
        }
        if (!plugin.getLineManager().setLineRoutePoints(session.lineId, points, System.currentTimeMillis(),
                session.recorderId, session.cartId)) {
            return FinishResult.failed(session.lineId, points.size(), session.recorderId, session.cartId);
        }
        plugin.getLogger().info("[RouteRecorder] Saved " + points.size() + " route points for line " + session.lineId + ".");
        return FinishResult.saved(session.lineId, points.size(), session.recorderId, session.cartId);
    }

    private List<RoutePoint> simplifyRoutePoints(List<RoutePoint> points) {
        if (points == null || points.size() < 3 || !shouldSimplifyCollinearPoints()) {
            return points == null ? List.of() : points;
        }

        List<RoutePoint> simplified = new ArrayList<>();
        simplified.add(points.get(0));
        double epsilon = simplifyEpsilonBlocks();
        for (int i = 1; i < points.size() - 1; i++) {
            RoutePoint previous = simplified.get(simplified.size() - 1);
            RoutePoint current = points.get(i);
            RoutePoint next = points.get(i + 1);
            if (!isRedundantCollinearPoint(previous, current, next, epsilon)) {
                simplified.add(current);
            }
        }
        simplified.add(points.get(points.size() - 1));
        return simplified;
    }

    private boolean shouldSimplifyCollinearPoints() {
        return plugin.getConfigFacade() == null || plugin.getConfigFacade().isRouteRecordingSimplifyCollinearPoints();
    }

    private double minSampleDistanceSquared() {
        double distance = plugin.getConfigFacade() == null
                ? DEFAULT_MIN_SAMPLE_DISTANCE_BLOCKS
                : plugin.getConfigFacade().getRouteRecordingMinSampleDistanceBlocks();
        return distance * distance;
    }

    private double simplifyEpsilonBlocks() {
        return plugin.getConfigFacade() == null
                ? DEFAULT_SIMPLIFY_EPSILON_BLOCKS
                : plugin.getConfigFacade().getRouteRecordingSimplifyEpsilonBlocks();
    }

    private static boolean isRedundantCollinearPoint(RoutePoint previous, RoutePoint current, RoutePoint next,
                                                    double epsilon) {
        if (previous == null || current == null || next == null
                || !previous.worldName().equals(current.worldName())
                || !previous.worldName().equals(next.worldName())) {
            return false;
        }

        double acX = next.x() - previous.x();
        double acY = next.y() - previous.y();
        double acZ = next.z() - previous.z();
        double abX = current.x() - previous.x();
        double abY = current.y() - previous.y();
        double abZ = current.z() - previous.z();
        double acLengthSquared = acX * acX + acY * acY + acZ * acZ;
        if (acLengthSquared <= 0.000001D) {
            return current.distanceSquared(previous) <= epsilon * epsilon;
        }

        double projection = abX * acX + abY * acY + abZ * acZ;
        double tolerance = Math.max(epsilon, 0.000001D);
        if (projection < -tolerance || projection > acLengthSquared + tolerance) {
            return false;
        }

        double crossX = abY * acZ - abZ * acY;
        double crossY = abZ * acX - abX * acZ;
        double crossZ = abX * acY - abY * acX;
        double distanceSquared = (crossX * crossX + crossY * crossY + crossZ * crossZ) / acLengthSquared;
        return distanceSquared <= epsilon * epsilon;
    }

    private static class RecordingSession {
        private final String lineId;
        private final UUID recorderId;
        private final List<RoutePoint> points = new ArrayList<>();
        private UUID cartId;
        private RoutePoint lastPoint;

        private RecordingSession(String lineId, UUID recorderId) {
            this.lineId = lineId;
            this.recorderId = recorderId;
        }

        private synchronized void sample(UUID candidateCartId, RoutePoint routePoint, double minSampleDistanceSquared) {
            if (cartId == null) {
                cartId = candidateCartId;
            }
            if (!cartId.equals(candidateCartId)) {
                return;
            }
            if (lastPoint != null && lastPoint.distanceSquared(routePoint) < minSampleDistanceSquared) {
                return;
            }
            points.add(routePoint);
            lastPoint = routePoint;
        }

        private synchronized boolean matchesCart(UUID candidateCartId) {
            return cartId == null || cartId.equals(candidateCartId);
        }

        private synchronized boolean transferCart(UUID previousCartId, UUID newCartId) {
            if (previousCartId == null || newCartId == null) {
                return false;
            }
            if (cartId == null) {
                cartId = newCartId;
                return true;
            }
            if (cartId.equals(newCartId)) {
                return true;
            }
            if (!cartId.equals(previousCartId)) {
                return false;
            }
            cartId = newCartId;
            return true;
        }

        private synchronized int pointCount() {
            return points.size();
        }

        private synchronized List<RoutePoint> snapshot() {
            return new ArrayList<>(points);
        }
    }

    public record FinishResult(Status status, String lineId, int pointCount, UUID recorderId, UUID cartId) {
        public enum Status {
            SAVED,
            NOT_RECORDING,
            TOO_FEW_POINTS,
            FAILED
        }

        private static FinishResult saved(String lineId, int pointCount, UUID recorderId, UUID cartId) {
            return new FinishResult(Status.SAVED, lineId, pointCount, recorderId, cartId);
        }

        private static FinishResult notRecording(String lineId) {
            return new FinishResult(Status.NOT_RECORDING, lineId, 0, null, null);
        }

        private static FinishResult tooFewPoints(String lineId, int pointCount, UUID recorderId, UUID cartId) {
            return new FinishResult(Status.TOO_FEW_POINTS, lineId, pointCount, recorderId, cartId);
        }

        private static FinishResult failed(String lineId, int pointCount, UUID recorderId, UUID cartId) {
            return new FinishResult(Status.FAILED, lineId, pointCount, recorderId, cartId);
        }
    }
}
