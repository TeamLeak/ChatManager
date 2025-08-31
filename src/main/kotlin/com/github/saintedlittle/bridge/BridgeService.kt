package com.github.saintedlittle.bridge

interface BridgeService {
    fun start()
    fun stop()

    /**
     * Отправить текст в «внешний» канал (Discord/Telegram) по игровому channelId.
     * Возвращает true, если получилось.
     */
    fun sendToExternal(gameChannelId: String, text: String): Boolean
}
