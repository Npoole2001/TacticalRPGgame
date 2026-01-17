package sh.vibecraft.hexcontrol.orchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Enforces bounded change sets per agent run per spec.
 * Limits: max files, max lines, max runtime.
 */
public class ChangeBudget {

    // Configurable limits
    private int maxFilesChanged;
    private int maxLinesChanged;
    private Duration maxRuntime;

    // Current usage
    private int filesChanged;
    private int linesAdded;
    private int linesRemoved;
    private Instant startTime;
    private final Set<String> touchedFiles;

    // State
    private boolean budgetExceeded;
    private String exceedReason;

    // Default limits per spec (configurable)
    public static final int DEFAULT_MAX_FILES = 10;
    public static final int DEFAULT_MAX_LINES = 400;
    public static final Duration DEFAULT_MAX_RUNTIME = Duration.ofMinutes(10);

    public ChangeBudget() {
        this(DEFAULT_MAX_FILES, DEFAULT_MAX_LINES, DEFAULT_MAX_RUNTIME);
    }

    public ChangeBudget(int maxFiles, int maxLines, Duration maxRuntime) {
        this.maxFilesChanged = maxFiles;
        this.maxLinesChanged = maxLines;
        this.maxRuntime = maxRuntime;
        this.touchedFiles = new HashSet<>();
        this.budgetExceeded = false;
        reset();
    }

    /**
     * Start tracking a new task session.
     */
    public void startSession() {
        reset();
        startTime = Instant.now();
    }

    /**
     * Reset all counters.
     */
    public void reset() {
        filesChanged = 0;
        linesAdded = 0;
        linesRemoved = 0;
        startTime = null;
        touchedFiles.clear();
        budgetExceeded = false;
        exceedReason = null;
    }

    /**
     * Record a file being modified.
     * @return true if still within budget, false if budget exceeded
     */
    public boolean recordFileChange(String filePath, int addedLines, int removedLines) {
        if (budgetExceeded) return false;

        boolean isNewFile = touchedFiles.add(filePath);
        if (isNewFile) {
            filesChanged++;
        }

        linesAdded += addedLines;
        linesRemoved += removedLines;

        return checkBudget();
    }

    /**
     * Check if we're still within budget.
     */
    public boolean checkBudget() {
        if (budgetExceeded) return false;

        // Check file limit
        if (filesChanged > maxFilesChanged) {
            budgetExceeded = true;
            exceedReason = String.format("File limit exceeded: %d files (max %d)",
                filesChanged, maxFilesChanged);
            return false;
        }

        // Check lines limit
        int totalLines = linesAdded + linesRemoved;
        if (totalLines > maxLinesChanged) {
            budgetExceeded = true;
            exceedReason = String.format("Line limit exceeded: %d lines (max %d)",
                totalLines, maxLinesChanged);
            return false;
        }

        // Check runtime limit
        if (startTime != null) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            if (elapsed.compareTo(maxRuntime) > 0) {
                budgetExceeded = true;
                exceedReason = String.format("Runtime limit exceeded: %d minutes (max %d)",
                    elapsed.toMinutes(), maxRuntime.toMinutes());
                return false;
            }
        }

        return true;
    }

    /**
     * Get remaining budget as percentages.
     */
    public BudgetStatus getStatus() {
        int totalLines = linesAdded + linesRemoved;
        Duration elapsed = startTime != null ?
            Duration.between(startTime, Instant.now()) : Duration.ZERO;

        return new BudgetStatus(
            (float) filesChanged / maxFilesChanged,
            (float) totalLines / maxLinesChanged,
            (float) elapsed.toMillis() / maxRuntime.toMillis(),
            budgetExceeded,
            exceedReason
        );
    }

    /**
     * Get detailed usage report.
     */
    public String getUsageReport() {
        int totalLines = linesAdded + linesRemoved;
        Duration elapsed = startTime != null ?
            Duration.between(startTime, Instant.now()) : Duration.ZERO;

        StringBuilder sb = new StringBuilder();
        sb.append("=== Change Budget Usage ===\n");
        sb.append(String.format("Files:   %d / %d (%.0f%%)\n",
            filesChanged, maxFilesChanged, 100.0 * filesChanged / maxFilesChanged));
        sb.append(String.format("Lines:   %d / %d (%.0f%%) [+%d / -%d]\n",
            totalLines, maxLinesChanged, 100.0 * totalLines / maxLinesChanged,
            linesAdded, linesRemoved));
        sb.append(String.format("Runtime: %d / %d min (%.0f%%)\n",
            elapsed.toMinutes(), maxRuntime.toMinutes(),
            100.0 * elapsed.toMillis() / maxRuntime.toMillis()));

        if (budgetExceeded) {
            sb.append("\n*** BUDGET EXCEEDED: ").append(exceedReason).append(" ***\n");
        }

        return sb.toString();
    }

    /**
     * Estimate if a proposed change would exceed budget.
     */
    public boolean wouldExceed(int newFiles, int newLines) {
        return (filesChanged + newFiles > maxFilesChanged) ||
               (linesAdded + linesRemoved + newLines > maxLinesChanged);
    }

    // Getters and setters
    public int getMaxFilesChanged() { return maxFilesChanged; }
    public void setMaxFilesChanged(int max) { this.maxFilesChanged = max; }

    public int getMaxLinesChanged() { return maxLinesChanged; }
    public void setMaxLinesChanged(int max) { this.maxLinesChanged = max; }

    public Duration getMaxRuntime() { return maxRuntime; }
    public void setMaxRuntime(Duration max) { this.maxRuntime = max; }

    public int getFilesChanged() { return filesChanged; }
    public int getLinesAdded() { return linesAdded; }
    public int getLinesRemoved() { return linesRemoved; }
    public Set<String> getTouchedFiles() { return Collections.unmodifiableSet(touchedFiles); }

    public boolean isBudgetExceeded() { return budgetExceeded; }
    public String getExceedReason() { return exceedReason; }

    /**
     * Snapshot of budget status.
     */
    public record BudgetStatus(
        float filesUsedRatio,
        float linesUsedRatio,
        float runtimeUsedRatio,
        boolean exceeded,
        String exceedReason
    ) {
        public float getMaxUsedRatio() {
            return Math.max(filesUsedRatio, Math.max(linesUsedRatio, runtimeUsedRatio));
        }
    }
}
