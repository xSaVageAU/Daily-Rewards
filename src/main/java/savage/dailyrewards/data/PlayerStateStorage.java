package savage.dailyrewards.data;

import java.util.UUID;

/**
 * Interface defining the operations required for player reward state storage backends.
 */
public interface PlayerStateStorage {

    /**
     * Initializes the storage provider (e.g. creating files/directories, connecting to database).
     */
    void init();

    /**
     * Loads the player reward state for the given UUID from the storage backend.
     *
     * @param uuid the player's unique ID
     * @return the loaded PlayerRewardState, or null if it does not exist
     */
    PlayerRewardState loadState(UUID uuid);

    /**
     * Saves the player reward state to the storage backend.
     *
     * @param uuid  the player's unique ID
     * @param state the player's reward state to save
     */
    void saveState(UUID uuid, PlayerRewardState state);

    /**
     * Shuts down the storage provider, flushing any remaining caches and closing connections.
     */
    void shutdown();
}
