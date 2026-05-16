package org.cubexmc.metro.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class OctreeTest {

    @Test
    void shouldInsertFindRemoveAndClearRanges() {
        Octree<String> octree = new Octree<>(new Range3D(0, 0, 0, 100, 100, 100), 2, 1);
        Range3D first = new Range3D(0, 0, 0, 10, 10, 10);
        Range3D second = new Range3D(40, 40, 40, 50, 50, 50);
        Range3D outside = new Range3D(200, 200, 200, 210, 210, 210);

        assertTrue(octree.insert(first, "first"));
        assertTrue(octree.insert(second, "second"));
        assertEquals("first", octree.firstRange(new Point3D(5, 5, 5)));
        assertEquals(List.of("second"), octree.getAllRanges(new Point3D(45, 45, 45)));

        assertTrue(octree.remove(first));
        assertNull(octree.firstRange(new Point3D(5, 5, 5)));
        assertTrue(octree.getAllRanges(new Point3D(150, 150, 150)).isEmpty());

        octree.clear();
        assertNull(octree.firstRange(new Point3D(45, 45, 45)));
        assertTrue(octree.getAllRanges(new Point3D(45, 45, 45)).isEmpty());
        assertTrue(!octree.insert(outside, "outside"));
    }

    @Test
    void shouldSubdivideRangesAndCompareByCoordinates() {
        Range3D range = new Range3D(10, 20, 30, 0, 0, 0);
        Range3D[] children = range.subdivide();

        assertEquals(8, children.length);
        assertTrue(range.contains(new Point3D(5, 10, 15)));
        assertTrue(range.intersects(new Range3D(5, 5, 5, 15, 15, 15)));
        assertEquals(new Range3D(0, 0, 0, 10, 20, 30), range);
        assertEquals(new Range3D(0, 0, 0, 10, 20, 30).hashCode(), range.hashCode());
    }
}
