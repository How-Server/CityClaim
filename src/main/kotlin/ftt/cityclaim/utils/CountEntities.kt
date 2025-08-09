package ftt.cityclaim.utils

import ftt.cityclaim.CityClaim.server
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

object CountEntities {
    fun inClaim(claim: me.drex.itsours.claim.AbstractClaim): EntityCount {
        val world = server.getWorld(claim.dimension)
        if (world == null) {
            return EntityCount(0, 0, 0)
        }
        val offset = 0.01
        val claimBox = Box(
            claim.box.min.x.toDouble() - offset, 68.0, claim.box.min.z.toDouble() - offset,
            claim.box.max.x.toDouble() + 1.0 + offset, 117.0, claim.box.max.z.toDouble() + 1.0 + offset
        )

        var armorStandCount = 0
        var itemFrameCount = 0
        var signCount = 0

        world.getEntitiesByClass(ArmorStandEntity::class.java, claimBox) { true }.forEach { _ -> armorStandCount++ }
        world.getEntitiesByClass(ItemFrameEntity::class.java, claimBox) { true }.forEach { _ -> itemFrameCount++ }

        val minPos = BlockPos(claim.box.min.x, 68, claim.box.min.z)
        val maxPos = BlockPos(claim.box.max.x, 117, claim.box.max.z)

        BlockPos.iterate(minPos, maxPos).forEach { pos ->
            val blockEntity = world.getBlockEntity(pos)
            if (blockEntity is SignBlockEntity) {
                var hasText = false
                for (i in 0..3) {
                    val frontText = blockEntity.frontText.getMessage(i, false)
                    if (frontText.string.trim().isNotEmpty()) {
                        hasText = true
                        break
                    }
                }
                if (!hasText) {
                    for (i in 0..3) {
                        val backText = blockEntity.backText.getMessage(i, false)
                        if (backText.string.trim().isNotEmpty()) {
                            hasText = true
                            break
                        }
                    }
                }
                if (hasText) signCount++
            }
        }
        return EntityCount(armorStandCount, itemFrameCount, signCount)
    }

    fun totalFee(entityCount: EntityCount): Int {
        val armorStandFee = entityCount.armorStands * 1
        val itemFrameFee = entityCount.itemFrames * 1
        val signFee = entityCount.signs * 1
        return armorStandFee + itemFrameFee + signFee
    }

    data class EntityCount(
        val armorStands: Int,
        val itemFrames: Int,
        val signs: Int
    )
}