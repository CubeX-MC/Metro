package org.cubexmc.metro.train;

import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;

import org.cubexmc.metro.Metro;
import org.cubexmc.metro.event.MetroTrainArrivalEvent;
import org.cubexmc.metro.event.MetroTrainDepartureEvent;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.model.Line;
import org.cubexmc.metro.model.Stop;
import org.cubexmc.metro.util.SchedulerUtil;
import org.cubexmc.metro.util.SoundUtil;
import org.cubexmc.metro.util.TextUtil;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class TrainDisplayController implements Listener {

    private final Metro plugin;

    public TrainDisplayController(Metro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrainArrival(MetroTrainArrivalEvent event) {
        Player passenger = event.getPassenger();
        if (passenger == null || !passenger.isOnline())
            return;

        Stop targetStop = event.getCurrentStop();
        Line line = event.getLine();
        Minecart minecart = event.getMinecart();

        if (event.getArrivalType() == MetroTrainArrivalEvent.ArrivalType.ENTERING) {
            // 播放到站音乐
            playArrivalSound(passenger);
            // 播放站台到站音乐（给站台上的玩家听）
            playStationArrivalSound(targetStop, passenger);

            if (event.isTerminus()) {
                if (plugin.getConfigFacade().isTerminalStopTitleEnabled()) {
                    showTerminalStopInfo(passenger, targetStop, line);
                }
            } else {
                showArriveStopInfo(passenger, targetStop, line);
            }
        } else if (event.getArrivalType() == MetroTrainArrivalEvent.ArrivalType.DOCKED) {
            if (!event.isTerminus()) {
                // 非终点站，显示等待发车信息
                showWaitingInfo(passenger, minecart, targetStop, line);
                // 播放等待发车音乐
                startWaitingSound(minecart, passenger);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrainDeparture(MetroTrainDepartureEvent event) {
        Player passenger = event.getPassenger();
        if (passenger == null || !passenger.isOnline())
            return;

        Stop currentStop = event.getCurrentStop();
        Stop nextStop = event.getNextStop();
        Line line = event.getLine();
        Minecart minecart = event.getMinecart();

        // 播放发车音乐
        playDepartureSound(passenger);

        // 显示行程信息
        if (plugin.getConfigFacade().isDepartureTitleEnabled()) {
            showDepartureInfo(passenger, minecart, currentStop, nextStop, line);
        }
    }

    private void playStationArrivalSound(Stop stop, Player passenger) {
        if (!plugin.getConfigFacade().isStationArrivalSoundEnabled())
            return;

        List<String> notes = plugin.getConfigFacade().getStationArrivalNotes();
        if (notes.isEmpty())
            return;

        int initialDelay = plugin.getConfigFacade().getStationArrivalInitialDelay();
        Location stopLocation = stop.getStopPointLocation();
        if (stopLocation == null || stopLocation.getWorld() == null)
            return;

        for (Player player : stopLocation.getWorld().getPlayers()) {
            if (player.equals(passenger))
                continue;
            if (stop.isInStop(player.getLocation())) {
                SoundUtil.playNoteSequence(plugin, player, notes, initialDelay);
            }
        }
    }

    private void playArrivalSound(Player passenger) {
        if (plugin.getConfigFacade().isArrivalSoundEnabled() && !plugin.getConfigFacade().getArrivalNotes().isEmpty()
                && passenger != null) {
            SoundUtil.playNoteSequence(plugin, passenger, plugin.getConfigFacade().getArrivalNotes(),
                    plugin.getConfigFacade().getArrivalInitialDelay());
        }
    }

    private void playDepartureSound(Player passenger) {
        if (plugin.getConfigFacade().isDepartureSoundEnabled()
                && !plugin.getConfigFacade().getDepartureNotes().isEmpty() && passenger != null) {
            SoundUtil.playNoteSequence(plugin, passenger, plugin.getConfigFacade().getDepartureNotes(),
                    plugin.getConfigFacade().getDepartureInitialDelay());
        }
    }

    private void showArriveStopInfo(Player passenger, Stop stop, Line line) {
        if (passenger == null || !passenger.isOnline())
            return;

        String nextStopId = line.getNextStopId(stop.getId());
        Stop nextStop = nextStopId != null ? plugin.getStopManager().getStop(nextStopId) : null;

        List<String> stopIds = line.getOrderedStopIds();
        Stop terminusStop = !stopIds.isEmpty() ? plugin.getStopManager().getStop(stopIds.get(stopIds.size() - 1))
                : null;

        showStopInfo(passenger, line, "arrive_stop", stop, null, nextStop, terminusStop,
                plugin.getConfigFacade().getArriveStopFadeIn(), plugin.getConfigFacade().getArriveStopStay(),
                plugin.getConfigFacade().getArriveStopFadeOut());
    }

    private void showTerminalStopInfo(Player passenger, Stop stop, Line line) {
        if (passenger == null || !passenger.isOnline())
            return;

        int fadeIn = plugin.getConfigFacade().getTerminalStopFadeIn();
        int stay = plugin.getConfigFacade().getTerminalStopStay();
        int fadeOut = plugin.getConfigFacade().getTerminalStopFadeOut();

        showStopInfo(passenger, line, "terminal_stop", stop, null, null, stop, fadeIn, stay, fadeOut);
    }

    private void showDepartureInfo(Player passenger, Minecart minecart, Stop currentStop, Stop nextStop, Line line) {
        if (passenger == null || !passenger.isOnline())
            return;

        List<String> stopIds = line.getOrderedStopIds();
        Stop terminusStop = !stopIds.isEmpty() ? plugin.getStopManager().getStop(stopIds.get(stopIds.size() - 1))
                : null;

        int fadeIn = plugin.getConfigFacade().getDepartureFadeIn();
        int stay = plugin.getConfigFacade().getDepartureStay();
        int fadeOut = plugin.getConfigFacade().getDepartureFadeOut();

        String actionbarTemplate = plugin.getConfigFacade().getDepartureActionbar();
        Map<String, String> customTitle = currentStop.getCustomTitle("departure");
        if (customTitle != null && customTitle.containsKey("actionbar")) {
            actionbarTemplate = customTitle.get("actionbar");
        }

        if (actionbarTemplate.contains("{countdown}")) {
            startCountdownActionbar(passenger, minecart, line, "departure", currentStop, currentStop, nextStop,
                    terminusStop);
        }

        showStopInfo(passenger, line, "departure", currentStop, currentStop, nextStop, terminusStop, fadeIn, stay,
                fadeOut);
    }

    private void showWaitingInfo(Player passenger, Minecart minecart, Stop currentStop, Line line) {
        if (passenger == null || !passenger.isOnline())
            return;
        if (!plugin.getConfigFacade().isWaitingTitleEnabled())
            return;

        StopManager stopManager = plugin.getStopManager();
        String nextStopId = line.getNextStopId(currentStop.getId());
        Stop nextStop = nextStopId != null ? stopManager.getStop(nextStopId) : null;

        List<String> stopIds = line.getOrderedStopIds();
        Stop terminusStop = !stopIds.isEmpty() ? stopManager.getStop(stopIds.get(stopIds.size() - 1)) : null;

        LineManager lineManager = plugin.getLineManager();

        String titleTemplate = plugin.getConfigFacade().getWaitingTitle();
        String subtitleTemplate = plugin.getConfigFacade().getWaitingSubtitle();

        Map<String, String> customTitle = currentStop.getCustomTitle("waiting");
        if (customTitle != null) {
            if (customTitle.containsKey("title"))
                titleTemplate = customTitle.get("title");
            if (customTitle.containsKey("subtitle"))
                subtitleTemplate = customTitle.get("subtitle");
        }

        String title = TextUtil.replacePlaceholders(titleTemplate, line, currentStop, null, nextStop, terminusStop,
                lineManager);
        String subtitle = TextUtil.replacePlaceholders(subtitleTemplate, line, currentStop, null, nextStop,
                terminusStop, lineManager);

        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);

        passenger.sendTitle(title, subtitle, 0, 1000000, 0);

        String actionbarTemplate = plugin.getConfigFacade().getWaitingActionbar();
        if (customTitle != null && customTitle.containsKey("actionbar")) {
            actionbarTemplate = customTitle.get("actionbar");
        }

        if (actionbarTemplate.contains("{countdown}")) {
            startCountdownActionbar(passenger, minecart, line, "waiting", currentStop, null, nextStop, terminusStop);
        } else {
            String actionbarText = TextUtil.replacePlaceholders(actionbarTemplate, line, currentStop, null, nextStop,
                    terminusStop, lineManager);
            actionbarText = ChatColor.translateAlternateColorCodes('&', actionbarText);
            passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbarText));
        }
    }

    private void startCountdownActionbar(Player passenger, Minecart minecart, Line line, String infoType,
            Stop mainStop, Stop prevStop, Stop nextStop, Stop terminusStop) {
        if (passenger == null || !passenger.isOnline())
            return;

        String actionbarTemplate = "";
        switch (infoType) {
            case "waiting":
                actionbarTemplate = plugin.getConfigFacade().getWaitingActionbar();
                break;
            case "departure":
                actionbarTemplate = plugin.getConfigFacade().getDepartureActionbar();
                break;
            case "arrive_stop":
            case "terminal_stop":
                // No default actionbar for these yet, wait for implementation if needed
                break;
        }

        Map<String, String> customTitle = mainStop.getCustomTitle(infoType);
        if (customTitle != null && customTitle.containsKey("actionbar")) {
            actionbarTemplate = customTitle.get("actionbar");
        }

        final String template = actionbarTemplate;
        LineManager lineManager = plugin.getLineManager();
        int totalSeconds = (int) Math.ceil(plugin.getConfigFacade().getCartDepartureDelay() / 20.0);

        for (int i = totalSeconds; i >= 0; i--) {
            final int secondsLeft = i;
            long delayTicks = (totalSeconds - i) * 20L;

            SchedulerUtil.entityRun(plugin, minecart, () -> {
                if (passenger == null || !passenger.isOnline() || passenger.getVehicle() != minecart)
                    return;

                String text = template.replace("{countdown}", String.valueOf(secondsLeft));
                text = TextUtil.replacePlaceholders(text, line, mainStop, prevStop, nextStop, terminusStop,
                        lineManager);
                text = ChatColor.translateAlternateColorCodes('&', text);

                passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
            }, delayTicks, -1);
        }
    }

    private void startWaitingSound(Minecart minecart, Player passenger) {
        if (!plugin.getConfigFacade().isWaitingSoundEnabled() || plugin.getConfigFacade().getWaitingNotes().isEmpty()
                || passenger == null)
            return;

        SchedulerUtil.entityRun(plugin, minecart, () -> {
            playWaitingSoundOnce(passenger);
        }, plugin.getConfigFacade().getWaitingInitialDelay(), -1);

        int interval = plugin.getConfigFacade().getWaitingSoundInterval();
        if (interval <= 0)
            return;

        long repeatTimes = (plugin.getConfigFacade().getCartDepartureDelay() + interval - 1L) / interval;

        for (long i = 1; i <= repeatTimes; i++) {
            SchedulerUtil.entityRun(plugin, minecart, () -> {
                if (passenger != null && passenger.isOnline() && passenger.getVehicle() == minecart) {
                    playWaitingSoundOnce(passenger);
                }
            }, plugin.getConfigFacade().getWaitingInitialDelay() + (interval * i), -1);
        }
    }

    private void playWaitingSoundOnce(Player passenger) {
        if (passenger != null && passenger.isOnline()) {
            SoundUtil.playNoteSequence(plugin, passenger, plugin.getConfigFacade().getWaitingNotes(), 0);
        }
    }

    private void showStopInfo(Player passenger, Line line, String infoType, Stop mainStop, Stop prevStop,
            Stop nextStop, Stop terminusStop, int fadeIn, int stay, int fadeOut) {
        if (passenger == null || !passenger.isOnline() || mainStop == null)
            return;

        LineManager lineManager = plugin.getLineManager();
        String titleTemplate = "";
        String subtitleTemplate = "";
        String actionbarTemplate = "";

        switch (infoType) {
            case "arrive_stop":
                titleTemplate = plugin.getConfigFacade().getArriveStopTitle();
                subtitleTemplate = plugin.getConfigFacade().getArriveStopSubtitle();
                break;
            case "terminal_stop":
                titleTemplate = plugin.getConfigFacade().getTerminalStopTitle();
                subtitleTemplate = plugin.getConfigFacade().getTerminalStopSubtitle();
                break;
            case "departure":
                titleTemplate = plugin.getConfigFacade().getDepartureTitle();
                subtitleTemplate = plugin.getConfigFacade().getDepartureSubtitle();
                actionbarTemplate = plugin.getConfigFacade().getDepartureActionbar();
                break;
            case "waiting":
                titleTemplate = plugin.getConfigFacade().getWaitingTitle();
                subtitleTemplate = plugin.getConfigFacade().getWaitingSubtitle();
                actionbarTemplate = plugin.getConfigFacade().getWaitingActionbar();
                break;
        }

        Map<String, String> customTitle = mainStop.getCustomTitle(infoType);
        if (customTitle != null) {
            if (customTitle.containsKey("title"))
                titleTemplate = customTitle.get("title");
            if (customTitle.containsKey("subtitle"))
                subtitleTemplate = customTitle.get("subtitle");
            if (customTitle.containsKey("actionbar"))
                actionbarTemplate = customTitle.get("actionbar");
        }

        String title = TextUtil.replacePlaceholders(titleTemplate, line, mainStop, prevStop, nextStop, terminusStop,
                lineManager);
        String subtitle = TextUtil.replacePlaceholders(subtitleTemplate, line, mainStop, prevStop, nextStop,
                terminusStop, lineManager);
        String actionbar = TextUtil.replacePlaceholders(actionbarTemplate, line, mainStop, prevStop, nextStop,
                terminusStop, lineManager);

        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
        actionbar = ChatColor.translateAlternateColorCodes('&', actionbar);

        passenger.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionbar));
    }
}
