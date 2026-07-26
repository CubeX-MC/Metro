package org.cubexmc.metro.util

import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.model.Stop

/**
 * 文本工具类，提供文本处理相关功能
 */
object TextUtil {
    @JvmStatic
    fun replacePlaceholders(
        text: String?,
        line: Line?,
        stop: Stop?,
        lastStop: Stop?,
        nextStop: Stop?,
        terminalStop: Stop?,
        lineManager: LineManager?,
    ): String = replacePlaceholders(text, line, stop, lastStop, nextStop, terminalStop, lineManager, null)

    /**
     * @param nextStopDistanceBlocks remaining distance to [nextStop] in blocks,
     *   or `null` when it is unknown (e.g. while docked). Unknown distances
     *   render as an empty string.
     */
    @JvmStatic
    fun replacePlaceholders(
        text: String?,
        line: Line?,
        stop: Stop?,
        lastStop: Stop?,
        nextStop: Stop?,
        terminalStop: Stop?,
        lineManager: LineManager?,
        nextStopDistanceBlocks: Double?,
    ): String {
        if (text == null) {
            return ""
        }

        var result = text
        result = result.replace("{next_stop_distance}", formatDistance(nextStopDistanceBlocks))

        if (line != null) {
            result = result.replace("{line}", line.name)
            result = result.replace("{line_id}", line.id)
            result = result.replace("{line_color_code}", ColorUtil.colorizeOrEmpty(line.color))

            var termName = line.terminusName
            if (line.isCircular) {
                if (termName.isNullOrEmpty()) {
                    termName = nextStop?.name ?: ""
                }
            } else {
                if (termName.isNullOrEmpty()) {
                    termName = terminalStop?.name ?: ""
                }
            }
            result = result.replace("{terminus_name}", termName ?: "")

            if (line.orderedStopIds.isNotEmpty()) {
                val destStopId = line.orderedStopIds[line.orderedStopIds.size - 1]
                result = result.replace("{destination_stop_id}", destStopId)
            } else {
                result = result.replace("{destination_stop_id}", "")
            }
        }

        if (stop != null) {
            result = result.replace("{stop_name}", stop.name)
            result = result.replace("{stop_id}", stop.id)
            result = result.replace("{stop_transfers}", if (lineManager != null) formatTransferableLines(stop, lineManager) else "")
        }

        if (lastStop != null) {
            result = result.replace("{last_stop_name}", lastStop.name)
            result = result.replace("{last_stop_id}", lastStop.id)
        } else {
            result = result.replace("{last_stop_name}", "")
            result = result.replace("{last_stop_id}", "")
        }

        if (nextStop != null) {
            result = result.replace("{next_stop_name}", nextStop.name)
            result = result.replace("{next_stop_id}", nextStop.id)
            result = result.replace("{next_stop_transfers}", if (lineManager != null) formatTransferableLines(nextStop, lineManager) else "")
        } else {
            result = result.replace("{next_stop_name}", "")
            result = result.replace("{next_stop_id}", "")
            result = result.replace("{next_stop_transfers}", "")
        }

        if (terminalStop != null) {
            result = result.replace("{terminal_stop_name}", terminalStop.name)
            result = result.replace("{terminal_stop_id}", terminalStop.id)
            result = result.replace("{destination_stop_name}", terminalStop.name)
        } else {
            result = result.replace("{terminal_stop_name}", "")
            result = result.replace("{terminal_stop_id}", "")
            result = result.replace("{destination_stop_name}", "")
        }

        return result
    }

    private fun formatDistance(distanceBlocks: Double?): String {
        if (distanceBlocks == null || distanceBlocks.isNaN() || distanceBlocks < 0.0) {
            return ""
        }
        return kotlin.math.round(distanceBlocks).toInt().toString()
    }

    private fun formatTransferableLines(stop: Stop?, lineManager: LineManager?): String {
        if (stop == null || lineManager == null) {
            return ""
        }

        val transferableLineIds = stop.transferableLines ?: return ""
        if (transferableLineIds.isEmpty()) {
            return ""
        }

        val result = StringBuilder()
        var first = true
        for (lineId in transferableLineIds) {
            val transferLine = lineManager.getLine(lineId)
            if (transferLine != null) {
                if (!first) {
                    result.append("§f, ")
                }
                result.append(ColorUtil.colorizeOrEmpty(transferLine.color))
                    .append(transferLine.name)
                first = false
            }
        }
        return result.toString()
    }
}
