package savage.dailyrewards.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

    /**
     * Calculates the time remaining until the next daily reset (midnight of the configured timezone).
     *
     * @return A formatted string (e.g., "14h 32m" or "5m")
     */
    public static String getTimeUntilNextReset() {
        ZoneId zone = ZoneId.systemDefault();
        String configZone = ConfigManager.getConfig().timezone;
        
        if (configZone != null && !configZone.trim().isEmpty()) {
            try {
                zone = ZoneId.of(configZone.trim());
            } catch (Exception e) {
                // Ignore, fallback to system default
            }
        }
        
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextReset = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        
        Duration duration = Duration.between(now, nextReset);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return "< 1m";
        }
    }
}
