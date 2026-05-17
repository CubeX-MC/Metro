package org.cubexmc.metro.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe, generic octree for spatial indexing.
 * <p>
 * Items are inserted with an associated {@link Range3D} extent.  The tree
 * subdivides automatically when a node exceeds {@code maxItems} items,
 * up to a maximum depth of {@code maxDepth}.  Items that span multiple child
 * boundaries are placed into the first intersecting child; queries that miss
 * in the tree should fall back to a full scan for correctness.
 * <p>
 * <b>Thread-safety:</b> all public operations acquire the appropriate
 * read or write lock.  Multiple concurrent readers are allowed; writers
 * are exclusive.
 *
 * @param <T> the type of data stored in the tree
 */
public class Octree<T> {
    private final Range3D boundary;
    private final int maxDepth;
    private final int maxItems;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Range3D, T> items = new ConcurrentHashMap<>();
    private Octree<T>[] children;
    private int depth;

    /**
     * Creates an octree with the given root boundary and growth limits.
     *
     * @param boundary the root node's spatial extent
     * @param maxDepth maximum subdivision depth
     * @param maxItems maximum items per node before subdivision
     */
    public Octree(Range3D boundary, int maxDepth, int maxItems) {
        this(boundary, maxDepth, maxItems, 0);
    }

    private Octree(Range3D boundary, int maxDepth, int maxItems, int depth) {
        this.boundary = boundary;
        this.maxDepth = maxDepth;
        this.maxItems = maxItems;
        this.depth = depth;
    }

    /**
     * Inserts an item at the given spatial range.
     *
     * @param range the spatial extent of the item
     * @param data  the item to store
     * @return {@code true} if the item was inserted; {@code false} if the
     *         range does not intersect this tree's boundary
     */
    public boolean insert(Range3D range, T data) {
        lock.writeLock().lock();
        try {
            if (!boundary.intersects(range)) return false;

            if (children != null) {
                for (Octree<T> child : children) {
                    if (child.boundary.intersects(range)) {
                        if (child.insert(range, data)) return true;
                    }
                }
                return false;
            }

            items.put(range, data);

            if (items.size() > maxItems && depth < maxDepth) {
                subdivide();
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes an item by its spatial range.
     *
     * @param range the previously-inserted range
     * @return {@code true} if the item was found and removed
     */
    public boolean remove(Range3D range) {
        lock.writeLock().lock();
        try {
            if (items.remove(range) != null) return true;

            if (children != null) {
                for (Octree<T> child : children) {
                    if (child.boundary.intersects(range)) {
                        if (child.remove(range)) return true;
                    }
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds the first item whose range contains the given point.
     * <p>
     * This performs an approximate spatial lookup.  Items that span
     * multiple child nodes may be missed; the caller should fall back
     * to a full scan for correctness.
     *
     * @param point the query point
     * @return the first matching item, or {@code null} if none found
     */
    public T firstRange(Point3D point) {
        lock.readLock().lock();
        try {
            if (!boundary.contains(point)) return null;

            for (Map.Entry<Range3D, T> entry : items.entrySet()) {
                if (entry.getKey().contains(point)) return entry.getValue();
            }

            if (children != null) {
                for (Octree<T> child : children) {
                    if (child.boundary.contains(point)) {
                        T found = child.firstRange(point);
                        if (found != null) return found;
                    }
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all items whose range contains the given point.
     * <p>
     * Like {@link #firstRange}, this may miss items that span multiple
     * child nodes.  Callers should fall back to a full scan.
     *
     * @param point the query point
     * @return a (possibly empty) list of matching items
     */
    public List<T> getAllRanges(Point3D point) {
        List<T> results = new ArrayList<>();
        lock.readLock().lock();
        try {
            if (!boundary.contains(point)) return results;

            for (Map.Entry<Range3D, T> entry : items.entrySet()) {
                if (entry.getKey().contains(point)) {
                    results.add(entry.getValue());
                }
            }

            if (children != null) {
                for (Octree<T> child : children) {
                    if (child.boundary.contains(point)) {
                        results.addAll(child.getAllRanges(point));
                    }
                }
            }
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private void subdivide() {
        if (children != null || depth >= maxDepth) return;
        Range3D[] subRanges = boundary.subdivide();
        children = new Octree[8];
        for (int i = 0; i < 8; i++) {
            children[i] = new Octree<>(subRanges[i], maxDepth, maxItems, depth + 1);
        }

        Map<Range3D, T> currentItems = new java.util.HashMap<>(items);
        items.clear();
        for (Map.Entry<Range3D, T> entry : currentItems.entrySet()) {
            boolean inserted = false;
            for (Octree<T> child : children) {
                if (child.boundary.intersects(entry.getKey())) {
                    if (child.insert(entry.getKey(), entry.getValue())) {
                        inserted = true;
                        break;
                    }
                }
            }
            if (!inserted) items.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Removes all items from the tree and collapses all children.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            items.clear();
            if (children != null) {
                for (Octree<T> child : children) child.clear();
                children = null;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
