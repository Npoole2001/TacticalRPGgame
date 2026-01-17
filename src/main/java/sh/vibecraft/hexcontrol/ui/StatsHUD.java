package sh.vibecraft.hexcontrol.ui;

import org.joml.Vector3f;
import sh.vibecraft.hexcontrol.agent.AgentStatus;
import sh.vibecraft.hexcontrol.render.HexGrid;

import static org.lwjgl.opengl.GL11.*;

/**
 * Top-right stats panel showing agent counts and system status.
 * Per spec: compact operator dashboard, collapsible, high contrast.
 */
public class StatsHUD {

    private boolean collapsed = false;
    private float collapseProgress = 1.0f;  // 1.0 = expanded, 0.0 = collapsed
    private float animationSpeed = 8.0f;

    // Stats
    private int totalAgents = 0;
    private int runningAgents = 0;
    private int idleAgents = 0;
    private int sleepingAgents = 0;
    private int blockedAgents = 0;
    private int errorAgents = 0;
    private int queueDepth = 0;
    private boolean providerConnected = false;
    private String gitStatus = "Clean";

    // Token/cost metrics
    private int totalTokensUsed = 0;
    private int totalCostCents = 0;

    // Panel dimensions
    private float panelWidth = 200;
    private float panelHeight = 280;
    private float collapsedHeight = 30;
    private float margin = 10;

    public StatsHUD() {
    }

    public void update(float deltaTime, HexGrid hexGrid) {
        // Animate collapse
        float target = collapsed ? 0.0f : 1.0f;
        collapseProgress += (target - collapseProgress) * animationSpeed * deltaTime;

        // Update stats from hex grid
        totalAgents = 0;
        runningAgents = 0;
        idleAgents = 0;
        sleepingAgents = 0;
        blockedAgents = 0;
        errorAgents = 0;
        totalTokensUsed = 0;
        totalCostCents = 0;

        for (var cell : hexGrid.getCells()) {
            var agent = cell.getAgent();
            if (agent != null && agent.getType() != sh.vibecraft.hexcontrol.agent.AgentType.EMPTY) {
                totalAgents++;
                totalTokensUsed += agent.getTotalTokensUsed();
                totalCostCents += agent.getTotalCostCents();

                switch (agent.getStatus()) {
                    case IDLE -> idleAgents++;
                    case RUNNING -> runningAgents++;
                    case SLEEPING -> sleepingAgents++;
                    case BLOCKED -> blockedAgents++;
                    case ERROR -> errorAgents++;
                }
            }
        }
    }

    public void render(int windowWidth, int windowHeight) {
        // Setup 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);

        float x = windowWidth - panelWidth - margin;
        float y = margin;
        float currentHeight = collapsedHeight + (panelHeight - collapsedHeight) * collapseProgress;

        // Panel background
        glColor4f(0.08f, 0.08f, 0.1f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + panelWidth, y);
        glVertex2f(x + panelWidth, y + currentHeight);
        glVertex2f(x, y + currentHeight);
        glEnd();

        // Panel border
        glColor4f(0.3f, 0.3f, 0.35f, 1.0f);
        glLineWidth(1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + panelWidth, y);
        glVertex2f(x + panelWidth, y + currentHeight);
        glVertex2f(x, y + currentHeight);
        glEnd();

        // Header bar
        glColor4f(0.15f, 0.15f, 0.2f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + panelWidth, y);
        glVertex2f(x + panelWidth, y + collapsedHeight);
        glVertex2f(x, y + collapsedHeight);
        glEnd();

        // Collapse indicator (triangle)
        float triX = x + panelWidth - 20;
        float triY = y + collapsedHeight / 2;
        float triSize = 6;

        glColor4f(0.7f, 0.7f, 0.7f, 1.0f);
        glBegin(GL_TRIANGLES);
        if (collapsed) {
            glVertex2f(triX - triSize, triY - triSize / 2);
            glVertex2f(triX + triSize, triY);
            glVertex2f(triX - triSize, triY + triSize / 2);
        } else {
            glVertex2f(triX - triSize / 2, triY - triSize);
            glVertex2f(triX + triSize / 2, triY - triSize);
            glVertex2f(triX, triY + triSize / 2);
        }
        glEnd();

        // Only render stats if expanded
        if (collapseProgress > 0.1f) {
            float alpha = collapseProgress;
            float statY = y + collapsedHeight + 15;
            float lineHeight = 22;
            float labelX = x + 12;
            float valueX = x + panelWidth - 50;

            // Total agents
            drawStatBar(labelX, statY, panelWidth - 24, "Agents", String.valueOf(totalAgents),
                new Vector3f(0.5f, 0.5f, 0.5f), alpha);
            statY += lineHeight;

            // Running (green)
            drawStatBar(labelX, statY, panelWidth - 24, "Running", String.valueOf(runningAgents),
                new Vector3f(0.2f, 0.8f, 0.3f), alpha);
            statY += lineHeight;

            // Idle (gray)
            drawStatBar(labelX, statY, panelWidth - 24, "Idle", String.valueOf(idleAgents),
                new Vector3f(0.4f, 0.4f, 0.4f), alpha);
            statY += lineHeight;

            // Sleeping (blue-gray)
            drawStatBar(labelX, statY, panelWidth - 24, "Sleeping", String.valueOf(sleepingAgents),
                new Vector3f(0.3f, 0.3f, 0.5f), alpha);
            statY += lineHeight;

            // Blocked (amber)
            drawStatBar(labelX, statY, panelWidth - 24, "Blocked", String.valueOf(blockedAgents),
                new Vector3f(0.8f, 0.6f, 0.1f), alpha);
            statY += lineHeight;

            // Error (red)
            drawStatBar(labelX, statY, panelWidth - 24, "Error", String.valueOf(errorAgents),
                new Vector3f(0.8f, 0.2f, 0.2f), alpha);
            statY += lineHeight + 10;

            // Separator
            glColor4f(0.25f, 0.25f, 0.3f, alpha);
            glBegin(GL_LINES);
            glVertex2f(labelX, statY);
            glVertex2f(x + panelWidth - 12, statY);
            glEnd();
            statY += 10;

            // Provider status
            Vector3f providerColor = providerConnected ?
                new Vector3f(0.2f, 0.8f, 0.3f) : new Vector3f(0.5f, 0.5f, 0.5f);
            drawStatBar(labelX, statY, panelWidth - 24, "Provider",
                providerConnected ? "Connected" : "Offline", providerColor, alpha);
            statY += lineHeight;

            // Git status
            Vector3f gitColor = gitStatus.equals("Clean") ?
                new Vector3f(0.5f, 0.5f, 0.5f) : new Vector3f(0.8f, 0.6f, 0.1f);
            drawStatBar(labelX, statY, panelWidth - 24, "Git", gitStatus, gitColor, alpha);
            statY += lineHeight + 10;

            // Token/cost metrics
            if (totalTokensUsed > 0 || totalCostCents > 0) {
                glColor4f(0.25f, 0.25f, 0.3f, alpha);
                glBegin(GL_LINES);
                glVertex2f(labelX, statY);
                glVertex2f(x + panelWidth - 12, statY);
                glEnd();
                statY += 10;

                drawStatBar(labelX, statY, panelWidth - 24, "Tokens",
                    formatNumber(totalTokensUsed), new Vector3f(0.5f, 0.5f, 0.6f), alpha);
                statY += lineHeight;

                drawStatBar(labelX, statY, panelWidth - 24, "Cost",
                    formatCost(totalCostCents), new Vector3f(0.5f, 0.6f, 0.5f), alpha);
            }
        }

        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void drawStatBar(float x, float y, float width, String label, String value,
                              Vector3f color, float alpha) {
        // Status indicator dot
        glColor4f(color.x, color.y, color.z, alpha);
        float dotSize = 4;
        glBegin(GL_QUADS);
        glVertex2f(x, y - dotSize);
        glVertex2f(x + dotSize * 2, y - dotSize);
        glVertex2f(x + dotSize * 2, y + dotSize);
        glVertex2f(x, y + dotSize);
        glEnd();

        // Note: Text rendering would go here with NanoVG or font atlas
        // For now we just show colored indicators
    }

    private String formatNumber(int num) {
        if (num >= 1000000) {
            return String.format("%.1fM", num / 1000000.0);
        } else if (num >= 1000) {
            return String.format("%.1fK", num / 1000.0);
        }
        return String.valueOf(num);
    }

    private String formatCost(int cents) {
        return String.format("$%.2f", cents / 100.0);
    }

    public boolean handleClick(float mouseX, float mouseY, int windowWidth, int windowHeight) {
        float x = windowWidth - panelWidth - margin;
        float y = margin;

        // Check if click is in header area (for collapse toggle)
        if (mouseX >= x && mouseX <= x + panelWidth &&
            mouseY >= y && mouseY <= y + collapsedHeight) {
            collapsed = !collapsed;
            return true;
        }

        return false;
    }

    public void setProviderConnected(boolean connected) {
        this.providerConnected = connected;
    }

    public void setGitStatus(String status) {
        this.gitStatus = status;
    }

    public void setQueueDepth(int depth) {
        this.queueDepth = depth;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }
}
