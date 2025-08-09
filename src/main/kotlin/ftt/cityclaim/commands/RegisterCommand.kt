package ftt.cityclaim.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim.cityManager
import ftt.cityclaim.utils.Feedback.sendFeedback
import me.drex.itsours.claim.list.ClaimList
import net.minecraft.server.command.ServerCommandSource
import kotlin.jvm.optionals.getOrNull

object RegisterCommand {
    fun registerClaim(context: CommandContext<ServerCommandSource>): Int {
        val cost = IntegerArgumentType.getInteger(context, "cost")
        val daysPerRent = IntegerArgumentType.getInteger(context, "period")

        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()
        if (claim == null) {
            sendFeedback(context, "這裡沒有領地")
            return 0
        }
        val result = cityManager.registerClaim(claim, cost, daysPerRent)
        if (result) {
            sendFeedback(context, "註冊成功！${claim.fullName}，價格：${cost}，租借時間：${daysPerRent}")
            return 1
        }
        sendFeedback(context, "註冊失敗")
        return 0
    }
}