package com.github.saintedlittle.util

import com.github.saintedlittle.MainActivity
import net.luckperms.api.LuckPerms
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object FormatUtil {

    fun luckPermsPrefixSuffix(lp: LuckPerms?, player: Player): Pair<Component, Component> {
        if (lp == null) return Component.empty() to Component.empty()
        return try {
            val adapter = lp.getPlayerAdapter(Player::class.java)
            val meta = adapter.getMetaData(player)
            val legacy = LegacyComponentSerializer.legacyAmpersand()
            val prefix = meta.prefix?.let { legacy.deserialize(it) } ?: Component.empty()
            val suffix = meta.suffix?.let { legacy.deserialize(it) } ?: Component.empty()
            prefix to suffix
        } catch (_: Exception) {
            Component.empty() to Component.empty()
        }
    }

    /**
     * Build hover text for a 'viewer' (normal vs admin section).
     * Supports PlaceholderAPI placeholders if present.
     * Replaces {uuid} and {name}.
     */
    fun buildHover(plugin: MainActivity, subject: Player, viewer: Audience): Component {
        val legacy = LegacyComponentSerializer.legacyAmpersand()
        val normalLines = plugin.config.getStringList("hover.normal.lines")
        val adminPerm: String = plugin.config.getString("hover.admin.permission", "chat.hover.admin")!!
        val adminLines = plugin.config.getStringList("hover.admin.lines")

        val lines: List<String> =
            if (viewer is Player && viewer.hasPermission(adminPerm)) adminLines else normalLines

        val joined = lines.joinToString("\n") { raw ->
            var s = raw.replace("{uuid}", subject.uniqueId.toString())
                .replace("{name}", subject.name)
            if (plugin.isPlaceholderAPI()) {
                s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(subject, s)
            }
            s
        }
        return legacy.deserialize(joined)
    }

    /**
     * Render a format string with {tokens}, static text supports & color codes.
     * Example: "&7[{channel}] {prefix}{displayname}&7: {message}"
     */
    fun renderFormat(legacy: LegacyComponentSerializer, format: String, tokens: Map<String, Component>): Component {
        val regex = Regex("\\{(\\w+)}")
        var last = 0
        var comp = Component.empty()

        val matches = regex.findAll(format).toList()
        for (m in matches) {
            val start = m.range.first
            if (start > last) {
                val staticText = format.substring(last, start)
                if (staticText.isNotEmpty()) comp = comp.append(legacy.deserialize(staticText))
            }
            val key = m.groupValues[1]
            comp = comp.append(tokens[key] ?: Component.text("{$key}"))
            last = m.range.last + 1
        }
        if (last < format.length) {
            val tail = format.substring(last)
            if (tail.isNotEmpty()) comp = comp.append(legacy.deserialize(tail))
        }
        return comp
    }
}
