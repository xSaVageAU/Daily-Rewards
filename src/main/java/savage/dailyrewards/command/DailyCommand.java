package savage.dailyrewards.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import savage.dailyrewards.config.ConfigManager;
import savage.dailyrewards.config.DailyRewardsConfig;
import savage.dailyrewards.data.PlayerRewardState;
import savage.dailyrewards.data.PlayerStateManager;
import savage.dailyrewards.integration.EconomyIntegration;
import savage.dailyrewards.util.TimeUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
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
                        .executes(DailyCommand::claimReward))
                .then(Commands.literal("reload")
                        .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(DailyCommand::reloadConfig));

        dispatcher.register(dailyNode);
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        ConfigManager.load();
        context.getSource().sendSystemMessage(Component.literal("Daily Rewards configuration reloaded.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            PlayerRewardState state = PlayerStateManager.getOrCreateState(player.getUUID(), player.getGameProfile().name());
            DailyRewardsConfig config = ConfigManager.getConfig();
            long currentDay = TimeUtils.getCurrentEpochDay();

            int maxDays = config.rewards.isEmpty() ? 7 : config.rewards.size();


            context.getSource().sendSystemMessage(
                Component.literal("=== Daily Rewards Status ===").withStyle(ChatFormatting.GOLD)
            );
            
            if (config.mode == DailyRewardsConfig.RewardMode.STREAK) {
                int displayStreak = state.currentStreak;
                if (state.lastClaimEpochDay < currentDay - 1) {
                    displayStreak = 0;
                }
                context.getSource().sendSystemMessage(
                    Component.literal("Current Streak: ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(displayStreak + " / " + maxDays + " days").withStyle(ChatFormatting.GREEN))
                );
            } else {
                context.getSource().sendSystemMessage(
                    Component.literal("Reward Mode: ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("Mystery Rewards Pool").withStyle(ChatFormatting.LIGHT_PURPLE))
                );
            }

            if (state.lastClaimEpochDay >= currentDay) {
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

            if (state.lastClaimEpochDay >= currentDay) {
                context.getSource().sendFailure(
                    Component.literal("You have already claimed today's reward!").withStyle(ChatFormatting.RED)
                );
                return 1;
            }

            int maxDays = config.rewards.isEmpty() ? 7 : config.rewards.size();
            DailyRewardsConfig.RewardEntry reward;

            if (config.mode == DailyRewardsConfig.RewardMode.STREAK) {
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

                int index = state.currentStreak - 1;
                if (index >= 0 && index < config.rewards.size()) {
                    reward = config.rewards.get(index);
                } else {
                    reward = new DailyRewardsConfig.RewardEntry("Day " + state.currentStreak + " Reward", 100.0, List.of());
                }
            } else {
                // RANDOM mode: Weighted random selection
                reward = getWeightedRandomReward(config.rewards);
            }

            // Lock progress and set last claim day
            state.lastClaimEpochDay = currentDay;
            PlayerStateManager.save(player.getUUID());

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
            Component formattedDeposit = null;
            if (payout > 0) {
                formattedDeposit = EconomyIntegration.payout(player, payout);
            }

            // Deliver native items
            List<Component> claimedItemComponents = new ArrayList<>();
            if (reward.items != null) {
                for (String itemStr : reward.items) {
                    if (itemStr == null || itemStr.trim().isEmpty()) {
                        continue;
                    }
                    
                    String[] parts = itemStr.trim().split("\\s+");
                    String itemIdStr = parts[0];
                    int count = 1;
                    if (parts.length > 1) {
                        try {
                            count = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            // Leave as 1
                        }
                    }
                    
                    try {
                        Identifier itemId = Identifier.parse(itemIdStr);
                        Item item = BuiltInRegistries.ITEM.get(itemId)
                            .map(net.minecraft.core.Holder::value)
                            .orElse(Items.AIR);
                        if (item != Items.AIR) {
                            ItemStack stack = new ItemStack(item, count);
                            boolean added = player.getInventory().add(stack);
                            if (!added || !stack.isEmpty()) {
                                player.drop(stack, false);
                            }
                            
                            MutableComponent itemText = Component.literal(count + "x ")
                                .append(Component.translatable(item.getDescriptionId()).withStyle(ChatFormatting.AQUA));
                            claimedItemComponents.add(itemText);
                        }
                    } catch (Exception e) {
                        // Ignore invalid item ID
                    }
                }
            }

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

            if (!claimedItemComponents.isEmpty()) {
                MutableComponent itemsMessage = Component.literal("  » Received: ").withStyle(ChatFormatting.DARK_GREEN);
                for (int i = 0; i < claimedItemComponents.size(); i++) {
                    if (i > 0) {
                        itemsMessage.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                    }
                    itemsMessage.append(claimedItemComponents.get(i));
                }
                player.sendSystemMessage(itemsMessage);
            }

        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("An error occurred while claiming your reward.").withStyle(ChatFormatting.RED)
            );
        }
        return 1;
    }

    private static DailyRewardsConfig.RewardEntry getWeightedRandomReward(List<DailyRewardsConfig.RewardEntry> rewards) {
        if (rewards.isEmpty()) {
            return new DailyRewardsConfig.RewardEntry("Default Reward", 100.0, 100, List.of(), List.of());
        }

        int totalWeight = 0;
        for (DailyRewardsConfig.RewardEntry entry : rewards) {
            totalWeight += Math.max(1, entry.weight);
        }

        int randomValue = java.util.concurrent.ThreadLocalRandom.current().nextInt(totalWeight);
        int currentSum = 0;

        for (DailyRewardsConfig.RewardEntry entry : rewards) {
            currentSum += Math.max(1, entry.weight);
            if (randomValue < currentSum) {
                return entry;
            }
        }

        return rewards.get(0);
    }
}
