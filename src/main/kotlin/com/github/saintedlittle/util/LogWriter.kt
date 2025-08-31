package com.github.saintedlittle.util

import com.github.saintedlittle.MainActivity
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LogWriter(private val plugin: MainActivity) {

    private var currentDate: LocalDate = LocalDate.now()
    private var chatWriter: FileWriter? = null
    private var pmWriter: FileWriter? = null

    private val cfg get() = plugin.configManager.logging
    private val dateFmt = DateTimeFormatter.ofPattern(cfg.dateFmt)
    private val timeFmt = DateTimeFormatter.ofPattern(cfg.lineDateFmt)

    init {
        if (cfg.enabled) openWritersFor(currentDate)
    }

    fun close() {
        chatWriter?.close(); chatWriter = null
        pmWriter?.close(); pmWriter = null
    }

    private fun ensureDate() {
        if (!cfg.enabled) return
        val now = LocalDate.now()
        if (now != currentDate) {
            close()
            currentDate = now
            openWritersFor(now)
        }
    }

    private fun openWritersFor(date: LocalDate) {
        val dateStr = date.format(dateFmt)
        val chatPath = cfg.chatFile.replace("{date}", dateStr)
        val pmPath = cfg.pmFile.replace("{date}", dateStr)
        fun open(path: String): FileWriter {
            val f = File(plugin.dataFolder, path)
            f.parentFile?.mkdirs()
            if (!f.exists()) f.createNewFile()
            return FileWriter(f, true)
        }
        chatWriter = open(chatPath)
        pmWriter = open(pmPath)
    }

    private fun prefix(): String = LocalTime.now().format(timeFmt)

    @Synchronized
    fun logChat(channelId: String, player: String, message: String) {
        if (!cfg.enabled) return
        ensureDate()
        chatWriter?.apply {
            write("[${prefix()}][$channelId][$player] $message\n")
            flush()
        }
    }

    @Synchronized
    fun logPM(from: String, to: String, message: String) {
        if (!cfg.enabled) return
        ensureDate()
        pmWriter?.apply {
            write("[${prefix()}][$from -> $to] $message\n")
            flush()
        }
    }
}
