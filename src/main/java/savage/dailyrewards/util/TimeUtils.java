package savage.dailyrewards.util;

import java.time.LocalDate;
import java.time.ZoneId;

import savage.dailyrewards.config.ConfigManager;
import savage.dailyrewards.DailyRewards;

/**
 * Utility class for calendar and epoch-day calculations.
 */
public final class TimeUtils {

    private TimeUtils() {
        // Prevent instantiation of utility class
    }

    /**
     * Gets the current epoch day based on the configured timezone.
     *
     * @return the current day as epoch days
     */
    public static long getCurrentEpochDay() {
        ZoneId zone = ZoneId.systemDefault();
        String configZone = ConfigManager.getConfig().timezone;
        
        if (configZone != null && !configZone.trim().isEmpty()) {
            try {
                zone = ZoneId.of(configZone.trim());
            } catch (Exception e) {
                DailyRewards.LOGGER.warn("Invalid timezone '{}' in config.toml. Falling back to system default.", configZone);
            }
        }
        
        return LocalDate.now(zone).toEpochDay();
    }
}
