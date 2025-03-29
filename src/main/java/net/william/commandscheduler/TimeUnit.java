package net.william.commandscheduler;

public enum TimeUnit {
    TICKS,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS;

    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = TimeUnit.TICKS_PER_SECOND * 60; // 1 200
    public static final int TICKS_PER_HOUR = TimeUnit.TICKS_PER_MINUTE * 60; // 72 000
    public static final int TICKS_PER_DAY = TimeUnit.TICKS_PER_HOUR * 24; // 1 728 000

    public static boolean isValid(String input) {
        for (TimeUnit unit : TimeUnit.values()) {
            if (unit.name().equalsIgnoreCase(input)) {
                return true;
            }
        }
        return false;
    }

    public static TimeUnit fromString(String input) throws IllegalArgumentException {
        for (TimeUnit unit : TimeUnit.values()) {
            if (unit.name().equalsIgnoreCase(input)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Invalid time unit: " + input);
    }

    public static int getTickCountForUnits(TimeUnit unit, int numberOfUnits) {
        return switch (unit) {
            case TICKS -> numberOfUnits;
            case SECONDS -> numberOfUnits * TICKS_PER_SECOND;
            case MINUTES -> numberOfUnits * TICKS_PER_MINUTE;
            case HOURS -> numberOfUnits * TICKS_PER_HOUR;
            case DAYS -> numberOfUnits * TICKS_PER_DAY;
        };
    }
}