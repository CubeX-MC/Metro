package org.cubexmc.metro.lifecycle;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.command.newcmd.LineCommand;
import org.cubexmc.metro.command.newcmd.MetroMainCommand;
import org.cubexmc.metro.command.newcmd.PortalCommand;
import org.cubexmc.metro.command.newcmd.StopCommand;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.StopManager;
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

    public CommandRegistration(Metro plugin, LineManager lineManager, StopManager stopManager) {
        this.plugin = plugin;
        this.lineManager = lineManager;
        this.stopManager = stopManager;
    }

    public Result register() {
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
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize Cloud Command Framework:");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(plugin);
            return null;
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

            CommandManager<CommandSender> manager =
                    (CommandManager<CommandSender>) PaperCommandManager.builder((SenderMapper) mapper)
                            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                            .buildOnEnable(plugin);
            plugin.getLogger().info("已加载新版 PaperCommandManager (1.20.5+)");
            return manager;
        } catch (ClassNotFoundException e) {
            LegacyPaperCommandManager<CommandSender> legacyManager =
                    LegacyPaperCommandManager.createNative(plugin, ExecutionCoordinator.simpleCoordinator());

            if (legacyManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
                legacyManager.registerBrigadier();
            }
            if (legacyManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
                legacyManager.registerAsynchronousCompletions();
            }

            plugin.getLogger().info("已加载兼容版 LegacyPaperCommandManager (1.20.4 及以下)");
            return legacyManager;
        }
    }

    private void registerSuggestionProviders(CommandManager<CommandSender> commandManager) {
        commandManager.parserRegistry().registerSuggestionProvider("lineIds",
                (context, input) -> toSuggestionsFuture(lineIdSuggestions(context, input)));
        commandManager.parserRegistry().registerSuggestionProvider("stopIds",
                (context, input) -> toSuggestionsFuture(stopIdSuggestions(context, input)));
    }

    private Iterable<String> lineIdSuggestions(CommandContext<CommandSender> context, CommandInput input) {
        return lineManager.getAllLines().stream()
                .map(org.cubexmc.metro.model.Line::getId)
                .toList();
    }

    private Iterable<String> stopIdSuggestions(CommandContext<CommandSender> context, CommandInput input) {
        return new ArrayList<>(stopManager.getAllStopIds());
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
