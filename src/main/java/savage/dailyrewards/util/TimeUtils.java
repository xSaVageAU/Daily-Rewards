package savage.dailyrewards.util;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Utility class for calendar and epoch-day calculations.
 */
public final class TimeUtils {

    private TimeUtils() {
        // Prevent instantiation of utility class
    }

    /**
     * Gets the current epoch day based on the server's system default timezone.
     *
     * @return the current day as epoch days
     */
    public static long getCurrentEpochDay() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }
}
