package ftt.cityclaim.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import ftt.cityclaim.CityClaim
import ftt.cityclaim.utils.Feedback.sendFeedback
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import java.util.function.Predicate

object CityCommands {
    private const val BASIC = "${CityClaim.MODID}.basic"
    private const val REGISTER = "${CityClaim.MODID}.register"

    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: CommandManager.RegistrationEnvironment){
        dispatcher.register(
            CommandManager.literal("city").requires(checkPermission((BASIC))).executes(::showGuide)
            .then(CommandManager.literal("rent").executes(RentCommand::rentClaim))

            .then(CommandManager.literal("check").executes(CheckCommand::checkClaim))

            .then(CommandManager.literal("renew")
                .then(CommandManager.literal("on").executes { context -> RenewCommand.toggleRenewClaim(context, true) })
                .then(CommandManager.literal("off").executes { context -> RenewCommand.toggleRenewClaim(context, false) }))

            .then(CommandManager.literal("share")
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile()).executes(ShareCommand::shareClaim)))

            .then(CommandManager.literal("unshare")
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile()).executes(ShareCommand::unshareClaim)))

            .then(CommandManager.literal("register").requires(checkPermission(REGISTER))
                .then(CommandManager.argument("cost", IntegerArgumentType.integer(1))
                    .then(CommandManager.argument("period", IntegerArgumentType.integer(1)).executes(RegisterCommand::registerClaim))))

            .then(CommandManager.literal("unrent").requires(checkPermission(REGISTER)).executes(RentCommand::unrentClaim))
        )
    }

    private fun checkPermission(permission: String): Predicate<ServerCommandSource> {
        return Predicate { source ->
            val player = source.player ?: return@Predicate false
            Permissions.check(player, permission)
        }
    }

    fun showGuide(context: CommandContext<ServerCommandSource>): Int {
        var message = """
            §e/city rent §r承租目前所在的租地
            §e/city check §r查看目前所在租地的租約及實體用量
            §e/city renew §r開啟/關閉自動續約（預設開啟）
            §e/city share <player> §r分享租地給其他玩家
            §e/city unshare <player> §r取消其他玩家的租地分享
        """.trimIndent()

        val player = context.source.player
        if (player != null && player.hasPermissionLevel(4)) {
            message = message.plus("\n§e/city register <cost> <period> §r註冊租地給玩家使用")
        }
        sendFeedback(context, message, false)
        return 1
    }
}