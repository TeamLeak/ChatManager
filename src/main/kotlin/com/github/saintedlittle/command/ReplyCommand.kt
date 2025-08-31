package com.github.saintedlittle.command

import com.github.saintedlittle.MainActivity
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ReplyCommand(private val plugin: MainActivity) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true
        if (!sender.hasPermission("chat.message")) {
            sender.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.no-permission")!!)); return true
        }
        if (args.isEmpty()) { sender.sendMessage(plugin.configManager.mini.deserialize("<gray>Использование: /reply <text></gray>")); return true }
        val last = plugin.messageService.getLastTarget(sender.uniqueId)
        if (last == null) { sender.sendMessage(plugin.configManager.mini.deserialize("<gray>Нет адресата для ответа.</gray>")); return true }
        val target = sender.server.getPlayer(last)
        if (target == null) { sender.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.player-not-found")!!)); return true }
        val text = args.joinToString(" ").trim(); if (text.isEmpty()) return true
        plugin.messageService.sendPM(sender, target, text); return true
    }
}
