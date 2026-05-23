package savage.dailyrewards.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import savage.dailyrewards.DailyRewards;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages player states and coordinates high-performance background file IO.
 */
public final class PlayerStateManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("dailyrewards");
    private static final Path DATA_DIR = CONFIG_DIR.resolve("playerdata");
    
    private static final ConcurrentHashMap<UUID, PlayerRewardState> STATES = new ConcurrentHashMap<>();
    
    // Executor using Java Virtual Threads for background file operations (JDK 25)
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private PlayerStateManager() {
        // Prevent instantiation
    }

    public static void load() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }
        } catch (IOException e) {
            DailyRewards.LOGGER.error("Failed to create player data directory", e);
        }
    }

    public static void save(UUID uuid) {
        PlayerRewardState state = STATES.get(uuid);
        if (state == null) return;
        
        // Take a deep-copy snapshot for thread safety
        PlayerRewardState snapshot;
        synchronized (state) {
            snapshot = state.copy();
        }

        EXECUTOR.execute(() -> {
            try {
                if (!Files.exists(DATA_DIR)) {
                    Files.createDirectories(DATA_DIR);
                }
                
                Path playerFile = DATA_DIR.resolve(uuid.toString() + ".json");
                Path tmpFile = DATA_DIR.resolve(uuid.toString() + ".json.tmp");

                try (FileWriter writer = new FileWriter(tmpFile.toFile())) {
                    GSON.toJson(snapshot, writer);
                }
                
                Files.move(tmpFile, playerFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                DailyRewards.LOGGER.error("Failed to save player data asynchronously for {}", uuid, e);
            }
        });
    }

    /**
     * Fetches or initializes a player's daily reward progress. Automatically triggers 
     * streak updates and daily resets as epoch days change.
     */
    public static PlayerRewardState getOrCreateState(UUID uuid, String username) {
        return STATES.computeIfAbsent(uuid, k -> {
            PlayerRewardState state = loadPlayer(uuid);
            if (state == null) {
                state = new PlayerRewardState(username);
            }
            if (username != null && !username.isEmpty()) {
                state.username = username;
            }
            return state;
        });
    }

    private static PlayerRewardState loadPlayer(UUID uuid) {
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

    public static void evict(UUID uuid) {
        STATES.remove(uuid);
    }

    public static void shutdown() {
        // Blocking write to guarantee saving during server stop
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }
            STATES.forEach((uuid, state) -> {
                PlayerRewardState snapshot;
                synchronized (state) {
                    snapshot = state.copy();
                }
                try {
                    Path playerFile = DATA_DIR.resolve(uuid.toString() + ".json");
                    Path tmpFile = DATA_DIR.resolve(uuid.toString() + ".json.tmp");
                    try (FileWriter writer = new FileWriter(tmpFile.toFile())) {
                        GSON.toJson(snapshot, writer);
                    }
                    Files.move(tmpFile, playerFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    DailyRewards.LOGGER.error("Failed to save player data for {} during shutdown", uuid, e);
                }
            });
        } catch (IOException e) {
            DailyRewards.LOGGER.error("Failed to execute final database save during shutdown", e);
        }
        
        EXECUTOR.shutdown();
    }
}
