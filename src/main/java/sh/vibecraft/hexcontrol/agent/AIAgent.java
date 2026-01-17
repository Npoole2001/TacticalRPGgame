package sh.vibecraft.hexcontrol.agent;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an AI agent that can be controlled through the hex interface.
 * Full model per spec with workspace binding, tasks, and policies.
 */
public class AIAgent {

    // Identity
    private final String id;
    private String name;
    private AgentType type;
    private AgentRole role;
    private Vector3f color;
    private List<String> tags;

    // Status
    private AgentStatus status;
    private String currentTask;
    private String currentObjective;

    // Workspace binding
    private String repoPath;
    private String branchName;
    private String worktreePath;

    // Policy profile
    private boolean dryRunMode = true;  // Default to safe mode per spec
    private List<String> allowedDirectories;
    private List<String> allowedTools;
    private int tokenBudget = -1;  // -1 = unlimited
    private int costBudgetCents = -1;

    // Activity telemetry
    private long lastActivityTime;
    private int totalTokensUsed;
    private int totalCostCents;
    private List<String> recentEvents;

    // Connection (for provider)
    private String providerId;
    private boolean connected;

    public AIAgent(String name, AgentType type) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
        this.role = AgentRole.GENERAL;
        this.color = type.getDefaultColor();
        this.status = AgentStatus.IDLE;
        this.tags = new ArrayList<>();
        this.allowedDirectories = new ArrayList<>();
        this.allowedTools = new ArrayList<>();
        this.recentEvents = new ArrayList<>();
        this.lastActivityTime = System.currentTimeMillis();
    }

    // Identity getters/setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AgentType getType() { return type; }
    public void setType(AgentType type) {
        this.type = type;
        this.color = type.getDefaultColor();
    }

    public AgentRole getRole() { return role; }
    public void setRole(AgentRole role) { this.role = role; }

    public Vector3f getColor() { return new Vector3f(color); }
    public void setColor(Vector3f color) { this.color = new Vector3f(color); }

    public List<String> getTags() { return tags; }
    public void addTag(String tag) { tags.add(tag); }
    public void removeTag(String tag) { tags.remove(tag); }

    // Status getters/setters
    public AgentStatus getStatus() { return status; }
    public void setStatus(AgentStatus status) {
        this.status = status;
        this.lastActivityTime = System.currentTimeMillis();
        addEvent("Status changed to " + status.getDisplayName());
    }

    public String getCurrentTask() { return currentTask; }
    public void setCurrentTask(String currentTask) {
        this.currentTask = currentTask;
        addEvent("Task: " + (currentTask != null ? currentTask : "cleared"));
    }

    public String getCurrentObjective() { return currentObjective; }
    public void setCurrentObjective(String currentObjective) {
        this.currentObjective = currentObjective;
    }

    // Workspace binding
    public String getRepoPath() { return repoPath; }
    public void setRepoPath(String repoPath) { this.repoPath = repoPath; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public String getWorktreePath() { return worktreePath; }
    public void setWorktreePath(String worktreePath) { this.worktreePath = worktreePath; }

    // Policy
    public boolean isDryRunMode() { return dryRunMode; }
    public void setDryRunMode(boolean dryRunMode) { this.dryRunMode = dryRunMode; }

    public List<String> getAllowedDirectories() { return allowedDirectories; }
    public List<String> getAllowedTools() { return allowedTools; }

    public int getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }

    public int getCostBudgetCents() { return costBudgetCents; }
    public void setCostBudgetCents(int costBudgetCents) { this.costBudgetCents = costBudgetCents; }

    // Telemetry
    public long getLastActivityTime() { return lastActivityTime; }
    public int getTotalTokensUsed() { return totalTokensUsed; }
    public void addTokensUsed(int tokens) {
        this.totalTokensUsed += tokens;
        this.lastActivityTime = System.currentTimeMillis();
    }

    public int getTotalCostCents() { return totalCostCents; }
    public void addCost(int cents) { this.totalCostCents += cents; }

    public List<String> getRecentEvents() { return recentEvents; }
    public void addEvent(String event) {
        recentEvents.add(0, "[" + System.currentTimeMillis() + "] " + event);
        if (recentEvents.size() > 100) {
            recentEvents.remove(recentEvents.size() - 1);
        }
    }

    // Connection
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    /**
     * Check if agent is currently active (running or blocked).
     */
    public boolean isActive() {
        return status == AgentStatus.RUNNING || status == AgentStatus.BLOCKED;
    }

    /**
     * Check if agent needs user input.
     */
    public boolean needsInput() {
        return status == AgentStatus.BLOCKED;
    }

    /**
     * Check if within token budget.
     */
    public boolean isWithinBudget() {
        if (tokenBudget < 0) return true;
        return totalTokensUsed < tokenBudget;
    }

    /**
     * Get a display label for the tile.
     */
    public String getDisplayLabel() {
        if (type == AgentType.EMPTY) {
            return "Empty";
        }
        return name;
    }

    /**
     * Get status indicator color for UI effects.
     */
    public Vector3f getStatusColor() {
        return status.getColor();
    }

    @Override
    public String toString() {
        return String.format("%s (%s/%s) - %s",
            name, type.getDisplayName(), role.getDisplayName(), status.getDisplayName());
    }
}
