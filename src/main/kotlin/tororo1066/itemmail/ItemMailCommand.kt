package tororo1066.itemmail

import tororo1066.commandapi.argumentType.EntityArg
import tororo1066.commandapi.argumentType.StringArg
import tororo1066.itemmail.database.ItemMailDatabase
import tororo1066.tororopluginapi.SStr
import tororo1066.tororopluginapi.annotation.SCommandV2Body
import tororo1066.tororopluginapi.database.SDBCondition
import tororo1066.tororopluginapi.otherUtils.UsefulUtility
import tororo1066.tororopluginapi.sCommand.v2.SCommandV2
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.sendMessage
import java.util.Date
import java.util.UUID

class ItemMailCommand : SCommandV2("itemmail") {

    init {
        root.setPermission("itemmail.user")
    }

    @SCommandV2Body
    val commandObject = command {
        literal("send") {
            setPermission("itemmail.op")
            argument("server", StringArg.word()) {
                suggest { _, _, _ ->
                    ItemMailPlugin.servers.map { it toolTip it }
                }
                argument("player", EntityArg(singleTarget = true, playersOnly = true)) {
                    argument("item", StringArg.phrase()) {
                        setFunctionExecutor { sender, _, args ->
                            val item = SItem.fromBase64(args.getArgument("item", String::class.java))
                            if (item == null) {
                                sender.sendMessage(SStr("Invalid item"))
                                return@setFunctionExecutor
                            }
                            val player = args.getEntities("player").first()
                            val server = args.getArgument("server", String::class.java)
                            if (!ItemMailPlugin.servers.contains(server)) {
                                sender.sendMessage(SStr("Invalid server"))
                                return@setFunctionExecutor
                            }

                            ItemMailDatabase.insert(
                                ItemMail(
                                    UUID.randomUUID(),
                                    server,
                                    player.uniqueId,
                                    player.name,
                                    item.build(),
                                    Date()
                                )
                            ).thenAccept {
                                if (it) {
                                    sender.sendMessage(SStr("Sent"))
                                } else {
                                    sender.sendMessage(SStr("Failed to send"))
                                }
                            }
                        }
                    }
                }
            }
        }

        literal("list") {
            setPermission("itemmail.user")
            setPlayerFunctionExecutor { sender, _, _ ->
                ItemMailDatabase.select(
                    SDBCondition()
                        .equal("server", ItemMailPlugin.thisServerName)
                        .and(SDBCondition().equal("player", sender.uniqueId.toString()))
                ).thenAccept { mails ->
                    if (mails.isEmpty()) {
                        sender.sendMessage(SStr("No mail"))
                        return@thenAccept
                    }
                    mails.forEach {
                        sender.sendMessage(
                            SStr("&6${it.item.itemMeta?.displayName} &7x${it.item.amount}")
                                .commandText("/itemmail receive ${it.uuid}")
                                .hoverText("§cClick to receive")
                        )
                    }
                }
            }
        }

        literal("receive") {
            setPermission("itemmail.user")
            argument("uuid", StringArg.word()) {
                setPlayerFunctionExecutor { sender, _, args ->
                    val uuid = UsefulUtility.sTry({ UUID.fromString(args.getArgument("uuid", String::class.java)) }, { null })
                    if (uuid == null) {
                        sender.sendMessage(SStr("Invalid UUID"))
                        return@setPlayerFunctionExecutor
                    }
                    ItemMailDatabase.receive(sender, uuid).thenAccept {
                        if (it) {
                            sender.sendMessage(SStr("Received"))
                        } else {
                            sender.sendMessage(SStr("Failed to receive"))
                        }
                    }
                }
            }
        }

        literal("createTable") {
            setPermission("itemmail.op")
            setFunctionExecutor { sender, _, _ ->
                ItemMailDatabase.createTable()
                sender.sendMessage(SStr("Table created"))
            }
        }
    }
}
