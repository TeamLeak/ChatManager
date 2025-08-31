package com.github.saintedlittle.bridge

import com.github.saintedlittle.MainActivity
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.events.session.ReadyEvent          // ✅ JDA v5
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy

class DiscordBridgeService(private val plugin: MainActivity) : BridgeService {

    private var jda: net.dv8tion.jda.api.JDA? = null
    private val optedOut = mutableSetOf<Long>() // userId'ы, чьи сообщения НЕ идут в Minecraft

    override fun start() {
        val cfg = plugin.configManager.bridge.discord
        val token = cfg.token ?: return

        jda = JDABuilder.createDefault(token)
            .enableIntents(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS
            )
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setStatus(OnlineStatus.ONLINE)
            .addEventListeners(DiscordListener())
            .build()

        plugin.logger.info("DiscordBridgeService started.")
    }

    override fun stop() {
        jda?.shutdownNow()
        jda = null
        optedOut.clear()
        plugin.logger.info("DiscordBridgeService stopped.")
    }

    override fun sendToExternal(gameChannelId: String, text: String): Boolean {
        val map = plugin.configManager.bridge.discord.channelMap
        val discordChannelId = map[gameChannelId] ?: return false
        val j = jda ?: return false
        val ch = j.getTextChannelById(discordChannelId) ?: return false
        ch.sendMessage(text).queue()
        return true
    }

    /** JDA listener */
    private inner class DiscordListener : ListenerAdapter() {

        override fun onReady(event: ReadyEvent) {
            // Регистрируем /relay как глобальную слэш-команду
            event.jda.updateCommands().addCommands(
                Commands.slash("relay", "Вкл/выкл зеркалирование ваших сообщений в Minecraft")
                    .addOption(OptionType.STRING, "mode", "on|off|toggle", false)
                    .setGuildOnly(true)
            ).queue()
            plugin.logger.info("DiscordBridgeService: slash /relay registered.")
        }

        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            if (event.name != "relay") return
            val userId = event.user.idLong
            val mode = event.getOption("mode")?.asString?.lowercase() ?: "toggle"

            when (mode) {
                "on" -> optedOut.remove(userId)
                "off" -> optedOut.add(userId)
                "toggle" -> if (!optedOut.add(userId)) optedOut.remove(userId)
                else -> {
                    event.reply("Usage: /relay [on|off|toggle]").setEphemeral(true).queue()
                    return
                }
            }
            val isOn = !optedOut.contains(userId)
            event.reply("Relay is now **${if (isOn) "ON" else "OFF"}** for <@${userId}>")
                .setEphemeral(true).queue()
        }

        override fun onMessageReceived(event: MessageReceivedEvent) {
            val j = this@DiscordBridgeService.jda ?: return  // ✅ явная ссылка на внешнее поле
            if (event.author.isBot || event.isWebhookMessage) return

            val map = plugin.configManager.bridge.discord.channelMap
            val gameChannelId = map.entries.firstOrNull { it.value == event.channel.id }?.key ?: return

            // /relay выключает зеркалирование из Discord -> Minecraft
            if (optedOut.contains(event.author.idLong)) return

            val content = event.message.contentDisplay.trim()
            if (content.isEmpty()) return

            plugin.bridgeManager?.receiveExternalToMinecraft(
                from = "discord",
                gameChannelId = gameChannelId,
                user = event.author.name,
                message = content
            )
        }
    }
}
