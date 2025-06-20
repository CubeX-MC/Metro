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
        if (index == -1 || index == orderedStopIds.size() - 1) {
            return null;
        }
        return orderedStopIds.get(index + 1);
    }
    
    /**
     * 获取指定停靠区的上一个停靠区ID
     * 
     * @param currentStopId 当前停靠区ID
     * @return 上一个停靠区ID，如果当前是起点站或不存在，则返回null
     */
    public String getPreviousStopId(String currentStopId) {
        int index = orderedStopIds.indexOf(currentStopId);
        if (index <= 0) {
            return null;
        }
        return orderedStopIds.get(index - 1);
    }
} 