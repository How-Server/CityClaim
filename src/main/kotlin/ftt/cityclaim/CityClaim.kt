package ftt.cityclaim

import com.gmail.sneakdevs.diamondeconomy.DiamondUtils
import ftt.cityclaim.commands.CityCommands
import ftt.cityclaim.routine.CheckClaim
import ftt.sql.CityclaimManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer


object CityClaim : ModInitializer {
    const val MODID = "cityclaim"
    const val CLAIM_ROLE = "trusted"

    val moneyManager = DiamondUtils.getDatabaseManager()
    lateinit var cityManager: CityclaimManager
    lateinit var server: MinecraftServer

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(::initDatabase)
        ServerLifecycleEvents.SERVER_STOPPED.register(::stopDatabase)

        ServerPlayConnectionEvents.JOIN.register(CheckClaim::playerRent)
        CommandRegistrationCallback.EVENT.register(CityCommands::register)
    }

    private fun initDatabase(server: MinecraftServer) {
        this.server = server
        cityManager = CityclaimManager(server)
    }

    private fun stopDatabase(server: MinecraftServer) {
        cityManager.stopConnection()
    }
}