package sh.vibecraft.hexcontrol.demo;

import org.joml.Vector3f;
import sh.vibecraft.hexcontrol.agent.*;
import sh.vibecraft.hexcontrol.render.HexGrid;

import java.util.List;
import java.util.Random;

/**
 * Demo mode provides sample data for testing UI features without real API connections.
 * Populates the grid with various agents in different states and simulates activity.
 */
public class DemoMode {

    private static final Random random = new Random(42);  // Fixed seed for reproducible demos

    private boolean enabled = false;
    private boolean simulationRunning = false;
    private float simulationTimer = 0.0f;

    // Demo agent templates
    private static final DemoAgentTemplate[] DEMO_AGENTS = {
        new DemoAgentTemplate("Claude PM", AgentType.CLAUDE, AgentRole.PRODUCT_MANAGER, AgentStatus.RUNNING,
            "Coordinating feature implementation", "Planning V1 milestone tasks"),
        new DemoAgentTemplate("GPT Architect", AgentType.CHATGPT, AgentRole.TECH_LEAD, AgentStatus.IDLE,
            null, "Reviewing architecture decisions"),
        new DemoAgentTemplate("Claude Backend", AgentType.CLAUDE, AgentRole.BACKEND_CODER, AgentStatus.RUNNING,
            "Implementing TaskManager", "Building orchestration core"),
        new DemoAgentTemplate("Gemini Frontend", AgentType.GEMINI, AgentRole.FRONTEND_CODER, AgentStatus.BLOCKED,
            "Waiting for API response", "Creating settings panel UI"),
        new DemoAgentTemplate("Claude FX", AgentType.CLAUDE, AgentRole.GRAPHICS_CODER, AgentStatus.RUNNING,
            "Adding particle effects", "Enhancing visual feedback"),
        new DemoAgentTemplate("GPT QA", AgentType.CHATGPT, AgentRole.QA_ENGINEER, AgentStatus.SLEEPING,
            null, "Test suite maintenance"),
        new DemoAgentTemplate("Copilot Docs", AgentType.COPILOT, AgentRole.DOCS_ENGINEER, AgentStatus.IDLE,
            null, "Documentation updates"),
        new DemoAgentTemplate("Claude Backend 2", AgentType.CLAUDE, AgentRole.BACKEND_CODER, AgentStatus.ERROR,
            "Build failed: missing dependency", "Persistence layer refactor"),
        new DemoAgentTemplate("Gemini Research", AgentType.GEMINI, AgentRole.RESEARCHER, AgentStatus.RUNNING,
            "Analyzing codebase patterns", "Architecture analysis"),
        new DemoAgentTemplate("Llama Tester", AgentType.LLAMA, AgentRole.TESTER, AgentStatus.IDLE,
            null, "Integration test coverage"),
        new DemoAgentTemplate("Mistral Ops", AgentType.MISTRAL, AgentRole.OPS, AgentStatus.RUNNING,
            "Configuring CI pipeline", "Build system optimization"),
        new DemoAgentTemplate("Claude Reviewer", AgentType.CLAUDE, AgentRole.REVIEWER, AgentStatus.BLOCKED,
            "Awaiting PR for review", "Code review queue"),
    };

    // Demo events for timeline
    private static final String[] DEMO_EVENTS = {
        "Started task execution",
        "Reading file: src/main/java/Engine.java",
        "Analyzing code structure",
        "Proposing changes to 3 files",
        "Running tests: 15 passed, 0 failed",
        "Committing changes to branch",
        "Build succeeded in 4.2s",
        "Waiting for user input",
        "Received API response (245 tokens)",
        "File modified: Renderer.java",
        "Conflict detected with another agent",
        "Rebasing onto main branch",
        "PR #42 created successfully",
        "Test coverage: 78.5%",
        "Memory usage: 256MB",
    };

    public DemoMode() {
    }

    /**
     * Populate the hex grid with demo agents.
     */
    public void populateGrid(HexGrid hexGrid) {
        var cells = hexGrid.getCells();
        int agentCount = Math.min(DEMO_AGENTS.length, cells.size());

        for (int i = 0; i < agentCount; i++) {
            var cell = cells.get(i);
            DemoAgentTemplate template = DEMO_AGENTS[i];

            AIAgent agent = new AIAgent(template.name, template.type);
            agent.setRole(template.role);
            agent.setStatus(template.status);

            if (template.currentTask != null) {
                agent.setCurrentTask(template.currentTask);
            }
            agent.setCurrentObjective(template.objective);

            // Add some fake telemetry
            agent.addTokensUsed(random.nextInt(50000) + 1000);
            agent.addCost(random.nextInt(500) + 10);

            // Add some demo events
            int eventCount = random.nextInt(8) + 3;
            for (int e = 0; e < eventCount; e++) {
                agent.addEvent(DEMO_EVENTS[random.nextInt(DEMO_EVENTS.length)]);
            }

            // Add tags
            if (template.role.isCoordinator()) {
                agent.addTag("coordinator");
            }
            if (template.status == AgentStatus.RUNNING) {
                agent.addTag("active");
            }
            agent.addTag("demo");

            cell.setAgent(agent);
        }

        enabled = true;
        System.out.println("Demo mode: Populated " + agentCount + " agents");
    }

    /**
     * Update demo simulation (animate status changes, etc.)
     */
    public void update(float deltaTime, HexGrid hexGrid) {
        if (!enabled || !simulationRunning) return;

        simulationTimer += deltaTime;

        // Periodically simulate activity
        if (simulationTimer >= 3.0f) {
            simulationTimer = 0.0f;
            simulateActivity(hexGrid);
        }
    }

    private void simulateActivity(HexGrid hexGrid) {
        var cells = hexGrid.getCells();

        for (var cell : cells) {
            AIAgent agent = cell.getAgent();
            if (agent == null || agent.getType() == AgentType.EMPTY) continue;

            // Small chance to change status
            if (random.nextFloat() < 0.15f) {
                AgentStatus newStatus = getRandomTransition(agent.getStatus());
                if (newStatus != agent.getStatus()) {
                    agent.setStatus(newStatus);
                }
            }

            // Running agents generate events
            if (agent.getStatus() == AgentStatus.RUNNING) {
                agent.addTokensUsed(random.nextInt(100) + 10);
                if (random.nextFloat() < 0.3f) {
                    agent.addEvent(DEMO_EVENTS[random.nextInt(DEMO_EVENTS.length)]);
                }
            }
        }
    }

    private AgentStatus getRandomTransition(AgentStatus current) {
        return switch (current) {
            case IDLE -> random.nextFloat() < 0.3f ? AgentStatus.RUNNING : AgentStatus.IDLE;
            case RUNNING -> {
                float r = random.nextFloat();
                if (r < 0.1f) yield AgentStatus.BLOCKED;
                if (r < 0.15f) yield AgentStatus.ERROR;
                if (r < 0.20f) yield AgentStatus.PAUSED;
                if (r < 0.30f) yield AgentStatus.IDLE;
                yield AgentStatus.RUNNING;
            }
            case PAUSED -> random.nextFloat() < 0.5f ? AgentStatus.RUNNING : AgentStatus.PAUSED;
            case BLOCKED -> random.nextFloat() < 0.4f ? AgentStatus.RUNNING : AgentStatus.BLOCKED;
            case ERROR -> random.nextFloat() < 0.2f ? AgentStatus.IDLE : AgentStatus.ERROR;
            case SLEEPING -> random.nextFloat() < 0.1f ? AgentStatus.IDLE : AgentStatus.SLEEPING;
            case COMPLETE -> AgentStatus.COMPLETE;
        };
    }

    /**
     * Get demo objectives for the pinned objectives panel.
     */
    public static List<DemoObjective> getDemoObjectives() {
        return List.of(
            new DemoObjective("V1 Release", "Complete all V1 features", "HIGH", "ACTIVE",
                List.of("Claude PM", "Claude Backend")),
            new DemoObjective("Settings UI", "Create settings page for API keys", "HIGH", "ACTIVE",
                List.of("Gemini Frontend")),
            new DemoObjective("Mini-map", "Implement mini-map component", "MEDIUM", "ACTIVE",
                List.of("Claude FX")),
            new DemoObjective("Test Coverage", "Increase test coverage to 80%", "MEDIUM", "BLOCKED",
                List.of("GPT QA", "Llama Tester")),
            new DemoObjective("Documentation", "Update README and user guide", "LOW", "ACTIVE",
                List.of("Copilot Docs"))
        );
    }

    /**
     * Get demo context sheet content.
     */
    public static DemoContextSheet getDemoContextSheet() {
        return new DemoContextSheet(
            "HexControl - AI Agent Orchestration Platform",
            "/home/user/TacticalRPGgame",
            List.of("V1 Feature Implementation", "Performance Optimization", "Documentation"),
            List.of("No external network calls without consent", "Dry-run mode by default",
                    "Max 400 lines per change", "Tests must pass before merge"),
            "All V1 features working, documented, with 75%+ test coverage",
            List.of("Gemini Frontend blocked on API response", "Claude Backend 2 build failure")
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSimulationRunning() {
        return simulationRunning;
    }

    public void setSimulationRunning(boolean running) {
        this.simulationRunning = running;
    }

    public void toggleSimulation() {
        this.simulationRunning = !this.simulationRunning;
        System.out.println("Demo simulation: " + (simulationRunning ? "Running" : "Paused"));
    }

    /**
     * Template for demo agents.
     */
    private record DemoAgentTemplate(
        String name,
        AgentType type,
        AgentRole role,
        AgentStatus status,
        String currentTask,
        String objective
    ) {}

    /**
     * Demo objective for testing.
     */
    public record DemoObjective(
        String title,
        String description,
        String priority,
        String status,
        List<String> owners
    ) {}

    /**
     * Demo context sheet for testing.
     */
    public record DemoContextSheet(
        String projectGoal,
        String repoLocation,
        List<String> currentEpics,
        List<String> constraints,
        String definitionOfDone,
        List<String> blockers
    ) {}
}
