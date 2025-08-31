package com.github.saintedlittle.data

import com.github.saintedlittle.MainActivity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class RecentCommandsTracker(private val plugin: MainActivity) : Listener {
    @EventHandler
    fun onCommand(e: PlayerCommandPreprocessEvent) {
        val p: Player = e.player
        val limit = plugin.configManager.recentCommandsCfg.limit
        if (limit <= 0) return
        plugin.dataStore.appendRecentCommand(p.uniqueId.toString(), e.message, limit)
    }
}
