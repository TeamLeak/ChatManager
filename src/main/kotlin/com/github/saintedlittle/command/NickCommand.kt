package com.github.saintedlittle.command

import com.github.saintedlittle.MainActivity
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class NickCommand(private val plugin: MainActivity) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true
        val mini = plugin.configManager.mini

        if (!sender.hasPermission("chat.nick")) {
            sender.sendMessage(mini.deserialize(plugin.config.getString("messages.no-permission")!!))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(mini.deserialize("<gray>Использование: </gray><white>/nick <новый ник|reset></white>"))
            return true
        }

        if (args[0].equals("reset", true)) {
            plugin.dataStore.setNick(sender.uniqueId.toString(), null)
            sender.displayName(Component.text(sender.name))
            sender.playerListName(Component.text(sender.name))
            sender.sendMessage(mini.deserialize(plugin.config.getString("messages.nick-reset")!!))
            return true
        }

        val newNick = args.joinToString(" ").trim()
        if (newNick.isEmpty()) return true

        val myUuid = sender.uniqueId
        val myStoredNick = plugin.dataStore.getNick(myUuid.toString())

        // ---- Собираем занятые имена/ники ----

        // 1) Онлайн-игроки: их account name и их сохранённый nick
        val onlineTaken = buildSet<String> {
            for (p in plugin.server.onlinePlayers) {
                if (p.uniqueId == myUuid) continue
                add(p.name)
                plugin.dataStore.getNick(p.uniqueId.toString())?.let { add(it) }
            }
        }

        // 2) Оффлайн-игроки: их account name (если есть)
        val offlineTaken = buildSet<String> {
            for (op: OfflinePlayer in plugin.server.offlinePlayers) {
                if (op.uniqueId == myUuid) continue
                op.name?.let { add(it) }
            }
        }

        // 3) Все сохранённые ники в БД: ник → владелец
        val allNicks = plugin.dataStore.allNicks() // Map<UUID, String>
        val storedTaken = buildSet<String> {
            for ((uuid, nick) in allNicks) {
                if (uuid == myUuid) continue
                add(nick)
            }
        }

        // ---- Проверка занятого ника (строгое совпадение, case-sensitive) ----
        val isTaken =
            (newNick in onlineTaken) ||
                    (newNick in offlineTaken) ||
                    (newNick in storedTaken)

        // разрешаем, если пользователь повторно вводит свой же текущий nick из БД
        val isSameAsMine = (myStoredNick != null && myStoredNick == newNick)

        if (isTaken && !isSameAsMine) {
            val msg = plugin.config.getString("messages.nick-taken")
                ?: "<red>Этот ник уже занят.</red>"
            sender.sendMessage(mini.deserialize(msg))
            return true
        }

        // ---- Применяем ----
        plugin.dataStore.setNick(myUuid.toString(), newNick)
        sender.displayName(Component.text(newNick))
        sender.playerListName(Component.text(newNick))
        sender.sendMessage(
            mini.deserialize(
                plugin.config.getString("messages.nick-set")!!.replace("{nick}", newNick)
            )
        )
        return true
    }
}
