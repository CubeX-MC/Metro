package org.cubexmc.metro.util;

import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Note;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * 声音工具类，用于处理音符序列的播放
 */
public class SoundUtil {
    
    /**
     * 为指定玩家播放音符序列
     * 
     * @param plugin 插件实例
     * @param player 目标玩家
     * @param noteSequence 音符序列
     */
    public static void playNoteSequence(JavaPlugin plugin, Player player, List<String> noteSequence) {
        playNoteSequence(plugin, player, noteSequence, 0);
    }
    
    /**
     * 为指定玩家播放音符序列，带初始延迟
     * 
     * @param plugin 插件实例
     * @param player 目标玩家
     * @param noteSequence 音符序列
     * @param initialDelay 整个音符序列的初始延迟（ticks）
     */
    public static void playNoteSequence(JavaPlugin plugin, Player player, List<String> noteSequence, int initialDelay) {
        if (player == null || !player.isOnline() || noteSequence == null || noteSequence.isEmpty()) {
            return;
        }
        
        long totalDelay = initialDelay; // 加入初始延迟
        
        for (String noteData : noteSequence) {
            String[] parts = noteData.split(",");
            if (parts.length < 4) {
                continue; // 跳过格式不正确的音符
            }
            
            String type = parts[0].trim();
            
            try {
                int tone = Integer.parseInt(parts[1].trim());
                float volume = Float.parseFloat(parts[2].trim());
                String instrumentName = parts[3].trim();
                
                // 获取延迟时间（如果提供）
                int delay = (parts.length > 4) ? Integer.parseInt(parts[4].trim()) : 0;
                totalDelay += delay;
                
                // 使用匿名内部类创建延迟任务
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            if ("NOTE".equals(type)) {
                                playNote(player, tone, volume, instrumentName);
                            } else if ("CUSTOM".equals(type)) {
                                // 自定义声音的播放逻辑，如果需要
                                player.playSound(player.getLocation(), instrumentName, volume, getNoteFrequency(tone));
                            }
                        }
                    }
                }.runTaskLater(plugin, totalDelay);
            } catch (NumberFormatException e) {
                // 忽略格式不正确的音符
            }
        }
    }
    
    /**
     * 为特定位置播放音符序列（所有附近的玩家都能听到）
     * 
     * @param plugin 插件实例
     * @param location 播放位置
     * @param noteSequence 音符序列
     */
    public static void playNoteSequenceAtLocation(JavaPlugin plugin, Location location, List<String> noteSequence) {
        playNoteSequenceAtLocation(plugin, location, noteSequence, 0);
    }
    
    /**
     * 为特定位置播放音符序列（所有附近的玩家都能听到），带初始延迟
     * 
     * @param plugin 插件实例
     * @param location 播放位置
     * @param noteSequence 音符序列
     * @param initialDelay 整个音符序列的初始延迟（ticks）
     */
    public static void playNoteSequenceAtLocation(JavaPlugin plugin, Location location, List<String> noteSequence, int initialDelay) {
        if (location == null || location.getWorld() == null || noteSequence == null || noteSequence.isEmpty()) {
            return;
        }
        
        long totalDelay = initialDelay; // 加入初始延迟
        
        for (String noteData : noteSequence) {
            String[] parts = noteData.split(",");
            if (parts.length < 4) {
                continue; // 跳过格式不正确的音符
            }
            
            String type = parts[0].trim();
            
            try {
                int tone = Integer.parseInt(parts[1].trim());
                float volume = Float.parseFloat(parts[2].trim());
                String instrumentName = parts[3].trim();
                
                // 获取延迟时间（如果提供）
                int delay = (parts.length > 4) ? Integer.parseInt(parts[4].trim()) : 0;
                totalDelay += delay;
                
                // 使用匿名内部类创建延迟任务
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if ("NOTE".equals(type)) {
                            playNoteAtLocation(location, tone, volume, instrumentName);
                        } else if ("CUSTOM".equals(type)) {
                            // 自定义声音的播放逻辑，如果需要
                            location.getWorld().playSound(location, instrumentName, volume, getNoteFrequency(tone));
                        }
                    }
                }.runTaskLater(plugin, totalDelay);
            } catch (NumberFormatException e) {
                // 忽略格式不正确的音符
            }
        }
    }
    
    /**
     * 为玩家播放单个音符
     */
    private static void playNote(Player player, int tone, float volume, String instrumentName) {
        Instrument instrument = getInstrument(instrumentName);
        Note note = getNote(tone);
        
        if (instrument != null && note != null) {
            player.playNote(player.getLocation(), instrument, note);
        }
    }
    
    /**
     * 在指定位置播放单个音符
     */
    private static void playNoteAtLocation(Location location, int tone, float volume, String instrumentName) {
        Instrument instrument = getInstrument(instrumentName);
        Note note = getNote(tone);
        
        if (instrument != null && note != null && location.getWorld() != null) {
            location.getWorld().playNote(location, instrument, note);
        }
    }
    
    /**
     * 获取音符盒乐器类型
     */
    private static Instrument getInstrument(String name) {
        try {
            return Instrument.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Instrument.PIANO; // 默认为钢琴
        }
    }
    
    /**
     * 根据音高获取音符
     */
    private static Note getNote(int tone) {
        try {
            // 确保音高在有效范围内
            tone = Math.max(0, Math.min(tone, 24));
            return new Note(tone);
        } catch (IllegalArgumentException e) {
            return new Note(12); // 默认为中音C
        }
    }
    
    /**
     * 根据音高获取频率（用于自定义声音）
     */
    private static float getNoteFrequency(int tone) {
        // 将音高转换为频率因子（0-24 -> 0.5-2.0）
        return (float) Math.pow(2.0, (tone - 12) / 12.0);
    }
} 