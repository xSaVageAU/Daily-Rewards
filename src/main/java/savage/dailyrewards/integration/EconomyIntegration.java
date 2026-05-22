package savage.dailyrewards.integration;

import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import savage.dailyrewards.DailyRewards;
import savage.dailyrewards.config.ConfigManager;
import savage.dailyrewards.config.DailyRewardsConfig;

import java.math.BigInteger;

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
     * @param amount the deposit amount
     * @return the formatted Component representing the deposit amount, or null if failed
     */
    public static Component payout(ServerPlayer player, double amount) {
        try {
            var server = player.level() instanceof ServerLevel 
                ? ((ServerLevel) player.level()).getServer() 
                : null;
            if (server == null) {
                DailyRewards.LOGGER.error("MinecraftServer was null when trying to execute payout for {}", player.getGameProfile().name());
                return null;
            }

            DailyRewardsConfig config = ConfigManager.getConfig();
            Identifier currencyId = Identifier.fromNamespaceAndPath(config.economyProvider, config.currencyId);

            // 1. Get Provider by Namespace
            EconomyProvider provider = CommonEconomy.getProvider(currencyId.getNamespace());
            if (provider == null) {
                DailyRewards.LOGGER.warn("Economy provider not found: {}", currencyId.getNamespace());
                player.sendSystemMessage(
                    Component.literal("[Daily Rewards] Warning: Economy provider '" + currencyId.getNamespace() + "' not found. Payout failed.")
                        .withStyle(ChatFormatting.RED)
                );
                return null;
            }

            // 2. Get Currency from Provider
            EconomyCurrency currency = provider.getCurrency(server, currencyId.getPath());
            if (currency == null) {
                // Try full ID if path fails
                currency = provider.getCurrency(server, currencyId.toString());
            }

            if (currency == null) {
                DailyRewards.LOGGER.warn("Currency not found: {} in provider {}", currencyId.getPath(), currencyId.getNamespace());
                player.sendSystemMessage(
                    Component.literal("[Daily Rewards] Warning: Currency '" + currencyId.getPath() + "' not found. Payout failed.")
                        .withStyle(ChatFormatting.RED)
                );
                return null;
            }

            // 3. Get Default Account ID
            var accountId = provider.defaultAccount(server, player.getGameProfile(), currency);
            if (accountId == null) {
                DailyRewards.LOGGER.warn("Default account ID is null for player {}", player.getGameProfile().name());
                player.sendSystemMessage(
                    Component.literal("[Daily Rewards] Warning: Economy account not found. Payout failed.")
                        .withStyle(ChatFormatting.RED)
                );
                return null;
            }

            // 4. Get Account
            EconomyAccount account = provider.getAccount(server, player.getGameProfile(), accountId);
            if (account == null) {
                DailyRewards.LOGGER.warn("Failed to retrieve economy account for player: {}", player.getGameProfile().name());
                player.sendSystemMessage(
                    Component.literal("[Daily Rewards] Warning: Economy account was null. Payout failed.")
                        .withStyle(ChatFormatting.RED)
                );
                return null;
            }

            // Parse currency value
            BigInteger rawAmount = currency.parseValue(String.valueOf(amount));
            if (rawAmount == null) {
                rawAmount = BigInteger.ZERO;
            }

            // Execute deposit
            account.increaseBalance(rawAmount);
            
            // Format currency value cleanly using Common Economy Component representation
            Component formatted = currency.formatValueComponent(rawAmount, false);
            DailyRewards.LOGGER.info("Successfully paid {} to player {}", formatted.getString(), player.getGameProfile().name());
            
            return formatted;

        } catch (Exception e) {
            DailyRewards.LOGGER.error("Failed to execute economy payout for player {}", player.getGameProfile().name(), e);
            player.sendSystemMessage(
                Component.literal("[Daily Rewards] An unexpected error occurred during your economy payout.")
                    .withStyle(ChatFormatting.RED)
            );
            return null;
        }
    }
}
