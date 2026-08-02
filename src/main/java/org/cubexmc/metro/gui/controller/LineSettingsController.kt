package org.cubexmc.metro.gui.controller

import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.gui.ChatInputManager
import org.cubexmc.metro.gui.GuiHolder
import org.cubexmc.metro.gui.GuiSlots
import org.cubexmc.metro.manager.LanguageManager
import org.cubexmc.metro.manager.RouteRecorder
import org.cubexmc.metro.model.Line
import org.cubexmc.metro.service.LineCommandService

class LineSettingsController(private val plugin: Metro) {

    private val lineService = LineCommandService(plugin.lineManager)
    private val permissionGuard = GuiPermissionGuard(plugin)

    fun handleClick(player: Player, holder: GuiHolder, slot: Int) {
        val lineId = holder.getData<String>("lineId")
        val line = if (lineId == null) null else plugin.lineManager.getLine(lineId)
        if (line == null) {
            player.closeInventory()
            return
        }

        val previousView = holder.getPreviousView()
        when (slot) {
            GuiSlots.LINE_SETTINGS_ROUTE_RECORDING -> {
                if (!permissionGuard.requireManageLine(player, line)) {
                    player.closeInventory()
                    return
                }
                handleRouteRecordingToggle(player, line)
                plugin.guiManager.openLineSettings(player, lineId, previousView)
            }

            GuiSlots.LINE_SETTINGS_ROUTE_INFO -> {
                sendRouteInfo(player, line)
                plugin.guiManager.openLineSettings(player, lineId, previousView)
            }

            GuiSlots.LINE_SETTINGS_CLEAR_ROUTE -> {
                if (!permissionGuard.requireManageLine(player, line)) {
                    player.closeInventory()
                    return
                }
                plugin.guiManager.openConfirmAction(
                    player,
                    "CLEAR_ROUTE",
                    lineId,
                    line.name,
                    null,
                    0,
                    holder.snapshot(),
                )
            }

            GuiSlots.LINE_SETTINGS_RAIL_PROTECTION -> {
                if (!permissionGuard.requireManageLine(player, line)) {
                    player.closeInventory()
                    return
                }
                toggleRailProtection(player, line)
                plugin.guiManager.openLineSettings(player, lineId, previousView)
            }

            GuiSlots.LINE_SETTINGS_RENAME -> requestLineRename(player, line, previousView)

            GuiSlots.LINE_SETTINGS_MAX_SPEED -> requestLineSpeed(player, line, previousView)

            GuiSlots.LINE_SETTINGS_TICKET_PRICE -> requestLinePrice(player, line, previousView)

            GuiSlots.LINE_SETTINGS_CLONE_REVERSE -> requestCloneReverse(player, line, previousView)

            GuiSlots.LINE_SETTINGS_DELETE -> {
                if (!permissionGuard.requireManageLine(player, line)) {
                    player.closeInventory()
                    return
                }
                plugin.guiManager.openConfirmAction(
                    player,
                    "DELETE_LINE",
                    lineId,
                    line.name,
                    null,
                    0,
                    holder.snapshot(),
                )
            }

            GuiSlots.LINE_SETTINGS_COLOR -> requestLineColor(player, line, previousView)

            GuiSlots.LINE_SETTINGS_TERMINUS -> requestLineTerminus(player, line, previousView)

            GuiSlots.LINE_SETTINGS_BACK ->
                plugin.guiManager.openPreviousView(player, holder) {
                    plugin.guiManager.openLineList(player, 0, false)
                }

            else -> Unit
        }
    }

    private fun toggleRailProtection(player: Player, line: Line) {
        val lineId = line.id
        val enabled = !line.isRailProtected
        if (lineService.setRailProtected(lineId, enabled) == LineCommandService.WriteStatus.SUCCESS) {
            val stateKey = if (enabled) "line.protect_state_enabled" else "line.protect_state_disabled"
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.protect_updated",
                    args("line_id", lineId, "state", plugin.languageManager.getMessage(stateKey)),
                ),
            )
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.protect_update_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
        }
    }

    private fun requestLineRename(player: Player, line: Line, previousView: GuiHolder.GuiView?) {
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory()
            return
        }
        val lineId = line.id
        val oldName = line.name
        requestInput(
            player,
            "chat.enter_new_name",
            lineId,
            previousView,
        ) { input ->
            if (requireCurrentLine(player, lineId) == null) {
                return@requestInput
            }
            if (lineService.renameLine(lineId, input) == LineCommandService.WriteStatus.SUCCESS) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.rename_success",
                        args("old_name", oldName, "line_id", lineId, "new_name", input),
                    ),
                )
            } else {
                player.sendMessage(plugin.languageManager.getMessage("line.rename_fail"))
            }
            plugin.guiManager.openLineSettings(player, lineId, previousView)
        }
    }

    private fun requestLineSpeed(player: Player, line: Line, previousView: GuiHolder.GuiView?) {
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory()
            return
        }
        val lineId = line.id
        requestInput(player, "chat.enter_new_speed", lineId, previousView) { input ->
            if (requireCurrentLine(player, lineId) == null) {
                return@requestInput
            }
            try {
                val speed = input.toDouble()
                val status = lineService.setMaxSpeed(lineId, speed)
                if (status == LineCommandService.WriteStatus.INVALID_VALUE) {
                    throw NumberFormatException()
                }
                if (status == LineCommandService.WriteStatus.SUCCESS) {
                    player.sendMessage(
                        plugin.languageManager.getMessage(
                            "line.setmaxspeed_success",
                            args("line_id", lineId, "max_speed", speed.toString()),
                        ),
                    )
                }
            } catch (_: NumberFormatException) {
                player.sendMessage(plugin.languageManager.getMessage("line.setmaxspeed_invalid"))
            }
            plugin.guiManager.openLineSettings(player, lineId, previousView)
        }
    }

    private fun requestLinePrice(player: Player, line: Line, previousView: GuiHolder.GuiView?) {
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory()
            return
        }
        val lineId = line.id
        requestInput(player, "chat.enter_new_price", lineId, previousView) { input ->
            val currentLine = requireCurrentLine(player, lineId) ?: return@requestInput
            try {
                val price = input.toDouble()
                val status = lineService.setTicketPrice(lineId, price)
                if (status == LineCommandService.WriteStatus.INVALID_VALUE) {
                    throw NumberFormatException()
                }
                if (status == LineCommandService.WriteStatus.SUCCESS) {
                    player.sendMessage(
                        plugin.languageManager.getMessage(
                            "line.setprice_success",
                            args("line_name", currentLine.name, "price", price.toString()),
                        ),
                    )
                } else {
                    player.sendMessage(plugin.languageManager.getMessage("line.setprice_fail"))
                }
            } catch (_: NumberFormatException) {
                player.sendMessage(plugin.languageManager.getMessage("line.setprice_invalid"))
            }
            plugin.guiManager.openLineSettings(player, lineId, previousView)
        }
    }

    private fun requestCloneReverse(player: Player, line: Line, previousView: GuiHolder.GuiView?) {
        if (!permissionGuard.requireManageLine(player, line) || !permissionGuard.requireCreateLine(player)) {
            player.closeInventory()
            return
        }
        val lineId = line.id
        requestInput(player, "chat.enter_clone_info", lineId, previousView) { input ->
            if (requireCurrentLine(player, lineId) == null || !permissionGuard.requireCreateLine(player)) {
                player.closeInventory()
                return@requestInput
            }
            val parts = input.split(" ")
            if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                val newId = parts[0]
                val stopIdSuffix = if (parts.size > 1) parts[1] else "_rev"
                val status = lineService.cloneReverseLine(lineId, newId, stopIdSuffix, player.uniqueId)
                if (status == LineCommandService.WriteStatus.SUCCESS) {
                    player.sendMessage(
                        plugin.languageManager.getMessage(
                            "line.clone_success",
                            LanguageManager.put(LanguageManager.args(), "new_line_id", newId),
                        ),
                    )
                } else {
                    player.sendMessage(plugin.languageManager.getMessage("line.clone_fail"))
                }
            }
            plugin.guiManager.openLineSettings(player, lineId, previousView)
        }
    }

    private fun requestLineColor(player: Player, line: Line, previousView: GuiHolder.GuiView?) {
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory()
            return
        }
        val lineId = line.id
        requestInput(player, "chat.enter_new_color", lineId, previousView) { input ->
            val currentLine = requireCurrentLine(player, lineId) ?: return@requestInput
            val color = input.trim()
            when (lineService.setColor(lineId, color)) {
                LineCommandService.WriteStatus.INVALID_COLOR ->
                    player.sendMessage(
                        plugin.languageManager.getMessage(
                            "line.setcolor_invalid",
                            LanguageManager.put(LanguageManager.args(), "color", color),
                        ),
                    )

                LineCommandService.WriteStatus.SUCCESS ->
                    player.sendMessage(
                        plugin.languageManager.getMessage(
                            "line.setcolor_success",
                            args("line_id", lineId, "line_name", currentLine.name, "color", color),
                        ),
                    )

                else -> player.sendMessage(plugin.languageManager.getMessage("line.setcolor_fail"))
            }
            plugin.guiManager.openLineSettings(player, lineId, previousView)
        }
    }

    private fun requestLineTerminus(player: Player, line: Line, previousView: GuiHolder.GuiView?) {
        if (!permissionGuard.requireManageLine(player, line)) {
            player.closeInventory()
            return
        }
        val lineId = line.id
        requestInput(player, "chat.enter_new_terminus", lineId, previousView) { input ->
            if (requireCurrentLine(player, lineId) == null) {
                return@requestInput
            }
            val terminusName = input.trim()
            if (lineService.setTerminusName(lineId, terminusName) == LineCommandService.WriteStatus.SUCCESS) {
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.setterminus_success",
                        args("line_id", lineId, "terminus_name", terminusName),
                    ),
                )
            } else {
                player.sendMessage(plugin.languageManager.getMessage("line.setterminus_fail"))
            }
            plugin.guiManager.openLineSettings(player, lineId, previousView)
        }
    }

    /**
     * Prompts for chat input; cancelling always returns to the line settings view, while each
     * caller decides when to reopen it, mirroring the per-action anonymous callbacks the Java
     * implementation used.
     */
    private fun requestInput(
        player: Player,
        promptKey: String,
        lineId: String,
        previousView: GuiHolder.GuiView?,
        onInput: (String) -> Unit,
    ) {
        plugin.chatInputManager.requestInput(
            player,
            plugin.languageManager.getMessage(promptKey),
            object : ChatInputManager.ChatInputCallback {
                override fun onInput(input: String) {
                    onInput.invoke(input)
                }

                override fun onCancel() {
                    plugin.guiManager.openLineSettings(player, lineId, previousView)
                }
            },
        )
    }

    private fun requireCurrentLine(player: Player, lineId: String): Line? {
        val currentLine = plugin.lineManager.getLine(lineId)
        if (currentLine == null) {
            player.closeInventory()
            return null
        }
        if (!permissionGuard.requireManageLine(player, currentLine)) {
            player.closeInventory()
            return null
        }
        return currentLine
    }

    private fun handleRouteRecordingToggle(player: Player, line: Line) {
        val lineId = line.id
        val recorder = plugin.routeRecorder
        if (recorder.isRecording(lineId)) {
            sendFinishResult(player, lineId, recorder.stopAndSave(lineId))
            return
        }

        if (recorder.start(lineId)) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.record_started",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
            player.sendMessage(plugin.languageManager.getMessage("line.record_hint"))
        } else {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "line.record_already",
                    LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                ),
            )
        }
    }

    private fun sendFinishResult(player: Player, lineId: String, result: RouteRecorder.FinishResult) {
        when (result.status()) {
            RouteRecorder.FinishResult.Status.SAVED ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.record_saved",
                        args("line_id", lineId, "point_count", result.pointCount().toString()),
                    ),
                )

            RouteRecorder.FinishResult.Status.TOO_FEW_POINTS -> sendRecordTooFew(player, result)

            RouteRecorder.FinishResult.Status.FAILED ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.record_failed",
                        LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                    ),
                )

            RouteRecorder.FinishResult.Status.NOT_RECORDING ->
                player.sendMessage(
                    plugin.languageManager.getMessage(
                        "line.record_not_recording",
                        LanguageManager.put(LanguageManager.args(), "line_id", lineId),
                    ),
                )
        }
    }

    private fun sendRouteInfo(player: Player, line: Line) {
        val lang = plugin.languageManager
        val recorder = plugin.routeRecorder
        player.sendMessage(
            lang.getMessage("line.routeinfo_header", args("line_name", line.name, "line_id", line.id)),
        )
        player.sendMessage(
            lang.getMessage(
                "line.routeinfo_saved_points",
                LanguageManager.put(LanguageManager.args(), "point_count", line.routePoints.size.toString()),
            ),
        )
        val protectionStateKey =
            if (line.isRailProtected) "line.protect_state_enabled" else "line.protect_state_disabled"
        player.sendMessage(
            lang.getMessage(
                "line.protect_status",
                LanguageManager.put(LanguageManager.args(), "state", lang.getMessage(protectionStateKey)),
            ),
        )
        val protectedBlocks = plugin.railProtectionManager?.getProtectedBlockCount(line.id) ?: 0
        player.sendMessage(
            lang.getMessage(
                "line.protect_blocks",
                LanguageManager.put(LanguageManager.args(), "count", protectedBlocks.toString()),
            ),
        )
        if (line.isRailProtected && protectedBlocks == 0) {
            player.sendMessage(lang.getMessage("line.protect_no_blocks"))
        }
        sendRecordingState(player, line, recorder)
    }

    private fun sendRecordingState(player: Player, line: Line, recorder: RouteRecorder) {
        val lang = plugin.languageManager
        if (!recorder.isRecording(line.id)) {
            player.sendMessage(
                lang.getMessage(
                    "line.routeinfo_recording",
                    LanguageManager.put(
                        LanguageManager.args(),
                        "state",
                        lang.getMessage("line.routeinfo_recording_inactive"),
                    ),
                ),
            )
            return
        }

        val cartId = recorder.getRecordingCartId(line.id)
        player.sendMessage(
            lang.getMessage(
                "line.routeinfo_recording",
                LanguageManager.put(
                    LanguageManager.args(),
                    "state",
                    lang.getMessage("line.routeinfo_recording_active"),
                ),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "line.routeinfo_buffered_points",
                LanguageManager.put(
                    LanguageManager.args(),
                    "point_count",
                    recorder.getActivePointCount(line.id).toString(),
                ),
            ),
        )
        player.sendMessage(
            lang.getMessage(
                "line.routeinfo_bound_cart",
                LanguageManager.put(
                    LanguageManager.args(),
                    "cart_id",
                    cartId?.toString() ?: lang.getMessage("line.routeinfo_waiting_cart"),
                ),
            ),
        )
    }

    private fun args(vararg replacements: Any?): MutableMap<String, Any?> {
        val args = LanguageManager.args()
        var index = 0
        while (index < replacements.size - 1) {
            LanguageManager.put(args, replacements[index].toString(), replacements[index + 1])
            index += 2
        }
        return args
    }

    private fun sendRecordTooFew(player: Player, result: RouteRecorder.FinishResult) {
        player.sendMessage(
            plugin.languageManager.getMessage(
                "line.record_too_few",
                LanguageManager.put(LanguageManager.args(), "point_count", result.pointCount().toString()),
            ),
        )
        player.sendMessage(plugin.languageManager.getMessage("line.record_too_few_hint"))
    }
}
