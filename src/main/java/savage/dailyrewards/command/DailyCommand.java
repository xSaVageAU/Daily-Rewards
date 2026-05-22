package savage.dailyrewards.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import savage.dailyrewards.config.ConfigManager;
import savage.dailyrewards.config.DailyRewardsConfig;
import savage.dailyrewards.data.PlayerRewardState;
import savage.dailyrewards.data.PlayerStateManager;
import savage.dailyrewards.integration.EconomyIntegration;
import savage.dailyrewards.util.TimeUtils;

import java.util.List;

/**
 * Handles Brigadier command registrations and operations for daily rewards.
 */
public final class DailyCommand {

    private DailyCommand() {
        // Prevent instantiation
    }

    /**
     * Registers Brigadier command nodes.
     *
     * @param dispatcher Brigadier dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var dailyNode = Commands.literal("daily")
                .executes(DailyCommand::showStatus)
                .then(Commands.literal("status")
                        .executes(DailyCommand::showStatus))
                .then(Commands.literal("claim")
                        .executes(DailyCommand::claimReward));

        dispatcher.register(dailyNode);
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            PlayerRewardState state = PlayerStateManager.getOrCreateState(player.getUUID(), player.getGameProfile().name());
            DailyRewardsConfig config = ConfigManager.getConfig();

            context.getSource().sendSystemMessage(Component.literal("§6=== Daily Rewards Status ==="));
            context.getSource().sendSystemMessage(Component.literal("§eCurrent Streak: §a" + state.currentStreak + " / 7 days"));

            if (state.claimedToday) {
                context.getSource().sendSystemMessage(Component.literal("§cYou have already claimed today's reward!"));
                context.getSource().sendSystemMessage(Component.literal("§7Come back tomorrow for your next reward."));
            } else {
                int required = config.requiredPlaytimeSeconds;
                int accumulated = state.playtimeTodaySeconds;
                
                if (accumulated >= required) {
                    context.getSource().sendSystemMessage(Component.literal("§a✔ Your daily reward is ready to be claimed!"));
                    context.getSource().sendSystemMessage(Component.literal("§eType §d/daily claim §eto claim it."));
                } else {
                    int remaining = required - accumulated;
                    int minutes = remaining / 60;
                    int seconds = remaining % 60;
                    context.getSource().sendSystemMessage(Component.literal("§c⌚ Playtime remaining: §e" + minutes + "m " + seconds + "s"));
                    context.getSource().sendSystemMessage(Component.literal("§7Keep active on the server to unlock your reward!"));
                }
            }
            context.getSource().sendSystemMessage(Component.literal("§6==========================="));
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Only online players can run this command."));
        }
        return 1;
    }

    private static int claimReward(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            PlayerRewardState state = PlayerStateManager.getOrCreateState(player.getUUID(), player.getGameProfile().name());
            DailyRewardsConfig config = ConfigManager.getConfig();
            long currentDay = TimeUtils.getCurrentEpochDay();

            if (state.claimedToday || state.lastClaimEpochDay >= currentDay) {
                context.getSource().sendFailure(Component.literal("§cYou have already claimed today's reward!"));
                return 1;
            }

            int required = config.requiredPlaytimeSeconds;
            if (state.playtimeTodaySeconds < required) {
                int remaining = required - state.playtimeTodaySeconds;
                int minutes = remaining / 60;
                int seconds = remaining % 60;
                context.getSource().sendFailure(Component.literal(
                    "§cYou must play for " + minutes + "m " + seconds + "s more before claiming today's reward!"
                ));
                return 1;
            }

            // Streak determination
            if (state.lastClaimEpochDay == currentDay - 1) {
                // Consecutive check-in: Increment streak (max 7)
                state.currentStreak = Math.min(7, state.currentStreak + 1);
            } else {
                // Streak broken or brand new: Reset to Day 1
                state.currentStreak = 1;
            }

            String streakKey = String.valueOf(state.currentStreak);
            DailyRewardsConfig.RewardEntry reward = config.streakRewards.get(streakKey);

            if (reward == null) {
                reward = new DailyRewardsConfig.RewardEntry("Day " + state.currentStreak + " Reward", 100.0, List.of());
            }

            // Lock progress and set last claim day
            state.claimedToday = true;
            state.lastClaimEpochDay = currentDay;
            PlayerStateManager.save();

            // Run console commands
            var server = context.getSource().getServer();
            if (server != null && reward.commands != null) {
                for (String cmd : reward.commands) {
                    String formattedCmd = cmd.replace("%player%", player.getGameProfile().name());
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), formattedCmd);
                }
            }

            // Execute economy integration payouts (Phase 5 will implement the detailed hook)
            double payout = reward.economyPayout;
            player.sendSystemMessage(Component.literal(
                "§a[Daily Rewards] Successfully claimed Day " + state.currentStreak + " Reward: " + reward.displayName + "!"
            ));
            
            // Economy integration
            EconomyIntegration.payout(player, payout);

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cAn error occurred while claiming your reward."));
        }
        return 1;
    }
}
