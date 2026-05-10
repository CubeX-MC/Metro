package org.cubexmc.metro.lifecycle;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.command.newcmd.LineCommand;
import org.cubexmc.metro.command.newcmd.MetroMainCommand;
import org.cubexmc.metro.command.newcmd.PortalCommand;
import org.cubexmc.metro.command.newcmd.StopCommand;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.cubexmc.metro.service.StopCommandService;
import org.cubexmc.metro.util.VersionUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.suggestion.Suggestion;

/**
 * Registers Cloud commands and suggestion providers.
 */
public class CommandRegistration {

    private final Metro plugin;
    private final LineManager lineManager;
    private final StopManager stopManager;
    private final PortalManager portalManager;

    public CommandRegistration(Metro plugin, LineManager lineManager, StopManager stopManager, PortalManager portalManager) {
        this.plugin = plugin;
        this.lineManager = lineManager;
        this.stopManager = stopManager;
        this.portalManager = portalManager;
    }

    public Result register() {
        if (VersionUtil.isVersionAtLeast(26, 1, 0)) {
            try {
                new BukkitFallbackCommandRegistration(plugin, lineManager, stopManager, portalManager).register();
                plugin.getLogger().info("已为 Minecraft 26.1+ 启用 Bukkit 命令兼容层。");
                return new Result(null, null);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize Bukkit command fallback:");
                e.printStackTrace();
                Bukkit.getPluginManager().disablePlugin(plugin);
                return null;
            }
        }

        try {
            CommandManager<CommandSender> commandManager = createCommandManager();
            AnnotationParser<CommandSender> annotationParser =
                    new AnnotationParser<>(commandManager, CommandSender.class);

            registerSuggestionProviders(commandManager);
            annotationParser.parse(
                    new MetroMainCommand(plugin, lineManager, stopManager),
                    new LineCommand(plugin, lineManager, stopManager),
                    new StopCommand(plugin, stopManager, lineManager),
                    new PortalCommand(plugin)
            );

            plugin.getLogger().info("Cloud Command Framework initialized successfully.");
            return new Result(commandManager, annotationParser);
        } catch (Exception | LinkageError e) {
            plugin.getLogger().warning("Cloud Command Framework 初始化失败，将尝试 Bukkit fallback。原因: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            try {
                new BukkitFallbackCommandRegistration(plugin, lineManager, stopManager, portalManager).register();
                return new Result(null, null);
            } catch (Exception fallbackError) {
                plugin.getLogger().severe("Failed to initialize Cloud Command Framework and Bukkit fallback:");
                fallbackError.printStackTrace();
                Bukkit.getPluginManager().disablePlugin(plugin);
                return null;
            }
        }
    }

    private CommandManager<CommandSender> createCommandManager() throws Exception {
        try {
            Class.forName("io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager");
            final Class<?> commandSourceStackClass =
                    Class.forName("io.papermc.paper.command.brigadier.CommandSourceStack");

            @SuppressWarnings({"unchecked", "rawtypes"})
            SenderMapper<?, CommandSender> mapper = SenderMapper.create(
                    source -> {
                        try {
                            return (CommandSender) source.getClass().getMethod("getSender").invoke(source);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to map CommandSourceStack to CommandSender", e);
                        }
                    },
                    sender -> Proxy.newProxyInstance(
                            commandSourceStackClass.getClassLoader(),
                            new Class<?>[]{commandSourceStackClass},
                            (proxy, method, args) -> {
                                String name = method.getName();
                                if ("getSender".equals(name)) {
                                    return sender;
                                }
                                if ("getLocation".equals(name)) {
                                    return sender instanceof Entity entity ? entity.getLocation() : null;
                                }
                                if ("getExecutor".equals(name)) {
                                    return sender instanceof Entity ? sender : null;
                                }
                                if ("toString".equals(name)) {
                                    return "CommandSourceStackProxy[" + sender.getName() + "]";
                                }
                                if ("equals".equals(name)) {
                                    return args != null && args.length == 1 && proxy == args[0];
                                }
                                if ("hashCode".equals(name)) {
                                    return System.identityHashCode(proxy);
                                }
                                return null;
                            }
                    )
            );

            try {
                CommandManager<CommandSender> manager =
                        (CommandManager<CommandSender>) PaperCommandManager.builder((SenderMapper) mapper)
                                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                                .buildOnEnable(plugin);
                plugin.getLogger().info("已加载新版 PaperCommandManager (1.20.5+)");
                return manager;
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("新版 PaperCommandManager 初始化失败，将降级为兼容命令注册。原因: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                return createLegacyCommandManager(false);
            }
        } catch (ClassNotFoundException e) {
            return createLegacyCommandManager(true);
        }
    }

    private LegacyPaperCommandManager<CommandSender> createLegacyCommandManager(boolean enableNativeBrigadier)
            throws Exception {
        LegacyPaperCommandManager<CommandSender> legacyManager =
                new LegacyPaperCommandManager<>(
                        plugin,
                        ExecutionCoordinator.simpleCoordinator(),
                        SenderMapper.identity()
                );

        if (enableNativeBrigadier && legacyManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            try {
                legacyManager.registerBrigadier();
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("Legacy Brigadier 注册失败，将继续使用 Bukkit 命令。原因: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        if (legacyManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            try {
                legacyManager.registerAsynchronousCompletions();
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("异步命令补全注册失败，将继续使用同步补全。原因: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info(enableNativeBrigadier
                ? "已加载兼容版 LegacyPaperCommandManager (1.20.4 及以下)"
                : "已加载降级版 LegacyPaperCommandManager (Bukkit command fallback)");
        return legacyManager;
    }

    private void registerSuggestionProviders(CommandManager<CommandSender> commandManager) {
        commandManager.parserRegistry().registerSuggestionProvider("lineIds",
                (context, input) -> toSuggestionsFuture(lineIdSuggestions(context, input)));
        commandManager.parserRegistry().registerSuggestionProvider("stopIds",
                (context, input) -> toSuggestionsFuture(stopIdSuggestions(context, input)));
        commandManager.parserRegistry().registerSuggestionProvider("portalIds",
                (context, input) -> toSuggestionsFuture(portalIdSuggestions(context, input)));
        commandManager.parserRegistry().registerSuggestionProvider("playerNames",
                (context, input) -> toSuggestionsFuture(playerNameSuggestions(context, input)));
        commandManager.parserRegistry().registerSuggestionProvider("players",
                (context, input) -> toSuggestionsFuture(playerNameSuggestions(context, input)));
        commandManager.parserRegistry().registerSuggestionProvider("lineColors",
                (context, input) -> toSuggestionsFuture(List.of(
                        "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7",
                        "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f",
                        "&#55AAFF")));
        commandManager.parserRegistry().registerSuggestionProvider("protectModes",
                (context, input) -> toSuggestionsFuture(List.of(
                        "status", "on", "off", "enable", "disable", "enabled", "disabled", "true", "false")));
        commandManager.parserRegistry().registerSuggestionProvider("titleTypes",
                (context, input) -> toSuggestionsFuture(StopCommandService.TITLE_TYPES.stream().sorted().toList()));
        commandManager.parserRegistry().registerSuggestionProvider("titleKeys",
                (context, input) -> toSuggestionsFuture(StopCommandService.TITLE_KEYS.stream().sorted().toList()));
        commandManager.parserRegistry().registerSuggestionProvider("linkActions",
                (context, input) -> toSuggestionsFuture(List.of("allow", "deny")));
        commandManager.parserRegistry().registerSuggestionProvider("pageNumbers",
                (context, input) -> toSuggestionsFuture(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")));
        commandManager.parserRegistry().registerSuggestionProvider("stopIndexes",
                (context, input) -> toSuggestionsFuture(List.of("0", "1", "2", "3", "4", "5", "10")));
        commandManager.parserRegistry().registerSuggestionProvider("yawValues",
                (context, input) -> toSuggestionsFuture(List.of("0", "90", "180", "-90")));
        commandManager.parserRegistry().registerSuggestionProvider("speedValues",
                (context, input) -> toSuggestionsFuture(List.of("0.4", "0.8", "1.0", "1.2")));
        commandManager.parserRegistry().registerSuggestionProvider("priceValues",
                (context, input) -> toSuggestionsFuture(List.of("0", "1", "2", "5", "10")));
        commandManager.parserRegistry().registerSuggestionProvider("priceModes",
                (context, input) -> toSuggestionsFuture(List.of("flat", "distance", "interval")));
        commandManager.parserRegistry().registerSuggestionProvider("lineStatusValues",
                (context, input) -> toSuggestionsFuture(List.of("normal", "suspended", "maintenance")));
    }

    private Iterable<String> lineIdSuggestions(CommandContext<CommandSender> context, CommandInput input) {
        return lineManager.getAllLines().stream()
                .map(org.cubexmc.metro.model.Line::getId)
                .toList();
    }

    private Iterable<String> stopIdSuggestions(CommandContext<CommandSender> context, CommandInput input) {
        return new ArrayList<>(stopManager.getAllStopIds());
    }

    private Iterable<String> portalIdSuggestions(CommandContext<CommandSender> context, CommandInput input) {
        return portalManager.getAllPortals().stream()
                .map(org.cubexmc.metro.model.Portal::getId)
                .sorted()
                .toList();
    }

    private Iterable<String> playerNameSuggestions(CommandContext<CommandSender> context, CommandInput input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted()
                .toList();
    }

    private CompletableFuture<? extends Iterable<? extends Suggestion>> toSuggestionsFuture(Iterable<String> values) {
        ArrayList<Suggestion> suggestions = new ArrayList<>();
        for (String value : values) {
            suggestions.add(Suggestion.suggestion(value));
        }
        return CompletableFuture.completedFuture(suggestions);
    }

    public record Result(CommandManager<CommandSender> commandManager,
                         AnnotationParser<CommandSender> annotationParser) {
    }
}
