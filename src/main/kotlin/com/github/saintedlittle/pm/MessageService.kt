package com.github.saintedlittle.pm

import com.github.saintedlittle.MainActivity
import com.github.saintedlittle.util.MiniUtil
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

class MessageService(private val plugin: MainActivity) {

    private val lastPartner = mutableMapOf<UUID, UUID>() // who last PMed whom

    fun setLastPair(from: UUID, to: UUID) { lastPartner[from] = to; lastPartner[to] = from }
    fun getLastTarget(sender: UUID): UUID? = lastPartner[sender]

    fun sendPM(from: Player, to: Player, rawText: String) {
        if (!from.hasPermission("chat.ignore.bypass") && plugin.dataStore.isIgnoring(to.uniqueId.toString(), from.uniqueId)) {
            from.sendMessage(plugin.configManager.mini.deserialize(plugin.config.getString("messages.target-ignores-you")!!))
            return
        }

        val cfg = plugin.configManager
        val msgComp = MiniUtil.deserializeUserText(cfg.mini, rawText, cfg.colors.translateLegacyAmpersand)

        val incoming = cfg.mini.deserialize(plugin.config.getString("pm.incoming-format")!!,
            MiniUtil.placeholders("from" to Component.text(from.name), "to" to Component.text(to.name), "message" to msgComp))
        val outgoing = cfg.mini.deserialize(plugin.config.getString("pm.outgoing-format")!!,
            MiniUtil.placeholders("from" to Component.text(from.name), "to" to Component.text(to.name), "message" to msgComp))

        to.sendMessage(incoming)
        from.sendMessage(outgoing)

        // Social-Spy
        val spyFormat = plugin.config.getString("pm.spy-format")!!
        val spyCompBase = cfg.mini.deserialize(spyFormat,
            MiniUtil.placeholders("from" to Component.text(from.name), "to" to Component.text(to.name)))
        Bukkit.getOnlinePlayers()
            .filter { it.uniqueId != from.uniqueId && it.uniqueId != to.uniqueId }
            .filter { it.hasPermission("chat.message.spy") && plugin.dataStore.isSpyEnabled(it.uniqueId.toString()) }
            .forEach { spy ->
                spy.sendMessage(spyCompBase.append(msgComp))
            }

        setLastPair(from.uniqueId, to.uniqueId)
        plugin.logWriter.logPM(from.name, to.name, rawText)
    }

    fun findOnlineByName(name: String): Player? =
        Bukkit.getPlayerExact(name) ?: Bukkit.getOnlinePlayers().firstOrNull { it.name.equals(name, true) }
}
