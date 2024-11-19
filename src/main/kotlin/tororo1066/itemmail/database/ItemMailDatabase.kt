package tororo1066.itemmail.database

import org.bukkit.entity.Player
import tororo1066.itemmail.ItemMail
import tororo1066.tororopluginapi.SJavaPlugin
import tororo1066.tororopluginapi.database.SDBCondition
import tororo1066.tororopluginapi.database.SDBVariable
import tororo1066.tororopluginapi.database.SDatabase
import tororo1066.tororopluginapi.database.SDatabase.Companion.toSQLVariable
import tororo1066.tororopluginapi.database.mongo.SMongo
import tororo1066.tororopluginapi.database.mysql.SMySQL
import tororo1066.tororopluginapi.database.sqlite.SSQLite
import tororo1066.tororopluginapi.sItem.SItem
import tororo1066.tororopluginapi.utils.returnItem
import tororo1066.tororopluginapi.utils.toDate
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture

object ItemMailDatabase {
    val database = SDatabase.newInstance(SJavaPlugin.plugin)

    fun createTable() {
        database.backGroundCreateTable(
            "item_mail",
            mapOf(
                "id" to SDBVariable(SDBVariable.Int, autoIncrement = true),
                "uuid" to SDBVariable(SDBVariable.VarChar, 36),
                "server" to SDBVariable(SDBVariable.VarChar, 16),
                "player" to SDBVariable(SDBVariable.VarChar, 36),
                "playerName" to SDBVariable(SDBVariable.VarChar, 16),
                "item" to SDBVariable(SDBVariable.Text),
                "date" to SDBVariable(SDBVariable.DateTime)
            )
        )
    }

    fun select(condition: SDBCondition = SDBCondition.empty()): CompletableFuture<List<ItemMail>> {
        return database.asyncSelect("item_mail", condition).thenApplyAsync {
            it.map { data ->
                ItemMail(
                    UUID.fromString(data.getString("uuid")),
                    data.getString("server"),
                    UUID.fromString(data.getString("player")),
                    data.getString("playerName"),
                    SItem.fromBase64(data.getString("item"))!!.build(),
                    if (database.isMongo) data.getDate("date") else data.getLocalDateTime("date").toDate()
                )
            }
        }
    }

    fun insert(data: ItemMail): CompletableFuture<Boolean> {
        return database.asyncInsert(
            "item_mail",
            mapOf(
                "uuid" to data.uuid.toString(),
                "server" to data.server,
                "player" to data.playerUUID.toString(),
                "playerName" to data.playerName,
                "item" to SItem(data.item).toBase64(),
                "date" to if (database.isMongo) data.date else data.date.toSQLVariable(SDBVariable.DateTime)
            )
        )
    }

    fun delete(uuid: UUID): CompletableFuture<Boolean> {
        return database.asyncDelete("item_mail", SDBCondition().equal("uuid", uuid.toString()))
    }

    fun receive(player: Player, uuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            val db = database
            if (db is SMySQL || db is SSQLite) {
                try {
                    val connection = db.open() as Connection
                    connection.autoCommit = false
                    try {
                        val statement = connection.prepareStatement("SELECT * FROM item_mail WHERE uuid = ? FOR UPDATE")
                        statement.setString(1, uuid.toString())
                        val result = statement.executeQuery()
                        if (!result.next()) {
                            player.sendMessage("Mail not found.")
                            statement.close()
                            connection.rollback()
                            return@supplyAsync false
                        }
                        val item = SItem.fromBase64(result.getString("item"))!!.build()
                        statement.close()
                        val deleteStatement = connection.prepareStatement("DELETE FROM item_mail WHERE uuid = ?")
                        deleteStatement.setString(1, uuid.toString())
                        val deleteResult = deleteStatement.execute()
                        deleteStatement.close()
                        if (deleteResult) {
                            connection.commit()
                            player.returnItem(item)
                        } else {
                            player.sendMessage("Error occurred.")
                            connection.rollback()
                        }
                    } catch (e: Exception) {
                        connection.rollback()
                        e.printStackTrace()
                        return@supplyAsync false
                    } finally {
                        connection.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@supplyAsync false
                }
            } else if (db is SMongo) {
                try {
                    val (client, mongoDB) = db.open()
                    val collection = mongoDB.getCollection("item_mail")
                    try {
                        val result = collection.findOneAndDelete(SDBCondition().equal("uuid", uuid.toString()).buildAsMongo())
                        if (result != null) {
                            val item = SItem.fromBase64(result.getString("item"))!!.build()
                            player.returnItem(item)
                        } else {
                            player.sendMessage("Mail not found.")
                            return@supplyAsync false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@supplyAsync false
                    } finally {
                        client.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@supplyAsync false
                }
            }

            return@supplyAsync true
        }
    }
}
