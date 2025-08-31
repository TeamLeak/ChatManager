package com.github.saintedlittle.bridge

import com.github.saintedlittle.MainActivity
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault
import org.telegram.telegrambots.meta.generics.TelegramClient
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import java.util.concurrent.ConcurrentHashMap

class TelegramBridgeService(private val plugin: MainActivity) : BridgeService {

    private var application: TelegramBotsLongPollingApplication? = null
    private var telegramClient: TelegramClient? = null

    override fun start() {
        val cfg = plugin.configManager.bridge.telegram
        val token = cfg.token ?: return

        telegramClient = OkHttpTelegramClient(token)

        val bot = Bot(plugin, telegramClient!!)
        application = TelegramBotsLongPollingApplication()
        application?.registerBot(token, bot)

        // /relay команда
        val cmds = listOf(
            BotCommand("relay", "Вкл/выкл зеркалирование ваших сообщений в Minecraft: /relay on|off|toggle")
        )
        runCatching {
            telegramClient?.execute(SetMyCommands(cmds, BotCommandScopeDefault(), null))
        }

        plugin.logger.info("TelegramBridgeService started.")
    }

    override fun stop() {
        application?.stop()
        application = null
        telegramClient = null
        plugin.logger.info("TelegramBridgeService stopped.")
    }

    override fun sendToExternal(gameChannelId: String, text: String): Boolean {
        val map = plugin.configManager.bridge.telegram.chatMap
        val chatId = map[gameChannelId] ?: return false
        val client = telegramClient ?: return false
        safeSend(client, chatId, text)
        return true
    }

    private fun safeSend(client: TelegramClient, chatId: Long, text: String) {
        runCatching {
            client.execute(SendMessage(chatId.toString(), text))
        }.onFailure {
            plugin.logger.warning("Telegram send failed: ${it.message}")
        }
    }

    // --- Bot impl ---
    private class Bot(
        private val plugin: MainActivity,
        private val telegramClient: TelegramClient
    ) : LongPollingSingleThreadUpdateConsumer {

        // Пользователи, чьи сообщения НЕ идут в Minecraft
        private val optedOut = ConcurrentHashMap.newKeySet<Long>()

        override fun consume(update: Update) {
            val msg = update.message ?: update.editedMessage ?: return
            val chatId = msg.chatId
            val text = msg.text ?: return

            // Карта соответствий каналов
            val map = plugin.configManager.bridge.telegram.chatMap
            val gameChannelId = map.entries.firstOrNull { it.value == chatId }?.key

            // Команда /relay
            if (text.startsWith("/relay")) {
                val parts = text.trim().split("\\s+".toRegex(), limit = 2)
                val arg = parts.getOrNull(1)?.lowercase() ?: "toggle"
                val userId = msg.from?.id?.toLong() ?: return

                when (arg) {
                    "on" -> optedOut.remove(userId)
                    "off" -> optedOut.add(userId)
                    "toggle" -> if (!optedOut.add(userId)) optedOut.remove(userId)
                    else -> {
                        safeReply(chatId, "Usage: /relay on|off|toggle")
                        return
                    }
                }
                val isOn = !optedOut.contains(userId)
                val who = msg.from?.userName ?: msg.from?.firstName ?: "user"
                safeReply(chatId, "Relay is now ${if (isOn) "ON" else "OFF"} for @$who")
                return
            }

            // Если чат не привязан — игнор
            if (gameChannelId == null) return

            val senderId = msg.from?.id?.toLong() ?: return
            if (optedOut.contains(senderId)) return

            val content = text.trim()
            if (content.isEmpty()) return

            // Forward в Minecraft
            plugin.bridgeManager?.receiveExternalToMinecraft(
                from = "telegram",
                gameChannelId = gameChannelId,
                user = msg.from?.userName ?: msg.from?.firstName ?: "tg_user",
                message = content
            )
        }

        private fun safeReply(chatId: Long, text: String) {
            runCatching {
                telegramClient.execute(SendMessage(chatId.toString(), text))
            }.onFailure {
                plugin.logger.warning("Telegram send failed: ${it.message}")
            }
        }
    }
}