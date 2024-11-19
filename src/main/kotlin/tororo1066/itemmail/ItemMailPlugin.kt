package tororo1066.itemmail

import com.google.common.io.ByteStreams
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import tororo1066.itemmail.database.ItemMailDatabase
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.sEvent.SEvent

class ItemMailPlugin : SJavaPlugin() {

    companion object {
        val prefix = SStr("&b[&6ItemMail&b]&r")
        val servers = mutableListOf<String>()
        lateinit var thisServerName: String
    }

    override fun onStart() {
        saveDefaultConfig()

        if (!server.spigot().spigotConfig.getBoolean("settings.bungeecord")) {
            logger.warning("This plugin requires BungeeCord to work.")
            server.pluginManager.disablePlugin(this)
            return
        }

        server.messenger.registerIncomingPluginChannel(this, "BungeeCord") { _, _, message ->
            val data = ByteStreams.newDataInput(message)
            val subChannel = data.readUTF()
            when (subChannel) {
                "GetServers" -> {
                    val serverList = data.readUTF().split(", ")
                    servers.clear()
                    servers.addAll(serverList)
                }
                "GetServer" -> {
                    thisServerName = data.readUTF()
                }
            }
        }
        server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")
        SEvent().register<PlayerJoinEvent> { e ->
            Bukkit.getScheduler().runTaskLater(
                this,
                Runnable {
                    ByteStreams.newDataOutput().apply {
                        writeUTF("GetServers")
                    }.toByteArray().let {
                        e.player.sendPluginMessage(this, "BungeeCord", it)
                    }
                    ByteStreams.newDataOutput().apply {
                        writeUTF("GetServer")
                    }.toByteArray().let {
                        e.player.sendPluginMessage(this, "BungeeCord", it)
                    }
                },
                20
            )
        }

        ItemMailCommand()
    }

    override fun onDisable() {
        server.messenger.unregisterOutgoingPluginChannel(this, "BungeeCord")
        server.messenger.unregisterIncomingPluginChannel(this, "BungeeCord")
        ItemMailDatabase.database.close()
    }
}
