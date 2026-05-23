package savage.dailyrewards.data;

/**
 * Represents the persistent daily reward progress and state for an individual player.
 */
public class PlayerRewardState {
    
    public String username;
    public long lastClaimEpochDay;
    public int currentStreak;

    // Required default constructor for GSON serialization
    public PlayerRewardState() {
        this.username = "";
        this.lastClaimEpochDay = 0;
        this.currentStreak = 0;
    }

    public PlayerRewardState(String username) {
        this.username = username;
        this.lastClaimEpochDay = 0;
        this.currentStreak = 0;
    }

    public void validateStreak(long currentDay) {
        if (this.lastClaimEpochDay < currentDay - 1) {
            this.currentStreak = 0;
        }
    }

    public PlayerRewardState copy() {
        PlayerRewardState copy = new PlayerRewardState(this.username);
        copy.lastClaimEpochDay = this.lastClaimEpochDay;
        copy.currentStreak = this.currentStreak;
        return copy;
    }
}
