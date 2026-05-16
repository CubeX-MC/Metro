package org.cubexmc.metro.command.newcmd;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotation.specifier.Greedy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.manager.LanguageManager;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.RouteRecorder;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Portal;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.service.CommandDisplayService;
import org.cubexmc.metro.service.LineCommandService;
import org.cubexmc.metro.util.OwnershipUtil;

public class LineCommand {

    private final Metro plugin;
    private final LineManager lineManager;
    private final CommandGuard guard;
    private final LineCommandService lineService;
    private final LineCommandView view;

    public LineCommand(Metro plugin, LineManager lineManager, StopManager stopManager) {
        this.plugin = plugin;
        this.lineManager = lineManager;
        this.guard = new CommandGuard(plugin, lineManager, stopManager);
        this.lineService = new LineCommandService(lineManager);
        this.view = new LineCommandView(plugin, stopManager, guard, new CommandDisplayService());
    }

    @Command("m|metro line|l")
    @CommandDescription("Show Line Help Menu")
    public void help(CommandSender sender) {
        view.showHelp(sender, 1);
    }

    @Command("m|metro line|l help [page]")
    @CommandDescription("Show Line Help Menu Page")
    public void helpPage(CommandSender sender, @Argument(value = "page", suggestions = "pageNumbers") Integer page) {
        view.showHelp(sender, page);
    }

    @Command("m|metro line|l list [page]")
    @CommandDescription("List all metro lines")
    public void list(CommandSender sender, @Argument(value = "page", suggestions = "pageNumbers") Integer page) {
        view.listLines(sender, lineService.listLines(), page);
    }

    @Command("m|metro line|l create <id> <name>")
    @CommandDescription("Create a new metro line")
    public void create(Player player, @Argument("id") String id, @Greedy @Argument("name") String name) {
        if (!OwnershipUtil.canCreateLine(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_create"));
            return;
        }

        LineCommandService.WriteStatus status = lineService.createLine(id, name, player.getUniqueId());
        switch (status) {
            case SUCCESS -> player.sendMessage(plugin.getLanguageManager().getMessage("line.create_success",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            case INVALID_ID -> player.sendMessage(plugin.getLanguageManager().getMessage("line.id_invalid",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            case EXISTS -> player.sendMessage(plugin.getLanguageManager().getMessage("line.create_exists",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
            default -> player.sendMessage(plugin.getLanguageManager().getMessage("line.create_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
        }
    }

    @Command("m|metro line|l delete <lineId> [confirm]")
    @CommandDescription("Delete a metro line")
    public void delete(Player player,
                       @Argument(value = "lineId", suggestions = "lineIds") String id,
                       @Argument("confirm") String confirm) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }
        if (!guard.requireConfirmation(player, confirm, "/m line delete " + id + " confirm")) {
            return;
        }

        if (lineService.deleteLine(id) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_success",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delete_fail"));
        }
    }

    @Command("m|metro line|l rename <lineId> <name>")
    @CommandDescription("Rename a metro line")
    public void rename(Player player, @Argument(value = "lineId", suggestions = "lineIds") String id, @Greedy @Argument("name") String name) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        if (lineService.renameLine(id, name) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", id), "new_name", name)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.rename_fail"));
        }
    }

    @Command("m|metro line|l setcolor <lineId> <color>")
    @CommandDescription("Set the color of a metro line")
    public void setColor(Player player,
                         @Argument(value = "lineId", suggestions = "lineIds") String id,
                         @Argument(value = "color", suggestions = "lineColors") String color) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        LineCommandService.WriteStatus status = lineService.setColor(id, color);
        if (status == LineCommandService.WriteStatus.INVALID_COLOR) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_invalid",
                    LanguageManager.put(LanguageManager.args(), "color", color)));
            return;
        }
        if (status == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", line.getId()), "line_name", line.getName()), "color", color)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setcolor_fail"));
        }
    }

    @Command("m|metro line|l setterminus <lineId> <terminus>")
    @CommandDescription("Set terminus name for a line")
    public void setTerminus(Player player, @Argument(value = "lineId", suggestions = "lineIds") String id, @Greedy @Argument("terminus") String terminus) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        if (lineService.setTerminusName(id, terminus) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setterminus_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "terminus_name", terminus)));
        }
    }

    @Command("m|metro line|l setmaxspeed <lineId> <speed>")
    @CommandDescription("Set max speed for a line")
    public void setMaxSpeed(Player player,
                            @Argument(value = "lineId", suggestions = "lineIds") String id,
                            @Argument(value = "speed", suggestions = "speedValues") double speed) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }
        LineCommandService.WriteStatus status = lineService.setMaxSpeed(id, speed);
        if (status == LineCommandService.WriteStatus.INVALID_VALUE) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_invalid"));
            return;
        }
        if (status == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setmaxspeed_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "max_speed", String.valueOf(speed))));
        }
    }

    @Command("m|metro line|l addstop <lineId> <stopId> [index]")
    @CommandDescription("Add a stop to a line")
    public void addStop(Player player,
                        @Argument(value = "lineId", suggestions = "lineIds") String lineId,
                        @Argument(value = "stopId", suggestions = "stopIds") String stopId,
                        @Argument(value = "index", suggestions = "stopIndexes") Integer index) {
        Line line = guard.requireManageableLine(player, lineId);
        if (line == null) {
            return;
        }

        Stop stop = guard.requireStop(player, stopId);
        if (stop == null) {
            return;
        }

        if (!guard.canModifyLineStops(player, line, stop)) {
            return;
        }

        LineCommandService.AddStopResult result = lineService.addStopToLine(line, stop, index);
        switch (result.status()) {
            case SUCCESS -> player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stopId), "line_id", line.getId())));
            case STOP_NO_WORLD -> player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_stop_no_world",
                    LanguageManager.put(LanguageManager.args(), "stop_id", stopId)));
            case WORLD_MISMATCH -> player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_world_mismatch",
                    LanguageManager.put(LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_id", lineId), "line_world", result.lineWorld()), "stop_world", result.stopWorld())));
            case CIRCULAR_INVALID_INDEX -> player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_circular_invalid_index"));
            default -> player.sendMessage(plugin.getLanguageManager().getMessage("line.addstop_fail"));
        }
    }

    @Command("m|metro line|l delstop <lineId> <stopId>")
    @CommandDescription("Remove a stop from a line")
    public void delStop(Player player, @Argument(value = "lineId", suggestions = "lineIds") String lineId, @Argument(value = "stopId", suggestions = "stopIds") String stopId) {
        Line line = guard.requireManageableLine(player, lineId);
        if (line == null) {
            return;
        }

        if (lineService.removeStopFromLine(line, stopId) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "stop_id", stopId), "line_id", line.getId())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.delstop_fail"));
        }
    }

    @Command("m|metro line|l addportal <lineId> <portalId>")
    @CommandDescription("Allow a line to use a portal")
    public void addPortal(Player player,
                          @Argument(value = "lineId", suggestions = "lineIds") String lineId,
                          @Argument(value = "portalId", suggestions = "portalIds") String portalId) {
        Line line = guard.requireManageableLine(player, lineId);
        if (line == null) {
            return;
        }

        Portal portal = plugin.getPortalManager() != null ? plugin.getPortalManager().getPortal(portalId) : null;
        if (portal == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("portal.not_found",
                    LanguageManager.put(LanguageManager.args(), "portal_id", portalId)));
            return;
        }

        LineCommandService.WriteStatus status = lineService.addPortalToLine(line, portal);
        if (status == LineCommandService.WriteStatus.EXISTS) {
            player.sendMessage(msg("line.addportal_exists", "portal_id", portalId, "line_id", line.getId()));
        } else if (status == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(msg("line.addportal_success", "portal_id", portalId, "line_id", line.getId()));
        } else {
            player.sendMessage(msg("line.addportal_fail", "portal_id", portalId, "line_id", line.getId()));
        }
    }

    @Command("m|metro line|l delportal <lineId> <portalId>")
    @CommandDescription("Remove a portal from a line")
    public void delPortal(Player player,
                          @Argument(value = "lineId", suggestions = "lineIds") String lineId,
                          @Argument(value = "portalId", suggestions = "portalIds") String portalId) {
        Line line = guard.requireManageableLine(player, lineId);
        if (line == null) {
            return;
        }

        LineCommandService.WriteStatus status = lineService.removePortalFromLine(line, portalId);
        if (status == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(msg("line.delportal_success", "portal_id", portalId, "line_id", line.getId()));
        } else if (status == LineCommandService.WriteStatus.NOT_FOUND) {
            player.sendMessage(msg("line.delportal_missing", "portal_id", portalId, "line_id", line.getId()));
        } else {
            player.sendMessage(msg("line.delportal_fail", "portal_id", portalId, "line_id", line.getId()));
        }
    }

    @Command("m|metro line|l portals <lineId> [page]")
    @CommandDescription("List portals enabled for a line")
    public void portals(Player player, @Argument(value = "lineId", suggestions = "lineIds") String id,
                        @Argument(value = "page", suggestions = "pageNumbers") Integer page) {
        Line line = guard.requireLine(player, id);
        if (line == null) {
            return;
        }

        view.sendPortals(player, line, page);
    }

    @Command("m|metro line|l stops <lineId> [page]")
    @CommandDescription("List all stops in line")
    public void stops(Player player, @Argument(value = "lineId", suggestions = "lineIds") String id,
                      @Argument(value = "page", suggestions = "pageNumbers") Integer page) {
        Line line = guard.requireLine(player, id);
        if (line == null) {
            return;
        }

        view.sendStops(player, line, page);
    }

    @Command("m|metro line|l info <lineId>")
    @CommandDescription("Show line details")
    public void info(Player player, @Argument(value = "lineId", suggestions = "lineIds") String id) {
        Line line = guard.requireLine(player, id);
        if (line == null) {
            return;
        }

        view.sendInfo(player, line);
    }

    @Command("m|metro line|l protect <lineId> <mode>")
    @CommandDescription("Enable, disable, or inspect rail protection for a line")
    public void protectRoute(Player player,
                             @Argument(value = "lineId", suggestions = "lineIds") String id,
                             @Argument(value = "mode", suggestions = "protectModes") String mode) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        String normalizedMode = mode.toLowerCase(java.util.Locale.ROOT);
        if ("status".equals(normalizedMode)) {
            view.sendProtectionStatus(player, line);
            return;
        }

        Boolean enabled = parseToggle(normalizedMode);
        if (enabled == null) {
            player.sendMessage(msg("line.usage_protect"));
            return;
        }
        if (lineService.setRailProtected(id, enabled) != LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(msg("line.protect_update_fail", "line_id", id));
            return;
        }

        Line updatedLine = lineManager.getLine(id);
        player.sendMessage(msg("line.protect_updated",
                "line_id", id,
                "state", msg(enabled ? "line.protect_state_enabled" : "line.protect_state_disabled")));
        if (updatedLine != null) {
            view.sendProtectionStatus(player, updatedLine);
        }
    }

    @Command("m|metro line|l recordroute <lineId>")
    @CommandDescription("Start or finish recording route points for a line")
    public void recordRoute(Player player, @Argument(value = "lineId", suggestions = "lineIds") String id) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        RouteRecorder recorder = plugin.getRouteRecorder();
        if (recorder.isRecording(id)) {
            RouteRecorder.FinishResult result = recorder.stopAndSave(id);
            switch (result.status()) {
                case SAVED -> player.sendMessage(msg("line.record_saved",
                        "line_id", id,
                        "point_count", result.pointCount()));
                case TOO_FEW_POINTS -> sendRecordTooFew(player, result);
                case FAILED -> player.sendMessage(msg("line.record_failed", "line_id", id));
                case NOT_RECORDING -> player.sendMessage(msg("line.record_not_recording", "line_id", id));
            }
            return;
        }

        if (recorder.start(id, player.getUniqueId())) {
            player.sendMessage(msg("line.record_started", "line_id", id));
            player.sendMessage(msg("line.record_hint"));
        } else {
            player.sendMessage(msg("line.record_already", "line_id", id));
        }
    }

    @Command("m|metro line|l clearroute <lineId> [confirm]")
    @CommandDescription("Clear recorded route points for a line")
    public void clearRoute(Player player,
                           @Argument(value = "lineId", suggestions = "lineIds") String id,
                           @Argument("confirm") String confirm) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }
        if (!guard.requireConfirmation(player, confirm, "/m line clearroute " + id + " confirm")) {
            return;
        }

        plugin.getRouteRecorder().clearActive(id);
        LineCommandService.ClearRouteResult result = lineService.clearRoutePoints(line);
        if (result.status() == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(msg("line.clearroute_success",
                    "line_id", id,
                    "point_count", result.previousPointCount()));
        } else {
            player.sendMessage(msg("line.clearroute_fail", "line_id", id));
        }
    }

    @Command("m|metro line|l routeinfo <lineId>")
    @CommandDescription("Show recorded route point status for a line")
    public void routeInfo(Player player, @Argument(value = "lineId", suggestions = "lineIds") String id) {
        Line line = guard.requireLine(player, id);
        if (line == null) {
            return;
        }

        view.sendRouteInfo(player, line);
    }

    private Boolean parseToggle(String mode) {
        return switch (mode) {
            case "on", "true", "enable", "enabled" -> true;
            case "off", "false", "disable", "disabled" -> false;
            default -> null;
        };
    }

    private String msg(String key, Object... replacements) {
        return view.msg(key, replacements);
    }

    private void sendRecordTooFew(Player player, RouteRecorder.FinishResult result) {
        player.sendMessage(msg("line.record_too_few", "point_count", result.pointCount()));
        player.sendMessage(msg("line.record_too_few_hint"));
    }

    @Command("m|metro line|l trust <lineId> <playerName>")
    @CommandDescription("Grant line admin")
    public void trust(Player player,
                      @Argument(value = "lineId", suggestions = "lineIds") String id,
                      @Argument(value = "playerName", suggestions = "playerNames") String playerName) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        LineCommandService.WriteStatus status = lineService.grantAdmin(line, target.getUniqueId());
        if (status == LineCommandService.WriteStatus.EXISTS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.trust_exists",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
        } else if (status == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.trust_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "player", playerName)));
        }
    }

    @Command("m|metro line|l untrust <lineId> <playerName>")
    @CommandDescription("Revoke line admin")
    public void untrust(Player player,
                        @Argument(value = "lineId", suggestions = "lineIds") String id,
                        @Argument(value = "playerName", suggestions = "playerNames") String playerName) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (lineService.revokeAdmin(line, target.getUniqueId()) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.untrust_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "player", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.untrust_fail",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
        }
    }

    @Command("m|metro line|l owner <lineId> <playerName>")
    @CommandDescription("Transfer line ownership")
    public void owner(Player player,
                      @Argument(value = "lineId", suggestions = "lineIds") String id,
                      @Argument(value = "playerName", suggestions = "playerNames") String playerName) {
        Line line = guard.requireLine(player, id);
        if (line == null) {
            return;
        }
        if (!guard.requireLineOwner(player, line)) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("command.player_not_found",
                    LanguageManager.put(LanguageManager.args(), "player", playerName)));
            return;
        }
        if (lineService.transferOwner(line, target.getUniqueId()) == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.owner_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(), "line_id", id), "owner", playerName)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.owner_fail",
                    LanguageManager.put(LanguageManager.args(), "line_id", id)));
        }
    }

    @Command("m|metro line|l clonereverse <sourceId> <newId>")
    @CommandDescription("Clone a line and its stops in reverse order")
    public void cloneReverse(Player player, @Argument(value = "sourceId", suggestions = "lineIds") String sourceId, @Argument("newId") String newId) {
        cloneReverseWithSuffix(player, sourceId, newId, "_rev");
    }

    @Command("m|metro line|l clonereverse <sourceId> <newId> <stopIdSuffix>")
    @CommandDescription("Clone a line and its stops in reverse order with custom suffix")
    public void cloneReverseWithSuffix(Player player, @Argument(value = "sourceId", suggestions = "lineIds") String sourceId, @Argument("newId") String newId, @Argument("stopIdSuffix") String stopIdSuffix) {
        Line sourceLine = guard.requireManageableLine(player, sourceId);
        if (sourceLine == null) {
            return;
        }
        if (!OwnershipUtil.canCreateLine(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.permission_create"));
            return;
        }

        LineCommandService.WriteStatus status = lineService.cloneReverseLine(sourceId, newId, stopIdSuffix, player.getUniqueId());
        if (status == LineCommandService.WriteStatus.INVALID_ID) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.id_invalid",
                    LanguageManager.put(LanguageManager.args(), "line_id", newId)));
            return;
        }
        if (status == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.clone_success",
                    LanguageManager.put(LanguageManager.args(), "new_line_id", newId)));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.clone_fail"));
        }
    }

    @Command("m|metro line|l setprice <lineId> <price>")
    @CommandDescription("Set the ticket price for a metro line (legacy flat)")
    public void setPrice(Player player,
                         @Argument(value = "lineId", suggestions = "lineIds") String id,
                         @Argument(value = "price", suggestions = "priceValues") double price) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        LineCommandService.WriteStatus status = lineService.setTicketPrice(id, price);
        if (status == LineCommandService.WriteStatus.INVALID_VALUE) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_invalid"));
            return;
        }

        if (status == LineCommandService.WriteStatus.SUCCESS) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_success",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "line_name", line.getName()), "price", String.valueOf(price))));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_fail"));
        }
    }

    @Command("m|metro line|l setprice <lineId> flat <base>")
    @CommandDescription("Set flat pricing for a line")
    public void setPriceFlat(Player player,
                             @Argument(value = "lineId", suggestions = "lineIds") String id,
                             @Argument(value = "base", suggestions = "priceValues") double basePrice) {
        setPriceRule(player, id, "flat", basePrice, null, null);
    }

    @Command("m|metro line|l setprice <lineId> distance <base> <perBlock> [max]")
    @CommandDescription("Set distance-based pricing for a line")
    public void setPriceDistance(Player player,
                                 @Argument(value = "lineId", suggestions = "lineIds") String id,
                                 @Argument(value = "base", suggestions = "priceValues") double basePrice,
                                 @Argument(value = "perBlock", suggestions = "priceValues") double perBlock,
                                 @Argument(value = "max", suggestions = "priceValues") Double maxPrice) {
        setPriceRule(player, id, "distance", basePrice, perBlock, maxPrice);
    }

    @Command("m|metro line|l setprice <lineId> interval <base> <perStop> [max]")
    @CommandDescription("Set interval-based pricing for a line")
    public void setPriceInterval(Player player,
                                 @Argument(value = "lineId", suggestions = "lineIds") String id,
                                 @Argument(value = "base", suggestions = "priceValues") double basePrice,
                                 @Argument(value = "perStop", suggestions = "priceValues") double perStop,
                                 @Argument(value = "max", suggestions = "priceValues") Double maxPrice) {
        setPriceRule(player, id, "interval", basePrice, perStop, maxPrice);
    }

    private void setPriceRule(Player player, String id, String mode, double basePrice, Double perUnit, Double maxPrice) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        LineCommandService.WriteStatus status = lineService.setPriceRule(id, mode, basePrice, perUnit, maxPrice);
        switch (status) {
            case SUCCESS:
                player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_success",
                        LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
                break;
            case INVALID_VALUE:
                player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_invalid"));
                break;
            default:
                player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_fail"));
                break;
        }
    }

    @Command("m|metro line|l setprice reset <lineId>")
    @CommandDescription("Reset pricing rule to use legacy flat ticket price")
    public void resetPrice(Player player,
                           @Argument(value = "lineId", suggestions = "lineIds") String id) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        if (lineService.resetPriceRule(id)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_reset",
                    LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("line.setprice_fail"));
        }
    }

    @Command("m|metro line|l priceinfo <lineId>")
    @CommandDescription("View pricing details and active discounts for a line")
    public void priceInfo(Player player,
                          @Argument(value = "lineId", suggestions = "lineIds") String id) {
        Line line = guard.requireLine(player, id);
        if (line == null) {
            return;
        }

        view.sendPriceInfo(player, line);
    }

    @Command("m|metro line|l setstatus <lineId> <status>")
    @CommandDescription("Set line operational status (normal/suspended/maintenance)")
    public void setStatus(Player player,
                          @Argument(value = "lineId", suggestions = "lineIds") String id,
                          @Argument(value = "status", suggestions = "lineStatusValues") String status) {
        Line line = guard.requireManageableLine(player, id);
        if (line == null) {
            return;
        }

        LineCommandService.WriteStatus writeStatus = lineService.setLineStatus(id, status);
        switch (writeStatus) {
            case SUCCESS:
                player.sendMessage(plugin.getLanguageManager().getMessage("line.setstatus_success",
                        LanguageManager.put(LanguageManager.args(), "line_name", line.getName())));
                break;
            case INVALID_VALUE:
                player.sendMessage(plugin.getLanguageManager().getMessage("line.setstatus_invalid"));
                break;
            default:
                player.sendMessage(plugin.getLanguageManager().getMessage("line.setstatus_fail"));
                break;
        }
    }

}
