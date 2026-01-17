package sh.vibecraft.hexcontrol.security;

import java.util.regex.Pattern;

/**
 * Privacy mode system for redacting sensitive information.
 * Per spec: hide usernames, absolute paths, repo identifiers, redact exports.
 */
public class PrivacyMode {

    private boolean enabled = false;
    private boolean streamingMode = false;

    // Redaction patterns
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
        "/Users/[^/]+/|/home/[^/]+/|C:\\\\Users\\\\[^\\\\]+\\\\",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile(
        "(/[a-zA-Z][a-zA-Z0-9_-]*/)+|([A-Z]:\\\\[^\\\\]+\\\\)+",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REPO_PATTERN = Pattern.compile(
        "github\\.com/[^/]+/[^/\\s]+|gitlab\\.com/[^/]+/[^/\\s]+|bitbucket\\.org/[^/]+/[^/\\s]+",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "(sk-[a-zA-Z0-9]{20,})|" +
        "(anthropic-[a-zA-Z0-9-]+)|" +
        "(api[_-]?key|apikey|secret|password|token|auth|bearer)\\s*[:=]\\s*['\"]?[^'\"\\s]+",
        Pattern.CASE_INSENSITIVE
    );

    // Placeholder replacements
    private static final String USERNAME_PLACEHOLDER = "[USER]";
    private static final String PATH_PLACEHOLDER = "[PATH]";
    private static final String REPO_PLACEHOLDER = "[REPO]";
    private static final String EMAIL_PLACEHOLDER = "[EMAIL]";
    private static final String SECRET_PLACEHOLDER = "[REDACTED]";

    public PrivacyMode() {
    }

    /**
     * Redact sensitive information from a string.
     */
    public String redact(String input) {
        if (!enabled || input == null) {
            return input;
        }

        String result = input;

        // Always redact secrets (even when privacy mode is off)
        result = API_KEY_PATTERN.matcher(result).replaceAll(SECRET_PLACEHOLDER);

        if (streamingMode) {
            // Full redaction for streaming/sharing
            result = USERNAME_PATTERN.matcher(result).replaceAll(USERNAME_PLACEHOLDER + "/");
            result = ABSOLUTE_PATH_PATTERN.matcher(result).replaceAll(PATH_PLACEHOLDER + "/");
            result = REPO_PATTERN.matcher(result).replaceAll(REPO_PLACEHOLDER);
            result = EMAIL_PATTERN.matcher(result).replaceAll(EMAIL_PLACEHOLDER);
        } else {
            // Light redaction for normal privacy mode
            result = USERNAME_PATTERN.matcher(result).replaceAll(USERNAME_PLACEHOLDER + "/");
        }

        return result;
    }

    /**
     * Redact a path specifically.
     */
    public String redactPath(String path) {
        if (!enabled || path == null) {
            return path;
        }

        String result = path;

        // Replace user directory
        result = USERNAME_PATTERN.matcher(result).replaceAll(USERNAME_PLACEHOLDER + "/");

        if (streamingMode) {
            // Replace entire path up to project root
            int lastSlash = result.lastIndexOf('/');
            int lastBackslash = result.lastIndexOf('\\');
            int lastSep = Math.max(lastSlash, lastBackslash);

            if (lastSep > 0) {
                String filename = result.substring(lastSep + 1);
                result = PATH_PLACEHOLDER + "/" + filename;
            }
        }

        return result;
    }

    /**
     * Redact an agent name (for export/sharing).
     */
    public String redactAgentName(String name) {
        if (!streamingMode || name == null) {
            return name;
        }

        // Replace with generic identifier
        return "Agent-" + Math.abs(name.hashCode() % 1000);
    }

    /**
     * Redact repository information.
     */
    public String redactRepo(String repoUrl) {
        if (!enabled || repoUrl == null) {
            return repoUrl;
        }

        return REPO_PATTERN.matcher(repoUrl).replaceAll(REPO_PLACEHOLDER);
    }

    /**
     * Create a sanitized export of text.
     */
    public String sanitizeForExport(String input) {
        if (input == null) return null;

        String result = input;

        // Always redact secrets
        result = API_KEY_PATTERN.matcher(result).replaceAll(SECRET_PLACEHOLDER);

        // Full path redaction
        result = USERNAME_PATTERN.matcher(result).replaceAll(USERNAME_PLACEHOLDER + "/");
        result = ABSOLUTE_PATH_PATTERN.matcher(result).replaceAll(PATH_PLACEHOLDER + "/");

        // Repo redaction
        result = REPO_PATTERN.matcher(result).replaceAll(REPO_PLACEHOLDER);

        // Email redaction
        result = EMAIL_PATTERN.matcher(result).replaceAll(EMAIL_PLACEHOLDER);

        return result;
    }

    /**
     * Check if a string contains sensitive information.
     */
    public boolean containsSensitiveInfo(String input) {
        if (input == null) return false;

        return API_KEY_PATTERN.matcher(input).find() ||
               EMAIL_PATTERN.matcher(input).find() ||
               (streamingMode && (
                   USERNAME_PATTERN.matcher(input).find() ||
                   REPO_PATTERN.matcher(input).find()
               ));
    }

    /**
     * Get a display-safe version of a string.
     * This is called for all UI text when privacy mode is enabled.
     */
    public String getDisplaySafe(String input) {
        if (!enabled) return input;
        return redact(input);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    public boolean isStreamingMode() {
        return streamingMode;
    }

    public void setStreamingMode(boolean streamingMode) {
        this.streamingMode = streamingMode;
    }

    /**
     * Get a status string for the HUD.
     */
    public String getStatusString() {
        if (!enabled) return null;
        if (streamingMode) return "STREAMING";
        return "PRIVACY";
    }
}
