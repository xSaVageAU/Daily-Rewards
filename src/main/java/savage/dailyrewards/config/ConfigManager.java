package savage.dailyrewards.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import net.fabricmc.loader.api.FabricLoader;
import savage.dailyrewards.DailyRewards;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence operations for the configuration system using TOML format.
 */
public final class ConfigManager {

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("dailyrewards");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.toml");
    
    private static DailyRewardsConfig config;

    private static final String DEFAULT_TOML_CONFIG = """
            # =========================================================================
            #   Daily Rewards Configuration
            # =========================================================================
            
            # The hour of the day (0-23) when the daily rewards cycle resets.
            # Default: 0 (Midnight)
            resetHour = 0
            
            # The economy provider to use for cash payouts.
            # Must match a provider registered in Patbox's Common Economy API.
            # Default: "savs_common_economy"
            economyProvider = "savs_common_economy"
            
            # The currency ID to use for cash payouts.
            # Default: "dollar"
            currencyId = "dollar"
            
            # --- Streak Rewards ---
            # Configure rewards for each consecutive day of the streak.
            # The player will progress through these in the exact order they are listed below.
            # Add or remove blocks as desired! The mod will dynamically handle any number of days.
            # Format:
            # [[rewards]]
            # displayName = "Display Name in Chat"
            # economyPayout = <amount_to_deposit>
            # commands = ["list", "of", "commands", "to", "run"]
            
            [[rewards]]
            displayName = "Day 1 Reward"
            economyPayout = 100.0
            commands = []
            
            [[rewards]]
            displayName = "Day 2 Reward"
            economyPayout = 200.0
            commands = []
            
            [[rewards]]
            displayName = "Day 3 Reward"
            economyPayout = 300.0
            commands = []
            
            [[rewards]]
            displayName = "Day 4 Reward"
            economyPayout = 400.0
            commands = []
            
            [[rewards]]
            displayName = "Day 5 Reward"
            economyPayout = 500.0
            commands = []
            
            [[rewards]]
            displayName = "Day 6 Reward"
            economyPayout = 600.0
            commands = []
            
            [[rewards]]
            displayName = "Day 7 Reward (Legendary)"
            economyPayout = 1000.0
            commands = [
                "give %player% diamond 1"
            ]
            """;

    private ConfigManager() {
        // Prevent instantiation
    }

    /**
     * Loads the configuration from the disk, creating a default one if none exists.
     */
    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            File file = CONFIG_FILE.toFile();
            if (!file.exists()) {
                DailyRewards.LOGGER.info("Config file not found. Creating default TOML configuration at: {}", CONFIG_FILE);
                Files.writeString(CONFIG_FILE, DEFAULT_TOML_CONFIG);
                config = new DailyRewardsConfig();
            } else {
                parseConfig(file);
            }
        } catch (Exception e) {
            DailyRewards.LOGGER.error("Failed to load daily rewards configuration", e);
            config = new DailyRewardsConfig();
        }
    }

    /**
     * Manually and safely parses the TOML file into the config data object to avoid class coercion/reflection issues.
     */
    private static void parseConfig(File file) {
        try {
            Toml toml = new Toml().read(file);
            DailyRewardsConfig loaded = new DailyRewardsConfig();

            if (toml.contains("resetHour")) {
                loaded.resetHour = toml.getLong("resetHour").intValue();
            }
            if (toml.contains("economyProvider")) {
                loaded.economyProvider = toml.getString("economyProvider");
            }
            if (toml.contains("currencyId")) {
                loaded.currencyId = toml.getString("currencyId");
            }

            if (toml.contains("rewards")) {
                List<Toml> rewardsTables = toml.getTables("rewards");
                List<DailyRewardsConfig.RewardEntry> rewards = new ArrayList<>();

                for (Toml rewardTable : rewardsTables) {
                    if (rewardTable != null) {
                        String displayName = rewardTable.getString("displayName", "Daily Reward");
                        Double economyPayout = rewardTable.getDouble("economyPayout");
                        if (economyPayout == null) {
                            // Support integer entries as well for double
                            Long payoutLong = rewardTable.getLong("economyPayout");
                            economyPayout = payoutLong != null ? payoutLong.doubleValue() : 100.0;
                        }
                        List<String> commands = rewardTable.getList("commands");
                        if (commands == null) {
                            commands = new ArrayList<>();
                        }
                        rewards.add(new DailyRewardsConfig.RewardEntry(displayName, economyPayout, commands));
                    }
                }
                if (!rewards.isEmpty()) {
                    loaded.rewards = rewards;
                }
            }

            config = loaded;
        } catch (Exception e) {
            DailyRewards.LOGGER.error("Error parsing config.toml. Falling back to default configuration.", e);
            config = new DailyRewardsConfig();
        }
    }

    /**
     * Saves the current configuration to the disk in TOML format.
     */
    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                new TomlWriter().write(config, writer);
            }
        } catch (IOException e) {
            DailyRewards.LOGGER.error("Failed to save daily rewards configuration", e);
        }
    }

    /**
     * Retrieves the loaded configuration, loading it first if it hasn't been initialized.
     *
     * @return the configuration object
     */
    public static DailyRewardsConfig getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }
}
