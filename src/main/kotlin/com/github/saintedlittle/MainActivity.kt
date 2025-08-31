package com.github.saintedlittle

import com.github.saintedlittle.bridge.BridgeManager
import com.github.saintedlittle.chat.ChatListener
import com.github.saintedlittle.command.*
import com.github.saintedlittle.config.ConfigManager
import com.github.saintedlittle.data.PlayerDataStore
import com.github.saintedlittle.data.RecentCommandsTracker
import com.github.saintedlittle.pm.MessageService
import com.github.saintedlittle.slowmode.SlowmodeService
import com.github.saintedlittle.util.LogWriter
import net.luckperms.api.LuckPerms
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class MainActivity : JavaPlugin(), Listener {

    lateinit var configManager: ConfigManager;        private set
    lateinit var dataStore: PlayerDataStore;          private set
    lateinit var recentCommands: RecentCommandsTracker; private set
    lateinit var messageService: MessageService;      private set
    lateinit var logWriter: LogWriter;                private set
    lateinit var slowmode: SlowmodeService;           private set
    var luckPerms: LuckPerms? = null;                 private set

    var bridgeManager: BridgeManager? = null
        private set

    override fun onEnable() {
        saveDefaultConfig()
        luckPerms = server.servicesManager.load(LuckPerms::class.java)
        configManager = ConfigManager(this)
        dataStore = PlayerDataStore(this)
        recentCommands = RecentCommandsTracker(this)
        logWriter = LogWriter(this)
        messageService = MessageService(this)
        slowmode = SlowmodeService()

        bridgeManager = BridgeManager(this).also { it.initAndStart() }

        server.pluginManager.registerEvents(ChatListener(this), this)
        server.pluginManager.registerEvents(recentCommands, this)
        server.pluginManager.registerEvents(this, this)

        getCommand("chat")?.let { val exec = ChatCommand(this); it.setExecutor(exec); it.tabCompleter = exec }
        getCommand("nick")?.setExecutor(NickCommand(this))
        getCommand("message")?.setExecutor(MessageCommand(this))
        getCommand("reply")?.setExecutor(ReplyCommand(this))
        getCommand("channel")?.setExecutor(ChannelCommand(this))
        getCommand("relay")?.setExecutor(RelayCommand(this)) // NEW

        logger.info("ChatManager enabled | LuckPerms=${luckPerms != null} | PAPI=${isPlaceholderAPI()}")
    }

    override fun onDisable() {
        bridgeManager?.stopAll()
        dataStore.saveAll()
        dataStore.close()
        logWriter.close()
    }

    fun reloadAll() {
        reloadConfig()
        configManager.reload()
        bridgeManager?.stopAll()
        bridgeManager = BridgeManager(this).also { it.initAndStart() }
    }

    fun isPlaceholderAPI(): Boolean = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val id = dataStore.getDefaultChannel(e.player.uniqueId.toString()) ?: configManager.defaultChannelId
        val ch = configManager.channelById(id) ?: return
        e.player.sendMessage(configManager.mini.deserialize("<gray>Активный канал:</gray> <white>${ch.name}</white>"))
    }
}
