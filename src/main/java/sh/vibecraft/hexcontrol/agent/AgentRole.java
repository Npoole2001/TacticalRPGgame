package sh.vibecraft.hexcontrol.agent;

/**
 * Agent roles that determine biome visual patterns per spec.
 */
public enum AgentRole {
    BUILDER("Builder", "diagonal"),       // Diagonal micro-lines
    TESTER("Tester", "dots"),             // Dot-grid pattern
    REVIEWER("Reviewer", "crosshatch"),   // Thin crosshatch
    RESEARCHER("Researcher", "waveform"), // Subtle waveform line
    OPS("Ops/Integrator", "circuit"),     // Circuit-like pattern
    GENERAL("General", "none");           // No pattern

    private final String displayName;
    private final String patternId;

    AgentRole(String displayName, String patternId) {
        this.displayName = displayName;
        this.patternId = patternId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPatternId() {
        return patternId;
    }
}
