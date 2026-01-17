package sh.vibecraft.hexcontrol.persistence;

import sh.vibecraft.hexcontrol.agent.*;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable application state for persistence.
 * Per spec: grid layout, camera pose, agents, UI settings.
 */
public class AppState {

    // Schema version for forward compatibility
    public int schemaVersion = 1;

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

    // Global settings
    public boolean globalDryRunMode = true;
    public boolean privacyMode = false;

    // Provider settings (no secrets)
    public String activeProviderId = null;

    /**
     * Capture current state from live objects.
     */
    public static AppState capture(Camera camera, HexGrid hexGrid) {
        AppState state = new AppState();

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
                as.currentTask = agent.getCurrentTask();
                as.currentObjective = agent.getCurrentObjective();
                as.totalTokensUsed = agent.getTotalTokensUsed();
                as.totalCostCents = agent.getTotalCostCents();
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
                    AgentType type = AgentType.valueOf(as.type);
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
                    if (as.currentTask != null) {
                        agent.setCurrentTask(as.currentTask);
                    }
                    agent.setCurrentObjective(as.currentObjective);
                    for (String tag : as.tags) {
                        agent.addTag(tag);
                    }

                    cell.setAgent(agent);
                }
            }
        }
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
        public boolean dryRunMode;
        public String repoPath;
        public String branchName;
        public String currentTask;
        public String currentObjective;
        public int totalTokensUsed;
        public int totalCostCents;
        public List<String> tags = new ArrayList<>();
    }
}
