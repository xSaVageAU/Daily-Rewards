package savage.dailyrewards.data;

import savage.dailyrewards.DailyRewards;
import savage.dailyrewards.config.ConfigManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages player states in a thread-safe cache and coordinates saving/loading
 * operations via an active PlayerStateStorage provider backend.
 */
public final class PlayerStateManager {

    private static final ConcurrentHashMap<String, PlayerStateStorage> PROVIDERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PlayerRewardState> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap.KeySetView<UUID, Boolean> LOADING = ConcurrentHashMap.newKeySet();
    
    // Executor using Java Virtual Threads for background storage operations (JDK 25)
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    
    private static PlayerStateStorage activeStorage;

    static {
        // Register the default local JSON file-based storage
        registerProvider("JSON", new JsonPlayerStateStorage());
    }

    private PlayerStateManager() {
        // Prevent instantiation
    }

    /**
     * Registers a new storage provider under the given name.
     *
     * @param name     the provider identifier name (case-insensitive)
     * @param provider the PlayerStateStorage instance
     */
    public static void registerProvider(String name, PlayerStateStorage provider) {
        PROVIDERS.put(name.toUpperCase(), provider);
    }

    /**
     * Initializes the manager, resolving and loading the configured storage provider.
     */
    public static void load() {
        String storageType = ConfigManager.getConfig().storageType.toUpperCase();
        activeStorage = PROVIDERS.get(storageType);
        
        if (activeStorage == null) {
            DailyRewards.LOGGER.warn("Unknown storage type '{}'. Defaulting to JSON.", storageType);
            activeStorage = PROVIDERS.get("JSON");
        }
        
        if (activeStorage != null) {
            activeStorage.init();
        }
    }

    /**
     * Asynchronously pre-loads player state into the in-memory cache upon player join.
     * This eliminates storage IO overhead when they run reward commands.
     *
     * @param uuid     the player's unique ID
     * @param username the player's username
     */
    public static void preLoad(UUID uuid, String username) {
        if (STATES.containsKey(uuid)) return;

        LOADING.add(uuid);
        EXECUTOR.execute(() -> {
            try {
                PlayerRewardState state = null;
                if (activeStorage != null) {
                    state = activeStorage.loadState(uuid);
                }
                if (state == null) {
                    state = new PlayerRewardState(username);
                }
                if (username != null && !username.isEmpty()) {
                    state.username = username;
                }
                STATES.put(uuid, state);
            } catch (Exception e) {
                DailyRewards.LOGGER.error("Failed to preload player data for {}", uuid, e);
            } finally {
                LOADING.remove(uuid);
            }
        });
    }

    /**
     * Checks if a player's state is currently being loaded asynchronously in the background.
     *
     * @param uuid the player's unique ID
     * @return true if the player state is loading, false otherwise
     */
    public static boolean isLoading(UUID uuid) {
        return LOADING.contains(uuid);
    }

    /**
     * Asynchronously saves a player's current state to the active storage provider.
     *
     * @param uuid the player's unique ID
     */
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
                if (activeStorage != null) {
                    activeStorage.saveState(uuid, snapshot);
                }
            } catch (Exception e) {
                DailyRewards.LOGGER.error("Failed to save player data asynchronously for {}", uuid, e);
            }
        });
    }

    /**
     * Fetches or initializes a player's daily reward progress. Automatically triggers 
     * streak updates and daily resets as epoch days change.
     *
     * @param uuid     the player's unique ID
     * @param username the player's username
     * @return the cached or newly loaded reward state
     */
    public static PlayerRewardState getOrCreateState(UUID uuid, String username) {
        return STATES.computeIfAbsent(uuid, k -> {
            PlayerRewardState state = null;
            if (activeStorage != null) {
                state = activeStorage.loadState(uuid);
            }
            if (state == null) {
                state = new PlayerRewardState(username);
            }
            if (username != null && !username.isEmpty()) {
                state.username = username;
            }
            return state;
        });
    }

    /**
     * Evicts a player's state from the in-memory cache.
     *
     * @param uuid the player's unique ID
     */
    public static void evict(UUID uuid) {
        STATES.remove(uuid);
        LOADING.remove(uuid);
    }

    /**
     * Executes final blocking saves for all cached states and shuts down the storage provider.
     */
    public static void shutdown() {
        try {
            STATES.forEach((uuid, state) -> {
                PlayerRewardState snapshot;
                synchronized (state) {
                    snapshot = state.copy();
                }
                try {
                    if (activeStorage != null) {
                        activeStorage.saveState(uuid, snapshot);
                    }
                } catch (Exception e) {
                    DailyRewards.LOGGER.error("Failed to save player data for {} during shutdown", uuid, e);
                }
            });
        } catch (Exception e) {
            DailyRewards.LOGGER.error("Failed to execute final database save during shutdown", e);
        }
        
        if (activeStorage != null) {
            activeStorage.shutdown();
        }
        
        EXECUTOR.shutdown();
    }
}
