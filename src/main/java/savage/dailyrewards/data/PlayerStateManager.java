package savage.dailyrewards.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import savage.dailyrewards.DailyRewards;
import savage.dailyrewards.util.TimeUtils;

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
    private static final Path DATA_DIR = FabricLoader.getInstance().getConfigDir().resolve("dailyrewards");
    private static final Path DATA_FILE = DATA_DIR.resolve("players.json");
    
    private static final ConcurrentHashMap<UUID, PlayerRewardState> STATES = new ConcurrentHashMap<>();
    
    // Executor using Java Virtual Threads for background file operations (JDK 25)
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private PlayerStateManager() {
        // Prevent instantiation
    }

    /**
     * Loads the players state registry from disk.
     */
    public static void load() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }
            if (!Files.exists(DATA_FILE)) {
                DailyRewards.LOGGER.info("Player daily rewards database not found. Starting with a fresh database.");
                return;
            }

            try (FileReader reader = new FileReader(DATA_FILE.toFile())) {
                Type type = new TypeToken<Map<String, PlayerRewardState>>() {}.getType();
                Map<String, PlayerRewardState> rawMap = GSON.fromJson(reader, type);
                if (rawMap != null) {
                    STATES.clear();
                    rawMap.forEach((uuidStr, state) -> {
                        try {
                            STATES.put(UUID.fromString(uuidStr), state);
                        } catch (IllegalArgumentException e) {
                            DailyRewards.LOGGER.error("Failed to parse UUID string: {}", uuidStr, e);
                        }
                    });
                }
            }
        } catch (IOException e) {
            DailyRewards.LOGGER.error("Failed to load player rewards data", e);
        }
    }

    /**
     * Saves the current active states asynchronously via a virtual thread.
     */
    public static void save() {
        EXECUTOR.execute(() -> {
            synchronized (PlayerStateManager.class) {
                try {
                    if (!Files.exists(DATA_DIR)) {
                        Files.createDirectories(DATA_DIR);
                    }
                    
                    // Convert UUID keys to String representation for clean JSON serialization
                    Map<String, PlayerRewardState> rawMap = new HashMap<>();
                    STATES.forEach((uuid, state) -> rawMap.put(uuid.toString(), state));

                    try (FileWriter writer = new FileWriter(DATA_FILE.toFile())) {
                        GSON.toJson(rawMap, writer);
                    }
                } catch (IOException e) {
                    DailyRewards.LOGGER.error("Failed to save player rewards data asynchronously", e);
                }
            }
        });
    }

    /**
     * Fetches or initializes a player's daily reward progress. Automatically triggers 
     * streak updates and daily resets as epoch days change.
     *
     * @param uuid the player's UUID
     * @param username the player's username
     * @return the associated daily reward state
     */
    public static PlayerRewardState getOrCreateState(UUID uuid, String username) {
        long currentDay = TimeUtils.getCurrentEpochDay();
        PlayerRewardState state = STATES.computeIfAbsent(uuid, k -> new PlayerRewardState(username));

        if (username != null && !username.isEmpty()) {
            state.username = username;
        }

        // Streak Reset Check:
        // If their last claim day was before yesterday, they missed their streak check-in window.
        if (state.lastClaimEpochDay < currentDay - 1) {
            state.currentStreak = 0;
        }

        // Daily Reset Check:
        // If their last active day is in the past, reset daily playtime and claim availability.
        if (state.lastActiveEpochDay < currentDay) {
            state.playtimeTodaySeconds = 0;
            state.claimedToday = false;
            state.lastActiveEpochDay = currentDay;
            save(); // Trigger background write for state transition
        }

        return state;
    }

    /**
     * Executes final blocking save and shuts down background execution services.
     */
    public static void shutdown() {
        // Blocking write to guarantee saving during server stop
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }
            Map<String, PlayerRewardState> rawMap = new HashMap<>();
            STATES.forEach((uuid, state) -> rawMap.put(uuid.toString(), state));

            try (FileWriter writer = new FileWriter(DATA_FILE.toFile())) {
                GSON.toJson(rawMap, writer);
            }
        } catch (IOException e) {
            DailyRewards.LOGGER.error("Failed to execute final database save during shutdown", e);
        }
        
        EXECUTOR.shutdown();
    }
}
