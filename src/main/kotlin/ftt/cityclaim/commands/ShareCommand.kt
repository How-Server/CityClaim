package ftt.cityclaim.commands

import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim.CLAIM_ROLE
import ftt.cityclaim.CityClaim.cityManager
import ftt.cityclaim.utils.Feedback.sendFeedback
import me.drex.itsours.claim.list.ClaimList
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.server.command.ServerCommandSource
import kotlin.jvm.optionals.getOrNull

object ShareCommand {
    fun shareClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val profileList = GameProfileArgumentType.getProfileArgument(context, "player")
        if (profileList.size > 1) {
            sendFeedback(context, "一次請只指定一名玩家")
            return 0
        }
        val profile = profileList.firstOrNull() ?: return 0
        val rentClaims = cityManager.getPlayerClaims(player)
        for (claimData in rentClaims) {
            val result = cityManager.shareClaim(claimData, profile)
            if (result == -1) {
                sendFeedback(context, "你已經分享過租地權限給 ${profile.name} 了")
                return 0
            }
            val claimItem = ClaimList.getClaim(claimData.claim.split("@")[0]).getOrNull()
            claimItem?.groupManager?.getGroup(CLAIM_ROLE)?.players()?.add(profile.id)
        }
        sendFeedback(context, "成功分享租地權限給 ${profile.name}")
        return 1
    }

    fun unshareClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val profileList = GameProfileArgumentType.getProfileArgument(context, "player")
        if (profileList.size > 1) {
            sendFeedback(context, "一次請只指定一名玩家")
            return 0
        }
        val profile = profileList.firstOrNull() ?: return 0
        val rentClaims = cityManager.getPlayerClaims(player)
        for (claimData in rentClaims) {
            cityManager.removeSharedClaim(claimData, profile)
            val claimItem = ClaimList.getClaim(claimData.claim.split("@")[0]).getOrNull()
            claimItem?.groupManager?.getGroup(CLAIM_ROLE)?.players()?.remove(profile.id)
        }
        sendFeedback(context, "已取消 ${profile.name} 的租地權限")
        return 1
    }
}