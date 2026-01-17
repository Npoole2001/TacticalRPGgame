package sh.vibecraft.hexcontrol.agent;

import org.joml.Vector3f;

/**
 * Represents an AI agent that can be controlled through the hex interface.
 */
public class AIAgent {

    private String name;
    private AgentType type;
    private Vector3f color;
    private AgentStatus status;

    // Connection info (for future use)
    private String endpoint;
    private String apiKey;

    // Session state
    private boolean connected;
    private String lastMessage;

    public AIAgent(String name, AgentType type, Vector3f color) {
        this.name = name;
        this.type = type;
        this.color = new Vector3f(color);
        this.status = AgentStatus.IDLE;
        this.connected = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AgentType getType() {
        return type;
    }

    public void setType(AgentType type) {
        this.type = type;
        this.color = type.getDefaultColor();
    }

    public Vector3f getColor() {
        return color;
    }

    public void setColor(Vector3f color) {
        this.color = new Vector3f(color);
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    /**
     * Get status indicator color for UI.
     */
    public Vector3f getStatusColor() {
        return switch (status) {
            case IDLE -> new Vector3f(0.5f, 0.5f, 0.5f);
            case ACTIVE -> new Vector3f(0.2f, 0.8f, 0.2f);
            case BUSY -> new Vector3f(0.8f, 0.8f, 0.2f);
            case ERROR -> new Vector3f(0.8f, 0.2f, 0.2f);
            case OFFLINE -> new Vector3f(0.3f, 0.3f, 0.3f);
        };
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - %s", name, type.getDisplayName(), status);
    }
}
