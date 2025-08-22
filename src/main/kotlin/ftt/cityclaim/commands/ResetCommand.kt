package ftt.cityclaim.commands

import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim.cityManager
import ftt.cityclaim.utils.Feedback.sendFeedback
import me.drex.itsours.claim.list.ClaimList
import net.minecraft.block.Blocks
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import kotlin.jvm.optionals.getOrNull

object ResetCommand {

    fun resetClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()

        if (claim == null) {
            sendFeedback(context, "這裡沒有租地")
            return 0
        }

        val rentableClaim = cityManager.getClaim(claim)
        if (rentableClaim == null) {
            sendFeedback(context, "這塊地不是租地")
            return 0
        }

        if (rentableClaim.uuid != player.uuidAsString && !context.source.hasPermissionLevel(4)) {
            sendFeedback(context, "你没有權限重置這塊租地")
            return 0
        }

        val world = player.world
        val claimBox = claim.box
        val size = claimBox.blockCountX

        if (size !in listOf(10, 15, 20) || claimBox.blockCountX != claimBox.blockCountZ) {
            sendFeedback(context, "不支援的租地大小: ${claimBox.blockCountX}x${claimBox.blockCountZ}")
            return 0
        }

        resetClaimArea(world, claimBox.minX, claimBox.minZ, size)
        sendFeedback(context, "成功重置 ${size}x${size} 租地到原始狀態")
        return 1
    }

    private fun resetClaimArea(world: World, startX: Int, startZ: Int, size: Int) {
        clearArea(world, startX, startZ, size)
        setGrassGround(world, startX, startZ, size)
        addCornerFences(world, startX, startZ, size)
    }

    private fun setGrassGround(world: World, startX: Int, startZ: Int, size: Int) {
        for (x in 0 until size) {
            for (z in 0 until size) {
                world.setBlockState(BlockPos(startX + x, 74, startZ + z), Blocks.GRASS_BLOCK.defaultState)
            }
        }
    }

    private fun addCornerFences(world: World, startX: Int, startZ: Int, size: Int) {
        val endX = startX + size - 1
        val endZ = startZ + size - 1

        val corners = listOf(
            Triple(startX, startZ, listOf(Pair(1, 0), Pair(0, 1))),
            Triple(endX, startZ, listOf(Pair(-1, 0), Pair(0, 1))),
            Triple(startX, endZ, listOf(Pair(1, 0), Pair(0, -1))),
            Triple(endX, endZ, listOf(Pair(-1, 0), Pair(0, -1)))
        )

        corners.forEach { (x, z, directions) ->
            world.setBlockState(BlockPos(x, 75, z), Blocks.BAMBOO_FENCE.defaultState)
            world.setBlockState(BlockPos(x, 76, z), Blocks.LANTERN.defaultState)
            directions.forEach { (dx, dz) ->
                world.setBlockState(BlockPos(x + dx, 75, z + dz), Blocks.BAMBOO_FENCE.defaultState)
            }
        }
    }

    private fun clearArea(world: World, startX: Int, startZ: Int, size: Int) {
        for (x in 0 until size) {
            for (z in 0 until size) {
                for (y in 70..115) {
                    val pos = BlockPos(startX + x, y, startZ + z)
                    if (world.getBlockState(pos).block != Blocks.BEDROCK) {
                        world.setBlockState(pos, Blocks.AIR.defaultState)
                    }
                }
            }
        }
        val offset = 0.01
        val box = Box(
            startX.toDouble() - offset, 70.0, startZ.toDouble() - offset,
            (startX + size).toDouble() + offset, 116.0, (startZ + size).toDouble() + offset
        )
        world.getEntitiesByClass(net.minecraft.entity.Entity::class.java, box) {
            it !is net.minecraft.entity.player.PlayerEntity
        }.forEach { it.discard() }
    }
}
