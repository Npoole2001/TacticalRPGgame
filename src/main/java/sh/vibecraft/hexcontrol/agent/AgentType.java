package sh.vibecraft.hexcontrol.agent;

import org.joml.Vector3f;

/**
 * Types of AI agents that can be managed.
 */
public enum AgentType {
    CLAUDE("Claude", new Vector3f(0.8f, 0.5f, 0.2f)),        // Orange/amber
    CHATGPT("ChatGPT", new Vector3f(0.2f, 0.7f, 0.5f)),      // Teal/green
    GEMINI("Gemini", new Vector3f(0.3f, 0.5f, 0.9f)),        // Blue
    COPILOT("Copilot", new Vector3f(0.5f, 0.3f, 0.8f)),      // Purple
    LLAMA("Llama", new Vector3f(0.6f, 0.6f, 0.3f)),          // Yellow/olive
    MISTRAL("Mistral", new Vector3f(0.7f, 0.3f, 0.4f)),      // Rose
    EMPTY("Empty", new Vector3f(0.3f, 0.3f, 0.35f));         // Gray (unassigned)

    private final String displayName;
    private final Vector3f defaultColor;

    AgentType(String displayName, Vector3f defaultColor) {
        this.displayName = displayName;
        this.defaultColor = defaultColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Vector3f getDefaultColor() {
        return new Vector3f(defaultColor);
    }
}
