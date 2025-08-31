package com.github.saintedlittle.data

import com.github.saintedlittle.MainActivity
import java.sql.PreparedStatement
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PlayerDataStore(private val plugin: MainActivity) {
    private val db = Database(plugin)
    private val lock = ReentrantLock()

    private data class Keys(
        val upsertPlayer: PreparedStatement,
        val upsertPrefs: PreparedStatement,
        val getColor: PreparedStatement,
        val setColor: PreparedStatement,
        val getNick: PreparedStatement,
        val setNick: PreparedStatement,
        val getDefaultChannel: PreparedStatement,
        val setDefaultChannel: PreparedStatement,
        val getSpy: PreparedStatement,
        val setSpy: PreparedStatement,
        val insertIgnore: PreparedStatement,
        val deleteIgnore: PreparedStatement,
        val checkIgnore: PreparedStatement,
        val listIgnore: PreparedStatement,
        val insertMute: PreparedStatement,
        val deleteMute: PreparedStatement,
        val checkMute: PreparedStatement,
        val listMute: PreparedStatement,
        val insertRecent: PreparedStatement,
        val listRecentLimit: PreparedStatement,
        val trimRecent: PreparedStatement,
        val listAllNicks: PreparedStatement,
        val nickExists: PreparedStatement,
        val nickOwner: PreparedStatement,
        // NEW
        val getRelayOptOut: PreparedStatement,
        val setRelayOptOut: PreparedStatement
    )

    private lateinit var ps: Keys

    init {
        db.withConn { conn ->
            ps = Keys(
                upsertPlayer = conn.prepareStatement("INSERT OR IGNORE INTO players(uuid, created_at, updated_at) VALUES(?, strftime('%s','now'), strftime('%s','now'));"),
                upsertPrefs  = conn.prepareStatement("INSERT OR IGNORE INTO player_prefs(uuid, spy, relay_optout) VALUES(?, 0, 0);"),
                getColor     = conn.prepareStatement("SELECT color FROM player_prefs WHERE uuid=?;"),
                setColor     = conn.prepareStatement("UPDATE player_prefs SET color=? WHERE uuid=?;"),
                getNick      = conn.prepareStatement("SELECT nick FROM player_prefs WHERE uuid=?;"),
                setNick      = conn.prepareStatement("UPDATE player_prefs SET nick=? WHERE uuid=?;"),
                getDefaultChannel = conn.prepareStatement("SELECT default_channel FROM player_prefs WHERE uuid=?;"),
                setDefaultChannel = conn.prepareStatement("UPDATE player_prefs SET default_channel=? WHERE uuid=?;"),
                getSpy       = conn.prepareStatement("SELECT spy FROM player_prefs WHERE uuid=?;"),
                setSpy       = conn.prepareStatement("UPDATE player_prefs SET spy=? WHERE uuid=?;"),

                insertIgnore = conn.prepareStatement("INSERT OR IGNORE INTO ignores(owner_uuid, target_uuid) VALUES(?, ?);"),
                deleteIgnore = conn.prepareStatement("DELETE FROM ignores WHERE owner_uuid=? AND target_uuid=?;"),
                checkIgnore  = conn.prepareStatement("SELECT 1 FROM ignores WHERE owner_uuid=? AND target_uuid=?;"),
                listIgnore   = conn.prepareStatement("SELECT target_uuid FROM ignores WHERE owner_uuid=?;"),

                insertMute   = conn.prepareStatement("INSERT OR IGNORE INTO mutes(owner_uuid, channel_id) VALUES(?, ?);"),
                deleteMute   = conn.prepareStatement("DELETE FROM mutes WHERE owner_uuid=? AND channel_id=?;"),
                checkMute    = conn.prepareStatement("SELECT 1 FROM mutes WHERE owner_uuid=? AND channel_id=?;"),
                listMute     = conn.prepareStatement("SELECT channel_id FROM mutes WHERE owner_uuid=?;"),

                insertRecent = conn.prepareStatement("INSERT INTO recent_commands(uuid, ts, cmd) VALUES(?, strftime('%s','now')*1000, ?);"),
                listRecentLimit = conn.prepareStatement("SELECT cmd FROM recent_commands WHERE uuid=? ORDER BY ts DESC LIMIT ?;"),
                trimRecent      = conn.prepareStatement("""
                    DELETE FROM recent_commands
                    WHERE uuid = ?
                      AND ts NOT IN (SELECT ts FROM recent_commands WHERE uuid = ? ORDER BY ts DESC LIMIT ?);
                """.trimIndent()),

                listAllNicks = conn.prepareStatement("SELECT uuid, nick FROM player_prefs WHERE nick IS NOT NULL AND nick <> '';"),
                nickExists   = conn.prepareStatement("SELECT 1 FROM player_prefs WHERE nick = ? LIMIT 1;"),
                nickOwner    = conn.prepareStatement("SELECT uuid FROM player_prefs WHERE nick = ? LIMIT 1;"),

                getRelayOptOut = conn.prepareStatement("SELECT relay_optout FROM player_prefs WHERE uuid = ?;"),
                setRelayOptOut = conn.prepareStatement("UPDATE player_prefs SET relay_optout = ? WHERE uuid = ?;")
            )
        }
    }

    private fun ensureRow(uuid: String) = lock.withLock {
        db.withTx {
            ps.upsertPlayer.setString(1, uuid); ps.upsertPlayer.executeUpdate()
            ps.upsertPrefs.setString(1, uuid);  ps.upsertPrefs.executeUpdate()
        }
    }

    fun getColor(uuid: String): String? = lock.withLock {
        ensureRow(uuid)
        ps.getColor.setString(1, uuid)
        ps.getColor.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
    }
    fun setColor(uuid: String, value: String?) = lock.withLock {
        ensureRow(uuid)
        ps.setColor.setString(1, value)
        ps.setColor.setString(2, uuid)
        ps.setColor.executeUpdate()
    }

    fun getNick(uuid: String): String? = lock.withLock {
        ensureRow(uuid)
        ps.getNick.setString(1, uuid)
        ps.getNick.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
    }
    fun setNick(uuid: String, value: String?) = lock.withLock {
        ensureRow(uuid)
        ps.setNick.setString(1, value)
        ps.setNick.setString(2, uuid)
        ps.setNick.executeUpdate()
    }

    fun allNicks(): Map<UUID, String> = lock.withLock {
        val out = LinkedHashMap<UUID, String>()
        ps.listAllNicks.executeQuery().use { rs ->
            while (rs.next()) {
                val uuid = runCatching { UUID.fromString(rs.getString(1)) }.getOrNull() ?: continue
                val nick = rs.getString(2)
                out[uuid] = nick
            }
        }
        out
    }
    fun nickExistsExact(nick: String): Boolean = lock.withLock {
        ps.nickExists.setString(1, nick)
        ps.nickExists.executeQuery().use { it.next() }
    }
    fun nickOwner(nick: String): UUID? = lock.withLock {
        ps.nickOwner.setString(1, nick)
        ps.nickOwner.executeQuery().use { rs ->
            if (rs.next()) runCatching { UUID.fromString(rs.getString(1)) }.getOrNull() else null
        }
    }

    // Relay opt-out
    fun isRelayOptOut(uuid: String): Boolean = lock.withLock {
        ensureRow(uuid)
        ps.getRelayOptOut.setString(1, uuid)
        ps.getRelayOptOut.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) != 0 else false }
    }
    fun setRelayOptOut(uuid: String, optout: Boolean) = lock.withLock {
        ensureRow(uuid)
        ps.setRelayOptOut.setInt(1, if (optout) 1 else 0)
        ps.setRelayOptOut.setString(2, uuid)
        ps.setRelayOptOut.executeUpdate()
    }

    // Recent commands
    fun appendRecentCommand(uuid: String, cmd: String, limit: Int) = lock.withLock {
        ensureRow(uuid)
        db.withTx {
            ps.insertRecent.setString(1, uuid)
            ps.insertRecent.setString(2, cmd)
            ps.insertRecent.executeUpdate()
            if (limit > 0) {
                ps.trimRecent.setString(1, uuid)
                ps.trimRecent.setString(2, uuid)
                ps.trimRecent.setInt(3, limit)
                ps.trimRecent.executeUpdate()
            }
        }
    }
    fun recentCommands(uuid: String, limit: Int): List<String> = lock.withLock {
        ensureRow(uuid)
        ps.listRecentLimit.setString(1, uuid)
        ps.listRecentLimit.setInt(2, if (limit <= 0) 50 else limit)
        ps.listRecentLimit.executeQuery().use { rs ->
            val out = mutableListOf<String>()
            while (rs.next()) out += rs.getString(1)
            out
        }
    }

    // Ignore
    fun ignoreList(uuid: String): MutableSet<UUID> = lock.withLock {
        ensureRow(uuid)
        ps.listIgnore.setString(1, uuid)
        ps.listIgnore.executeQuery().use { rs ->
            val out = mutableSetOf<UUID>()
            while (rs.next()) runCatching { UUID.fromString(rs.getString(1)) }.getOrNull()?.let(out::add)
            out
        }
    }
    fun isIgnoring(uuid: String, target: UUID): Boolean = lock.withLock {
        ensureRow(uuid)
        ps.checkIgnore.setString(1, uuid)
        ps.checkIgnore.setString(2, target.toString())
        ps.checkIgnore.executeQuery().use { it.next() }
    }
    fun addIgnore(uuid: String, target: UUID): Boolean = lock.withLock {
        ensureRow(uuid)
        ps.insertIgnore.setString(1, uuid)
        ps.insertIgnore.setString(2, target.toString())
        ps.insertIgnore.executeUpdate() > 0
    }
    fun removeIgnore(uuid: String, target: UUID): Boolean = lock.withLock {
        ps.deleteIgnore.setString(1, uuid)
        ps.deleteIgnore.setString(2, target.toString())
        ps.deleteIgnore.executeUpdate() > 0
    }

    // Mutes
    fun mutedChannels(uuid: String): MutableSet<String> = lock.withLock {
        ensureRow(uuid)
        ps.listMute.setString(1, uuid)
        ps.listMute.executeQuery().use { rs ->
            val out = mutableSetOf<String>()
            while (rs.next()) out += rs.getString(1)
            out
        }
    }
    fun isMuted(uuid: String, channelId: String): Boolean = lock.withLock {
        ensureRow(uuid)
        ps.checkMute.setString(1, uuid)
        ps.checkMute.setString(2, channelId)
        ps.checkMute.executeQuery().use { it.next() }
    }
    fun addMute(uuid: String, channelId: String): Boolean = lock.withLock {
        ensureRow(uuid)
        ps.insertMute.setString(1, uuid)
        ps.insertMute.setString(2, channelId)
        ps.insertMute.executeUpdate() > 0
    }
    fun removeMute(uuid: String, channelId: String): Boolean = lock.withLock {
        ps.deleteMute.setString(1, uuid)
        ps.deleteMute.setString(2, channelId)
        ps.deleteMute.executeUpdate() > 0
    }

    // Spy
    fun isSpyEnabled(uuid: String): Boolean = lock.withLock {
        ensureRow(uuid)
        ps.getSpy.setString(1, uuid)
        ps.getSpy.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) != 0 else false }
    }
    fun setSpyEnabled(uuid: String, enabled: Boolean) = lock.withLock {
        ensureRow(uuid)
        ps.setSpy.setInt(1, if (enabled) 1 else 0)
        ps.setSpy.setString(2, uuid)
        ps.setSpy.executeUpdate()
    }

    // Default channel
    fun getDefaultChannel(uuid: String): String? = lock.withLock {
        ensureRow(uuid)
        ps.getDefaultChannel.setString(1, uuid)
        ps.getDefaultChannel.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
    }
    fun setDefaultChannel(uuid: String, channelId: String?) = lock.withLock {
        ensureRow(uuid)
        ps.setDefaultChannel.setString(1, channelId)
        ps.setDefaultChannel.setString(2, uuid)
        ps.setDefaultChannel.executeUpdate()
    }

    fun saveAll() { /* no-op */ }
    fun close() { db.close() }
}
