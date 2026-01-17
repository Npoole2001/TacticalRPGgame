package sh.vibecraft.hexcontrol.security;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Audit logging for security-relevant events per spec.
 * Maintains an append-only log of agent tool executions, file changes, and provider calls.
 *
 * Per spec: "Never log secrets (including in exception traces)"
 */
public class AuditLog {

    private static final String LOG_FILE = "audit.jsonl";
    private static final int MAX_LOG_SIZE_MB = 50;
    private static final int ROTATION_COUNT = 5;

    private final Path logFile;
    private final Path logDir;
    private final ExecutorService writeExecutor;
    private final BlockingQueue<AuditEntry> writeQueue;

    private boolean privacyMode = false;

    // Patterns to redact
    private static final List<String> REDACT_PATTERNS = Arrays.asList(
        "(?i)(api[_-]?key|apikey|secret|password|token|auth|bearer)\\s*[:=]\\s*['\"]?[^'\"\\s]+",
        "(?i)sk-[a-zA-Z0-9]{20,}",  // OpenAI API keys
        "(?i)anthropic-[a-zA-Z0-9-]+",  // Anthropic keys
        "/Users/[^/]+/",  // macOS usernames
        "/home/[^/]+/",   // Linux usernames
        "C:\\\\Users\\\\[^\\\\]+\\\\"  // Windows usernames
    );

    public AuditLog(Path stateDir) {
        this.logDir = stateDir.resolve("logs");
        this.logFile = logDir.resolve(LOG_FILE);
        this.writeQueue = new LinkedBlockingQueue<>();
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HexControl-AuditLog");
            t.setDaemon(true);
            return t;
        });

        // Start async writer
        writeExecutor.submit(this::processWriteQueue);
    }

    /**
     * Initialize the audit log.
     */
    public void initialize() {
        try {
            Files.createDirectories(logDir);
            rotateIfNeeded();
        } catch (IOException e) {
            System.err.println("Warning: Could not initialize audit log: " + e.getMessage());
        }
    }

    /**
     * Log an agent tool execution.
     */
    public void logToolExecution(String agentId, String agentName, String tool, String args, boolean success) {
        AuditEntry entry = new AuditEntry();
        entry.timestamp = Instant.now().toString();
        entry.type = "TOOL_EXEC";
        entry.agentId = sanitize(agentId);
        entry.agentName = sanitize(agentName);
        entry.details = Map.of(
            "tool", sanitize(tool),
            "args", sanitize(args),
            "success", String.valueOf(success)
        );
        writeQueue.offer(entry);
    }

    /**
     * Log a file change event.
     */
    public void logFileChange(String agentId, String agentName, String filePath, String operation) {
        AuditEntry entry = new AuditEntry();
        entry.timestamp = Instant.now().toString();
        entry.type = "FILE_CHANGE";
        entry.agentId = sanitize(agentId);
        entry.agentName = sanitize(agentName);
        entry.details = Map.of(
            "path", sanitize(filePath),
            "operation", operation
        );
        writeQueue.offer(entry);
    }

    /**
     * Log a provider API call.
     */
    public void logProviderCall(String agentId, String agentName, String provider, int tokensUsed, int latencyMs) {
        AuditEntry entry = new AuditEntry();
        entry.timestamp = Instant.now().toString();
        entry.type = "PROVIDER_CALL";
        entry.agentId = sanitize(agentId);
        entry.agentName = sanitize(agentName);
        entry.details = Map.of(
            "provider", provider,
            "tokens", String.valueOf(tokensUsed),
            "latencyMs", String.valueOf(latencyMs)
        );
        writeQueue.offer(entry);
    }

    /**
     * Log a security event.
     */
    public void logSecurityEvent(String event, String details) {
        AuditEntry entry = new AuditEntry();
        entry.timestamp = Instant.now().toString();
        entry.type = "SECURITY";
        entry.details = Map.of(
            "event", event,
            "details", sanitize(details)
        );
        writeQueue.offer(entry);
    }

    /**
     * Log an error event.
     */
    public void logError(String agentId, String agentName, String error) {
        AuditEntry entry = new AuditEntry();
        entry.timestamp = Instant.now().toString();
        entry.type = "ERROR";
        entry.agentId = sanitize(agentId);
        entry.agentName = sanitize(agentName);
        entry.details = Map.of(
            "error", sanitize(error)
        );
        writeQueue.offer(entry);
    }

    /**
     * Sanitize string for logging (redact secrets and PII).
     */
    private String sanitize(String input) {
        if (input == null) return null;

        String result = input;

        // Always redact known secret patterns
        for (String pattern : REDACT_PATTERNS) {
            result = result.replaceAll(pattern, "[REDACTED]");
        }

        // Additional redaction in privacy mode
        if (privacyMode) {
            // Redact paths
            result = result.replaceAll("/[^ \"']+", "[PATH]");
            result = result.replaceAll("\\\\[^ \"']+", "[PATH]");
        }

        return result;
    }

    /**
     * Process the write queue asynchronously.
     */
    private void processWriteQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AuditEntry entry = writeQueue.take();
                writeEntry(entry);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Drain remaining entries on shutdown
        List<AuditEntry> remaining = new ArrayList<>();
        writeQueue.drainTo(remaining);
        for (AuditEntry entry : remaining) {
            writeEntry(entry);
        }
    }

    /**
     * Write a single entry to the log file.
     */
    private synchronized void writeEntry(AuditEntry entry) {
        try {
            String json = entryToJson(entry);
            Files.writeString(logFile, json + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Warning: Could not write audit log: " + e.getMessage());
        }
    }

    /**
     * Convert entry to JSON string.
     */
    private String entryToJson(AuditEntry entry) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"timestamp\":\"").append(entry.timestamp).append("\"");
        sb.append(",\"type\":\"").append(entry.type).append("\"");

        if (entry.agentId != null) {
            sb.append(",\"agentId\":\"").append(escapeJson(entry.agentId)).append("\"");
        }
        if (entry.agentName != null) {
            sb.append(",\"agentName\":\"").append(escapeJson(entry.agentName)).append("\"");
        }
        if (entry.details != null && !entry.details.isEmpty()) {
            sb.append(",\"details\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : entry.details.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(e.getKey())).append("\":\"")
                  .append(escapeJson(e.getValue())).append("\"");
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Rotate log if it exceeds max size.
     */
    private void rotateIfNeeded() throws IOException {
        if (!Files.exists(logFile)) return;

        long sizeBytes = Files.size(logFile);
        if (sizeBytes > MAX_LOG_SIZE_MB * 1024 * 1024) {
            // Rotate existing logs
            for (int i = ROTATION_COUNT - 1; i >= 1; i--) {
                Path older = logDir.resolve(LOG_FILE + "." + i);
                Path newer = logDir.resolve(LOG_FILE + "." + (i - 1));
                if (Files.exists(newer)) {
                    Files.move(newer, older, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.move(logFile, logDir.resolve(LOG_FILE + ".0"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Export sanitized audit log for sharing.
     */
    public String exportSanitized() throws IOException {
        if (!Files.exists(logFile)) {
            return "[]";
        }

        List<String> lines = Files.readAllLines(logFile);
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append("  ").append(lines.get(i));
        }
        sb.append("\n]");
        return sb.toString();
    }

    public void setPrivacyMode(boolean privacyMode) {
        this.privacyMode = privacyMode;
    }

    public boolean isPrivacyMode() {
        return privacyMode;
    }

    /**
     * Shutdown the audit log.
     */
    public void shutdown() {
        writeExecutor.shutdownNow();
        try {
            writeExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Audit log entry structure.
     */
    private static class AuditEntry {
        String timestamp;
        String type;
        String agentId;
        String agentName;
        Map<String, String> details;
    }
}
