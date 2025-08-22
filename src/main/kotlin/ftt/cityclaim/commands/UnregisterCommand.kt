package ftt.cityclaim.commands

import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim.cityManager
import ftt.cityclaim.utils.Feedback.sendFeedback
import me.drex.itsours.claim.list.ClaimList
import net.minecraft.server.command.ServerCommandSource
import kotlin.jvm.optionals.getOrNull

object UnregisterCommand {
    fun unregisterClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()

        if (claim == null) {
            sendFeedback(context, "這裡沒有領地")
            return 0
        }

        val claimData = cityManager.getClaim(claim)
        if (claimData == null) {
            sendFeedback(context, "這個領地還沒有註冊")
            return 0
        }

        val result = cityManager.unregisterClaim(claim)
        if (result) {
            sendFeedback(context, "成功取消註冊領地：${claim.fullName}")
            return 1
        }

        sendFeedback(context, "取消註冊失敗")
        return 0
    }
}