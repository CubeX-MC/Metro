package org.cubexmc.metro.lifecycle;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

/**
 * Java interop shim for the modern {@link PaperCommandManager} bootstrap.
 *
 * <p>{@code PaperCommandManager.builder(...)} declares its sender mapper in terms of
 * {@code io.papermc.paper.command.brigadier.CommandSourceStack}, which is not on Metro's
 * Spigot compile classpath. Java raw types let us call it without naming that class;
 * Kotlin has no raw types, so this single call stays in Java.
 */
public final class PaperCommandManagerBootstrap {

    private PaperCommandManagerBootstrap() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static CommandManager<CommandSender> buildOnEnable(Plugin plugin, SenderMapper<?, CommandSender> mapper) {
        return (CommandManager<CommandSender>) PaperCommandManager.builder((SenderMapper) mapper)
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(plugin);
    }
}
