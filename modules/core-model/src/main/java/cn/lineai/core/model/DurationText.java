package cn.lineai.core.model;

/** Compact formatting for elapsed durations shown next to assistant turns. */
public final class DurationText {

    /** Not instantiable; formatting is static. */
    private DurationText() {
    }

    /**
     * Formats an elapsed duration in milliseconds.
     *
     * <p>Sub-minute durations keep one trimmed decimal of a second ({@code 8.4s}); longer
     * durations combine minutes and seconds, and hour-scale durations combine hours and minutes
     * so the label stays short enough for a single transcript line.</p>
     *
     * @param elapsedMillis elapsed duration; negative values are clamped to zero
     * @return compact duration such as {@code 0.8s}, {@code 8.4s}, {@code 1m 5s} or {@code 1h 2m}
     */
    public static String format(long elapsedMillis) {
        long millis = Math.max(0L, elapsedMillis);
        if (millis < 60000L) return seconds(millis);
        long totalSeconds = millis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes < 60L) return minutes + "m " + seconds + "s";
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        return rest == 0L ? hours + "h" : hours + "h " + rest + "m";
    }

    /** Formats a sub-minute duration with one trimmed decimal of a second. */
    private static String seconds(long millis) {
        long tenths = Math.round(millis / 100d);
        if (tenths % 10L == 0L) return (tenths / 10L) + "s";
        return (tenths / 10L) + "." + (tenths % 10L) + "s";
    }
}
