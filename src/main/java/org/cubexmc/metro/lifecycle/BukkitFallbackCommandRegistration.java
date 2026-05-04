package org.cubexmc.metro.lifecycle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.command.newcmd.LineCommand;
import org.cubexmc.metro.command.newcmd.MetroMainCommand;
import org.cubexmc.metro.command.newcmd.PortalCommand;
import org.cubexmc.metro.command.newcmd.StopCommand;
import org.cubexmc.metro.manager.LineManager;
import org.cubexmc.metro.manager.PortalManager;
import org.cubexmc.metro.manager.StopManager;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

/**
 * Minimal Bukkit command bridge used when cloud-bukkit is incompatible with a
 * newly released Minecraft server internals.
 */
final class BukkitFallbackCommandRegistration {

    private final Metro plugin;
    private final List<Entry> entries;

    BukkitFallbackCommandRegistration(Metro plugin, LineManager lineManager, StopManager stopManager, PortalManager portalManager) {
        this.plugin = plugin;
        this.entries = discoverEntries(List.of(
                new MetroMainCommand(plugin, lineManager, stopManager),
                new LineCommand(plugin, lineManager, stopManager),
                new StopCommand(plugin, stopManager, lineManager),
                new PortalCommand(plugin)
        ));
    }

    void register() throws ReflectiveOperationException {
        CommandMap commandMap = resolveCommandMap();
        MetroFallbackCommand command = new MetroFallbackCommand(plugin, entries);
        commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
        plugin.getLogger().info("Registered Metro Bukkit fallback commands.");
    }

    private CommandMap resolveCommandMap() throws ReflectiveOperationException {
        Method getCommandMap = Bukkit.getServer().getClass().getMethod("getCommandMap");
        return (CommandMap) getCommandMap.invoke(Bukkit.getServer());
    }

    private List<Entry> discoverEntries(List<Object> handlers) {
        List<Entry> discovered = new ArrayList<>();
        for (Object handler : handlers) {
            for (Method method : handler.getClass().getDeclaredMethods()) {
                org.incendo.cloud.annotations.Command command = method.getAnnotation(org.incendo.cloud.annotations.Command.class);
                if (command == null) {
                    continue;
                }
                method.setAccessible(true);
                discovered.add(new Entry(handler, method, parsePattern(command.value()), method.getAnnotation(Permission.class)));
            }
        }
        discovered.sort(Comparator
                .comparingInt((Entry entry) -> entry.literalCount()).reversed()
                .thenComparing(Comparator.comparingInt((Entry entry) -> entry.pattern().size()).reversed()));
        return discovered;
    }

    private List<Token> parsePattern(String pattern) {
        String[] rawTokens = pattern.trim().split("\\s+");
        List<Token> tokens = new ArrayList<>();
        for (int index = 1; index < rawTokens.length; index++) {
            String rawToken = rawTokens[index];
            if (rawToken.startsWith("<") && rawToken.endsWith(">")) {
                tokens.add(Token.required(rawToken.substring(1, rawToken.length() - 1)));
            } else if (rawToken.startsWith("[") && rawToken.endsWith("]")) {
                tokens.add(Token.optional(rawToken.substring(1, rawToken.length() - 1)));
            } else {
                tokens.add(Token.literal(rawToken));
            }
        }
        return tokens;
    }

    private record Token(TokenType type, String name, Set<String> aliases) {

        static Token literal(String raw) {
            Set<String> aliases = new HashSet<>();
            for (String alias : raw.split("\\|")) {
                aliases.add(alias.toLowerCase(Locale.ROOT));
            }
            return new Token(TokenType.LITERAL, raw, aliases);
        }

        static Token required(String name) {
            return new Token(TokenType.REQUIRED, name, Set.of());
        }

        static Token optional(String name) {
            return new Token(TokenType.OPTIONAL, name, Set.of());
        }
    }

    private enum TokenType {
        LITERAL,
        REQUIRED,
        OPTIONAL
    }

    private record Entry(Object handler, Method method, List<Token> pattern, Permission permission) {

        int literalCount() {
            int count = 0;
            for (Token token : pattern) {
                if (token.type() == TokenType.LITERAL) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class Match {
        private final Entry entry;
        private final Map<String, String> arguments;

        private Match(Entry entry, Map<String, String> arguments) {
            this.entry = entry;
            this.arguments = arguments;
        }
    }

    private final class MetroFallbackCommand extends Command {

        private final Metro plugin;
        private final List<Entry> entries;

        private MetroFallbackCommand(Metro plugin, List<Entry> entries) {
            super("metro", "Metro command", "/metro help", List.of("m"));
            this.plugin = plugin;
            this.entries = entries;
            setPermission(null);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            Match match = findMatch(args);
            if (match == null) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.help_header"));
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.help_line"));
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.help_stop"));
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.help_portal"));
                return true;
            }

            String permission = firstPermission(match.entry.permission());
            if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("plugin.no_permission"));
                return true;
            }

            try {
                match.entry.method().invoke(match.entry.handler(), buildInvocationArguments(sender, match));
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to execute fallback Metro command", e);
                sender.sendMessage(plugin.getLanguageManager().getMessage("command.help_header"));
            }
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            List<String> suggestions = new ArrayList<>();
            for (Entry entry : entries) {
                addTabSuggestions(entry, args, suggestions);
            }
            return suggestions.stream().distinct().sorted().toList();
        }

        private Match findMatch(String[] args) {
            for (Entry entry : entries) {
                Match match = matchEntry(entry, args);
                if (match != null) {
                    return match;
                }
            }
            return null;
        }

        private Match matchEntry(Entry entry, String[] args) {
            Map<String, String> values = new HashMap<>();
            int argIndex = 0;
            for (Token token : entry.pattern()) {
                if (token.type() == TokenType.LITERAL) {
                    if (argIndex >= args.length || !token.aliases().contains(args[argIndex].toLowerCase(Locale.ROOT))) {
                        return null;
                    }
                    argIndex++;
                    continue;
                }

                boolean greedy = isGreedyArgument(entry.method(), token.name());
                if (argIndex >= args.length) {
                    if (token.type() == TokenType.OPTIONAL) {
                        values.put(token.name(), null);
                        continue;
                    }
                    return null;
                }
                if (greedy) {
                    values.put(token.name(), String.join(" ", Arrays.copyOfRange(args, argIndex, args.length)));
                    argIndex = args.length;
                } else {
                    values.put(token.name(), args[argIndex]);
                    argIndex++;
                }
            }
            return argIndex == args.length ? new Match(entry, values) : null;
        }

        private boolean isGreedyArgument(Method method, String argumentName) {
            for (java.lang.reflect.Parameter parameter : method.getParameters()) {
                Argument argument = parameter.getAnnotation(Argument.class);
                if (argument != null && argumentName.equals(argument.value())) {
                    return parameter.isAnnotationPresent(Greedy.class);
                }
            }
            return false;
        }

        private Object[] buildInvocationArguments(CommandSender sender, Match match) {
            java.lang.reflect.Parameter[] parameters = match.entry.method().getParameters();
            Object[] values = new Object[parameters.length];
            for (int index = 0; index < parameters.length; index++) {
                Class<?> type = parameters[index].getType();
                if (CommandSender.class.isAssignableFrom(type)) {
                    values[index] = sender;
                    continue;
                }
                if (Player.class.isAssignableFrom(type)) {
                    if (!(sender instanceof Player player)) {
                        throw new IllegalArgumentException("Player sender is required");
                    }
                    values[index] = player;
                    continue;
                }

                Argument argument = parameters[index].getAnnotation(Argument.class);
                String rawValue = argument == null ? null : match.arguments.get(argument.value());
                values[index] = convertValue(type, rawValue);
            }
            return values;
        }

        private String firstPermission(Permission permission) {
            if (permission == null || permission.value().length == 0) {
                return null;
            }
            return permission.value()[0];
        }

        private Object convertValue(Class<?> type, String rawValue) {
            if (rawValue == null) {
                return null;
            }
            if (type == String.class) {
                return rawValue;
            }
            if (type == Integer.class || type == int.class) {
                return Integer.parseInt(rawValue);
            }
            if (type == Double.class || type == double.class) {
                return Double.parseDouble(rawValue);
            }
            if (type == Float.class || type == float.class) {
                return Float.parseFloat(rawValue);
            }
            return rawValue;
        }

        private void addTabSuggestions(Entry entry, String[] args, List<String> suggestions) {
            if (args.length == 0) {
                return;
            }
            int argIndex = 0;
            for (Token token : entry.pattern()) {
                boolean current = argIndex == args.length - 1;
                if (token.type() == TokenType.LITERAL) {
                    if (current) {
                        addMatching(suggestions, token.aliases(), args[argIndex]);
                        return;
                    }
                    if (argIndex >= args.length || !token.aliases().contains(args[argIndex].toLowerCase(Locale.ROOT))) {
                        return;
                    }
                    argIndex++;
                    continue;
                }
                if (current) {
                    addMatching(suggestions, suggestionsForArgument(token.name()), args[argIndex]);
                    return;
                }
                argIndex++;
            }
        }

        private void addMatching(List<String> suggestions, Collection<String> candidates, String prefix) {
            String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
            for (String candidate : candidates) {
                if (candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                    suggestions.add(candidate);
                }
            }
        }

        private Collection<String> suggestionsForArgument(String name) {
            return switch (name) {
                case "lineIds", "lineId", "sourceId" -> plugin.getLineManager().getAllLines().stream()
                        .map(org.cubexmc.metro.model.Line::getId)
                        .toList();
                case "stopIds", "stopId" -> new ArrayList<>(plugin.getStopManager().getAllStopIds());
                case "portalIds", "portalId", "id1", "id2" -> plugin.getPortalManager().getAllPortals().stream()
                        .map(org.cubexmc.metro.model.Portal::getId)
                        .sorted()
                        .toList();
                case "playerName" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
                case "page" -> List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
                case "color" -> List.of("&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f", "&#55AAFF");
                case "mode" -> List.of("status", "on", "off", "enable", "disable", "enabled", "disabled", "true", "false");
                case "titleType" -> StopCommandServiceValues.TITLE_TYPES;
                case "titleKey" -> StopCommandServiceValues.TITLE_KEYS;
                case "action" -> List.of("allow", "deny");
                case "index" -> List.of("0", "1", "2", "3", "4", "5", "10");
                case "yaw" -> List.of("0", "90", "180", "-90");
                case "speed" -> List.of("0.4", "0.8", "1.0", "1.2");
                case "price" -> List.of("0", "1", "2", "5", "10");
                default -> List.of();
            };
        }
    }

    private static final class StopCommandServiceValues {
        private static final List<String> TITLE_TYPES =
                org.cubexmc.metro.service.StopCommandService.TITLE_TYPES.stream().sorted().toList();
        private static final List<String> TITLE_KEYS =
                org.cubexmc.metro.service.StopCommandService.TITLE_KEYS.stream().sorted().toList();
    }
}
