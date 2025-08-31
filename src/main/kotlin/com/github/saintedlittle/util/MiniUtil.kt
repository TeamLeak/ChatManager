package com.github.saintedlittle.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object MiniUtil {
    private val legacyAmp = LegacyComponentSerializer.legacyAmpersand()

    fun deserializeUserText(
        mini: MiniMessage,
        raw: String,
        translateLegacyAmp: Boolean
    ): Component {
        return if (translateLegacyAmp && hasLegacyCodes(raw)) {
            legacyAmp.deserialize(raw)
        } else {
            mini.deserialize(raw)
        }
    }

    fun hasLegacyCodes(s: String): Boolean =
        Regex("(?i)&[0-9A-FK-ORX]").containsMatchIn(s)

    fun deserializeFormat(
        mini: MiniMessage,
        format: String,
        vararg placeholders: TagResolver
    ): Component = mini.deserialize(format, TagResolver.resolver(*placeholders))

    fun placeholders(vararg pairs: Pair<String, Component>): TagResolver =
        TagResolver.resolver(pairs.map { Placeholder.component(it.first, it.second) })
}
