package com.github.saintedlittle.bridge

import com.github.saintedlittle.MainActivity
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.World

class BridgeManager(
    private val plugin: MainActivity
) {
    private val cfg get() = plugin.configManager
    private val services = mutableListOf<BridgeService>()

    fun initAndStart() {
        services.clear()
        if (!cfg.bridge.enabled) return
        if (cfg.bridge.discord.enabled && !cfg.bridge.discord.token.isNullOrBlank()) {
            services += DiscordBridgeService(plugin)
        }
        if (cfg.bridge.telegram.enabled && !cfg.bridge.telegram.token.isNullOrBlank()) {
            services += TelegramBridgeService(plugin)
        }
        services.forEach { it.start() }
        plugin.logger.info("BridgeManager: ${services.map { it.javaClass.simpleName }}")
    }

    fun stopAll() {
        services.forEach { runCatching { it.stop() } }
        services.clear()
    }

    // Minecraft -> external
    fun forwardMinecraftMessage(gameChannelId: String, playerName: String, world: String?, dimension: String?, text: String) {
        if (!cfg.bridge.enabled) return

        fun renderOutbound(format: String): String {
            var s = format
                .replace("{channel}", gameChannelId)
                .replace("{player}", playerName)
                .replace("{message}", text)
            if (cfg.bridge.includeWorld) s = s.replace("{world}", world ?: "unknown")
            if (cfg.bridge.includeDimension) s = s.replace("{dimension}", dimension ?: "overworld")
            return s
        }

        // Discord
        if (cfg.bridge.discord.enabled) {
            val out = renderOutbound(cfg.bridge.discord.outboundFormat)
            services.filterIsInstance<DiscordBridgeService>().forEach { it.sendToExternal(gameChannelId, out) }
        }
        // Telegram
        if (cfg.bridge.telegram.enabled) {
            val out = renderOutbound(cfg.bridge.telegram.outboundFormat)
            services.filterIsInstance<TelegramBridgeService>().forEach { it.sendToExternal(gameChannelId, out) }
        }
    }

    // External -> Minecraft (вызови из реального обработчика Discord/TG, когда подключишь SDK)
    fun receiveExternalToMinecraft(from: String, gameChannelId: String, user: String, message: String) {
        val ch = cfg.channelById(gameChannelId) ?: return
        val format = when (from.lowercase()) {
            "discord" -> cfg.bridge.discord.inboundFormat
            "telegram" -> cfg.bridge.telegram.inboundFormat
            else -> "<gray>[<channel>]</gray> <white>{user}</white>: <message>"
        }
        val mm: MiniMessage = cfg.mini
        val rendered = mm.deserialize(
            format
                .replace("<channel>", ch.name)
                .replace("{user}", user)
                .replace("<message>", message) // безопасно, т.к. MiniMessage теги уже в формате
        )

        plugin.server.onlinePlayers.forEach { p ->
            // уважаем receivePerm и mute данного канала
            if (!ch.receivePerm.isNullOrBlank() && !p.hasPermission(ch.receivePerm)) return@forEach
            if (plugin.dataStore.isMuted(p.uniqueId.toString(), ch.id)) return@forEach
            p.sendMessage(rendered)
        }
    }

    companion object {
        fun envName(world: World?): String = when (world?.environment) {
            World.Environment.NETHER -> "nether"
            World.Environment.THE_END -> "end"
            else -> "overworld"
        }
    }
}
