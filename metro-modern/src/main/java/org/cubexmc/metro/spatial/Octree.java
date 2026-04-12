package org.cubexmc.metro.spatial;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Octree<T> {
    private final Range3D boundary;
    private final int maxDepth;
    private final int maxItems;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    // Using a ConcurrentHashMap with Range3D as key, but since ranges might overlap perfectly or change,
    // we use a specific wrapping to support multiple items per range if needed, or just let them overwrite.
    // In our case, a stop's range is unique enough, but we should map Range3D -> T
    private final Map<Range3D, T> items = new ConcurrentHashMap<>();
    private Octree<T>[] children;
    private int depth;

    public Octree(Range3D boundary, int maxDepth, int maxItems) {
        this(boundary, maxDepth, maxItems, 0);
    }

    private Octree(Range3D boundary, int maxDepth, int maxItems, int depth) {
        this.boundary = boundary;
        this.maxDepth = maxDepth;
        this.maxItems = maxItems;
        this.depth = depth;
    }

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
