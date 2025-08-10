package ftt.cityclaim.commands

import com.gmail.sneakdevs.diamondeconomy.DiamondUtils
import com.gmail.sneakdevs.diamondeconomy.sql.TransactionType
import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim.CLAIM_ROLE
import ftt.cityclaim.CityClaim.cityManager
import ftt.cityclaim.CityClaim.moneyManager
import ftt.cityclaim.commands.RenewCommand.checkAndRenewClaim
import ftt.cityclaim.commands.RenewCommand.renewClaim
import ftt.cityclaim.utils.Feedback.sendFeedback
import me.drex.itsours.claim.list.ClaimList
import net.minecraft.server.command.ServerCommandSource
import kotlin.jvm.optionals.getOrNull

object RentCommand {
    fun rentClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()

        if (checkAndRenewClaim(player)) {
            sendFeedback(context, "你已經有租借租地了")
            return 0
        }

        if (claim == null) {
            sendFeedback(context, "這裡沒有租地")
            return 0
        }

        val rentableClaim = cityManager.getClaim(claim)
        if (rentableClaim == null) {
            sendFeedback(context, "目前無人租用，有需要請聯絡 Howie 租地")
            return 0
        }

        if((rentableClaim.endTime ?: 0) > System.currentTimeMillis()) {
            sendFeedback(context, "該地已被租借")
            return 0
        }

        if (rentableClaim.renew == true && renewClaim(rentableClaim)) {
            sendFeedback(context, "該地已被租借")
            return 0
        }

        val money = moneyManager.getBalanceFromUUID(player.uuidAsString)
        val price = rentableClaim.cost
        if (money < price) {
            sendFeedback(context, "你只有 $money 元，這塊地要 $price 元才可以租")
            return 0
        }

        val trustedRole = claim.groupManager.getGroup(CLAIM_ROLE) ?: return 0
        if (cityManager.rentClaim(claim, player) == 0) {
            sendFeedback(context, "租用失敗，請確認租地是否可租用")
            return 0
        }

        moneyManager.changeBalance(player.uuidAsString, TransactionType.EXPENSE, -price, "City中央都市 - 租用 ${claim.fullName}")
        trustedRole.players().add(player.uuid)
        sendFeedback(context, "你租用了租地 ${claim.fullName}！§7餘額：${DiamondUtils.formatNumber(money - price)} 元")
        sendFeedback(context, "自動續租功能會自動開啟，不需要請 /city renew off 關閉")
        return 1
    }

    fun unrentClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull() ?: return 0
        val claimData = cityManager.getClaim(claim) ?: return 0
        claim.groupManager.getGroup(CLAIM_ROLE)?.players()?.clear()
        if (cityManager.removeClaimOwner(claimData) != 0) {
            sendFeedback(context, "已退租租地 "+claim.fullName)
            return 1
        }
        sendFeedback(context, "無租地被退租")
        return 0
    }
}