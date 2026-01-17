package sh.vibecraft.hexcontrol.git;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages git worktrees for agent isolation per spec.
 * Each agent operates in its own worktree + branch to prevent collisions.
 */
public class WorkspaceManager {

    private final Path repoRoot;
    private final Path worktreesDir;
    private final Map<String, WorktreeInfo> activeWorktrees;

    public WorkspaceManager(Path repoRoot) {
        this.repoRoot = repoRoot;
        this.worktreesDir = repoRoot.resolve(".hexcontrol-worktrees");
        this.activeWorktrees = new ConcurrentHashMap<>();
    }

    /**
     * Initialize workspace manager and ensure worktrees directory exists.
     */
    public void initialize() throws IOException {
        Files.createDirectories(worktreesDir);
        scanExistingWorktrees();
    }

    /**
     * Create or get a worktree for an agent.
     */
    public WorktreeInfo getOrCreateWorktree(String agentId, String agentName) throws IOException, InterruptedException {
        if (activeWorktrees.containsKey(agentId)) {
            return activeWorktrees.get(agentId);
        }

        String safeName = sanitizeName(agentName);
        String branchName = "agent/" + safeName + "-" + agentId.substring(0, 8);
        Path worktreePath = worktreesDir.resolve(safeName + "-" + agentId.substring(0, 8));

        // Create branch if it doesn't exist
        createBranchIfNotExists(branchName);

        // Create worktree
        if (!Files.exists(worktreePath)) {
            ProcessBuilder pb = new ProcessBuilder(
                "git", "worktree", "add", worktreePath.toString(), branchName
            );
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IOException("Failed to create worktree: " + output);
            }
        }

        WorktreeInfo info = new WorktreeInfo(agentId, branchName, worktreePath);
        activeWorktrees.put(agentId, info);

        return info;
    }

    /**
     * Remove a worktree when agent is decommissioned.
     */
    public void removeWorktree(String agentId) throws IOException, InterruptedException {
        WorktreeInfo info = activeWorktrees.remove(agentId);
        if (info == null) return;

        ProcessBuilder pb = new ProcessBuilder(
            "git", "worktree", "remove", "--force", info.path.toString()
        );
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();
    }

    /**
     * Get the current branch of a worktree.
     */
    public String getCurrentBranch(Path worktreePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "branch", "--show-current");
        pb.directory(worktreePath.toFile());
        Process process = pb.start();
        String branch = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor();
        return branch;
    }

    /**
     * Get modified files in a worktree.
     */
    public List<String> getModifiedFiles(Path worktreePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "diff", "--name-only", "HEAD");
        pb.directory(worktreePath.toFile());
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();

        return Arrays.stream(output.split("\n"))
            .filter(s -> !s.isEmpty())
            .toList();
    }

    /**
     * Get staged files in a worktree.
     */
    public List<String> getStagedFiles(Path worktreePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "diff", "--cached", "--name-only");
        pb.directory(worktreePath.toFile());
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();

        return Arrays.stream(output.split("\n"))
            .filter(s -> !s.isEmpty())
            .toList();
    }

    /**
     * Check if worktree has uncommitted changes.
     */
    public boolean hasUncommittedChanges(Path worktreePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "status", "--porcelain");
        pb.directory(worktreePath.toFile());
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        return !output.trim().isEmpty();
    }

    /**
     * Fetch and rebase worktree onto integration branch.
     */
    public boolean rebaseOntoIntegration(Path worktreePath, String integrationBranch)
            throws IOException, InterruptedException {
        // Fetch latest
        ProcessBuilder fetchPb = new ProcessBuilder("git", "fetch", "origin", integrationBranch);
        fetchPb.directory(worktreePath.toFile());
        fetchPb.start().waitFor();

        // Rebase
        ProcessBuilder rebasePb = new ProcessBuilder("git", "rebase", "origin/" + integrationBranch);
        rebasePb.directory(worktreePath.toFile());
        Process process = rebasePb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            // Abort failed rebase
            ProcessBuilder abortPb = new ProcessBuilder("git", "rebase", "--abort");
            abortPb.directory(worktreePath.toFile());
            abortPb.start().waitFor();
            return false;
        }

        return true;
    }

    private void createBranchIfNotExists(String branchName) throws IOException, InterruptedException {
        // Check if branch exists
        ProcessBuilder checkPb = new ProcessBuilder("git", "branch", "--list", branchName);
        checkPb.directory(repoRoot.toFile());
        Process checkProcess = checkPb.start();
        String output = new String(checkProcess.getInputStream().readAllBytes());
        checkProcess.waitFor();

        if (output.trim().isEmpty()) {
            // Create branch from current HEAD
            ProcessBuilder createPb = new ProcessBuilder("git", "branch", branchName);
            createPb.directory(repoRoot.toFile());
            createPb.start().waitFor();
        }
    }

    private void scanExistingWorktrees() throws IOException {
        if (!Files.exists(worktreesDir)) return;

        try (var stream = Files.list(worktreesDir)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                // Try to extract agent ID from directory name
                String dirName = path.getFileName().toString();
                int dashIdx = dirName.lastIndexOf('-');
                if (dashIdx > 0) {
                    String partialId = dirName.substring(dashIdx + 1);
                    // We can't fully recover the agent ID, but we can track the worktree
                    // The agent will re-register when it starts
                }
            });
        }
    }

    private String sanitizeName(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    public Path getRepoRoot() {
        return repoRoot;
    }

    public Map<String, WorktreeInfo> getActiveWorktrees() {
        return Collections.unmodifiableMap(activeWorktrees);
    }

    /**
     * Information about an agent's worktree.
     */
    public static class WorktreeInfo {
        public final String agentId;
        public final String branchName;
        public final Path path;
        public final long createdAt;

        public WorktreeInfo(String agentId, String branchName, Path path) {
            this.agentId = agentId;
            this.branchName = branchName;
            this.path = path;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
