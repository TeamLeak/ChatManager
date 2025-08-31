package com.github.saintedlittle.config

import com.github.saintedlittle.MainActivity
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

class ConfigManager(private val plugin: MainActivity) {

    data class ChannelCfg(
        val id: String,
        val name: String,
        val trigger: String,
        val sendPerm: String?,
        val receivePerm: String?,
        val range: Int,
        val format: String,
        val slowmodeSeconds: Int,
        val slowmodeBypassPerm: String
    )

    data class HoverCfg(
        val normalLines: List<String>,
        val adminPermission: String,
        val adminLines: List<String>
    )

    data class ColorsCfg(
        val allowHex: Boolean,
        val defaultColor: String,
        val translateLegacyAmpersand: Boolean
    )

    data class MiniMsgAllow(
        val colors: Boolean,
        val decorations: Boolean,
        val reset: Boolean,
        val gradients: Boolean
    )

    data class RecentCommandsCfg(val limit: Int)

    data class LoggingCfg(
        val enabled: Boolean,
        val chatFile: String,
        val pmFile: String,
        val dateFmt: String,
        val lineDateFmt: String
    )

    // --- NEW: Bridge config ---
    data class DiscordCfg(
        val enabled: Boolean,
        val token: String?,
        val guildId: String?,
        val channelMap: Map<String, String>, // game channel id -> discord channel id
        val outboundFormat: String,          // Minecraft -> Discord
        val inboundFormat: String            // Discord -> Minecraft
    )

    data class TelegramCfg(
        val enabled: Boolean,
        val token: String?,
        val chatMap: Map<String, Long>,      // game channel id -> telegram chat id
        val outboundFormat: String,          // Minecraft -> Telegram
        val inboundFormat: String            // Telegram -> Minecraft
    )

    data class BridgeCfg(
        val enabled: Boolean,
        val discord: DiscordCfg,
        val telegram: TelegramCfg,
        val includeWorld: Boolean,           // добавлять ли мир
        val includeDimension: Boolean        // добавлять ли измерение (overworld/nether/end)
    )

    lateinit var defaultChannelId: String
        private set
    private val channelsMap = mutableMapOf<String, ChannelCfg>()
    lateinit var hover: HoverCfg
        private set
    lateinit var colors: ColorsCfg
        private set
    lateinit var miniAllow: MiniMsgAllow
        private set
    lateinit var recentCommandsCfg: RecentCommandsCfg
        private set
    lateinit var logging: LoggingCfg
        private set

    // NEW
    lateinit var bridge: BridgeCfg
        private set

    lateinit var mini: MiniMessage
        private set

    fun reload() {
        channelsMap.clear()
        val cfg = plugin.config

        defaultChannelId = cfg.getString("default-channel", "global")!!

        val chSec = cfg.getConfigurationSection("channels")
        chSec?.getKeys(false)?.forEach { id ->
            val s = chSec.getConfigurationSection(id) ?: return@forEach
            val slowSec = s.getConfigurationSection("slowmode")
            channelsMap[id] = ChannelCfg(
                id = id,
                name = s.getString("name", id)!!,
                trigger = s.getString("trigger", "!")!!,
                sendPerm = s.getString("permission.send", null),
                receivePerm = s.getString("permission.receive", null),
                range = s.getInt("range", -1),
                format = s.getString(
                    "format",
                    "<gray>[<channel>]</gray> <prefix><displayname><gray>: </gray><message>"
                )!!,
                slowmodeSeconds = slowSec?.getInt("seconds", 0) ?: 0,
                slowmodeBypassPerm = slowSec?.getString("bypass-permission", "chat.slowmode.bypass")
                    ?: "chat.slowmode.bypass"
            )
        }

        hover = HoverCfg(
            cfg.getStringList("hover.normal.lines"),
            cfg.getString("hover.admin.permission", "chat.hover.admin")!!,
            cfg.getStringList("hover.admin.lines")
        )

        colors = ColorsCfg(
            allowHex = cfg.getBoolean("colors.allow-hex", true),
            defaultColor = cfg.getString("colors.default", "<white>")!!,
            translateLegacyAmpersand = cfg.getBoolean("miniMessage.translate-legacy-ampersand", true)
        )

        miniAllow = MiniMsgAllow(
            colors = cfg.getBoolean("miniMessage.allow.colors", true),
            decorations = cfg.getBoolean("miniMessage.allow.decorations", true),
            reset = cfg.getBoolean("miniMessage.allow.reset", true),
            gradients = cfg.getBoolean("miniMessage.allow.gradients", false)
        )

        recentCommandsCfg = RecentCommandsCfg(
            limit = cfg.getInt("recent-commands.limit", 5).coerceAtLeast(0)
        )

        logging = LoggingCfg(
            enabled = cfg.getBoolean("logging.enabled", true),
            chatFile = cfg.getString("logging.chat-file", "logs/chat-{date}.log")!!,
            pmFile = cfg.getString("logging.pm-file", "logs/pm-{date}.log")!!,
            dateFmt = cfg.getString("logging.date-format", "yyyy-MM-dd")!!,
            lineDateFmt = cfg.getString("logging.line-date-format", "HH:mm:ss")!!
        )

        // --- NEW: bridge ---
        val br = cfg.getConfigurationSection("bridge")
        val disc = br?.getConfigurationSection("discord")
        val tele = br?.getConfigurationSection("telegram")
        val dMap = mutableMapOf<String, String>()
        disc?.getConfigurationSection("channelMap")?.getKeys(false)?.forEach { k ->
            dMap[k] = disc.getString("channelMap.$k")!!
        }
        val tMap = mutableMapOf<String, Long>()
        tele?.getConfigurationSection("chatMap")?.getKeys(false)?.forEach { k ->
            tMap[k] = tele.getLong("chatMap.$k")
        }

        bridge = BridgeCfg(
            enabled = br?.getBoolean("enabled", false) ?: false,
            includeWorld = br?.getBoolean("includeWorld", true) ?: true,
            includeDimension = br?.getBoolean("includeDimension", true) ?: true,
            discord = DiscordCfg(
                enabled = disc?.getBoolean("enabled", false) ?: false,
                token = disc?.getString("token", null),
                guildId = disc?.getString("guildId", null),
                channelMap = dMap,
                outboundFormat = disc?.getString("outboundFormat", "**[{channel}]** {player} ({world}): {message}")!!,
                inboundFormat = disc?.getString("inboundFormat", "<gray>[<blue>DISCORD</blue> <channel>]</gray> <white>{user}</white><gray>:</gray> <message>")!!
            ),
            telegram = TelegramCfg(
                enabled = tele?.getBoolean("enabled", false) ?: false,
                token = tele?.getString("token", null),
                chatMap = tMap,
                outboundFormat = tele?.getString("outboundFormat", "[{channel}] {player} ({world}): {message}")!!,
                inboundFormat = tele?.getString("inboundFormat", "<gray>[<green>TELEGRAM</green> <channel>]</gray> <white>{user}</white><gray>:</gray> <message>")!!
            )
        )

        mini = buildSanitizedMiniMessage(miniAllow)
    }

    init { reload() }

    fun channels(): Collection<ChannelCfg> = channelsMap.values
    fun channelById(id: String): ChannelCfg? = channelsMap[id]
    fun channelByTriggerStart(msg: String): ChannelCfg? =
        channelsMap.values.firstOrNull { msg.startsWith(it.trigger) }

    private fun buildSanitizedMiniMessage(allow: MiniMsgAllow): MiniMessage {
        val resolvers = mutableListOf<TagResolver>()
        if (allow.colors) {
            resolvers += StandardTags.color()
            resolvers += StandardTags.rainbow()
            if (allow.gradients) resolvers += StandardTags.gradient()
        }
        if (allow.decorations) resolvers += StandardTags.decorations()
        if (allow.reset) resolvers += StandardTags.reset()
        return MiniMessage.builder().tags(TagResolver.resolver(resolvers)).build()
    }
}
