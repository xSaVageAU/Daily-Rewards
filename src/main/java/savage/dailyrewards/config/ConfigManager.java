package savage.dailyrewards.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import savage.dailyrewards.DailyRewards;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles persistence operations for the configuration system.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("dailyrewards");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    
    private static DailyRewardsConfig config;

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
                DailyRewards.LOGGER.info("Config file not found. Creating default configuration at: {}", CONFIG_FILE);
                config = new DailyRewardsConfig();
                save();
            } else {
                try (FileReader reader = new FileReader(file)) {
                    config = GSON.fromJson(reader, DailyRewardsConfig.class);
                    if (config == null) {
                        config = new DailyRewardsConfig();
                    }
                }
            }
        } catch (IOException e) {
            DailyRewards.LOGGER.error("Failed to load daily rewards configuration", e);
            config = new DailyRewardsConfig();
        }
    }

    /**
     * Saves the current configuration to the disk.
     */
    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(config, writer);
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
