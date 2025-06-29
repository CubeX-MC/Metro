package org.cubexmc.metro.util;

import java.util.List;

import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Note;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 声音工具类，用于处理音符序列的播放
 */
public class SoundUtil {

    /**
     * 为指定玩家播放音符序列
     *
     * @param plugin       插件实例
     * @param player       目标玩家
     * @param noteSequence 音符序列
     */
    public static void playNoteSequence(JavaPlugin plugin, Player player, List<String> noteSequence) {
        playNoteSequence(plugin, player, noteSequence, 0);
    }

    /**
     * 为指定玩家播放音符序列，带初始延迟
     *
     * @param plugin       插件实例
     * @param player       目标玩家
     * @param noteSequence 音符序列
     * @param initialDelay 整个音符序列的初始延迟（ticks）
     */
    public static void playNoteSequence(JavaPlugin plugin, Player player, List<String> noteSequence, int initialDelay) {
        if (player == null || !player.isOnline() || noteSequence == null || noteSequence.isEmpty()) {
            return;
        }
        // For player-specific sounds, the sound location for scheduling is the player's current location.
        // The actual sound playing action will also use the player's location at the moment of execution.
        scheduleNoteLogic(plugin, noteSequence, initialDelay, player.getLocation(), (soundPlayerLocation, noteParams) -> {
            // noteParams: type, tone, volume, instrumentName
            String type = noteParams[0];
            int tone = Integer.parseInt(noteParams[1]);
            float volume = Float.parseFloat(noteParams[2]);
            String instrumentName = noteParams[3];

            if ("NOTE".equals(type)) {
                playNote(player, tone, volume, instrumentName);
            } else if ("CUSTOM".equals(type)) {
                player.playSound(player.getLocation(), instrumentName, volume, getNoteFrequency(tone));
            }
        });
    }

    /**
     * 为特定位置播放音符序列（所有附近的玩家都能听到）
     *
     * @param plugin       插件实例
     * @param location     播放位置
     * @param noteSequence 音符序列
     */
    public static void playNoteSequenceAtLocation(JavaPlugin plugin, Location location, List<String> noteSequence) {
        playNoteSequenceAtLocation(plugin, location, noteSequence, 0);
    }

    /**
     * 为特定位置播放音符序列（所有附近的玩家都能听到），带初始延迟
     *
     * @param plugin       插件实例
     * @param location     播放位置
     * @param noteSequence 音符序列
     * @param initialDelay 整个音符序列的初始延迟（ticks）
     */
    public static void playNoteSequenceAtLocation(JavaPlugin plugin, Location location, List<String> noteSequence, int initialDelay) {
        if (location == null || location.getWorld() == null || noteSequence == null || noteSequence.isEmpty()) {
            return;
        }
        scheduleNoteLogic(plugin, noteSequence, initialDelay, location, (soundPlayLocation, noteParams) -> {
            // noteParams: type, tone, volume, instrumentName
            String type = noteParams[0];
            int tone = Integer.parseInt(noteParams[1]);
            float volume = Float.parseFloat(noteParams[2]);
            String instrumentName = noteParams[3];

            if ("NOTE".equals(type)) {
                playNoteAtLocation(soundPlayLocation, tone, volume, instrumentName);
            } else if ("CUSTOM".equals(type)) {
                soundPlayLocation.getWorld().playSound(soundPlayLocation, instrumentName, volume, getNoteFrequency(tone));
            }
        });
    }

    /**
     * 核心逻辑，用于解析音符序列并调度播放任务
     *
     * @param plugin        插件实例
     * @param noteSequence  音符序列
     * @param initialDelay  初始延迟 (ticks)
     * @param scheduleLoc   用于 SchedulerUtil.regionRun 的位置
     * @param playFunction  实际执行播放的函数，接收播放位置和音符参数 (type, tone, volume, instrumentName)
     */
    private static void scheduleNoteLogic(JavaPlugin plugin, List<String> noteSequence, int initialDelay,
                                          Location scheduleLoc, BiConsumer<Location, String[]> playFunction) {
        long totalDelay = initialDelay;

        for (String noteData : noteSequence) {
            String[] parts = noteData.split(",");
            if (parts.length < 4) {
                plugin.getLogger().warning("Invalid note data format, skipping: " + noteData);
                continue;
            }

            String type = parts[0].trim();
            String toneStr = parts[1].trim();
            String volumeStr = parts[2].trim();
            String instrumentName = parts[3].trim();
            int noteSpecificDelay = (parts.length > 4) ? Integer.parseInt(parts[4].trim()) : 0;

            totalDelay += noteSpecificDelay;

            // Final parameters for the lambda
            final String[] noteParams = new String[]{type, toneStr, volumeStr, instrumentName};
            final long currentTotalDelay = totalDelay; // Effectively final for lambda

            try {
                // Validate numeric parts before scheduling to avoid errors in the scheduled task
                Integer.parseInt(toneStr);
                Float.parseFloat(volumeStr);

                SchedulerUtil.regionRun(plugin, scheduleLoc, () -> {
                    playFunction.accept(scheduleLoc, noteParams);
                }, currentTotalDelay, -1);

            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid number format in note data, skipping: " + noteData + " - " + e.getMessage());
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