package sh.vibecraft.hexcontrol.agent;

import org.joml.Vector3f;

/**
 * Agent roles per spec addendum.
 * Each role has different permissions, UI affordances, and visual biome patterns.
 */
public enum AgentRole {

    // Coordination roles
    PRODUCT_MANAGER("Product Manager", "PM", "circuit",
        new Vector3f(0.6f, 0.4f, 0.8f),  // Purple
        new RoleCapabilities(false, false, true, true, true, true)),

    TECH_LEAD("Tech Lead", "TL", "crosshatch",
        new Vector3f(0.4f, 0.6f, 0.8f),  // Blue
        new RoleCapabilities(true, true, true, true, true, true)),

    // Coder roles
    BACKEND_CODER("Backend Coder", "BE", "diagonal",
        new Vector3f(0.3f, 0.7f, 0.5f),  // Green
        new RoleCapabilities(true, true, false, true, false, false)),

    FRONTEND_CODER("Frontend/UI Coder", "FE", "dots",
        new Vector3f(0.7f, 0.5f, 0.3f),  // Orange
        new RoleCapabilities(true, true, false, true, false, false)),

    GRAPHICS_CODER("Graphics/FX Coder", "GFX", "waveform",
        new Vector3f(0.8f, 0.3f, 0.5f),  // Pink
        new RoleCapabilities(true, true, false, true, false, false)),

    // Support roles
    QA_ENGINEER("QA / Test Engineer", "QA", "grid",
        new Vector3f(0.5f, 0.5f, 0.7f),  // Light blue
        new RoleCapabilities(true, true, false, true, true, false)),

    DOCS_ENGINEER("Docs / Release", "DOC", "lines",
        new Vector3f(0.6f, 0.6f, 0.4f),  // Yellow
        new RoleCapabilities(true, false, false, false, false, false)),

    // Legacy roles (for backwards compatibility)
    BUILDER("Builder", "BLD", "diagonal",
        new Vector3f(0.3f, 0.7f, 0.5f),
        new RoleCapabilities(true, true, false, true, false, false)),

    TESTER("Tester", "TST", "dots",
        new Vector3f(0.5f, 0.5f, 0.7f),
        new RoleCapabilities(true, true, false, true, true, false)),

    REVIEWER("Reviewer", "REV", "crosshatch",
        new Vector3f(0.4f, 0.6f, 0.8f),
        new RoleCapabilities(false, false, false, false, false, false)),

    RESEARCHER("Researcher", "RSH", "waveform",
        new Vector3f(0.6f, 0.5f, 0.7f),
        new RoleCapabilities(false, false, false, false, false, false)),

    OPS("Ops/Integrator", "OPS", "circuit",
        new Vector3f(0.5f, 0.6f, 0.5f),
        new RoleCapabilities(true, true, false, true, true, false)),

    // Generic
    GENERAL("General", "GEN", "none",
        new Vector3f(0.5f, 0.5f, 0.5f),  // Gray
        new RoleCapabilities(true, true, false, true, false, false));

    private final String displayName;
    private final String shortCode;
    private final String patternId;
    private final Vector3f accentColor;
    private final RoleCapabilities capabilities;

    AgentRole(String displayName, String shortCode, String patternId,
              Vector3f accentColor, RoleCapabilities capabilities) {
        this.displayName = displayName;
        this.shortCode = shortCode;
        this.patternId = patternId;
        this.accentColor = accentColor;
        this.capabilities = capabilities;
    }

    public String getDisplayName() { return displayName; }
    public String getShortCode() { return shortCode; }
    public String getPatternId() { return patternId; }
    public Vector3f getAccentColor() { return new Vector3f(accentColor); }
    public RoleCapabilities getCapabilities() { return capabilities; }

    // Convenience methods
    public boolean canWriteFiles() { return capabilities.canWriteFiles; }
    public boolean canRunShell() { return capabilities.canRunShell; }
    public boolean canAssignTasks() { return capabilities.canAssignTasks; }
    public boolean canOpenPR() { return capabilities.canOpenPR; }
    public boolean canRunTests() { return capabilities.canRunTests; }
    public boolean canManageAgents() { return capabilities.canManageAgents; }

    /**
     * Get default role for a new agent.
     */
    public static AgentRole getDefault() {
        return GENERAL;
    }

    /**
     * Get roles that can coordinate/manage.
     */
    public static AgentRole[] getCoordinatorRoles() {
        return new AgentRole[] { PRODUCT_MANAGER, TECH_LEAD };
    }

    /**
     * Get roles that write code.
     */
    public static AgentRole[] getCoderRoles() {
        return new AgentRole[] { BACKEND_CODER, FRONTEND_CODER, GRAPHICS_CODER, BUILDER };
    }

    /**
     * Check if this is a PM-type role.
     */
    public boolean isCoordinator() {
        return this == PRODUCT_MANAGER || this == TECH_LEAD;
    }

    /**
     * Capabilities record for a role.
     */
    public record RoleCapabilities(
        boolean canWriteFiles,
        boolean canRunShell,
        boolean canAssignTasks,
        boolean canOpenPR,
        boolean canRunTests,
        boolean canManageAgents
    ) {
        /**
         * Check if role has a specific capability.
         */
        public boolean hasCapability(String capability) {
            return switch (capability.toLowerCase()) {
                case "write", "files", "write_files" -> canWriteFiles;
                case "shell", "run_shell" -> canRunShell;
                case "assign", "assign_tasks" -> canAssignTasks;
                case "pr", "open_pr" -> canOpenPR;
                case "test", "run_tests" -> canRunTests;
                case "manage", "manage_agents" -> canManageAgents;
                default -> false;
            };
        }
    }
}
