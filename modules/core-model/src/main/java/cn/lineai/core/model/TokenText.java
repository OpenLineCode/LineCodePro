package cn.lineai.core.model;

/** Compact formatting for token amounts shown in the chat transcript and the statistics pages. */
public final class TokenText {

    /** Not instantiable; formatting is static. */
    private TokenText() {
    }

    /**
     * Formats a token amount with the conventional thousand and million suffixes.
     *
     * <p>Values below one thousand stay exact; larger values use one trimmed decimal and the
     * lowercase {@code k} or {@code m} suffix, so {@code 12345} reads {@code 12.3k} and
     * {@code 1000000} reads {@code 1m}.</p>
     *
     * @param tokens token amount; negative values are clamped to zero
     * @return compact amount such as {@code 980}, {@code 12.3k} or {@code 1.2m}
     */
    public static String format(long tokens) {
        long value = Math.max(0L, tokens);
        if (value < 1000L) return String.valueOf(value);
        if (value < 1000000L && Math.round(value / 100d) < 10000L) {
            return scaled(value, 1000d, "k");
        }
        return scaled(value, 1000000d, "m");
    }

    /** Formats {@code value / divisor} with at most one trimmed decimal and the suffix. */
    private static String scaled(long value, double divisor, String suffix) {
        double scaled = value / divisor;
        long tenths = Math.round(scaled * 10d);
        if (tenths % 10L == 0L) return (tenths / 10L) + suffix;
        return (tenths / 10L) + "." + (tenths % 10L) + suffix;
    }
}
