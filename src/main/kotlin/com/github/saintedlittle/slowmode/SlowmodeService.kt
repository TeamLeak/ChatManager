package com.github.saintedlittle.slowmode

import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SlowmodeService {
    // key = "<uuid>:<channelId>"
    private val lastSent: MutableMap<String, Long> = ConcurrentHashMap()

    private fun key(uuid: UUID, channelId: String) = "${uuid}:$channelId"

    /**
     * @return seconds left or 0 if allowed now
     */
    fun secondsLeft(uuid: UUID, channelId: String, cooldownSec: Int, nowMs: Long = System.currentTimeMillis()): Int {
        if (cooldownSec <= 0) return 0
        val k = key(uuid, channelId)
        val last = lastSent[k] ?: return 0
        val diff = (nowMs - last) / 1000
        val left = cooldownSec - diff.toInt()
        return if (left > 0) left else 0
    }

    fun markSent(uuid: UUID, channelId: String, atMs: Long = System.currentTimeMillis()) {
        lastSent[key(uuid, channelId)] = atMs
    }
}
