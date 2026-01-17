package sh.vibecraft.hexcontrol.ui;

import org.joml.Vector3f;
import sh.vibecraft.hexcontrol.agent.AIAgent;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Event timeline component showing agent activity history.
 * Per spec: file changes, build/test runs, provider calls, errors/warnings.
 */
public class EventTimeline {

    private boolean visible = false;
    private float openProgress = 0.0f;
    private float animationSpeed = 8.0f;

    // Scroll position
    private float scrollOffset = 0.0f;
    private float maxScrollOffset = 0.0f;

    // Panel dimensions
    private float panelWidth = 350;
    private float panelHeight = 400;
    private float margin = 10;

    // Current agent
    private AIAgent currentAgent;

    public EventTimeline() {
    }

    public void show(AIAgent agent) {
        this.currentAgent = agent;
        this.visible = true;
        this.scrollOffset = 0.0f;
    }

    public void hide() {
        this.visible = false;
    }

    public void toggle(AIAgent agent) {
        if (visible && currentAgent == agent) {
            hide();
        } else {
            show(agent);
        }
    }

    public boolean isVisible() {
        return visible || openProgress > 0.01f;
    }

    public void update(float deltaTime) {
        float target = visible ? 1.0f : 0.0f;
        openProgress += (target - openProgress) * animationSpeed * deltaTime;

        if (openProgress < 0.01f && !visible) {
            openProgress = 0.0f;
        }
    }

    public void render(int windowWidth, int windowHeight) {
        if (!isVisible() || currentAgent == null) return;

        // Position on the right side
        float x = windowWidth - panelWidth - margin;
        float y = 100;  // Below stats HUD

        // Setup 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);

        float alpha = openProgress;
        float slideX = (1.0f - openProgress) * panelWidth;

        float drawX = x + slideX;
        float drawY = y;
        float drawW = panelWidth;
        float drawH = panelHeight;

        // Panel background
        glColor4f(0.06f, 0.06f, 0.08f, 0.95f * alpha);
        glBegin(GL_QUADS);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + drawH);
        glVertex2f(drawX, drawY + drawH);
        glEnd();

        // Border
        glColor4f(0.25f, 0.25f, 0.3f, alpha);
        glLineWidth(1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + drawH);
        glVertex2f(drawX, drawY + drawH);
        glEnd();

        // Header
        glColor4f(0.1f, 0.1f, 0.12f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + 35);
        glVertex2f(drawX, drawY + 35);
        glEnd();

        // Agent color indicator
        Vector3f agentColor = currentAgent.getColor();
        glColor4f(agentColor.x, agentColor.y, agentColor.z, alpha);
        glBegin(GL_QUADS);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + 4, drawY);
        glVertex2f(drawX + 4, drawY + 35);
        glVertex2f(drawX, drawY + 35);
        glEnd();

        // Close button
        float closeX = drawX + drawW - 25;
        float closeY = drawY + 10;
        glColor4f(0.5f, 0.3f, 0.3f, alpha);
        glLineWidth(2.0f);
        glBegin(GL_LINES);
        glVertex2f(closeX, closeY);
        glVertex2f(closeX + 12, closeY + 12);
        glVertex2f(closeX + 12, closeY);
        glVertex2f(closeX, closeY + 12);
        glEnd();

        // Timeline content
        renderTimeline(drawX, drawY + 40, drawW, drawH - 50, alpha);

        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void renderTimeline(float x, float y, float w, float h, float alpha) {
        List<String> events = currentAgent.getRecentEvents();
        if (events.isEmpty()) {
            // No events message
            glColor4f(0.4f, 0.4f, 0.45f, alpha);
            // Render "No events yet" - would need text rendering
            return;
        }

        float eventHeight = 45;
        float padding = 8;
        float lineX = x + 15;  // Timeline line position

        // Calculate max scroll
        maxScrollOffset = Math.max(0, events.size() * eventHeight - h);

        // Clamp scroll
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));

        // Timeline vertical line
        glColor4f(0.2f, 0.2f, 0.25f, alpha);
        glLineWidth(2.0f);
        glBegin(GL_LINES);
        glVertex2f(lineX, y);
        glVertex2f(lineX, y + h);
        glEnd();

        // Enable scissor for clipping
        glEnable(GL_SCISSOR_TEST);
        glScissor((int)x, (int)(y - h), (int)w, (int)h);

        float eventY = y - scrollOffset;
        int visibleStart = (int)(scrollOffset / eventHeight);
        int visibleEnd = Math.min(events.size(), visibleStart + (int)(h / eventHeight) + 2);

        for (int i = visibleStart; i < visibleEnd; i++) {
            if (i >= events.size()) break;

            String event = events.get(i);
            float currentY = eventY + i * eventHeight;

            if (currentY < y - eventHeight || currentY > y + h) continue;

            // Parse event for type indicator
            EventType type = parseEventType(event);
            Vector3f typeColor = getEventTypeColor(type);

            // Timeline node
            glColor4f(typeColor.x, typeColor.y, typeColor.z, alpha);
            float nodeSize = 5;
            glBegin(GL_QUADS);
            glVertex2f(lineX - nodeSize, currentY + eventHeight / 2 - nodeSize);
            glVertex2f(lineX + nodeSize, currentY + eventHeight / 2 - nodeSize);
            glVertex2f(lineX + nodeSize, currentY + eventHeight / 2 + nodeSize);
            glVertex2f(lineX - nodeSize, currentY + eventHeight / 2 + nodeSize);
            glEnd();

            // Event background
            glColor4f(0.08f, 0.08f, 0.1f, alpha * 0.8f);
            glBegin(GL_QUADS);
            glVertex2f(lineX + 15, currentY + 5);
            glVertex2f(x + w - padding, currentY + 5);
            glVertex2f(x + w - padding, currentY + eventHeight - 5);
            glVertex2f(lineX + 15, currentY + eventHeight - 5);
            glEnd();

            // Type indicator bar
            glColor4f(typeColor.x, typeColor.y, typeColor.z, alpha * 0.5f);
            glBegin(GL_QUADS);
            glVertex2f(lineX + 15, currentY + 5);
            glVertex2f(lineX + 18, currentY + 5);
            glVertex2f(lineX + 18, currentY + eventHeight - 5);
            glVertex2f(lineX + 15, currentY + eventHeight - 5);
            glEnd();

            // Hover highlight
            // Note: would need mouse position tracking for proper hover
        }

        glDisable(GL_SCISSOR_TEST);

        // Scroll indicators
        if (scrollOffset > 0) {
            // Up arrow
            glColor4f(0.5f, 0.5f, 0.6f, alpha);
            float arrowX = x + w - 20;
            float arrowY = y + 10;
            glBegin(GL_TRIANGLES);
            glVertex2f(arrowX, arrowY + 8);
            glVertex2f(arrowX - 6, arrowY + 8);
            glVertex2f(arrowX - 3, arrowY);
            glEnd();
        }

        if (scrollOffset < maxScrollOffset) {
            // Down arrow
            glColor4f(0.5f, 0.5f, 0.6f, alpha);
            float arrowX = x + w - 20;
            float arrowY = y + h - 20;
            glBegin(GL_TRIANGLES);
            glVertex2f(arrowX, arrowY);
            glVertex2f(arrowX - 6, arrowY);
            glVertex2f(arrowX - 3, arrowY + 8);
            glEnd();
        }
    }

    private EventType parseEventType(String event) {
        String lower = event.toLowerCase();

        if (lower.contains("error") || lower.contains("failed") || lower.contains("failure")) {
            return EventType.ERROR;
        }
        if (lower.contains("warning") || lower.contains("conflict")) {
            return EventType.WARNING;
        }
        if (lower.contains("file") || lower.contains("modified") || lower.contains("reading")) {
            return EventType.FILE_CHANGE;
        }
        if (lower.contains("test") || lower.contains("build") || lower.contains("check")) {
            return EventType.BUILD_TEST;
        }
        if (lower.contains("api") || lower.contains("token") || lower.contains("response") || lower.contains("provider")) {
            return EventType.PROVIDER_CALL;
        }
        if (lower.contains("commit") || lower.contains("pr") || lower.contains("branch") || lower.contains("rebase")) {
            return EventType.GIT_OPERATION;
        }
        if (lower.contains("status") || lower.contains("started") || lower.contains("completed")) {
            return EventType.STATUS_CHANGE;
        }

        return EventType.INFO;
    }

    private Vector3f getEventTypeColor(EventType type) {
        return switch (type) {
            case ERROR -> new Vector3f(0.8f, 0.2f, 0.2f);
            case WARNING -> new Vector3f(0.8f, 0.6f, 0.1f);
            case FILE_CHANGE -> new Vector3f(0.3f, 0.6f, 0.8f);
            case BUILD_TEST -> new Vector3f(0.3f, 0.7f, 0.4f);
            case PROVIDER_CALL -> new Vector3f(0.6f, 0.4f, 0.8f);
            case GIT_OPERATION -> new Vector3f(0.5f, 0.7f, 0.6f);
            case STATUS_CHANGE -> new Vector3f(0.5f, 0.5f, 0.6f);
            case INFO -> new Vector3f(0.4f, 0.4f, 0.5f);
        };
    }

    public boolean handleClick(float mouseX, float mouseY, int windowWidth, int windowHeight) {
        if (!isVisible()) return false;

        float x = windowWidth - panelWidth - margin;
        float y = 100;

        // Check if click is outside panel
        if (mouseX < x || mouseX > x + panelWidth ||
            mouseY < y || mouseY > y + panelHeight) {
            return false;
        }

        // Close button
        float closeX = x + panelWidth - 25;
        float closeY = y + 10;
        if (mouseX >= closeX && mouseX <= closeX + 12 &&
            mouseY >= closeY && mouseY <= closeY + 12) {
            hide();
            return true;
        }

        return true;
    }

    public void handleScroll(float amount) {
        if (!isVisible()) return;
        scrollOffset -= amount * 30;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
    }

    public AIAgent getCurrentAgent() {
        return currentAgent;
    }

    /**
     * Event types for visual categorization.
     */
    private enum EventType {
        ERROR,
        WARNING,
        FILE_CHANGE,
        BUILD_TEST,
        PROVIDER_CALL,
        GIT_OPERATION,
        STATUS_CHANGE,
        INFO
    }
}
