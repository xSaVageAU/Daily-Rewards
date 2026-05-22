package savage.dailyrewards.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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

            int nextStreak = Math.min(7, state.currentStreak + 1);
            String nextStreakKey = String.valueOf(nextStreak);
            DailyRewardsConfig.RewardEntry nextReward = config.streakRewards.get(nextStreakKey);
            if (nextReward == null) {
                nextReward = new DailyRewardsConfig.RewardEntry("Day " + nextStreakKey + " Reward", 100.0, List.of());
            }

            // Streak star builder:
            MutableComponent streakIndicator = Component.literal("");
            for (int i = 1; i <= 7; i++) {
                if (i <= state.currentStreak) {
                    streakIndicator.append(Component.literal("★").withStyle(ChatFormatting.GOLD));
                } else {
                    streakIndicator.append(Component.literal("☆").withStyle(ChatFormatting.GRAY));
                }
                if (i < 7) {
                    streakIndicator.append(Component.literal(" "));
                }
            }

            context.getSource().sendSystemMessage(
                Component.literal("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    .withStyle(ChatFormatting.GOLD)
            );
            context.getSource().sendSystemMessage(
                Component.literal("             DAILY REWARDS STATUS             ")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
            );
            context.getSource().sendSystemMessage(
                Component.literal("  Current Streak: ").withStyle(ChatFormatting.GRAY)
                    .append(streakIndicator)
                    .append(Component.literal(" (" + state.currentStreak + "/7 days)").withStyle(ChatFormatting.YELLOW))
            );

            String dayLabel = state.claimedToday ? "Tomorrow" : "Today";
            context.getSource().sendSystemMessage(
                Component.literal("  Next Reward (" + dayLabel + "): ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Day " + nextStreakKey + " - " + nextReward.displayName).withStyle(ChatFormatting.LIGHT_PURPLE))
            );

            if (state.claimedToday || state.lastClaimEpochDay >= currentDay) {
                context.getSource().sendSystemMessage(
                    Component.literal("  Status: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Already claimed today!").withStyle(ChatFormatting.RED))
                );
                context.getSource().sendSystemMessage(
                    Component.literal("  Come back tomorrow for your next reward.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                );
            } else {
                context.getSource().sendSystemMessage(
                    Component.literal("  Status: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Ready to claim!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                );
                
                MutableComponent claimButton = Component.literal("  ▶ [CLICK HERE TO CLAIM REWARD] ◀")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/daily claim"))
                        .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("Click to claim your Day " + nextStreakKey + " Reward!").withStyle(ChatFormatting.GOLD)
                        ))
                    );
                context.getSource().sendSystemMessage(claimButton);
            }

            context.getSource().sendSystemMessage(
                Component.literal("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")
                    .withStyle(ChatFormatting.GOLD)
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
                    Component.literal("[Daily Rewards] You have already claimed today's reward!")
                        .withStyle(ChatFormatting.RED)
                );
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

            // Execute economy integration payouts (returns formatted component or null)
            double payout = reward.economyPayout;
            Component formattedDeposit = EconomyIntegration.payout(player, payout);

            // Construct unified beautiful claim message
            MutableComponent message = Component.literal("[Daily Rewards] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Successfully claimed ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("Day " + state.currentStreak + " Reward: " + reward.displayName).withStyle(ChatFormatting.YELLOW));
            
            if (formattedDeposit != null) {
                message.append(Component.literal(" (Deposited ").withStyle(ChatFormatting.GREEN))
                       .append(formattedDeposit.copy().withStyle(ChatFormatting.GOLD))
                       .append(Component.literal(")").withStyle(ChatFormatting.GREEN));
            } else {
                message.append(Component.literal("!").withStyle(ChatFormatting.GREEN));
            }

            player.sendSystemMessage(message);

        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("[Daily Rewards] An error occurred while claiming your reward.")
                    .withStyle(ChatFormatting.RED)
            );
        }
        return 1;
    }
}
