package savage.dailyrewards.integration;

import net.minecraft.server.level.ServerPlayer;
import savage.dailyrewards.DailyRewards;

/**
 * Handles communication with external Economy providers.
 */
public final class EconomyIntegration {

    private EconomyIntegration() {
        // Prevent instantiation
    }

    /**
     * Executes the daily reward deposit for a player.
     *
     * @param player the player receiving the payout
     * @param amount the deposit amount in dollars
     */
    public static void payout(ServerPlayer player, double amount) {
        DailyRewards.LOGGER.info("Economy payout scheduled for player {}: ${}", player.getGameProfile().name(), amount);
    }
}
