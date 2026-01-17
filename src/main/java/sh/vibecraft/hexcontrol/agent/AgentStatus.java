package sh.vibecraft.hexcontrol.agent;

/**
 * Status of an AI agent.
 */
public enum AgentStatus {
    IDLE("Idle"),
    ACTIVE("Active"),
    BUSY("Processing"),
    ERROR("Error"),
    OFFLINE("Offline");

    private final String displayName;

    AgentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
