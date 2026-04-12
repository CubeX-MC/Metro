package org.cubexmc.metro.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.md_5.bungee.api.ChatColor;

/**
 * 颜色解析工具类
 */
public class ColorUtil {
    
    // 匹配 &#RRGGBB 格式的十六进制颜色代码
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * 将包含颜色代码的字符串转换为带颜色的文本
     * 支持传统的 &0-&f 以及 &#RRGGBB 的十六进制颜色
     *
     * @param text 原始字符串
     * @return 转换颜色后的字符串
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = "#" + matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of(hex).toString());
        }
        matcher.appendTail(buffer);

        // 处理传统的 & 颜色代码
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
