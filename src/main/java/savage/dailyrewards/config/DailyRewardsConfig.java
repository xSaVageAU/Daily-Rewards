package savage.dailyrewards.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class representing the daily rewards configuration.
 */
public class DailyRewardsConfig {

    public int resetHour = 0;
    public String economyProvider = "savs_common_economy";
    public String currencyId = "dollar";
    public List<RewardEntry> rewards = new ArrayList<>();

    public DailyRewardsConfig() {
        // Initialize default rewards for a 7-day cycle in sequential order
        rewards.add(new RewardEntry("Day 1 Reward", 100.0, List.of()));
        rewards.add(new RewardEntry("Day 2 Reward", 200.0, List.of()));
        rewards.add(new RewardEntry("Day 3 Reward", 300.0, List.of()));
        rewards.add(new RewardEntry("Day 4 Reward", 400.0, List.of()));
        rewards.add(new RewardEntry("Day 5 Reward", 500.0, List.of()));
        rewards.add(new RewardEntry("Day 6 Reward", 600.0, List.of()));
        rewards.add(new RewardEntry("Day 7 Reward (Legendary)", 1000.0, List.of("give %player% diamond 1")));
    }

    /**
     * Represents a single reward entry for a daily milestone.
     */
    public static class RewardEntry {
        public String displayName;
        public double economyPayout;
        public List<String> commands;

        // Required default constructor for serialization
        public RewardEntry() {
            this.displayName = "Daily Reward";
            this.economyPayout = 100.0;
            this.commands = new ArrayList<>();
        }

        public RewardEntry(String displayName, double economyPayout, List<String> commands) {
            this.displayName = displayName;
            this.economyPayout = economyPayout;
            this.commands = commands;
        }
    }
}
