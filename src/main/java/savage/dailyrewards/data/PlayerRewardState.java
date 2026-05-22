package savage.dailyrewards.data;

/**
 * Represents the persistent daily reward progress and state for an individual player.
 */
public class PlayerRewardState {
    
    public String username;
    public long lastClaimEpochDay;
    public long lastActiveEpochDay;
    public int currentStreak;
    public boolean claimedToday;

    // Required default constructor for GSON serialization
    public PlayerRewardState() {
        this.username = "";
        this.lastClaimEpochDay = 0;
        this.lastActiveEpochDay = 0;
        this.currentStreak = 0;
        this.claimedToday = false;
    }

    public PlayerRewardState(String username) {
        this.username = username;
        this.lastClaimEpochDay = 0;
        this.lastActiveEpochDay = 0;
        this.currentStreak = 0;
        this.claimedToday = false;
    }
}
