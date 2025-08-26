package ftt.cityclaim.commands

import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim.cityManager
import ftt.cityclaim.utils.CountEntities
import ftt.cityclaim.utils.Feedback.sendFeedback
import me.drex.itsours.claim.list.ClaimList
import net.minecraft.server.command.ServerCommandSource
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.jvm.optionals.getOrNull

object CheckCommand {
    fun checkClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()
        if (claim == null) {
            sendFeedback(context, "這裡沒有租地")
            return 0
        }
        val playerClaimData = cityManager.getClaim(claim)
        if (playerClaimData == null) {
            sendFeedback(context, "目前無人租用，有需要請聯絡 @Howie 租地")
            return 0
        }

        val timestamp = playerClaimData.endTime ?: 0
        var dateStr = "可租借"
        if (timestamp > System.currentTimeMillis()) {
            dateStr = SimpleDateFormat("yyyy/MM/dd").format(Date(timestamp))
        }
        val claimName = playerClaimData.claim.split("@")[0]

        val entityCount = CountEntities.inClaim(claim)
        val entityFee = CountEntities.totalFee(entityCount)
        val totalCost = playerClaimData.cost + entityFee

        var message = """
            §6$claimName
            §e基本租金：§6${playerClaimData.cost} 元　§f租期：${playerClaimData.daysPerRent} 天
            §e目前／最後租借人：§f${playerClaimData.name ?: "無"}
            §e到期時間：§f$dateStr
            """.trimIndent()
        message = if (timestamp.toDouble() != 0.0) {
            message.plus("""
                    
                §e實體使用量(x1)：§7無字盔甲座 §f${entityCount.armorStands}　§7展示框 §f${entityCount.itemFrames}
                §e文字使用量(x2)：§7有字盔甲座 §f${entityCount.namedArmorStands}　§7展示櫃 §f${entityCount.displayCases}　§7有字告示牌 §f${entityCount.signs}
                §e預估使用費：§6${entityFee} 元
                §e§l預估總價：§6§l$totalCost 元
                """.trimIndent())
        } else {
            message.plus("""
                    
                §7實體費用將在續約時收取，租地後 /city check 查看實體使用量
                """.trimIndent())
        }

        sendFeedback(context, message)
        return 1
    }
}