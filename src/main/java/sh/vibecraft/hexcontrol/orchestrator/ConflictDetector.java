package sh.vibecraft.hexcontrol.orchestrator;

import java.util.*;
import java.util.concurrent.*;

/**
 * Detects and prevents file/module conflicts between agents per spec.
 * Tracks which files are "touched" by active agents and blocks concurrent modifications.
 */
public class ConflictDetector {

    // File locks: file path -> agent ID holding the lock
    private final ConcurrentHashMap<String, FileLock> fileLocks;

    // Module locks: module path -> agent ID
    private final ConcurrentHashMap<String, ModuleLock> moduleLocks;

    // Pending requests for locked resources
    private final ConcurrentHashMap<String, List<LockRequest>> pendingRequests;

    // Listeners for conflict events
    private final List<ConflictListener> listeners;

    public ConflictDetector() {
        this.fileLocks = new ConcurrentHashMap<>();
        this.moduleLocks = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Attempt to acquire a lock on a file for an agent.
     * @return LockResult indicating success or conflict details
     */
    public LockResult acquireFileLock(String agentId, String agentName, String filePath) {
        FileLock existing = fileLocks.get(filePath);

        if (existing != null) {
            if (existing.agentId.equals(agentId)) {
                // Already own the lock
                existing.touchCount++;
                existing.lastAccess = System.currentTimeMillis();
                return LockResult.success();
            } else {
                // Conflict!
                notifyConflict(agentId, filePath, existing);
                return LockResult.conflict(existing.agentId, existing.agentName, filePath);
            }
        }

        // Check module locks
        ModuleLock moduleLock = findModuleLock(filePath);
        if (moduleLock != null && !moduleLock.agentId.equals(agentId)) {
            notifyConflict(agentId, filePath, moduleLock);
            return LockResult.moduleConflict(moduleLock.agentId, moduleLock.agentName, moduleLock.modulePath);
        }

        // Acquire lock
        fileLocks.put(filePath, new FileLock(agentId, agentName, filePath));
        return LockResult.success();
    }

    /**
     * Release a file lock.
     */
    public void releaseFileLock(String agentId, String filePath) {
        FileLock lock = fileLocks.get(filePath);
        if (lock != null && lock.agentId.equals(agentId)) {
            fileLocks.remove(filePath);
            processPendingRequests(filePath);
        }
    }

    /**
     * Release all locks held by an agent.
     */
    public void releaseAllLocks(String agentId) {
        // Release file locks
        fileLocks.entrySet().removeIf(entry -> {
            if (entry.getValue().agentId.equals(agentId)) {
                processPendingRequests(entry.getKey());
                return true;
            }
            return false;
        });

        // Release module locks
        moduleLocks.entrySet().removeIf(entry -> entry.getValue().agentId.equals(agentId));
    }

    /**
     * Acquire a module-level lock (locks entire directory tree).
     */
    public LockResult acquireModuleLock(String agentId, String agentName, String modulePath) {
        // Check if any files in this module are locked by others
        for (FileLock lock : fileLocks.values()) {
            if (lock.filePath.startsWith(modulePath) && !lock.agentId.equals(agentId)) {
                return LockResult.conflict(lock.agentId, lock.agentName, lock.filePath);
            }
        }

        // Check if parent or child module is locked
        for (ModuleLock lock : moduleLocks.values()) {
            if (!lock.agentId.equals(agentId)) {
                if (lock.modulePath.startsWith(modulePath) || modulePath.startsWith(lock.modulePath)) {
                    return LockResult.moduleConflict(lock.agentId, lock.agentName, lock.modulePath);
                }
            }
        }

        moduleLocks.put(modulePath, new ModuleLock(agentId, agentName, modulePath));
        return LockResult.success();
    }

    /**
     * Release a module lock.
     */
    public void releaseModuleLock(String agentId, String modulePath) {
        ModuleLock lock = moduleLocks.get(modulePath);
        if (lock != null && lock.agentId.equals(agentId)) {
            moduleLocks.remove(modulePath);
        }
    }

    /**
     * Declare intent to modify files (before actually modifying).
     * Returns conflicts that would occur.
     */
    public List<LockResult> declareIntent(String agentId, String agentName, List<String> filePaths) {
        List<LockResult> conflicts = new ArrayList<>();

        for (String filePath : filePaths) {
            LockResult result = acquireFileLock(agentId, agentName, filePath);
            if (!result.success) {
                conflicts.add(result);
            }
        }

        return conflicts;
    }

    /**
     * Get all files currently locked by an agent.
     */
    public Set<String> getLockedFiles(String agentId) {
        Set<String> files = new HashSet<>();
        for (FileLock lock : fileLocks.values()) {
            if (lock.agentId.equals(agentId)) {
                files.add(lock.filePath);
            }
        }
        return files;
    }

    /**
     * Get all locks currently held.
     */
    public Map<String, String> getAllLocks() {
        Map<String, String> locks = new HashMap<>();
        for (FileLock lock : fileLocks.values()) {
            locks.put(lock.filePath, lock.agentName);
        }
        for (ModuleLock lock : moduleLocks.values()) {
            locks.put(lock.modulePath + "/*", lock.agentName);
        }
        return locks;
    }

    /**
     * Force override a lock (requires explicit user approval).
     */
    public void forceOverride(String agentId, String agentName, String filePath) {
        FileLock existing = fileLocks.get(filePath);
        if (existing != null) {
            notifyOverride(agentId, filePath, existing);
        }
        fileLocks.put(filePath, new FileLock(agentId, agentName, filePath));
    }

    /**
     * Add a pending request for a locked resource.
     */
    public void queueRequest(String agentId, String agentName, String filePath, Runnable onAvailable) {
        pendingRequests.computeIfAbsent(filePath, k -> new CopyOnWriteArrayList<>())
            .add(new LockRequest(agentId, agentName, onAvailable));
    }

    private ModuleLock findModuleLock(String filePath) {
        for (ModuleLock lock : moduleLocks.values()) {
            if (filePath.startsWith(lock.modulePath)) {
                return lock;
            }
        }
        return null;
    }

    private void processPendingRequests(String filePath) {
        List<LockRequest> requests = pendingRequests.remove(filePath);
        if (requests != null && !requests.isEmpty()) {
            LockRequest first = requests.get(0);
            // Auto-grant to first waiter
            fileLocks.put(filePath, new FileLock(first.agentId, first.agentName, filePath));
            first.onAvailable.run();

            // Re-queue remaining
            if (requests.size() > 1) {
                pendingRequests.put(filePath, new ArrayList<>(requests.subList(1, requests.size())));
            }
        }
    }

    private void notifyConflict(String requestingAgentId, String filePath, Object holder) {
        for (ConflictListener listener : listeners) {
            listener.onConflict(requestingAgentId, filePath,
                holder instanceof FileLock ? ((FileLock) holder).agentId : ((ModuleLock) holder).agentId);
        }
    }

    private void notifyOverride(String agentId, String filePath, FileLock existing) {
        for (ConflictListener listener : listeners) {
            listener.onOverride(agentId, filePath, existing.agentId);
        }
    }

    public void addListener(ConflictListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ConflictListener listener) {
        listeners.remove(listener);
    }

    /**
     * Result of a lock acquisition attempt.
     */
    public static class LockResult {
        public final boolean success;
        public final String conflictAgentId;
        public final String conflictAgentName;
        public final String conflictPath;
        public final boolean isModuleConflict;

        private LockResult(boolean success, String agentId, String agentName, String path, boolean isModule) {
            this.success = success;
            this.conflictAgentId = agentId;
            this.conflictAgentName = agentName;
            this.conflictPath = path;
            this.isModuleConflict = isModule;
        }

        public static LockResult success() {
            return new LockResult(true, null, null, null, false);
        }

        public static LockResult conflict(String agentId, String agentName, String filePath) {
            return new LockResult(false, agentId, agentName, filePath, false);
        }

        public static LockResult moduleConflict(String agentId, String agentName, String modulePath) {
            return new LockResult(false, agentId, agentName, modulePath, true);
        }
    }

    /**
     * File lock record.
     */
    private static class FileLock {
        final String agentId;
        final String agentName;
        final String filePath;
        final long acquiredAt;
        long lastAccess;
        int touchCount;

        FileLock(String agentId, String agentName, String filePath) {
            this.agentId = agentId;
            this.agentName = agentName;
            this.filePath = filePath;
            this.acquiredAt = System.currentTimeMillis();
            this.lastAccess = this.acquiredAt;
            this.touchCount = 1;
        }
    }

    /**
     * Module lock record.
     */
    private static class ModuleLock {
        final String agentId;
        final String agentName;
        final String modulePath;
        final long acquiredAt;

        ModuleLock(String agentId, String agentName, String modulePath) {
            this.agentId = agentId;
            this.agentName = agentName;
            this.modulePath = modulePath;
            this.acquiredAt = System.currentTimeMillis();
        }
    }

    /**
     * Pending lock request.
     */
    private record LockRequest(String agentId, String agentName, Runnable onAvailable) {}

    /**
     * Listener for conflict events.
     */
    public interface ConflictListener {
        void onConflict(String requestingAgentId, String path, String holdingAgentId);
        void onOverride(String overridingAgentId, String path, String previousAgentId);
    }
}
