package com.github.saintedlittle.util

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

object ColorUtil {
    private val ampMap = mapOf(
        "&0" to NamedTextColor.BLACK,
        "&1" to NamedTextColor.DARK_BLUE,
        "&2" to NamedTextColor.DARK_GREEN,
        "&3" to NamedTextColor.DARK_AQUA,
        "&4" to NamedTextColor.DARK_RED,
        "&5" to NamedTextColor.DARK_PURPLE,
        "&6" to NamedTextColor.GOLD,
        "&7" to NamedTextColor.GRAY,
        "&8" to NamedTextColor.DARK_GRAY,
        "&9" to NamedTextColor.BLUE,
        "&a" to NamedTextColor.GREEN,
        "&b" to NamedTextColor.AQUA,
        "&c" to NamedTextColor.RED,
        "&d" to NamedTextColor.LIGHT_PURPLE,
        "&e" to NamedTextColor.YELLOW,
        "&f" to NamedTextColor.WHITE
    )

    fun parseAny(input: String, allowHex: Boolean): TextColor? {
        val s = input.lowercase()
        ampMap[s]?.let { return it }
        if (allowHex && s.matches(Regex("^#([0-9a-f]{6})$"))) {
            val r = s.substring(1, 3).toInt(16)
            val g = s.substring(3, 5).toInt(16)
            val b = s.substring(5, 7).toInt(16)
            return TextColor.color(r, g, b)
        }
        return null
    }
}
