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
        val claimPos = claim.box

        val minX = claimPos.minX
        val maxX = claimPos.maxX
        val minZ = claimPos.minZ
        val maxZ = claimPos.maxZ
        val sizeX = maxX - minX + 1
        val sizeZ = maxZ - minZ + 1

        when (sizeX) {
            10 if sizeZ == 10 -> resetToPattern10x10(world, minX, minZ)
            15 if sizeZ == 15 -> resetToPattern15x15(world, minX, minZ)
            20 if sizeZ == 20 -> resetToPattern20x20(world, minX, minZ)
            else -> {
                sendFeedback(context, "不支援的租地大小: ${sizeX}x${sizeZ}")
                return 0
            }
        }

        sendFeedback(context, "成功重置 ${claim.name} 租地到原始狀態")
        return 1
    }

    private fun resetToPattern10x10(world: World, startX: Int, startZ: Int) {
        clearArea(world, startX, startZ, 10, 10)
        for (x in 0 until 10) {
            for (z in 0 until 10) {
                world.setBlockState(BlockPos(startX + x, 74, startZ + z), Blocks.GRASS_BLOCK.defaultState)
            }
        }
        addCornerFences(world, startX, startZ, 10, 10)
    }

    private fun resetToPattern15x15(world: World, startX: Int, startZ: Int) {
        clearArea(world, startX, startZ, 15, 15)
        for (x in 0 until 15) {
            for (z in 0 until 15) {
                world.setBlockState(BlockPos(startX + x, 74, startZ + z), Blocks.GRASS_BLOCK.defaultState)
            }
        }
        addCornerFences(world, startX, startZ, 15, 15)
    }

    private fun resetToPattern20x20(world: World, startX: Int, startZ: Int) {
        clearArea(world, startX, startZ, 20, 20)
        for (x in 0 until 20) {
            for (z in 0 until 20) {
                world.setBlockState(BlockPos(startX + x, 74, startZ + z), Blocks.GRASS_BLOCK.defaultState)
            }
        }
        addCornerFences(world, startX, startZ, 20, 20)
    }

    private fun addCornerFences(world: World, startX: Int, startZ: Int, sizeX: Int, sizeZ: Int) {
        val endX = startX + sizeX - 1
        val endZ = startZ + sizeZ - 1

        world.setBlockState(BlockPos(startX, 75, startZ), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(startX + 1, 75, startZ), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(startX, 75, startZ + 1), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(startX, 76, startZ), Blocks.LANTERN.defaultState)

        world.setBlockState(BlockPos(endX, 75, startZ), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(endX - 1, 75, startZ), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(endX, 75, startZ + 1), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(endX, 76, startZ), Blocks.LANTERN.defaultState)

        world.setBlockState(BlockPos(startX, 75, endZ), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(startX + 1, 75, endZ), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(startX, 75, endZ - 1), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(startX, 76, endZ), Blocks.LANTERN.defaultState)

        world.setBlockState(BlockPos(endX, 75, endZ), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(endX - 1, 75, endZ), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(endX, 75, endZ - 1), Blocks.BAMBOO_FENCE.defaultState)
        world.setBlockState(BlockPos(endX, 76, endZ), Blocks.LANTERN.defaultState)
    }

    private fun clearArea(world: World, startX: Int, startZ: Int, sizeX: Int, sizeZ: Int) {
        for (x in 0 until sizeX) {
            for (z in 0 until sizeZ) {
                for (y in 70..115) {
                    val pos = BlockPos(startX + x, y, startZ + z)
                    val blockState = world.getBlockState(pos)
                    if (blockState.block != Blocks.BEDROCK) {
                        world.setBlockState(pos, Blocks.AIR.defaultState)
                    }
                }
            }
        }
        val minPos = BlockPos(startX, 70, startZ)
        val maxPos = BlockPos(startX + sizeX, 115, startZ + sizeZ)
        val box = Box(
            minPos.x.toDouble() - 0.01, minPos.y.toDouble(), minPos.z.toDouble() - 0.01,
            maxPos.x.toDouble() + 0.01, maxPos.y.toDouble() + 1.0, maxPos.z.toDouble() + 0.01
        )
        world.getEntitiesByClass(net.minecraft.entity.Entity::class.java, box) { entity ->
            entity !is net.minecraft.entity.player.PlayerEntity
        }.forEach { entity ->
            entity.discard()
        }
    }
}
