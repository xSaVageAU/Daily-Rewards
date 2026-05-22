package savage.dailyrewards.integration;

import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import savage.dailyrewards.DailyRewards;

import java.math.BigInteger;
import java.util.Collection;

/**
 * Handles communication and transactions with Patbox's Common Economy API.
 */
public final class EconomyIntegration {

    private EconomyIntegration() {
        // Prevent instantiation
    }

    /**
     * Executes the daily reward deposit for a player via the Common Economy API.
     *
     * @param player the player receiving the payout
     * @param amount the deposit amount in dollars
     */
    public static void payout(ServerPlayer player, double amount) {
        try {
            var server = player.level() instanceof net.minecraft.server.level.ServerLevel 
                ? ((net.minecraft.server.level.ServerLevel) player.level()).getServer() 
                : null;
            if (server == null) {
                DailyRewards.LOGGER.error("MinecraftServer was null when trying to execute payout for {}", player.getGameProfile().name());
                return;
            }

            // Retrieve accounts associated with the player from registered economy providers
            Collection<EconomyAccount> accounts = CommonEconomy.getAccounts(server, player.getGameProfile());
            if (accounts.isEmpty()) {
                DailyRewards.LOGGER.warn("No active economy accounts found for player: {}", player.getGameProfile().name());
                player.sendSystemMessage(Component.literal("§e[Daily Rewards] §cWarning: No economy provider found. Cash reward could not be paid."));
                return;
            }

            // Pick the first registered economy account (e.g. Savs Common Economy account)
            EconomyAccount account = accounts.iterator().next();
            if (account == null) {
                DailyRewards.LOGGER.warn("Failed to retrieve a valid economy account for player: {}", player.getGameProfile().name());
                player.sendSystemMessage(Component.literal("§e[Daily Rewards] §cWarning: Economy account was null."));
                return;
            }

            // Convert payout double to cents (multiply by 100) because common-economy-api works in raw whole units
            // (e.g. cents) as defined in standard setups like SavsCommonEconomy.
            // 1.00 Dollar = 100 Cents
            long cents = Math.round(amount * 100.0);
            BigInteger value = BigInteger.valueOf(cents);

            // Execute deposit
            account.increaseBalance(value);
            DailyRewards.LOGGER.info("Successfully paid ${} to player {}", amount, player.getGameProfile().name());

        } catch (Exception e) {
            DailyRewards.LOGGER.error("Failed to execute economy payout for player {}", player.getGameProfile().name(), e);
            player.sendSystemMessage(Component.literal("§e[Daily Rewards] §cAn unexpected error occurred during your economy payout."));
        }
    }
}
