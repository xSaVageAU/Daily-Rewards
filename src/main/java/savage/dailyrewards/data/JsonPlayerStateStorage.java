package savage.dailyrewards.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import savage.dailyrewards.DailyRewards;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Implementation of PlayerStateStorage that writes individual player states
 * to isolated JSON files in the config folder. Uses atomic swaps to prevent corruption.
 */
public class JsonPlayerStateStorage implements PlayerStateStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("dailyrewards");
    private static final Path DATA_DIR = CONFIG_DIR.resolve("playerdata");

    @Override
    public void init() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }
        } catch (IOException e) {
            DailyRewards.LOGGER.error("Failed to create player data directory", e);
        }
    }

    @Override
    public PlayerRewardState loadState(UUID uuid) {
        Path playerFile = DATA_DIR.resolve(uuid.toString() + ".json");
        if (Files.exists(playerFile)) {
            try (FileReader reader = new FileReader(playerFile.toFile())) {
                return GSON.fromJson(reader, PlayerRewardState.class);
            } catch (Exception e) {
                DailyRewards.LOGGER.error("Failed to load player data for UUID {}", uuid, e);
            }
        }
        return null;
    }

    @Override
    public void saveState(UUID uuid, PlayerRewardState state) {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }

            Path playerFile = DATA_DIR.resolve(uuid.toString() + ".json");
            Path tmpFile = DATA_DIR.resolve(uuid.toString() + ".json.tmp");

            try (FileWriter writer = new FileWriter(tmpFile.toFile())) {
                GSON.toJson(state, writer);
            }

            Files.move(tmpFile, playerFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DailyRewards.LOGGER.error("Failed to save player data for {}", uuid, e);
        }
    }

    @Override
    public void shutdown() {
        // No special connections to clean up for JSON storage
    }
}
