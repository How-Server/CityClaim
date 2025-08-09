package ftt.cityclaim.commands

import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim.cityManager
import ftt.cityclaim.CityClaim.moneyManager
import ftt.cityclaim.utils.CountEntities
import ftt.cityclaim.utils.Feedback.sendFeedback
import ftt.sql.PlayerClaimData
import me.drex.itsours.claim.list.ClaimList
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.command.ServerCommandSource
import kotlin.jvm.optionals.getOrNull

object RenewCommand {
    fun toggleRenewClaim(context: CommandContext<ServerCommandSource>, state: Boolean): Int {
        val player = context.source.player ?: return 0
        val result = cityManager.setRenewClaim(player, state)
        if (result > 0 && state) {
            sendFeedback(context, "已啟用自動續租")
        } else if (result > 0) {
            sendFeedback(context, "已停用自動續租")
        } else {
            sendFeedback(context, "你還沒租借租地")
        }
        return 1
    }

    fun renewClaim(claim: PlayerClaimData): Boolean {
        val money = moneyManager.getBalanceFromUUID(claim.uuid)
        val basePrice = claim.cost

        val claimObject = ClaimList.getClaim(claim.claim.split("@")[0]).getOrNull()
        var totalPrice = basePrice

        if (claimObject != null) {
            val entityCount = CountEntities.inClaim(claimObject)
            val entityFee = CountEntities.totalFee(entityCount)
            totalPrice = basePrice + entityFee
        }

        if (money < totalPrice) {
            return false
        }
        if (claim.renew == false) {
            return false
        }
        if (cityManager.renewClaim(claim) == 0) {
            cityManager.removeClaimOwner(claim)
            return false
        }
        return moneyManager.changeBalance(claim.uuid, -totalPrice)
    }

    fun checkAndRenewClaim(player: PlayerEntity): Boolean {
        val rentedClaim = cityManager.getPlayerClaims(player)
        var result = false
        for (claim in rentedClaim) {
            if ((claim.endTime ?: 0) < System.currentTimeMillis()) {
                result = renewClaim(claim) || result
                continue
            }
            result = true
        }
        return result
    }
}