package savage.dailyrewards.tracker;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import savage.dailyrewards.data.PlayerRewardState;
import savage.dailyrewards.data.PlayerStateManager;

/**
 * Tracks active playtime for online players and handles connection events.
 */
public final class PlaytimeTracker {

    private static int tickCounter = 0;
    private static int saveCounter = 0;

    private PlaytimeTracker() {
        // Prevent instantiation
    }

    /**
     * Registers playtime tracking listeners and connection state changes.
     */
    public static void register() {
        // Ticks every server tick to increment playtime for active players once per second (20 ticks)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PlayerRewardState state = PlayerStateManager.getOrCreateState(player.getUUID(), player.getGameProfile().name());
                    // Only accumulate playtime if they haven't claimed today's reward yet
                    if (!state.claimedToday) {
                        state.playtimeTodaySeconds++;
                    }
                }

                // Automatically execute background save every 5 minutes (300 seconds)
                saveCounter++;
                if (saveCounter >= 300) {
                    saveCounter = 0;
                    PlayerStateManager.save();
                }
            }
        });

        // Pre-cache and refresh player states upon join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            PlayerStateManager.getOrCreateState(player.getUUID(), player.getGameProfile().name());
        });

        // Trigger immediate background save upon player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            // Force final update and save
            PlayerStateManager.getOrCreateState(player.getUUID(), player.getGameProfile().name());
            PlayerStateManager.save();
        });
    }
}
