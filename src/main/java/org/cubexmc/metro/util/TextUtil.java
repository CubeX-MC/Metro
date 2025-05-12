package org.cubexmc.metro.util;

import org.bukkit.ChatColor;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

import java.util.List;

/**
 * 文本工具类，提供文本处理相关功能
 */
public class TextUtil {
    
    /**
     * 替换文本中的占位符
     * 
     * @param text 原始文本
     * @param line 线路对象，可为null
     * @param stop 停靠区对象，可为null
     * @param lastStop 上一个停靠区对象，可为null（起始站时为null）
     * @param nextStop 下一个停靠区对象，可为null（终点站时为null）
     * @param terminalStop 线路终点站对象，可为null
     * @param lineManager 线路管理器，用于获取可换乘线路信息，可为null
     * @return 替换后的文本
     */
    public static String replacePlaceholders(String text, Line line, Stop stop, Stop lastStop, Stop nextStop, 
                                           Stop terminalStop, LineManager lineManager) {
        if (text == null) {
            return "";
        }
        
        String result = text;
        
        // 替换线路相关占位符
        if (line != null) {
            result = result.replace("{line}", line.getName());
            result = result.replace("{line_id}", line.getId());
            result = result.replace("{line_color_code}", line.getColor());
            result = result.replace("{terminus_name}", line.getTerminusName());
            
            // 目的地站点（线路终点）
            if (!line.getOrderedStopIds().isEmpty()) {
                String destStopId = line.getOrderedStopIds().get(line.getOrderedStopIds().size() - 1);
                result = result.replace("{destination_stop_id}", destStopId);
            } else {
                result = result.replace("{destination_stop_id}", "");
            }
        }
        
        // 替换停靠区相关占位符
        if (stop != null) {
            result = result.replace("{stop_name}", stop.getName());
            result = result.replace("{stop_id}", stop.getId());
        }
        
        // 替换上一站占位符
        if (lastStop != null) {
            result = result.replace("{last_stop_name}", lastStop.getName());
            result = result.replace("{last_stop_id}", lastStop.getId());
        } else {
            result = result.replace("{last_stop_name}", "");
            result = result.replace("{last_stop_id}", "");
        }
        
        // 替换下一站占位符
        if (nextStop != null) {
            result = result.replace("{next_stop_name}", nextStop.getName());
            result = result.replace("{next_stop_id}", nextStop.getId());
            
            // 如果有提供LineManager，生成下一站可换乘线路信息
            if (lineManager != null) {
                String transferLines = formatTransferableLines(nextStop, lineManager);
                result = result.replace("{transfer_lines}", transferLines);
            }
        } else {
            result = result.replace("{next_stop_name}", "");
            result = result.replace("{next_stop_id}", "");
            result = result.replace("{transfer_lines}", "");
        }
        
        // 替换终点站占位符
        if (terminalStop != null) {
            result = result.replace("{terminal_stop_name}", terminalStop.getName());
            result = result.replace("{terminal_stop_id}", terminalStop.getId());
            result = result.replace("{destination_stop_name}", terminalStop.getName());
        } else {
            result = result.replace("{terminal_stop_name}", "");
            result = result.replace("{terminal_stop_id}", "");
            result = result.replace("{destination_stop_name}", "");
        }
        
        return result;
    }
    
    /**
     * 格式化站点可换乘线路信息
     * 
     * @param stop 停靠区对象
     * @param lineManager 线路管理器
     * @return 格式化后的可换乘线路文本
     */
    private static String formatTransferableLines(Stop stop, LineManager lineManager) {
        if (stop == null || lineManager == null) {
            return "";
        }
        
        List<String> transferableLineIds = stop.getTransferableLines();
        if (transferableLineIds.isEmpty()) {
            return "无";
        }
        
        StringBuilder result = new StringBuilder();
        boolean first = true;
        
        for (String lineId : transferableLineIds) {
            Line transferLine = lineManager.getLine(lineId);
            if (transferLine != null) {
                if (!first) {
                    result.append("§f, ");
                }
                result.append(transferLine.getColor())
                      .append(transferLine.getName());
                first = false;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 替换文本中的占位符（简化版本，不含前后站和终点站信息）
     * 
     * @param text 原始文本
     * @param line 线路对象，可为null
     * @param stop 停靠区对象，可为null
     * @return 替换后的文本
     */
    public static String replacePlaceholders(String text, Line line, Stop stop) {
        return replacePlaceholders(text, line, stop, null, null, null, null);
    }
    
    /**
     * 替换文本中的占位符（含前后站信息，不含终点站信息）
     * 
     * @param text 原始文本
     * @param line 线路对象，可为null
     * @param stop 停靠区对象，可为null
     * @param lastStop 上一个停靠区对象，可为null
     * @param nextStop 下一个停靠区对象，可为null
     * @return 替换后的文本
     */
    public static String replacePlaceholders(String text, Line line, Stop stop, Stop lastStop, Stop nextStop) {
        return replacePlaceholders(text, line, stop, lastStop, nextStop, null, null);
    }
    
    /**
     * 替换文本中的占位符（旧版本兼容方法）
     * 
     * @param text 原始文本
     * @param line 线路对象，可为null
     * @param stop 停靠区对象，可为null
     * @param lastStop 上一个停靠区对象，可为null
     * @param nextStop 下一个停靠区对象，可为null
     * @param terminalStop 线路终点站对象，可为null
     * @return 替换后的文本
     */
    public static String replacePlaceholders(String text, Line line, Stop stop, Stop lastStop, Stop nextStop, Stop terminalStop) {
        return replacePlaceholders(text, line, stop, lastStop, nextStop, terminalStop, null);
    }
} 