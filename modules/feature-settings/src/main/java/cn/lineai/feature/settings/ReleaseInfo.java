package cn.lineai.feature.settings;

/**
 * Immutable description of one published application release.
 *
 * <p>The version is the tag without its leading {@code v}; the raw tag is kept for diagnostics and
 * the body holds the GitHub release notes shown in the update dialog.</p>
 */
public final class ReleaseInfo {

    /** Release version without the leading tag marker, e.g. {@code 2.2.0}. */
    public final String version;
    /** Raw release tag as published, e.g. {@code v2.2.0}. */
    public final String tag;
    /** Release notes in Markdown form; empty when the release has no body. */
    public final String body;
    /** Web address of the release page; empty when the feed omitted it. */
    public final String htmlUrl;

    /**
     * Creates a release description.
     *
     * @param tag raw release tag; null is normalised to the empty string
     * @param body release notes; null is normalised to the empty string
     * @param htmlUrl web address of the release page; null is normalised to the empty string
     */
    public ReleaseInfo(String tag, String body, String htmlUrl) {
        this.tag = tag == null ? "" : tag;
        this.version = versionOf(this.tag);
        this.body = body == null ? "" : body;
        this.htmlUrl = htmlUrl == null ? "" : htmlUrl;
    }

    /** Strips the leading {@code v} marker from a tag, tolerating surrounding whitespace. */
    private static String versionOf(String tag) {
        String value = tag.trim();
        if (!value.isEmpty() && (value.charAt(0) == 'v' || value.charAt(0) == 'V')) {
            return value.substring(1).trim();
        }
        return value;
    }
}
