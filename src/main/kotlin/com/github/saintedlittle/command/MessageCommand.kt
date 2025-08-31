package com.github.saintedlittle.command

import com.github.saintedlittle.MainActivity
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MessageCommand(private val plugin: MainActivity) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true
        if (!sender.hasPermission("chat.message")) {
            sender.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.no-permission")!!))
            return true
        }
        if (args.size < 2) {
            sender.sendMessage(plugin.configManager.mini.deserialize("<gray>Использование: /message <player> <text></gray>"))
            return true
        }
        val target = plugin.messageService.findOnlineByName(args[0])
        if (target == null) {
            sender.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.player-not-found")!!))
            return true
        }
        if (target.uniqueId == sender.uniqueId) {
            sender.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.cannot-message-yourself")!!))
            return true
        }
        val text = args.copyOfRange(1, args.size).joinToString(" ").trim()
        if (text.isEmpty()) return true

        plugin.messageService.sendPM(sender, target, text)
        return true
    }
}
