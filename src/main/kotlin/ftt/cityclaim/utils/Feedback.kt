package ftt.cityclaim.utils

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object Feedback {
    fun sendFeedback(
        context: CommandContext<ServerCommandSource>,
        message: String,
        header: Boolean = true
    ) {
        if (header) {
            return context.source.sendFeedback({
                Text.literal("【租地系統】").withColor(45824).append(Text.literal(message).withColor(16777215))
            }, false)
        }
        return context.source.sendFeedback({ Text.literal(message) }, false)
    }
}