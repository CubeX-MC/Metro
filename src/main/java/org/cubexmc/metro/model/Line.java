package org.cubexmc.metro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 代表地铁系统中的一条线路
 */
public class Line {
    private String id;
    private String name;
    private final List<String> orderedStopIds;
    private String color; // 线路颜色
    private String terminusName; // 终点站方向名称
    private Double maxSpeed; // 线路最大速度
    
    /**
     * 创建新线路
     * 
     * @param id 线路ID
     * @param name 线路名称
     */
    public Line(String id, String name) {
        this.id = id;
        this.name = name;
        this.orderedStopIds = new ArrayList<>();
        this.color = "&f"; // 默认白色
        this.terminusName = ""; // 默认空
        this.maxSpeed = null; // 默认使用config.yml中的maxspeed
    }
    
    /**
     * 获取线路ID
     * 
     * @return 线路ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * 获取线路名称
     * 
     * @return 线路名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 设置线路名称
     * 
     * @param name 新名称
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * 获取线路颜色
     * 
     * @return 线路颜色
     */
    public String getColor() {
        return color;
    }
    
    /**
     * 设置线路颜色
     * 
     * @param color 新颜色
     */
    public void setColor(String color) {
        this.color = color;
    }
    
    /**
     * 获取终点站方向名称
     * 
     * @return 终点站方向名称
     */
    public String getTerminusName() {
        return terminusName;
    }
    
    /**
     * 设置终点站方向名称
     * 
     * @param terminusName 新终点站方向名称
     */
    public void setTerminusName(String terminusName) {
        this.terminusName = terminusName;
    }
    
    /**
     * 获取线路最大速度
     * 
     * @return 线路最大速度，如果未设置则返回n-1.0
     */
    public Double getMaxSpeed() {
        if (maxSpeed == null)
            return -1.0;
        return maxSpeed;
    }
    
    /**
     * 设置线路最大速度
     * 
     * @param maxSpeed 新的最大速度
     */
    public void setMaxSpeed(Double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }
    
    /**
     * 获取有序停靠区ID列表
     * 
     * @return 有序停靠区ID列表
     */
    public List<String> getOrderedStopIds() {
        return new ArrayList<>(orderedStopIds);
    }
    
    /**
     * 向线路添加停靠区
     * 
     * @param stopId 停靠区ID
     * @param index 添加位置，-1表示添加到末尾
     */
    public void addStop(String stopId, int index) {
        // 先移除，防止重复
        if (orderedStopIds.contains(stopId)) {
            orderedStopIds.remove(stopId);
        }
        
        // 添加到指定位置或末尾
        if (index >= 0 && index < orderedStopIds.size()) {
            orderedStopIds.add(index, stopId);
        } else {
            orderedStopIds.add(stopId);
        }
    }
    
    /**
     * 从线路中移除停靠区
     * 
     * @param stopId 停靠区ID
     */
    public void removeStop(String stopId) {
        orderedStopIds.remove(stopId);
    }
    
    /**
     * 获取停靠区在线路中的索引
     * 
     * @param stopId 停靠区ID
     * @return 索引，不存在返回-1
     */
    public int getStopIndex(String stopId) {
        return orderedStopIds.indexOf(stopId);
    }
    
    /**
     * 检查线路是否包含指定停靠区
     * 
     * @param stopId 停靠区ID
     * @return 是否包含
     */
    public boolean containsStop(String stopId) {
        return orderedStopIds.contains(stopId);
    }
    
    /**
     * 获取指定停靠区的下一个停靠区ID
     * 
     * @param currentStopId 当前停靠区ID
     * @return 下一个停靠区ID，如果当前是终点站或不存在，则返回null
     */
    public String getNextStopId(String currentStopId) {
        int index = orderedStopIds.indexOf(currentStopId);
        if (index == -1) {
            return null;
        }
        if (isCircular()) {
            if (orderedStopIds.size() <= 1) return null; // Not a valid circle
            // For a circular line, the "next" of the last element (which is same as first) is the second element.
            // Or, more generally, next of index `i` is `(i + 1) % (list.size() - 1)` if we ignore the duplicate end.
            // Example: [A, B, C, A] size=4. Real elements = A, B, C. size for modulo = 3.
            // current A (idx 0): next B (idx 1)
            // current B (idx 1): next C (idx 2)
            // current C (idx 2): next A (idx 0)
            // current A (idx 3, duplicate): logically, this state should ideally map to A (idx 0) for consistency.
            // If currentStopId is the *last* element in `orderedStopIds` (which is a duplicate of the first for circular lines),
            // its next should be the second element.
            if (index == orderedStopIds.size() - 1) { // If current is the duplicate last stop (e.g. A in A-B-C-A)
                 // The next stop is the second stop in the sequence.
                return orderedStopIds.get(1 % (orderedStopIds.size() -1)); // Handles A->A case returning A (index 0), A->B->A returning B (index 1)
            }
            return orderedStopIds.get((index + 1) % (orderedStopIds.size() -1));
        } else {
            if (index == orderedStopIds.size() - 1) {
                return null; // Last stop of a non-circular line
            }
            return orderedStopIds.get(index + 1);
        }
    }
    
    /**
     * 获取指定停靠区的上一个停靠区ID
     * 
     * @param currentStopId 当前停靠区ID
     * @return 上一个停靠区ID，如果当前是起点站或不存在，则返回null
     */
    public String getPreviousStopId(String currentStopId) {
        int index = orderedStopIds.indexOf(currentStopId);
        if (index == -1) {
            return null;
        }

        if (isCircular()) {
            if (orderedStopIds.size() <= 1) return null; // Not a valid circle for previous
            // Example: [A, B, C, A], unique part is [A, B, C]
            // If current is A (index 0), previous is C.
            // If current is C (index 2), previous is B.
            // If current is B (index 1), previous is A.
            // If current is A (index 3, duplicate of first), treat as index 0.
            if (index == 0 || index == orderedStopIds.size() - 1) { // First stop or its duplicate at the end
                return orderedStopIds.get(orderedStopIds.size() - 2); // The stop before the duplicate (e.g. C in A-B-C-A)
            }
            return orderedStopIds.get(index - 1);
        } else {
            if (index == 0) {
                return null; // First stop of a non-circular line
            }
            return orderedStopIds.get(index - 1);
        }
    }

    /**
     * 检查线路是否为环线
     * 环线条件：停靠区列表不为空，首尾停靠区ID相同，并且至少有3个站点以形成一个有意义的环（例如 A->B->A）
     * A->A is not a meaningful loop in this context. A->B->A means orderedStopIds would be [A, B, A] (size 3)
     * @return 如果是环线则返回true
     */
    private boolean isCircularInternal() {
        if (orderedStopIds == null || orderedStopIds.isEmpty() || orderedStopIds.size() < 3) { // e.g. [A,B,A] is minimum
            return false;
        }
        return orderedStopIds.get(0).equals(orderedStopIds.get(orderedStopIds.size() - 1));
    }

    /**
     * 公开的检查线路是否为环线的方法
     * @return 如果是环线则返回true
     */
    public boolean isCircular() {
        return isCircularInternal();
    }
} 