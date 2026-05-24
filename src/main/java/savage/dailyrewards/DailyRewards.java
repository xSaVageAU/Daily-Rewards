package savage.dailyrewards;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.dailyrewards.command.DailyCommand;
import savage.dailyrewards.config.ConfigManager;
import savage.dailyrewards.data.PlayerStateManager;

import java.util.UUID;

public class DailyRewards implements ModInitializer {
	public static final String MOD_ID = "daily-rewards";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Load config and player database
		ConfigManager.load();
		PlayerStateManager.load();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DailyCommand.register(dispatcher);
		});

		// Save state database upon server shutdown
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Stopping and saving daily rewards player database...");
			PlayerStateManager.shutdown();
		});

		// Pre-load player daily rewards state asynchronously upon connect to avoid main-thread IO lag
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			PlayerStateManager.preLoad(player.getUUID(), player.getGameProfile().name());
		});

		// Free memory and ensure save on player disconnect
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID playerUuid = handler.getPlayer().getUUID();
			PlayerStateManager.save(playerUuid);
			PlayerStateManager.evict(playerUuid);
		});

		LOGGER.info("Daily Rewards Mod initialized successfully!");
	}
}