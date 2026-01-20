package sh.vibecraft.hexcontrol.agent;

import org.joml.Vector3f;

/**
 * Status of an AI agent per spec.
 */
public enum AgentStatus {
    IDLE("Idle", new Vector3f(0.4f, 0.4f, 0.4f)),
    RUNNING("Running", new Vector3f(0.2f, 0.8f, 0.3f)),
    PAUSED("Paused", new Vector3f(0.6f, 0.6f, 0.3f)),
    SLEEPING("Sleeping", new Vector3f(0.3f, 0.3f, 0.5f)),
    BLOCKED("Blocked", new Vector3f(0.8f, 0.6f, 0.1f)),
    ERROR("Error", new Vector3f(0.8f, 0.2f, 0.2f)),
    COMPLETE("Complete", new Vector3f(0.3f, 0.6f, 0.9f));

    private final String displayName;
    private final Vector3f color;

    AgentStatus(String displayName, Vector3f color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Vector3f getColor() {
        return new Vector3f(color);
    }
}
