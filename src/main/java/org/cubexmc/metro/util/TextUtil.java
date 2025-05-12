package org.cubexmc.metro.util;

import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;

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
     * @return 替换后的文本
     */
    public static String replacePlaceholders(String text, Line line, Stop stop, Stop lastStop, Stop nextStop, Stop terminalStop) {
        if (text == null) {
            return "";
        }
        
        String result = text;
        
        // 替换线路相关占位符
        if (line != null) {
            result = result.replace("{line}", line.getName());
            result = result.replace("{line_id}", line.getId());
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
        } else {
            result = result.replace("{next_stop_name}", "");
            result = result.replace("{next_stop_id}", "");
        }
        
        // 替换终点站占位符
        if (terminalStop != null) {
            result = result.replace("{terminal_stop_name}", terminalStop.getName());
            result = result.replace("{terminal_stop_id}", terminalStop.getId());
        } else {
            result = result.replace("{terminal_stop_name}", "");
            result = result.replace("{terminal_stop_id}", "");
        }
        
        return result;
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
        return replacePlaceholders(text, line, stop, null, null, null);
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
        return replacePlaceholders(text, line, stop, lastStop, nextStop, null);
    }
} 