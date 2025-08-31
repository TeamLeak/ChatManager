package com.github.saintedlittle.data

import com.github.saintedlittle.MainActivity
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Database(private val plugin: MainActivity) {
    private val lock = ReentrantLock()
    private val dbFile = File(plugin.dataFolder, "playerdata.db")
    lateinit var conn: Connection
        private set

    init {
        plugin.dataFolder.mkdirs()
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON;")
            st.execute("PRAGMA journal_mode = WAL;")
            st.execute("PRAGMA synchronous = NORMAL;")
            st.execute("PRAGMA temp_store = MEMORY;")
            st.execute("PRAGMA cache_size = -200000;")
        }
        migrate()
    }

    fun <T> withTx(body: (Connection) -> T): T {
        lock.withLock {
            val auto = conn.autoCommit
            conn.autoCommit = false
            return try {
                val res = body(conn)
                conn.commit()
                res
            } catch (e: Throwable) {
                try { conn.rollback() } catch (_: Throwable) {}
                throw e
            } finally {
                conn.autoCommit = auto
            }
        }
    }

    fun withConn(body: (Connection) -> Unit) = lock.withLock { body(conn) }

    fun close() { lock.withLock { try { conn.close() } catch (_: Exception) {} } }

    private fun currentVersion(): Int {
        return try {
            conn.createStatement().use { st ->
                st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version(
                      version INTEGER NOT NULL PRIMARY KEY,
                      applied_at INTEGER NOT NULL
                    );
                """.trimIndent())
            }
            conn.createStatement().use { st2 ->
                st2.executeQuery("SELECT COALESCE(MAX(version), 0) AS v FROM schema_version").use { rs ->
                    if (rs.next()) rs.getInt("v") else 0
                }
            }
        } catch (e: SQLException) { 0 }
    }

    private fun apply(version: Int, block: (Statement) -> Unit) {
        conn.createStatement().use { st ->
            block(st)
            conn.prepareStatement("INSERT INTO schema_version(version, applied_at) VALUES(?, ?)").use { ps ->
                ps.setInt(1, version)
                ps.setLong(2, Instant.now().epochSecond)
                ps.executeUpdate()
            }
        }
    }

    /**
     * v1: players, ignores, mutes, recent_commands
     * v2: triggers created_at/updated_at
     * v3: player_prefs (color, nick, default_channel, spy)
     * v4: player_prefs.relay_optout
     */
    private fun migrate() = withTx {
        when (val v = currentVersion()) {
            0 -> {
                apply(1) { st ->
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS players(
                          uuid TEXT PRIMARY KEY,
                          created_at INTEGER NOT NULL,
                          updated_at INTEGER NOT NULL
                        );
                    """.trimIndent())
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS ignores(
                          owner_uuid TEXT NOT NULL,
                          target_uuid TEXT NOT NULL,
                          PRIMARY KEY(owner_uuid, target_uuid),
                          FOREIGN KEY(owner_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
                          FOREIGN KEY(target_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                        );
                    """.trimIndent())
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS mutes(
                          owner_uuid TEXT NOT NULL,
                          channel_id TEXT NOT NULL,
                          PRIMARY KEY(owner_uuid, channel_id),
                          FOREIGN KEY(owner_uuid) REFERENCES players(uuid) ON DELETE CASCADE
                        );
                    """.trimIndent())
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS recent_commands(
                          uuid TEXT NOT NULL,
                          ts INTEGER NOT NULL,
                          cmd TEXT NOT NULL,
                          FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE
                        );
                    """.trimIndent())
                    st.execute("CREATE INDEX IF NOT EXISTS idx_recent_uuid_ts ON recent_commands(uuid, ts DESC);")
                    st.execute("CREATE INDEX IF NOT EXISTS idx_ignores_owner ON ignores(owner_uuid);")
                    st.execute("CREATE INDEX IF NOT EXISTS idx_mutes_owner ON mutes(owner_uuid);")
                    st.execute("CREATE INDEX IF NOT EXISTS idx_ignores_target ON ignores(target_uuid);")
                }
                apply(2) { st ->
                    st.execute("""
                        CREATE TRIGGER IF NOT EXISTS trg_players_set_created
                        AFTER INSERT ON players
                        BEGIN
                          UPDATE players SET created_at = strftime('%s','now'), updated_at = strftime('%s','now') WHERE uuid = NEW.uuid;
                        END;
                    """.trimIndent())
                    st.execute("""
                        CREATE TRIGGER IF NOT EXISTS trg_players_touch_update
                        AFTER UPDATE ON players
                        BEGIN
                          UPDATE players SET updated_at = strftime('%s','now') WHERE uuid = NEW.uuid;
                        END;
                    """.trimIndent())
                }
                apply(3) { st ->
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS player_prefs(
                          uuid TEXT PRIMARY KEY,
                          color TEXT,
                          nick TEXT,
                          default_channel TEXT,
                          spy INTEGER NOT NULL DEFAULT 0 CHECK(spy IN (0,1)),
                          FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE
                        );
                    """.trimIndent())
                    st.execute("CREATE INDEX IF NOT EXISTS idx_players_time ON players(updated_at DESC);")
                    st.execute("CREATE INDEX IF NOT EXISTS idx_recent_ts ON recent_commands(ts DESC);")
                }
                apply(4) { st ->
                    st.execute("ALTER TABLE player_prefs ADD COLUMN relay_optout INTEGER NOT NULL DEFAULT 0 CHECK(relay_optout IN (0,1));")
                }
            }
            1 -> {
                apply(2) { st ->
                    st.execute("""
                        CREATE TRIGGER IF NOT EXISTS trg_players_set_created
                        AFTER INSERT ON players
                        BEGIN
                          UPDATE players SET created_at = strftime('%s','now'), updated_at = strftime('%s','now') WHERE uuid = NEW.uuid;
                        END;
                    """.trimIndent())
                    st.execute("""
                        CREATE TRIGGER IF NOT EXISTS trg_players_touch_update
                        AFTER UPDATE ON players
                        BEGIN
                          UPDATE players SET updated_at = strftime('%s','now') WHERE uuid = NEW.uuid;
                        END;
                    """.trimIndent())
                }
                apply(3) { st ->
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS player_prefs(
                          uuid TEXT PRIMARY KEY,
                          color TEXT,
                          nick TEXT,
                          default_channel TEXT,
                          spy INTEGER NOT NULL DEFAULT 0 CHECK(spy IN (0,1)),
                          FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE
                        );
                    """.trimIndent())
                    st.execute("CREATE INDEX IF NOT EXISTS idx_players_time ON players(updated_at DESC);")
                    st.execute("CREATE INDEX IF NOT EXISTS idx_recent_ts ON recent_commands(ts DESC);")
                }
                apply(4) { st ->
                    st.execute("ALTER TABLE player_prefs ADD COLUMN relay_optout INTEGER NOT NULL DEFAULT 0 CHECK(relay_optout IN (0,1));")
                }
            }
            2 -> {
                apply(3) { st ->
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS player_prefs(
                          uuid TEXT PRIMARY KEY,
                          color TEXT,
                          nick TEXT,
                          default_channel TEXT,
                          spy INTEGER NOT NULL DEFAULT 0 CHECK(spy IN (0,1)),
                          FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE
                        );
                    """.trimIndent())
                    st.execute("CREATE INDEX IF NOT EXISTS idx_players_time ON players(updated_at DESC);")
                    st.execute("CREATE INDEX IF NOT EXISTS idx_recent_ts ON recent_commands(ts DESC);")
                }
                apply(4) { st ->
                    st.execute("ALTER TABLE player_prefs ADD COLUMN relay_optout INTEGER NOT NULL DEFAULT 0 CHECK(relay_optout IN (0,1));")
                }
            }
            3 -> {
                apply(4) { st ->
                    st.execute("ALTER TABLE player_prefs ADD COLUMN relay_optout INTEGER NOT NULL DEFAULT 0 CHECK(relay_optout IN (0,1));")
                }
            }
            else -> { /* up to date */ }
        }
    }
}
