package ftt.cityclaim

import com.gmail.sneakdevs.diamondeconomy.DiamondUtils
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import ftt.sql.CityclaimManager
import ftt.sql.PlayerClaimData
import me.drex.itsours.claim.list.ClaimList
import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull


object CityClaim : ModInitializer {
    const val MODID = "cityclaim"
    private const val PERMISSION_BASIC = "${MODID}.basic"
    private const val PERMISSION_REGISTER = "${MODID}.register"
    private const val CLAIM_ROLE = "trusted"
    private val logger = LoggerFactory.getLogger("cityclaim")
    private val moneyManager = DiamondUtils.getDatabaseManager();
    private lateinit var cityManager: CityclaimManager;
    private lateinit var server: MinecraftServer;

    override fun onInitialize() {
        // 初始化
        ServerLifecycleEvents.SERVER_STARTING.register(::initDatabase);
        ServerLifecycleEvents.SERVER_STOPPED.register(::stopDatabase);

        // 處理到期領地租約
        ServerPlayConnectionEvents.JOIN.register(::checkPlayerRent)

        // 指令註冊
        CommandRegistrationCallback.EVENT.register(::registerCommands)

    }

    private fun checkPlayerRent(
        handle: ServerPlayNetworkHandler?, sender: PacketSender?, server: MinecraftServer?
    ): Unit {
        val expiredClaims = cityManager.getExpiredClaim()
        for (claim in expiredClaims) {
            ClaimList.getClaims().forEach { anyClaim ->
                val anyClaimName = cityManager.getClaimName(anyClaim)
                if (anyClaimName != claim.claim) {
                    return@forEach
                }
                if (claim.renew == true && renewClaim(claim)) {
                    return@forEach
                }
                cityManager.removeClaimOwner(claim)
                anyClaim.groupManager.getGroup(CLAIM_ROLE)?.players()?.clear()
            }
        }
    }

    private fun initDatabase(server: MinecraftServer) {
        this.server = server
        cityManager = CityclaimManager(server)
    }

    private fun stopDatabase(server: MinecraftServer) {
        cityManager.stopConnection();
    }

    private fun countEntitiesInClaim(claim: me.drex.itsours.claim.AbstractClaim): EntityCount {
        val world = server.getWorld(claim.dimension)
        if (world == null) {
            return EntityCount(0, 0, 0)
        }

        val claimBox = Box(
            claim.box.min.x.toDouble(), 70.0, claim.box.min.z.toDouble(),
            claim.box.max.x.toDouble(), 116.0, claim.box.max.z.toDouble()
        )

        var armorStandCount = 0
        var itemFrameCount = 0
        var signCount = 0

        world.getEntitiesByClass(ArmorStandEntity::class.java, claimBox) { true }.forEach { _ -> armorStandCount++ }
        world.getEntitiesByClass(ItemFrameEntity::class.java, claimBox) { true }.forEach { _ -> itemFrameCount++ }

        val minPos = BlockPos(claim.box.min.x, 70, claim.box.min.z)
        val maxPos = BlockPos(claim.box.max.x, 116, claim.box.max.z)

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

    private fun calculateEntityFee(entityCount: EntityCount): Int {
        val armorStandFee = entityCount.armorStands * 1
        val itemFrameFee = entityCount.itemFrames * 1
        val signFee = entityCount.signs * 1
        return armorStandFee + itemFrameFee + signFee
    }

    private fun registerCommands(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        access: CommandRegistryAccess?,
        env: RegistrationEnvironment?
    ) {
        dispatcher.register(
            literal("city").requires(checkPermission((PERMISSION_BASIC))).executes(::showGuide).then(
                literal("rent").executes(::rentClaim)
            ).then(
                literal("check").executes(::checkClaim)
            ).then(
                literal("renew").then(literal("on").executes { context -> toggleRenewClaim(context, true) })
                    .then(literal("off").executes { context -> toggleRenewClaim(context, false) })
            ).then(
                literal("share").then(argument("player", GameProfileArgumentType.gameProfile()).executes(::shareClaim))
            ).then(
                literal("unshare").then(
                    argument(
                        "player",
                        GameProfileArgumentType.gameProfile()
                    ).executes(::unshareClaim)
                )
            ).then(
                literal("register").requires(checkPermission(PERMISSION_REGISTER)).then(
                    argument("cost", IntegerArgumentType.integer(1)).then(
                        argument("period", IntegerArgumentType.integer(1)).executes(::registerClaim)
                    )
                )
            ).then(
                literal("unrent").requires(checkPermission(PERMISSION_REGISTER)).executes(::unrentClaim)
            )
        )
    }

    private fun shareClaim(context: CommandContext<ServerCommandSource>): Int {
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
                sendFeedback(context, "你已經把租地分享給 ${profile.name} 了")
                return 0
            }
            val claimItem = ClaimList.getClaim(claimData.claim.split("@")[0]).getOrNull()
            claimItem?.groupManager?.getGroup(CLAIM_ROLE)?.players()?.add(profile.id)
        }
        sendFeedback(context, "成功租地分享給 ${profile.name}")
        return 1
    }

    private fun unshareClaim(context: CommandContext<ServerCommandSource>): Int {
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

    private fun toggleRenewClaim(context: CommandContext<ServerCommandSource>, state: Boolean): Int {
        val player = context.source.player ?: return 0
        val result = cityManager.setRenewClaim(player, state)
        if (result > 0 && state) {
            sendFeedback(context, "已啟用自動續租")
        } else if (result > 0) {
            sendFeedback(context, "已停用自動續租")
        } else {
            sendFeedback(context, "你沒有租借的領地可以續租")
        }
        return 1
    }

    private fun showGuide(context: CommandContext<ServerCommandSource>): Int {
        var message = """
            /city rent 承租目前所在的租地
            /city check 查看目前所在租地的租約
            /city renew 開啟/關閉自動續約（預設開啟）
            /city share <player> 分享租地給其他玩家
            /city unshare <player> 取消其他玩家的租地分享
        """.trimIndent()

        val player = context.source.player
        if (player != null && Permissions.check(player, PERMISSION_REGISTER)) {
            message = message.plus("\n/city register <cost> <period> 註冊租地給玩家使用 cost:價格 period:天數")
        }
        sendFeedback(context, message, false)
        return 1
    }

    private fun checkPermission(permission: String): Predicate<ServerCommandSource> {
        return Predicate { source ->
            val player = source.player ?: return@Predicate false
            Permissions.check(player, permission)
        }
    }

    private fun registerClaim(context: CommandContext<ServerCommandSource>): Int {
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

    private fun renewClaim(claim: PlayerClaimData): Boolean {
        val money = moneyManager.getBalanceFromUUID(claim.uuid)
        val basePrice = claim.cost

        val claimObject = ClaimList.getClaim(claim.claim.split("@")[0]).getOrNull()
        var totalPrice = basePrice

        if (claimObject != null) {
            val entityCount = countEntitiesInClaim(claimObject)
            val entityFee = calculateEntityFee(entityCount)
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

    private fun checkAndRenewClaim(player: PlayerEntity): Boolean {
        val rentedClaim = cityManager.getPlayerClaims(player)
        var result = false
        for (claim in rentedClaim) {
            if ((claim.endTime ?: 0) < System.currentTimeMillis()) {
                result = renewClaim(claim) || result
                continue
            }
            result = true
        }
        return result;
    }

    private fun rentClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()

        if (checkAndRenewClaim(player)) {
            sendFeedback(context, "你已經有租借領地了")
            return 0
        }

        if (claim == null) {
            sendFeedback(context, "這裡沒有領地")
            return 0
        }

        val rentableClaim = cityManager.getClaim(claim)
        if (rentableClaim == null) {
            sendFeedback(context, "目前無人租用，有需要請聯絡 @Howie 租地")
            return 0
        }

        if((rentableClaim.endTime ?: 0) > System.currentTimeMillis()) {
            sendFeedback(context, "該領地已被租借")
            return 0
        }

        if (rentableClaim.renew == true && renewClaim(rentableClaim)) {
            sendFeedback(context, "該領地已被租借")
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
            sendFeedback(context, "租用失敗，請確認領地是否可租用")
            return 0
        }

        moneyManager.changeBalance(player.uuidAsString, -price)
        trustedRole.players().add(player.uuid)
        sendFeedback(context, "你租用了領地 ${claim.fullName}！剩下 ${money - price} 元")
        sendFeedback(context, "自動續租功能會自動開啟，不需要請關閉")
        return 1
    }

    private fun unrentClaim(context: CommandContext<ServerCommandSource>): Int {
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

    private fun checkClaim(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val claim = ClaimList.getClaimAt(player).getOrNull()
        if (claim == null) {
            sendFeedback(context, "這裡沒有領地")
            return 0
        }
        val playerClaimData = cityManager.getClaim(claim)
        if (playerClaimData == null) {
            sendFeedback(context, "目前無人租用，有需要請聯絡 @Howie 租地")
            return 0
        }

        val timestamp = playerClaimData.endTime ?: 0;
        var dateStr = "可租借"
        if (timestamp > System.currentTimeMillis()) {
            dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Date(timestamp))
        }
        val claimName = playerClaimData.claim.split("@")[0]

        val entityCount = countEntitiesInClaim(claim)
        val entityFee = calculateEntityFee(entityCount)
        val totalCost = playerClaimData.cost + entityFee

        val message = """
            §6$claimName
            §e基本租金：§6${playerClaimData.cost} 元　§f租期：${playerClaimData.daysPerRent} 天
            §e目前／最後租借人：§f${playerClaimData.name ?: "無"}
            §e到期時間：§f$dateStr
            §e實體使用量：
              §7盔甲座 §f${entityCount.armorStands}　§7展示框 §f${entityCount.itemFrames}　§7告示牌 §f${entityCount.signs}
            §e實體使用費用：§6${entityFee} 元
            §e§l總價：§c$totalCost 元
            """.trimIndent()

        sendFeedback(context, message)
        return 1
    }

    private fun sendFeedback(
        context: CommandContext<ServerCommandSource>,
        message: String,
        header: Boolean = true
    ): Unit {
        if (header) {
            return context.source.sendFeedback({
                Text.literal("【租地系統】").withColor(45824).append(Text.literal(message).withColor(16777215))
            }, false);
        }
        return context.source.sendFeedback({ Text.literal(message) }, false);
    }
    data class EntityCount(
        val armorStands: Int,
        val itemFrames: Int,
        val signs: Int
    )
}