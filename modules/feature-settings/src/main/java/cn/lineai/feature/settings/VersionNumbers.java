package cn.lineai.feature.settings;

/**
 * Tolerant comparison of semantic version strings used for GitHub release tags.
 *
 * <p>Tags may carry a leading {@code v}, omit trailing segments, or append a pre-release suffix
 * such as {@code -beta}. Only the first three numeric segments are compared, missing segments
 * count as zero, and any version carrying a pre-release suffix ranks below the same release. Build
 * metadata ({@code +build}) is ignored.</p>
 */
public final class VersionNumbers {

    /** Not instantiable; comparison is static. */
    private VersionNumbers() {
    }

    /**
     * Compares two version strings.
     *
     * @param left first version; null, blank or non-numeric input ranks as {@code 0.0.0}
     * @param right second version compared the same way
     * @return negative when left is older, zero when both rank the same, positive when newer
     */
    public static int compare(String left, String right) {
        int[] first = segments(left);
        int[] second = segments(right);
        for (int index = 0; index < 3; index++) {
            int order = Integer.compare(first[index], second[index]);
            if (order != 0) return order;
        }
        String firstSuffix = suffix(left);
        String secondSuffix = suffix(right);
        if (firstSuffix.isEmpty() && secondSuffix.isEmpty()) return 0;
        if (firstSuffix.isEmpty()) return 1;
        if (secondSuffix.isEmpty()) return -1;
        return Integer.signum(firstSuffix.compareTo(secondSuffix));
    }

    /**
     * Reports whether the candidate version is strictly newer than the current one.
     *
     * @param candidate version offered by the release feed
     * @param current version installed on the device
     * @return true when the candidate ranks above the current version
     */
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    /** Extracts the first three numeric segments; missing or malformed segments count as zero. */
    private static int[] segments(String version) {
        int[] result = new int[3];
        String core = core(version);
        if (core.isEmpty()) return result;
        String[] parts = core.split("\\.");
        for (int index = 0; index < 3 && index < parts.length; index++) {
            result[index] = leadingNumber(parts[index]);
        }
        return result;
    }

    /** Returns the pre-release suffix after the first dash, or the empty string for a release. */
    private static String suffix(String version) {
        String text = normalize(version);
        int dash = text.indexOf('-');
        return dash < 0 ? "" : text.substring(dash + 1).trim();
    }

    /** Strips the leading v and everything from the first pre-release or build marker. */
    private static String core(String version) {
        String text = normalize(version);
        int cut = text.indexOf('-');
        int plus = text.indexOf('+');
        if (plus >= 0 && (cut < 0 || plus < cut)) cut = plus;
        return cut < 0 ? text : text.substring(0, cut);
    }

    /** Trims surrounding whitespace and drops one leading v/V tag marker. */
    private static String normalize(String version) {
        String text = version == null ? "" : version.trim();
        if (!text.isEmpty() && (text.charAt(0) == 'v' || text.charAt(0) == 'V')) {
            return text.substring(1).trim();
        }
        return text;
    }

    /** Parses the leading digits of one segment; an oversized number saturates. */
    private static int leadingNumber(String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(segment.substring(0, end));
        } catch (NumberFormatException error) {
            return Integer.MAX_VALUE;
        }
    }
}
