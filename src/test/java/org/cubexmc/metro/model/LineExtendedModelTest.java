package org.cubexmc.metro.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LineExtendedModelTest {

    @Test
    void shouldManageOwnerAndAdmins() {
        Line line = new Line("l1", "Line1");
        assertNull(line.getOwner());

        UUID owner = UUID.randomUUID();
        line.setOwner(owner);
        assertEquals(owner, line.getOwner());
        assertTrue(line.getAdmins().contains(owner));

        UUID admin = UUID.randomUUID();
        assertTrue(line.addAdmin(admin));
        assertFalse(line.addAdmin(admin)); // duplicate
        assertTrue(line.getAdmins().contains(admin));

        // cannot remove owner from admins
        assertFalse(line.removeAdmin(owner));
        assertTrue(line.getAdmins().contains(owner));

        // can remove normal admin
        assertTrue(line.removeAdmin(admin));
        assertFalse(line.getAdmins().contains(admin));
    }

    @Test
    void shouldIgnoreNullAdminOperations() {
        Line line = new Line("l1", "Line1");
        assertFalse(line.addAdmin(null));
        assertFalse(line.removeAdmin(null));
    }

    @Test
    void shouldSetAdminsAndPreserveOwner() {
        Line line = new Line("l1", "Line1");
        UUID owner = UUID.randomUUID();
        line.setOwner(owner);

        UUID admin = UUID.randomUUID();
        line.setAdmins(Set.of(owner, admin));

        assertTrue(line.getAdmins().contains(owner));
        assertTrue(line.getAdmins().contains(admin));
    }

    @Test
    void shouldGetPreviousStopOnNormalLine() {
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);

        assertNull(line.getPreviousStopId("A"));
        assertEquals("A", line.getPreviousStopId("B"));
        assertEquals("B", line.getPreviousStopId("C"));
    }

    @Test
    void shouldGetPreviousStopOnCircularLine() {
        Line line = new Line("l1", "Circle");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);
        line.addStop("A", -1); // circular

        assertEquals("C", line.getPreviousStopId("A"));
        assertEquals("A", line.getPreviousStopId("B"));
        assertEquals("B", line.getPreviousStopId("C"));
    }

    @Test
    void shouldReturnNullForUnknownStopPrevious() {
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        assertNull(line.getPreviousStopId("unknown"));
    }

    @Test
    void shouldCheckContainsStop() {
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);

        assertTrue(line.containsStop("A"));
        assertTrue(line.containsStop("B"));
        assertFalse(line.containsStop("C"));
    }

    @Test
    void shouldDeleteStop() {
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);
        line.addStop("B", -1);
        line.addStop("C", -1);

        line.delStop("B");
        assertEquals(List.of("A", "C"), line.getOrderedStopIds());
    }

    @Test
    void shouldManageSpeedAndPrice() {
        Line line = new Line("l1", "Line1");

        assertEquals(-1.0, line.getMaxSpeed());
        line.setMaxSpeed(0.8);
        assertEquals(0.8, line.getMaxSpeed());

        assertEquals(0.0, line.getTicketPrice());
        line.setTicketPrice(10.5);
        assertEquals(10.5, line.getTicketPrice());

        // negative price clamped to 0
        line.setTicketPrice(-5.0);
        assertEquals(0.0, line.getTicketPrice());
    }

    @Test
    void shouldManageRailProtection() {
        Line line = new Line("l1", "Line1");
        assertFalse(line.isRailProtected());
        line.setRailProtected(true);
        assertTrue(line.isRailProtected());
    }

    @Test
    void shouldManageRouteRecordingMetadata() {
        Line line = new Line("l1", "Line1");
        assertNull(line.getRouteRecordedAtEpochMillis());
        assertNull(line.getRouteRecordedBy());
        assertNull(line.getRouteRecordedCartId());

        UUID player = UUID.randomUUID();
        UUID cart = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();

        line.setRouteRecordingMetadata(timestamp, player, cart);
        assertEquals(timestamp, line.getRouteRecordedAtEpochMillis());
        assertEquals(player, line.getRouteRecordedBy());
        assertEquals(cart, line.getRouteRecordedCartId());

        line.clearRouteRecordingMetadata();
        assertNull(line.getRouteRecordedAtEpochMillis());
        assertNull(line.getRouteRecordedBy());
        assertNull(line.getRouteRecordedCartId());
    }

    @Test
    void shouldRejectZeroTimestamp() {
        Line line = new Line("l1", "Line1");
        line.setRouteRecordedAtEpochMillis(0L);
        assertNull(line.getRouteRecordedAtEpochMillis());

        line.setRouteRecordedAtEpochMillis(-1L);
        assertNull(line.getRouteRecordedAtEpochMillis());
    }

    @Test
    void shouldClearRoutePointsAndMetadata() {
        Line line = new Line("l1", "Line1");
        line.setRoutePoints(List.of(new RoutePoint("w", 1, 2, 3)));
        line.setRouteRecordingMetadata(System.currentTimeMillis(), UUID.randomUUID(), UUID.randomUUID());

        line.clearRoutePoints();

        assertTrue(line.getRoutePoints().isEmpty());
        assertNull(line.getRouteRecordedAtEpochMillis());
    }

    @Test
    void shouldFilterNullRoutePoints() {
        Line line = new Line("l1", "Line1");
        RoutePoint valid = new RoutePoint("w", 1, 2, 3);
        List<RoutePoint> input = new java.util.ArrayList<>();
        input.add(valid);
        input.add(null);
        input.add(valid);
        line.setRoutePoints(input);

        assertEquals(2, line.getRoutePoints().size());
    }

    @Test
    void shouldReturnDefensiveCopyOfStopIds() {
        Line line = new Line("l1", "Line1");
        line.addStop("A", -1);

        List<String> copy = line.getOrderedStopIds();
        copy.clear();

        assertEquals(List.of("A"), line.getOrderedStopIds());
    }

    @Test
    void shouldReturnDefensiveCopyOfAdmins() {
        Line line = new Line("l1", "Line1");
        UUID owner = UUID.randomUUID();
        line.setOwner(owner);

        Set<UUID> copy = line.getAdmins();
        copy.clear();

        assertTrue(line.getAdmins().contains(owner));
    }

    @Test
    void shouldNotBeCircularWithSingleStop() {
        Line line = new Line("l1", "L");
        line.addStop("A", -1);
        assertFalse(line.isCircular());
    }

    @Test
    void shouldNotBeCircularWhenEmpty() {
        Line line = new Line("l1", "L");
        assertFalse(line.isCircular());
    }

    @Test
    void shouldSetName() {
        Line line = new Line("l1", "Old");
        line.setName("New");
        assertEquals("New", line.getName());
    }

    @Test
    void shouldSetWorldName() {
        Line line = new Line("l1", "L");
        assertNull(line.getWorldName());
        line.setWorldName("world");
        assertEquals("world", line.getWorldName());
    }
}
