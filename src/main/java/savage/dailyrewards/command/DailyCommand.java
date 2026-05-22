package savage.dailyrewards.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
            long currentDay = TimeUtils.getCurrentEpochDay();

            int maxDays = config.streakRewards.isEmpty() ? 7 : config.streakRewards.size();

            // Calculate next streak dynamically with rollover
            int nextStreak;
            if (state.claimedToday || state.lastClaimEpochDay >= currentDay) {
                nextStreak = (state.currentStreak >= maxDays) ? 1 : state.currentStreak + 1;
            } else {
                nextStreak = (state.lastClaimEpochDay == currentDay - 1) 
                    ? ((state.currentStreak >= maxDays) ? 1 : state.currentStreak + 1)
                    : 1;
            }

            String nextStreakKey = String.valueOf(nextStreak);
            DailyRewardsConfig.RewardEntry nextReward = config.streakRewards.get(nextStreakKey);
            if (nextReward == null) {
                nextReward = new DailyRewardsConfig.RewardEntry("Day " + nextStreakKey + " Reward", 100.0, List.of());
            }

            context.getSource().sendSystemMessage(
                Component.literal("=== Daily Rewards Status ===").withStyle(ChatFormatting.GOLD)
            );
            
            context.getSource().sendSystemMessage(
                Component.literal("Current Streak: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(state.currentStreak + " / " + maxDays + " days").withStyle(ChatFormatting.GREEN))
            );

            if (state.claimedToday || state.lastClaimEpochDay >= currentDay) {
                context.getSource().sendSystemMessage(
                    Component.literal("You have already claimed today's reward!").withStyle(ChatFormatting.RED)
                );
                context.getSource().sendSystemMessage(
                    Component.literal("Come back tomorrow for your next reward.").withStyle(ChatFormatting.GRAY)
                );
            } else {
                context.getSource().sendSystemMessage(
                    Component.literal("✔ Your daily reward is ready to be claimed!").withStyle(ChatFormatting.GREEN)
                );
                context.getSource().sendSystemMessage(
                    Component.literal("Type ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("/daily claim").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" to claim it.").withStyle(ChatFormatting.YELLOW))
                );
            }

            context.getSource().sendSystemMessage(
                Component.literal("===========================").withStyle(ChatFormatting.GOLD)
            );
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
                context.getSource().sendFailure(
                    Component.literal("You have already claimed today's reward!").withStyle(ChatFormatting.RED)
                );
                return 1;
            }

            int maxDays = config.streakRewards.isEmpty() ? 7 : config.streakRewards.size();

            // Streak determination with dynamic rollover
            if (state.lastClaimEpochDay == currentDay - 1) {
                // Consecutive check-in: Increment streak, rollover to 1 if we exceed maxDays
                if (state.currentStreak >= maxDays) {
                    state.currentStreak = 1;
                } else {
                    state.currentStreak = state.currentStreak + 1;
                }
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

            // Execute economy integration payouts (returns formatted component or null)
            double payout = reward.economyPayout;
            Component formattedDeposit = EconomyIntegration.payout(player, payout);

            // Construct unified beautiful claim message
            MutableComponent message = Component.literal("[Daily Rewards] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Successfully claimed ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(reward.displayName).withStyle(ChatFormatting.YELLOW));
            
            if (formattedDeposit != null) {
                message.append(Component.literal("! (Deposited ").withStyle(ChatFormatting.GREEN))
                       .append(formattedDeposit.copy().withStyle(ChatFormatting.GOLD))
                       .append(Component.literal(")").withStyle(ChatFormatting.GREEN));
            } else {
                message.append(Component.literal("!").withStyle(ChatFormatting.GREEN));
            }

            player.sendSystemMessage(message);

        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("An error occurred while claiming your reward.").withStyle(ChatFormatting.RED)
            );
        }
        return 1;
    }
}
