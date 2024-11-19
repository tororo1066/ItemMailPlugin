package tororo1066.itemmail

import org.bukkit.inventory.ItemStack
import java.util.Date
import java.util.UUID

class ItemMail(
    var uuid: UUID,
    var server: String,
    var playerUUID: UUID,
    var playerName: String,
    var item: ItemStack,
    var date: Date
)
