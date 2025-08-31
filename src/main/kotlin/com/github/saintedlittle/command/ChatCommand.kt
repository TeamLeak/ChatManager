package com.github.saintedlittle.command

import com.github.saintedlittle.MainActivity
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ChatCommand(private val plugin: MainActivity) : CommandExecutor, TabCompleter {
    private val mini: MiniMessage get() = plugin.configManager.mini
    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(mini.deserialize("<gray>/chat color <&a|<#RRGGBB>|reset> | /chat reload</gray>"))
            sender.sendMessage(mini.deserialize("<gray>/chat mute <channel> | unmute <channel> | mlist</gray>"))
            sender.sendMessage(mini.deserialize("<gray>/chat ignore <player> | unignore <player> | ilist</gray>"))
            sender.sendMessage(mini.deserialize("<gray>/chat spy [on|off|toggle] | /chat channel <id></gray>"))
            return true
        }

        fun msg(path: String, replace: Map<String,String> = emptyMap()): String {
            var s = plugin.config.getString("messages.$path")!!
            replace.forEach { (k,v) -> s = s.replace("{$k}", v) }
            return s
        }

        when (args[0].lowercase()) {
            "reload" -> {
                if (!sender.hasPermission("chat.reload")) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.no-permission")!!)); return true }
                plugin.reloadAll(); sender.sendMessage(mini.deserialize(plugin.config.getString("messages.reloaded")!!)); return true
            }

            "color" -> { /* — без изменений, см. предыдущую версию — */
                if (sender !is Player) return true
                if (!sender.hasPermission("chat.color")) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.no-permission")!!)); return true }
                if (args.size < 2) { sender.sendMessage(mini.deserialize("<gray>Использование: /chat color <&a|<#RRGGBB>|reset></gray>")); return true }
                val input = args[1]
                if (input.equals("reset", true)) { plugin.dataStore.setColor(sender.uniqueId.toString(), null); sender.sendMessage(mini.deserialize(plugin.config.getString("messages.color-reset")!!)); return true }
                if (input.startsWith("#") && !sender.hasPermission("chat.color.hex")) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.no-permission")!!)); return true }
                val ok = input.matches(Regex("(?i)&[0-9A-F]")) || (plugin.configManager.colors.allowHex && input.matches(Regex("^#[0-9a-fA-F]{6}$")))
                if (!ok) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.invalid-color")!!)); return true }
                plugin.dataStore.setColor(sender.uniqueId.toString(), input); sender.sendMessage(mini.deserialize(plugin.config.getString("messages.color-set")!!)); return true
            }

            // ====== SOCIAL-SPY ======
            "spy" -> {
                if (sender !is Player) return true
                if (!sender.hasPermission("chat.message.spy")) {
                    sender.sendMessage(mini.deserialize(plugin.config.getString("messages.no-permission")!!)); return true
                }
                val uid = sender.uniqueId.toString()
                if (args.size == 1 || args[1].equals("toggle", true)) {
                    val newVal = !plugin.dataStore.isSpyEnabled(uid)
                    plugin.dataStore.setSpyEnabled(uid, newVal)
                    sender.sendMessage(mini.deserialize(plugin.config.getString(if (newVal) "messages.spy-on" else "messages.spy-off")!!))
                    return true
                }
                when (args[1].lowercase()) {
                    "on"  -> { plugin.dataStore.setSpyEnabled(uid, true);  sender.sendMessage(mini.deserialize(plugin.config.getString("messages.spy-on")!!));  return true }
                    "off" -> { plugin.dataStore.setSpyEnabled(uid, false); sender.sendMessage(mini.deserialize(plugin.config.getString("messages.spy-off")!!)); return true }
                    else  -> { sender.sendMessage(mini.deserialize("<gray>Исп: /chat spy [on|off|toggle]</gray>")); return true }
                }
            }

            // ====== DEFAULT CHANNEL ======
            "channel" -> {
                if (sender !is Player) return true
                if (args.size < 2) {
                    val cur = plugin.dataStore.getDefaultChannel(sender.uniqueId.toString()) ?: plugin.configManager.defaultChannelId
                    sender.sendMessage(mini.deserialize("<gray>Текущий канал по умолчанию:</gray> <white>$cur</white>"))
                    sender.sendMessage(mini.deserialize("<gray>Использование:</gray> <white>/chat channel <id></white>")); return true
                }
                val id = args[1].lowercase()
                val ch = plugin.configManager.channelById(id)
                if (ch == null) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.channel-invalid")!!)); return true }
                plugin.dataStore.setDefaultChannel(sender.uniqueId.toString(), id)
                sender.sendMessage(mini.deserialize(msg("channel-set", mapOf("channel" to ch.name))))
                return true
            }

            // ====== MUTE / IGNORE блоки — как в предыдущей версии ======
            "mute","unmute","mlist","ignore","unignore","ilist" -> {
                return handleMuteIgnore(sender, args)
            }

            else -> { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.unknown-subcommand")!!)); return true }
        }
    }

    private fun handleMuteIgnore(sender: CommandSender, args: Array<out String>): Boolean {
        val mini = plugin.configManager.mini
        fun msg(path: String, replace: Map<String,String> = emptyMap()): String {
            var s = plugin.config.getString("messages.$path")!!; replace.forEach { (k,v) -> s = s.replace("{$k}", v) }; return s
        }
        when (args[0].lowercase()) {
            "mute" -> {
                if (sender !is Player) return true
                if (args.size < 2) { sender.sendMessage(mini.deserialize("<gray>Использование: /chat mute <channel></gray>")); return true }
                val chId = args[1].lowercase(); val ch = plugin.configManager.channelById(chId)
                if (ch == null) { sender.sendMessage(mini.deserialize("<red>Канал не найден.</red>")); return true }
                val uid = sender.uniqueId.toString()
                if (plugin.dataStore.isMuted(uid, chId)) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.already-muted")!!)); return true }
                plugin.dataStore.addMute(uid, chId); sender.sendMessage(mini.deserialize(msg("muted", mapOf("channel" to ch.name)))); return true
            }
            "unmute" -> {
                if (sender !is Player) return true
                if (args.size < 2) { sender.sendMessage(mini.deserialize("<gray>Использование: /chat unmute <channel></gray>")); return true }
                val chId = args[1].lowercase(); val ch = plugin.configManager.channelById(chId)
                if (ch == null) { sender.sendMessage(mini.deserialize("<red>Канал не найден.</red>")); return true }
                val uid = sender.uniqueId.toString()
                if (!plugin.dataStore.isMuted(uid, chId)) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.not-muted")!!)); return true }
                plugin.dataStore.removeMute(uid, chId); sender.sendMessage(mini.deserialize(msg("unmuted", mapOf("channel" to ch.name)))); return true
            }
            "mlist" -> {
                if (sender !is Player) return true
                val set = plugin.dataStore.mutedChannels(sender.uniqueId.toString())
                if (set.isEmpty()) sender.sendMessage(mini.deserialize(plugin.config.getString("messages.muted-list-empty")!!))
                else sender.sendMessage(mini.deserialize("<gray>Muted:</gray> <white>${set.joinToString(", ")}</white>"))
                return true
            }
            "ignore" -> {
                if (sender !is Player) return true
                if (args.size < 2) { sender.sendMessage(mini.deserialize("<gray>Исп: /chat ignore <player></gray>")); return true }
                val target = sender.server.getPlayerExact(args[1]) ?: sender.server.onlinePlayers.firstOrNull { it.name.equals(args[1], true) }
                if (target == null) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.player-not-found")!!)); return true }
                if (target.uniqueId == sender.uniqueId) { sender.sendMessage(mini.deserialize("<gray>Нельзя игнорировать себя.</gray>")); return true }
                val uid = sender.uniqueId.toString()
                if (plugin.dataStore.isIgnoring(uid, target.uniqueId)) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.already-ignored")!!)); return true }
                plugin.dataStore.addIgnore(uid, target.uniqueId); sender.sendMessage(mini.deserialize(msg("ignored", mapOf("player" to target.name)))); return true
            }
            "unignore" -> {
                if (sender !is Player) return true
                if (args.size < 2) { sender.sendMessage(mini.deserialize("<gray>Исп: /chat unignore <player></gray>")); return true }
                val target = sender.server.getPlayerExact(args[1]) ?: sender.server.onlinePlayers.firstOrNull { it.name.equals(args[1], true) }
                if (target == null) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.player-not-found")!!)); return true }
                val uid = sender.uniqueId.toString()
                if (!plugin.dataStore.removeIgnore(uid, target.uniqueId)) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.not-ignored")!!)); return true }
                sender.sendMessage(mini.deserialize(msg("unignored", mapOf("player" to target.name)))); return true
            }
            "ilist" -> {
                if (sender !is Player) return true
                val list = plugin.dataStore.ignoreList(sender.uniqueId.toString())
                if (list.isEmpty()) { sender.sendMessage(mini.deserialize(plugin.config.getString("messages.ignore-list-empty")!!)); return true }
                val names = list.mapNotNull { sender.server.getPlayer(it)?.name ?: it.toString().substring(0,8) }
                sender.sendMessage(mini.deserialize("<gray>Ignore:</gray> <white>${names.joinToString(", ")}</white>")); return true
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> =
        when (args.size) {
            1 -> listOf("color","reload","mute","unmute","mlist","ignore","unignore","ilist","spy","channel")
                .filter { it.startsWith(args[0], true) }.toMutableList()
            2 -> when (args[0].lowercase()) {
                "mute","unmute","channel" ->
                    plugin.configManager.channels().map { it.id }.filter { it.startsWith(args[1], true) }.toMutableList()
                "ignore","unignore" ->
                    sender.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], true) }.toMutableList()
                "spy" -> listOf("on","off","toggle").filter { it.startsWith(args[1], true) }.toMutableList()
                "color" -> mutableListOf("&f","&7","&a","&b","&c","&d","&e","&6","&9","#ffffff","#55ff55","reset")
                    .filter { it.startsWith(args[1], true) }.toMutableList()
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
}
