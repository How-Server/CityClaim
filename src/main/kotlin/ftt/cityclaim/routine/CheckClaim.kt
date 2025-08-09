package ftt.cityclaim.routine

import ftt.cityclaim.CityClaim
import ftt.cityclaim.commands.RenewCommand
import me.drex.itsours.claim.list.ClaimList
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler

object CheckClaim {
    fun playerRent(handler: ServerPlayNetworkHandler, sender: PacketSender, server: MinecraftServer) {
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
    }
}