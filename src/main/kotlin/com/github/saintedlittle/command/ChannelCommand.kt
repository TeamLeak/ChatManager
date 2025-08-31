package com.github.saintedlittle.command

import com.github.saintedlittle.MainActivity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ChannelCommand(private val plugin: MainActivity) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true
        if (!sender.hasPermission("chat.channel")) {
            sender.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.no-permission")!!))
            return true
        }

        val cfg = plugin.configManager
        val current = plugin.dataStore.getDefaultChannel(sender.uniqueId.toString()) ?: cfg.defaultChannelId

        // /channel -> показать список с подсветкой и кликами
        if (args.isEmpty() || args[0].equals("list", true)) {
            sender.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.channel-list-header")!!))
            cfg.channels().forEach { ch ->
                val isActive = ch.id.equals(current, true)
                val tag = if (isActive) Component.text("● ", NamedTextColor.GREEN) else Component.text("○ ", NamedTextColor.GRAY)
                val line = Component.text()
                    .append(tag)
                    .append(Component.text(ch.id, if (isActive) NamedTextColor.GREEN else NamedTextColor.WHITE))
                    .append(Component.text("  (" + ch.name + ")", NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.runCommand("/channel ${ch.id}"))
                    .build()
                sender.sendMessage(line)
            }
            return true
        }

        // /channel <id> -> переключить
        val id = args[0].lowercase()
        val target = cfg.channelById(id)
        if (target == null) {
            sender.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.channel-invalid")!!))
            return true
        }
        plugin.dataStore.setDefaultChannel(sender.uniqueId.toString(), id)
        val ok = plugin.config.getString("messages.channel-switched")!!.replace("{channel}", target.name)
        sender.sendMessage(plugin.configManager.mini.deserialize(ok))
        return true
    }
}
