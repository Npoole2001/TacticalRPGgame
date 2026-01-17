package sh.vibecraft.hexcontrol.persistence;

import sh.vibecraft.hexcontrol.agent.*;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable application state for persistence per spec addendum.
 * Includes versioning, migration, and full session restoration.
 */
public class AppState {

    // Current schema version - increment when making breaking changes
    public static final int CURRENT_SCHEMA_VERSION = 2;

    // Schema version for forward compatibility
    public int schemaVersion = CURRENT_SCHEMA_VERSION;

    // Session metadata
    public long savedAt;
    public String sessionId;
    public boolean wasRecovered = false;

    // Grid configuration
    public int gridRings = 2;

    // Camera state
    public float cameraYaw = 0.0f;
    public float cameraPitch = 60.0f;
    public float cameraDistance = 15.0f;
    public float cameraTargetX = 0.0f;
    public float cameraTargetZ = 0.0f;

    // Agent data
    public List<AgentState> agents = new ArrayList<>();

    // UI layout
    public boolean statsHudCollapsed = false;
    public boolean minimapVisible = true;
    public boolean contextSheetVisible = false;

    // Global settings
    public boolean globalDryRunMode = true;
    public boolean privacyMode = false;

    // Change budget defaults
    public int defaultMaxFiles = 10;
    public int defaultMaxLines = 400;
    public int defaultMaxRuntimeMinutes = 10;

    // Provider settings (no secrets)
    public String activeProviderId = null;
    public List<String> configuredProviders = new ArrayList<>();

    // Pinned objectives
    public List<ObjectiveState> pinnedObjectives = new ArrayList<>();

    // Architecture map ownership
    public Map<String, String> moduleOwnership = new HashMap<>();

    // Global context sheet
    public ContextSheetState contextSheet = new ContextSheetState();

    /**
     * Capture current state from live objects.
     */
    public static AppState capture(Camera camera, HexGrid hexGrid) {
        AppState state = new AppState();
        state.savedAt = System.currentTimeMillis();
        state.sessionId = java.util.UUID.randomUUID().toString();

        // Camera
        state.cameraYaw = camera.getYaw();
        state.cameraPitch = camera.getPitch();
        state.cameraDistance = camera.getDistance();
        state.cameraTargetX = camera.getTarget().x;
        state.cameraTargetZ = camera.getTarget().z;

        // Agents
        for (var cell : hexGrid.getCells()) {
            AIAgent agent = cell.getAgent();
            if (agent != null) {
                AgentState as = new AgentState();
                as.id = agent.getId();
                as.name = agent.getName();
                as.type = agent.getType().name();
                as.role = agent.getRole().name();
                as.status = agent.getStatus().name();
                as.colorR = agent.getColor().x;
                as.colorG = agent.getColor().y;
                as.colorB = agent.getColor().z;
                as.tileIndex = hexGrid.getCells().indexOf(cell);
                as.dryRunMode = agent.isDryRunMode();
                as.repoPath = agent.getRepoPath();
                as.branchName = agent.getBranchName();
                as.worktreePath = agent.getWorktreePath();
                as.currentTask = agent.getCurrentTask();
                as.currentObjective = agent.getCurrentObjective();
                as.totalTokensUsed = agent.getTotalTokensUsed();
                as.totalCostCents = agent.getTotalCostCents();
                as.tokenBudget = agent.getTokenBudget();
                as.tags = new ArrayList<>(agent.getTags());
                state.agents.add(as);
            }
        }

        return state;
    }

    /**
     * Apply state to live objects.
     */
    public void apply(Camera camera, HexGrid hexGrid) {
        // Camera
        camera.setYaw(cameraYaw);
        camera.setPitch(cameraPitch);
        camera.setDistance(cameraDistance);
        camera.setTarget(cameraTargetX, cameraTargetZ);

        // Agents
        for (AgentState as : agents) {
            if (as.tileIndex >= 0 && as.tileIndex < hexGrid.getCells().size()) {
                var cell = hexGrid.getCell(as.tileIndex);
                if (cell != null) {
                    AgentType type;
                    try {
                        type = AgentType.valueOf(as.type);
                    } catch (IllegalArgumentException e) {
                        type = AgentType.EMPTY;
                    }

                    AIAgent agent = new AIAgent(as.name, type);

                    try {
                        agent.setRole(AgentRole.valueOf(as.role));
                    } catch (IllegalArgumentException e) {
                        agent.setRole(AgentRole.GENERAL);
                    }

                    try {
                        agent.setStatus(AgentStatus.valueOf(as.status));
                    } catch (IllegalArgumentException e) {
                        agent.setStatus(AgentStatus.IDLE);
                    }

                    agent.setColor(new org.joml.Vector3f(as.colorR, as.colorG, as.colorB));
                    agent.setDryRunMode(as.dryRunMode);
                    agent.setRepoPath(as.repoPath);
                    agent.setBranchName(as.branchName);
                    agent.setWorktreePath(as.worktreePath);
                    if (as.currentTask != null) {
                        agent.setCurrentTask(as.currentTask);
                    }
                    agent.setCurrentObjective(as.currentObjective);
                    agent.setTokenBudget(as.tokenBudget);
                    for (String tag : as.tags) {
                        agent.addTag(tag);
                    }

                    cell.setAgent(agent);
                }
            }
        }
    }

    /**
     * Migrate state from older versions.
     * @return true if migration succeeded
     */
    public boolean migrate() {
        if (schemaVersion == CURRENT_SCHEMA_VERSION) {
            return true;  // No migration needed
        }

        try {
            // Migration from v1 to v2
            if (schemaVersion == 1) {
                // Add new fields with defaults
                if (pinnedObjectives == null) pinnedObjectives = new ArrayList<>();
                if (moduleOwnership == null) moduleOwnership = new HashMap<>();
                if (contextSheet == null) contextSheet = new ContextSheetState();
                if (configuredProviders == null) configuredProviders = new ArrayList<>();

                // Migrate agent states
                for (AgentState as : agents) {
                    if (as.tokenBudget == 0) as.tokenBudget = -1;  // -1 = unlimited
                }

                schemaVersion = 2;
            }

            // Future migrations go here...

            return schemaVersion == CURRENT_SCHEMA_VERSION;
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validate state integrity.
     */
    public List<String> validate() {
        List<String> issues = new ArrayList<>();

        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            issues.add("State is from a newer version of the app");
        }

        for (AgentState as : agents) {
            if (as.id == null || as.id.isEmpty()) {
                issues.add("Agent missing ID");
            }
            if (as.tileIndex < 0) {
                issues.add("Agent " + as.name + " has invalid tile index");
            }
        }

        return issues;
    }

    /**
     * Per-agent state for serialization.
     */
    public static class AgentState {
        public String id;
        public String name;
        public String type;
        public String role;
        public String status;
        public float colorR, colorG, colorB;
        public int tileIndex;
        public boolean dryRunMode = true;
        public String repoPath;
        public String branchName;
        public String worktreePath;
        public String currentTask;
        public String currentObjective;
        public int totalTokensUsed;
        public int totalCostCents;
        public int tokenBudget = -1;
        public List<String> tags = new ArrayList<>();
        public List<String> recentEventSummaries = new ArrayList<>();
    }

    /**
     * Pinned objective state.
     */
    public static class ObjectiveState {
        public String id;
        public String title;
        public String description;
        public String priority;  // HIGH, MEDIUM, LOW
        public String status;    // ACTIVE, COMPLETED, BLOCKED
        public List<String> ownerAgentIds = new ArrayList<>();
        public List<String> linkedIssues = new ArrayList<>();
        public List<String> linkedPRs = new ArrayList<>();
        public String notes;
    }

    /**
     * Global context sheet state.
     */
    public static class ContextSheetState {
        public String projectGoal;
        public String repoLocation;
        public List<String> currentEpics = new ArrayList<>();
        public List<String> constraints = new ArrayList<>();
        public String definitionOfDone;
        public List<String> currentBlockers = new ArrayList<>();
        public long lastUpdated;
    }
}
