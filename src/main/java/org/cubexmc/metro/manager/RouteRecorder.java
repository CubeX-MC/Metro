package org.cubexmc.metro.manager;

import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.model.RoutePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RouteRecorder {

    private static final double MIN_SAMPLE_DISTANCE_SQUARED = 16.0;
    private static final int MIN_SAVE_POINTS = 2;

    private final Metro plugin;
    private final Map<String, RecordingSession> sessions = new ConcurrentHashMap<>();

    public RouteRecorder(Metro plugin) {
        this.plugin = plugin;
    }

    public boolean start(String lineId) {
        return sessions.putIfAbsent(lineId, new RecordingSession(lineId)) == null;
    }

    public FinishResult stopAndSave(String lineId) {
        RecordingSession session = sessions.remove(lineId);
        if (session == null) {
            return FinishResult.notRecording();
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

    public java.util.UUID getRecordingCartId(String lineId) {
        RecordingSession session = sessions.get(lineId);
        return session == null ? null : session.cartId;
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
        session.sample(minecart.getUniqueId(), routePoint);
    }

    public FinishResult finishIfRecording(String lineId, Minecart minecart) {
        RecordingSession session = sessions.get(lineId);
        if (session == null || minecart == null || !session.matchesCart(minecart.getUniqueId())) {
            return FinishResult.notRecording();
        }
        sessions.remove(lineId);
        return saveSession(session);
    }

    public void cancelAll() {
        sessions.clear();
    }

    private FinishResult saveSession(RecordingSession session) {
        List<RoutePoint> points = session.snapshot();
        if (points.size() < MIN_SAVE_POINTS) {
            return FinishResult.tooFewPoints(points.size());
        }
        if (!plugin.getLineManager().setLineRoutePoints(session.lineId, points)) {
            return FinishResult.failed(points.size());
        }
        plugin.getLogger().info("[RouteRecorder] Saved " + points.size() + " route points for line " + session.lineId + ".");
        return FinishResult.saved(points.size());
    }

    private static class RecordingSession {
        private final String lineId;
        private final List<RoutePoint> points = new ArrayList<>();
        private java.util.UUID cartId;
        private RoutePoint lastPoint;

        private RecordingSession(String lineId) {
            this.lineId = lineId;
        }

        private synchronized void sample(java.util.UUID candidateCartId, RoutePoint routePoint) {
            if (cartId == null) {
                cartId = candidateCartId;
            }
            if (!cartId.equals(candidateCartId)) {
                return;
            }
            if (lastPoint != null && lastPoint.distanceSquared(routePoint) < MIN_SAMPLE_DISTANCE_SQUARED) {
                return;
            }
            points.add(routePoint);
            lastPoint = routePoint;
        }

        private synchronized boolean matchesCart(java.util.UUID candidateCartId) {
            return cartId == null || cartId.equals(candidateCartId);
        }

        private synchronized int pointCount() {
            return points.size();
        }

        private synchronized List<RoutePoint> snapshot() {
            return new ArrayList<>(points);
        }
    }

    public record FinishResult(Status status, int pointCount) {
        public enum Status {
            SAVED,
            NOT_RECORDING,
            TOO_FEW_POINTS,
            FAILED
        }

        private static FinishResult saved(int pointCount) {
            return new FinishResult(Status.SAVED, pointCount);
        }

        private static FinishResult notRecording() {
            return new FinishResult(Status.NOT_RECORDING, 0);
        }

        private static FinishResult tooFewPoints(int pointCount) {
            return new FinishResult(Status.TOO_FEW_POINTS, pointCount);
        }

        private static FinishResult failed(int pointCount) {
            return new FinishResult(Status.FAILED, pointCount);
        }
    }
}
