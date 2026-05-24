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
                .executes(DailyCommand::executeDaily);

        var reloadNode = Commands.literal("dailyreload")
                .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(DailyCommand::reloadConfig);

        dispatcher.register(dailyNode);
        dispatcher.register(reloadNode);
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        ConfigManager.load();
        context.getSource().sendSystemMessage(Component.literal("Daily Rewards configuration reloaded.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int executeDaily(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            
            if (PlayerStateManager.isLoading(player.getUUID())) {
                context.getSource().sendFailure(
                    Component.literal("Your daily rewards profile is still loading. Please try again in a moment!").withStyle(ChatFormatting.RED)
                );
                return 1;
            }

            PlayerRewardState state = PlayerStateManager.getOrCreateState(player.getUUID(), player.getGameProfile().name());
            DailyRewardsConfig config = ConfigManager.getConfig();
            long currentDay = TimeUtils.getCurrentEpochDay();

            DailyRewardsConfig.RewardEntry reward;

            synchronized (state) {
                if (state.lastClaimEpochDay >= currentDay) {
                    printStatus(context.getSource(), state, config, currentDay);
                    return 1;
                }

                reward = determineReward(state, config, currentDay);
                state.lastClaimEpochDay = currentDay;
            }
            
            PlayerStateManager.save(player.getUUID());

            deliverRewards(player, reward, context.getSource().getServer());

        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("An error occurred while claiming your reward.").withStyle(ChatFormatting.RED)
            );
        }
        return 1;
    }

    private static void printStatus(CommandSourceStack source, PlayerRewardState state, DailyRewardsConfig config, long currentDay) {
        source.sendSystemMessage(Component.literal("=== Daily Rewards Status ===").withStyle(ChatFormatting.GOLD));
        
        int maxDays = config.rewards.isEmpty() ? 7 : config.rewards.size();
        
        if (config.mode == DailyRewardsConfig.RewardMode.STREAK) {
            state.validateStreak(currentDay);
            int displayStreak = state.currentStreak;
            source.sendSystemMessage(
                Component.literal("Current Streak: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(displayStreak + " / " + maxDays + " days").withStyle(ChatFormatting.GREEN))
            );
        } else {
            source.sendSystemMessage(
                Component.literal("Reward Mode: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Mystery Rewards Pool").withStyle(ChatFormatting.LIGHT_PURPLE))
            );
        }

        source.sendSystemMessage(Component.literal("You have already claimed today's reward!").withStyle(ChatFormatting.RED));
        
        String timeLeft = TimeUtils.getTimeUntilNextReset();
        source.sendSystemMessage(Component.literal("Next reset in: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(timeLeft).withStyle(ChatFormatting.AQUA)));
            
        source.sendSystemMessage(Component.literal("===========================").withStyle(ChatFormatting.GOLD));
    }

    private static DailyRewardsConfig.RewardEntry determineReward(PlayerRewardState state, DailyRewardsConfig config, long currentDay) {
        if (config.mode == DailyRewardsConfig.RewardMode.STREAK) {
            state.validateStreak(currentDay);
            int maxDays = config.rewards.isEmpty() ? 7 : config.rewards.size();

            // Increment sequentially or execute loop-around caps
            if (state.currentStreak >= maxDays) {
                state.currentStreak = 1;
            } else {
                state.currentStreak++;
            }

            int index = state.currentStreak - 1;
            if (index >= 0 && index < config.rewards.size()) {
                return config.rewards.get(index);
            } else {
                return new DailyRewardsConfig.RewardEntry("Day " + state.currentStreak + " Reward", 100.0, List.of());
            }
        } else {
            return getWeightedRandomReward(config.rewards);
        }
    }

    private static void deliverRewards(ServerPlayer player, DailyRewardsConfig.RewardEntry reward, net.minecraft.server.MinecraftServer server) {
        // Run console commands
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
                    Identifier itemId = Identifier.tryParse(itemIdStr);
                    if (itemId == null) {
                        continue;
                    }
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
