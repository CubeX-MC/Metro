package org.cubexmc.metro.lifecycle

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.cubexmc.metro.Metro
import org.cubexmc.metro.command.newcmd.LineCommand
import org.cubexmc.metro.command.newcmd.MetroMainCommand
import org.cubexmc.metro.command.newcmd.PortalCommand
import org.cubexmc.metro.command.newcmd.StopCommand
import org.cubexmc.metro.manager.LineManager
import org.cubexmc.metro.manager.PortalManager
import org.cubexmc.metro.manager.StopManager
import org.cubexmc.metro.service.StopCommandService
import org.incendo.cloud.annotation.specifier.Greedy
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Permission
import java.lang.reflect.Method
import java.util.Locale
import java.util.logging.Level

/**
 * Minimal Bukkit command bridge used when cloud-bukkit is incompatible with a
 * newly released Minecraft server internals.
 */
internal class BukkitFallbackCommandRegistration(
    private val plugin: Metro,
    lineManager: LineManager,
    stopManager: StopManager,
    portalManager: PortalManager,
) {

    private val entries: List<Entry> =
        discoverEntries(
            listOf(
                MetroMainCommand(plugin, lineManager, stopManager),
                LineCommand(plugin, lineManager, stopManager),
                StopCommand(plugin, stopManager, lineManager),
                PortalCommand(plugin),
            ),
        )

    @Throws(ReflectiveOperationException::class)
    fun register() {
        val commandMap = resolveCommandMap()
        val command = MetroFallbackCommand(plugin, entries)
        commandMap.register(plugin.name.lowercase(Locale.ROOT), command)
        plugin.logger.info("Registered Metro Bukkit fallback commands.")
    }

    @Throws(ReflectiveOperationException::class)
    private fun resolveCommandMap(): CommandMap {
        val getCommandMap = Bukkit.getServer().javaClass.getMethod("getCommandMap")
        return getCommandMap.invoke(Bukkit.getServer()) as CommandMap
    }

    private fun discoverEntries(handlers: List<Any>): List<Entry> {
        val discovered = ArrayList<Entry>()
        for (handler in handlers) {
            for (method in handler.javaClass.declaredMethods) {
                val command = method.getAnnotation(org.incendo.cloud.annotations.Command::class.java) ?: continue
                method.isAccessible = true
                discovered.add(
                    Entry(handler, method, parsePattern(command.value), method.getAnnotation(Permission::class.java)),
                )
            }
        }
        discovered.sortWith(
            compareByDescending<Entry> { it.literalCount() }.thenByDescending { it.pattern.size },
        )
        return discovered
    }

    private fun parsePattern(pattern: String): List<Token> {
        val rawTokens = pattern.trim().split(Regex("\\s+"))
        val tokens = ArrayList<Token>()
        for (index in 1 until rawTokens.size) {
            val rawToken = rawTokens[index]
            if (rawToken.startsWith("<") && rawToken.endsWith(">")) {
                tokens.add(Token.required(rawToken.substring(1, rawToken.length - 1)))
            } else if (rawToken.startsWith("[") && rawToken.endsWith("]")) {
                tokens.add(Token.optional(rawToken.substring(1, rawToken.length - 1)))
            } else {
                tokens.add(Token.literal(rawToken))
            }
        }
        return tokens
    }

    private class Token(val type: TokenType, val name: String, val aliases: Set<String>) {

        companion object {
            fun literal(raw: String): Token {
                val aliases = HashSet<String>()
                for (alias in raw.split("|")) {
                    aliases.add(alias.lowercase(Locale.ROOT))
                }
                return Token(TokenType.LITERAL, raw, aliases)
            }

            fun required(name: String): Token = Token(TokenType.REQUIRED, name, emptySet())

            fun optional(name: String): Token = Token(TokenType.OPTIONAL, name, emptySet())
        }
    }

    private enum class TokenType {
        LITERAL,
        REQUIRED,
        OPTIONAL,
    }

    private class Entry(
        val handler: Any,
        val method: Method,
        val pattern: List<Token>,
        val permission: Permission?,
    ) {
        fun literalCount(): Int = pattern.count { it.type == TokenType.LITERAL }
    }

    private class Match(val entry: Entry, val arguments: Map<String, String?>)

    private class MetroFallbackCommand(
        private val plugin: Metro,
        private val entries: List<Entry>,
    ) : Command("metro", "Metro command", "/metro help", listOf("m")) {

        init {
            permission = null
        }

        override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
            val match = findMatch(args)
            if (match == null) {
                sender.sendMessage(plugin.languageManager.getMessage("command.help_header"))
                sender.sendMessage(plugin.languageManager.getMessage("command.help_line"))
                sender.sendMessage(plugin.languageManager.getMessage("command.help_stop"))
                sender.sendMessage(plugin.languageManager.getMessage("command.help_portal"))
                return true
            }

            val permission = firstPermission(match.entry.permission)
            if (permission != null && permission.isNotBlank() && !sender.hasPermission(permission)) {
                sender.sendMessage(plugin.languageManager.getMessage("plugin.no_permission"))
                return true
            }

            try {
                match.entry.method.invoke(match.entry.handler, *buildInvocationArguments(sender, match))
            } catch (e: ReflectiveOperationException) {
                reportFailure(sender, e)
            } catch (e: RuntimeException) {
                reportFailure(sender, e)
            }
            return true
        }

        private fun reportFailure(sender: CommandSender, error: Throwable) {
            plugin.logger.log(Level.SEVERE, "Failed to execute fallback Metro command", error)
            sender.sendMessage(plugin.languageManager.getMessage("command.help_header"))
        }

        override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
            val suggestions = ArrayList<String>()
            for (entry in entries) {
                addTabSuggestions(entry, args, suggestions)
            }
            return suggestions.distinct().sorted()
        }

        private fun findMatch(args: Array<out String>): Match? {
            for (entry in entries) {
                val match = matchEntry(entry, args)
                if (match != null) {
                    return match
                }
            }
            return null
        }

        private fun matchEntry(entry: Entry, args: Array<out String>): Match? {
            val values = HashMap<String, String?>()
            var argIndex = 0
            for (token in entry.pattern) {
                if (token.type == TokenType.LITERAL) {
                    if (argIndex >= args.size || !token.aliases.contains(args[argIndex].lowercase(Locale.ROOT))) {
                        return null
                    }
                    argIndex++
                    continue
                }

                val greedy = isGreedyArgument(entry.method, token.name)
                if (argIndex >= args.size) {
                    if (token.type == TokenType.OPTIONAL) {
                        values[token.name] = null
                        continue
                    }
                    return null
                }
                if (greedy) {
                    values[token.name] = args.copyOfRange(argIndex, args.size).joinToString(" ")
                    argIndex = args.size
                } else {
                    values[token.name] = args[argIndex]
                    argIndex++
                }
            }
            return if (argIndex == args.size) Match(entry, values) else null
        }

        private fun isGreedyArgument(method: Method, argumentName: String): Boolean {
            for (parameter in method.parameters) {
                val argument = parameter.getAnnotation(Argument::class.java)
                if (argument != null && argumentName == argument.value) {
                    return parameter.isAnnotationPresent(Greedy::class.java)
                }
            }
            return false
        }

        private fun buildInvocationArguments(sender: CommandSender, match: Match): Array<Any?> {
            val parameters = match.entry.method.parameters
            val values = arrayOfNulls<Any>(parameters.size)
            for (index in parameters.indices) {
                val type = parameters[index].type
                if (CommandSender::class.java.isAssignableFrom(type)) {
                    values[index] = sender
                    continue
                }
                if (Player::class.java.isAssignableFrom(type)) {
                    require(sender is Player) { "Player sender is required" }
                    values[index] = sender
                    continue
                }

                val argument = parameters[index].getAnnotation(Argument::class.java)
                val rawValue = if (argument == null) null else match.arguments[argument.value]
                values[index] = convertValue(type, rawValue)
            }
            return values
        }

        private fun firstPermission(permission: Permission?): String? {
            if (permission == null || permission.value.isEmpty()) {
                return null
            }
            return permission.value[0]
        }

        private fun convertValue(type: Class<*>, rawValue: String?): Any? {
            if (rawValue == null) {
                return null
            }
            return when (type) {
                String::class.java -> rawValue
                Integer::class.java, Integer.TYPE -> rawValue.toInt()
                java.lang.Double::class.java, java.lang.Double.TYPE -> rawValue.toDouble()
                java.lang.Float::class.java, java.lang.Float.TYPE -> rawValue.toFloat()
                else -> rawValue
            }
        }

        private fun addTabSuggestions(entry: Entry, args: Array<out String>, suggestions: MutableList<String>) {
            if (args.isEmpty()) {
                return
            }
            var argIndex = 0
            for (token in entry.pattern) {
                val current = argIndex == args.size - 1
                if (token.type == TokenType.LITERAL) {
                    if (current) {
                        addMatching(suggestions, token.aliases, args[argIndex])
                        return
                    }
                    if (argIndex >= args.size || !token.aliases.contains(args[argIndex].lowercase(Locale.ROOT))) {
                        return
                    }
                    argIndex++
                    continue
                }
                if (current) {
                    addMatching(suggestions, suggestionsForArgument(token.name), args[argIndex])
                    return
                }
                argIndex++
            }
        }

        private fun addMatching(
            suggestions: MutableList<String>,
            candidates: Collection<String>,
            prefix: String,
        ) {
            val normalizedPrefix = prefix.lowercase(Locale.ROOT)
            for (candidate in candidates) {
                if (candidate.lowercase(Locale.ROOT).startsWith(normalizedPrefix)) {
                    suggestions.add(candidate)
                }
            }
        }

        private fun suggestionsForArgument(name: String): Collection<String> =
            when (name) {
                "lineIds", "lineId", "sourceId" -> plugin.lineManager.getAllLines().map { it.id }
                "stopIds", "stopId" -> ArrayList(plugin.stopManager.getAllStopIds())
                "portalIds", "portalId", "id1", "id2" ->
                    plugin.portalManager.getAllPortals().map { it.id }.sorted()
                "playerName" -> Bukkit.getOnlinePlayers().map { it.name }.sorted()
                "page" -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
                "color" -> LINE_COLORS
                "mode" -> listOf("status", "on", "off", "enable", "disable", "enabled", "disabled", "true", "false")
                "titleType" -> TITLE_TYPES
                "titleKey" -> TITLE_KEYS
                "action" -> listOf("allow", "deny")
                "index" -> listOf("0", "1", "2", "3", "4", "5", "10")
                "yaw" -> listOf("0", "90", "180", "-90")
                "speed" -> listOf("0.4", "0.8", "1.0", "1.2")
                "price" -> listOf("0", "1", "2", "5", "10")
                "priceModes" -> listOf("flat", "distance", "interval")
                "lineStatusValues" -> listOf("normal", "suspended", "maintenance")
                else -> emptyList()
            }

        private companion object {
            val LINE_COLORS =
                listOf(
                    "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7",
                    "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f",
                    "&#55AAFF",
                )
            val TITLE_TYPES: List<String> = StopCommandService.TITLE_TYPES.sorted()
            val TITLE_KEYS: List<String> = StopCommandService.TITLE_KEYS.sorted()
        }
    }
}
