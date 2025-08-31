package com.github.saintedlittle.command

import com.github.saintedlittle.MainActivity
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RelayCommand(private val plugin: MainActivity) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true

        if (args.isEmpty()) {
            val state = if (plugin.dataStore.isRelayOptOut(sender.uniqueId.toString())) "<red>OFF</red>" else "<green>ON</green>"
            sender.sendMessage(plugin.configManager.mini.deserialize("<gray>Bridge relay: $state</gray>"))
            sender.sendMessage(plugin.configManager.mini.deserialize("<gray>Использование: /relay <on|off|toggle></gray>"))
            return true
        }

        when (args[0].lowercase()) {
            "on" -> {
                plugin.dataStore.setRelayOptOut(sender.uniqueId.toString(), false)
                sender.sendMessage(plugin.configManager.mini.deserialize("<green>Ретрансляция ваших сообщений включена.</green>"))
            }
            "off" -> {
                plugin.dataStore.setRelayOptOut(sender.uniqueId.toString(), true)
                sender.sendMessage(plugin.configManager.mini.deserialize("<yellow>Ретрансляция ваших сообщений выключена.</yellow>"))
            }
            "toggle" -> {
                val cur = plugin.dataStore.isRelayOptOut(sender.uniqueId.toString())
                plugin.dataStore.setRelayOptOut(sender.uniqueId.toString(), !cur)
                val state = if (!cur) "<yellow>выключена</yellow>" else "<green>включена</green>"
                sender.sendMessage(plugin.configManager.mini.deserialize("<gray>Ретрансляция теперь $state.</gray>"))
            }
            else -> {
                sender.sendMessage(plugin.configManager.mini.deserialize("<gray>Использование: /relay <on|off|toggle></gray>"))
            }
        }
        return true
    }
}
