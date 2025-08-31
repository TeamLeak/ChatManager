package com.github.saintedlittle.chat

import com.github.saintedlittle.MainActivity
import org.bukkit.configuration.ConfigurationSection

class ChannelService(private val plugin: MainActivity) {

    private val channelMap = mutableMapOf<String, Channel>()
    val defaultChannelId: String = plugin.config.getString("default-channel", "global")!!

    init { reload() }

    fun reload() {
        channelMap.clear()
        val sec: ConfigurationSection = plugin.config.getConfigurationSection("channels")
            ?: return
        for (id in sec.getKeys(false)) {
            val c = sec.getConfigurationSection(id) ?: continue
            channelMap[id] = Channel(
                id = id,
                name = c.getString("name", id)!!,
                trigger = c.getString("trigger", "!")!!,
                sendPerm = c.getString("permission.send", null),
                receivePerm = c.getString("permission.receive", null),
                range = c.getInt("range", -1),
                format = c.getString("format", "&7[{channel}] {prefix}{displayname}&7: {message}")!!
            )
        }
    }

    fun byTriggerStart(msg: String): Channel? =
        channelMap.values.firstOrNull { msg.startsWith(it.trigger) }

    fun byId(id: String): Channel? = channelMap[id]

    fun all(): Collection<Channel> = channelMap.values
}
