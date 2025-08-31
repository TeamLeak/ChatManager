package com.github.saintedlittle.chat

import com.github.saintedlittle.MainActivity
import com.github.saintedlittle.bridge.BridgeManager
import com.github.saintedlittle.util.LPUtil
import com.github.saintedlittle.util.MiniUtil
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ChatListener(private val plugin: MainActivity) : Listener {

    @EventHandler
    fun onAsyncChat(e: AsyncChatEvent) {
        val player = e.player
        val cfg = plugin.configManager

        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(e.message())

        val channel = cfg.channelByTriggerStart(plain)
            ?: plugin.dataStore.getDefaultChannel(player.uniqueId.toString())?.let { cfg.channelById(it) }
            ?: cfg.channelById(cfg.defaultChannelId)
            ?: return

        val smSeconds = channel.slowmodeSeconds
        if (smSeconds > 0 && !player.hasPermission(channel.slowmodeBypassPerm)) {
            val left = plugin.slowmode.secondsLeft(player.uniqueId, channel.id, smSeconds)
            if (left > 0) {
                val msg = plugin.config.getString("messages.slowmode-wait")!!
                    .replace("{seconds}", left.toString())
                    .replace("{channel}", channel.name)
                player.sendMessage(plugin.configManager.mini.deserialize(msg))
                e.isCancelled = true
                return
            }
        }

        if (!channel.sendPerm.isNullOrBlank() && !player.hasPermission(channel.sendPerm)) {
            player.sendMessage(cfg.mini.deserialize(plugin.config.getString("messages.no-permission")!!))
            e.isCancelled = true; return
        }

        val raw = if (plain.startsWith(channel.trigger)) plain.removePrefix(channel.trigger).trimStart() else plain

        var messageComp = MiniUtil.deserializeUserText(cfg.mini, raw, cfg.colors.translateLegacyAmpersand)
        if (!raw.contains('<') && !MiniUtil.hasLegacyCodes(raw)) {
            val savedColor = plugin.dataStore.getColor(player.uniqueId.toString()) ?: cfg.colors.defaultColor
            messageComp = cfg.mini.deserialize(savedColor).append(messageComp)
        }

        val viewers = e.viewers()
        viewers.removeIf { a ->
            when (a) {
                is Player -> {
                    if (!channel.receivePerm.isNullOrBlank() && !a.hasPermission(channel.receivePerm)) return@removeIf true
                    if (channel.range > 0 && (a.world != player.world || a.location.distanceSquared(player.location) > (channel.range * channel.range))) return@removeIf true
                    if (plugin.dataStore.isMuted(a.uniqueId.toString(), channel.id)) return@removeIf true
                    if (!player.hasPermission("chat.ignore.bypass") && plugin.dataStore.isIgnoring(a.uniqueId.toString(), player.uniqueId)) return@removeIf true
                    false
                }
                else -> false
            }
        }

        fun hoverFor(viewer: Audience): Component {
            val lines = if (viewer is Player && viewer.hasPermission(cfg.hover.adminPermission)) cfg.hover.adminLines else cfg.hover.normalLines
            val joined = lines.joinToString("\n") { rawLine ->
                var s = rawLine.replace("{uuid}", player.uniqueId.toString())
                    .replace("{name}", player.name)
                    .replace("{recent_commands}", plugin.dataStore.recentCommands(player.uniqueId.toString(), cfg.recentCommandsCfg.limit).joinToString(", "))
                if (plugin.isPlaceholderAPI()) s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, s)
                s
            }
            return cfg.mini.deserialize(joined)
        }

        val (lpPrefix, lpSuffix) = LPUtil.prefixSuffix(plugin.luckPerms, player)

        e.renderer { _, displayName, _, viewer ->
            val displayBase = plugin.dataStore.getNick(player.uniqueId.toString())?.let { Component.text(it) } ?: displayName

            val nameClickable = displayBase
                .hoverEvent(HoverEvent.showText(hoverFor(viewer)))
                .clickEvent(ClickEvent.suggestCommand("/msg ${player.name} "))

            val msgClickable = messageComp.clickEvent(ClickEvent.suggestCommand("/reply "))

            val placeholders = MiniUtil.placeholders(
                "channel" to Component.text(channel.name, NamedTextColor.GRAY),
                "prefix" to lpPrefix,
                "suffix" to lpSuffix,
                "displayname" to nameClickable,
                "message" to msgClickable
            )

            MiniUtil.deserializeFormat(cfg.mini, channel.format, placeholders)
        }

        // MC -> External bridge (respect opt-out)
        if (plugin.configManager.bridge.enabled && !plugin.dataStore.isRelayOptOut(player.uniqueId.toString())) {
            val worldName = player.world?.name
            val dim = BridgeManager.envName(player.world)
            plugin.bridgeManager?.forwardMinecraftMessage(channel.id, player.name, worldName, dim, raw)
        }

        e.message(Component.text(raw))
        plugin.slowmode.markSent(player.uniqueId, channel.id)
        plugin.logWriter.logChat(channel.id, player.name, raw)
    }
}
