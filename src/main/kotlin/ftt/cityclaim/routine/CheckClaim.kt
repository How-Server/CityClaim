package ftt.cityclaim.routine

import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim
import ftt.cityclaim.commands.RenewCommand
import ftt.cityclaim.utils.Feedback.sendFeedback
import me.drex.itsours.claim.list.ClaimList
import net.minecraft.server.command.ServerCommandSource

object CheckClaim {
    fun playerRent(context: CommandContext<ServerCommandSource>): Int {
        val expiredClaims = CityClaim.cityManager.getExpiredClaim()
        for (claim in expiredClaims) {
            ClaimList.getClaims().forEach { anyClaim ->
                val anyClaimName = CityClaim.cityManager.getClaimName(anyClaim)
                if (anyClaimName != claim.claim) {
                    return@forEach
                }
                if (claim.renew == true && RenewCommand.renewClaim(claim)) {
                    return@forEach
                }
                CityClaim.cityManager.removeClaimOwner(claim)
                anyClaim.groupManager.getGroup(CityClaim.CLAIM_ROLE)?.players()?.clear()
            }
        }
        if (context.source.player != null) sendFeedback(context, "已確認所有租地，共有 ${expiredClaims.size} 筆租地到期")
        return 1
    }
}