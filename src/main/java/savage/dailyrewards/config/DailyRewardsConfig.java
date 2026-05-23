package savage.dailyrewards.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class representing the daily rewards configuration.
 */
public class DailyRewardsConfig {

    public enum RewardMode {
        STREAK,
        RANDOM
    }

    public String economyProvider = "savs_common_economy";
    public String currencyId = "dollar";
    public String timezone = "";
    public RewardMode mode = RewardMode.STREAK;
    public List<RewardEntry> rewards = new ArrayList<>();

    public DailyRewardsConfig() {
        // Initialize default rewards for a 7-day cycle in sequential order
        rewards.add(new RewardEntry("Day 1 Reward", 100.0, 100, List.of(), List.of()));
        rewards.add(new RewardEntry("Day 2 Reward", 150.0, 100, List.of(), List.of()));
        rewards.add(new RewardEntry("Day 3 Reward", 200.0, 100, List.of(), List.of()));
        rewards.add(new RewardEntry("Day 4 Reward", 250.0, 100, List.of(), List.of()));
        rewards.add(new RewardEntry("Day 5 Reward", 300.0, 100, List.of(), List.of()));
        rewards.add(new RewardEntry("Day 6 Reward", 400.0, 100, List.of(), List.of()));
        rewards.add(new RewardEntry("Day 7 Reward (Legendary)", 500.0, 10, List.of("minecraft:diamond 1"), List.of()));
    }

    /**
     * Represents a single reward entry for a daily milestone.
     */
    public static class RewardEntry {
        public String displayName;
        public double economyPayout;
        public int weight = 100;
        public List<String> items = new ArrayList<>();
        public List<String> commands = new ArrayList<>();

        // Required default constructor for serialization
        public RewardEntry() {
            this.displayName = "Daily Reward";
            this.economyPayout = 100.0;
            this.weight = 100;
            this.items = new ArrayList<>();
            this.commands = new ArrayList<>();
        }

        public RewardEntry(String displayName, double economyPayout, List<String> commands) {
            this(displayName, economyPayout, 100, new ArrayList<>(), commands);
        }

        public RewardEntry(String displayName, double economyPayout, List<String> items, List<String> commands) {
            this(displayName, economyPayout, 100, items, commands);
        }

        public RewardEntry(String displayName, double economyPayout, int weight, List<String> items, List<String> commands) {
            this.displayName = displayName;
            this.economyPayout = economyPayout;
            this.weight = weight;
            this.items = items != null ? items : new ArrayList<>();
            this.commands = commands != null ? commands : new ArrayList<>();
        }
    }
}
