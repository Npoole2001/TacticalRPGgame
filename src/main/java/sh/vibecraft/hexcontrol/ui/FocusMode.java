package sh.vibecraft.hexcontrol.ui;

import org.joml.Vector3f;
import sh.vibecraft.hexcontrol.agent.Agent;
import sh.vibecraft.hexcontrol.agent.AgentStatus;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;

import static org.lwjgl.opengl.GL11.*;

/**
 * Focus mode for detailed single-agent view.
 * Per spec: "Focus Mode (F): expand that tile; dim others;
 * show large panel with full logs, test output, diff preview, token count, etc."
 */
public class FocusMode {

    private boolean active = false;
    private Agent focusedAgent;
    private int focusedCellIndex = -1;

    // Animation state
    private float transitionProgress = 0.0f;
    private float dimLevel = 0.0f;
    private float panelProgress = 0.0f;

    // Animation speed
    private float transitionSpeed = 4.0f;

    // Panel dimensions
    private float panelWidth = 500;
    private float panelHeight = 600;
    private float panelMargin = 40;

    // Scroll state for logs
    private float logScrollOffset = 0;
    private float maxLogScroll = 0;

    // Tab state
    private FocusTab currentTab = FocusTab.OVERVIEW;

    public enum FocusTab {
        OVERVIEW("Overview"),
        LOGS("Logs"),
        TESTS("Tests"),
        DIFF("Diff"),
        TOKENS("Tokens");

        private final String label;

        FocusTab(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * Enter focus mode for an agent.
     */
    public void enter(Agent agent, int cellIndex, Camera camera, HexGrid hexGrid) {
        if (agent == null) return;

        this.focusedAgent = agent;
        this.focusedCellIndex = cellIndex;
        this.active = true;
        this.currentTab = FocusTab.OVERVIEW;
        this.logScrollOffset = 0;

        // Focus camera on the agent's hex
        var cell = hexGrid.getCell(cellIndex);
        if (cell != null) {
            camera.focusOn(cell.getWorldX(), cell.getWorldZ());
            camera.setTargetZoom(8.0f); // Closer zoom
        }

        System.out.println("[FocusMode] Entered focus mode for: " + agent.getName());
    }

    /**
     * Exit focus mode.
     */
    public void exit(Camera camera) {
        this.active = false;
        this.focusedAgent = null;
        this.focusedCellIndex = -1;

        // Reset camera zoom
        camera.setTargetZoom(15.0f);

        System.out.println("[FocusMode] Exited focus mode");
    }

    /**
     * Toggle focus mode for an agent.
     */
    public void toggle(Agent agent, int cellIndex, Camera camera, HexGrid hexGrid) {
        if (active && focusedAgent == agent) {
            exit(camera);
        } else {
            enter(agent, cellIndex, camera, hexGrid);
        }
    }

    public void update(float deltaTime) {
        if (active) {
            transitionProgress = Math.min(1.0f, transitionProgress + deltaTime * transitionSpeed);
            dimLevel = Math.min(0.7f, dimLevel + deltaTime * transitionSpeed);
            panelProgress = Math.min(1.0f, panelProgress + deltaTime * transitionSpeed);
        } else {
            transitionProgress = Math.max(0.0f, transitionProgress - deltaTime * transitionSpeed);
            dimLevel = Math.max(0.0f, dimLevel - deltaTime * transitionSpeed);
            panelProgress = Math.max(0.0f, panelProgress - deltaTime * transitionSpeed);
        }
    }

    /**
     * Render the focus mode overlay and panel.
     */
    public void render(int windowWidth, int windowHeight) {
        if (transitionProgress <= 0.01f) return;

        // Setup 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);

        // Render dim overlay (semi-transparent)
        renderDimOverlay(windowWidth, windowHeight);

        // Render focus panel
        if (focusedAgent != null) {
            renderFocusPanel(windowWidth, windowHeight);
        }

        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void renderDimOverlay(int windowWidth, int windowHeight) {
        // Only render edges - center stays clear to see focused hex
        float alpha = dimLevel * 0.5f;

        glColor4f(0.0f, 0.0f, 0.0f, alpha);

        // Top bar
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(windowWidth, 0);
        glVertex2f(windowWidth, 80);
        glVertex2f(0, 80);
        glEnd();

        // Bottom bar
        glBegin(GL_QUADS);
        glVertex2f(0, windowHeight - 60);
        glVertex2f(windowWidth, windowHeight - 60);
        glVertex2f(windowWidth, windowHeight);
        glVertex2f(0, windowHeight);
        glEnd();
    }

    private void renderFocusPanel(int windowWidth, int windowHeight) {
        float slideOffset = (1.0f - panelProgress) * (panelWidth + panelMargin);
        float panelX = windowWidth - panelWidth - panelMargin + slideOffset;
        float panelY = panelMargin + 60;
        float actualHeight = Math.min(panelHeight, windowHeight - panelY - panelMargin);

        // Panel background
        glColor4f(0.08f, 0.08f, 0.12f, 0.98f * panelProgress);
        glBegin(GL_QUADS);
        glVertex2f(panelX, panelY);
        glVertex2f(panelX + panelWidth, panelY);
        glVertex2f(panelX + panelWidth, panelY + actualHeight);
        glVertex2f(panelX, panelY + actualHeight);
        glEnd();

        // Panel border
        Vector3f agentColor = focusedAgent.getRole().getAccentColor();
        glColor4f(agentColor.x, agentColor.y, agentColor.z, panelProgress);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(panelX, panelY);
        glVertex2f(panelX + panelWidth, panelY);
        glVertex2f(panelX + panelWidth, panelY + actualHeight);
        glVertex2f(panelX, panelY + actualHeight);
        glEnd();

        // Header section
        renderPanelHeader(panelX, panelY, agentColor);

        // Tab bar
        renderTabBar(panelX, panelY + 70);

        // Content area
        renderTabContent(panelX, panelY + 110, panelWidth, actualHeight - 120);
    }

    private void renderPanelHeader(float x, float y, Vector3f color) {
        // Agent name header background
        glColor4f(color.x * 0.3f, color.y * 0.3f, color.z * 0.3f, panelProgress * 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + panelWidth, y);
        glVertex2f(x + panelWidth, y + 65);
        glVertex2f(x, y + 65);
        glEnd();

        // Status indicator
        Vector3f statusColor = getStatusColor(focusedAgent.getStatus());
        float indicatorX = x + 15;
        float indicatorY = y + 20;

        glColor4f(statusColor.x, statusColor.y, statusColor.z, panelProgress);
        glBegin(GL_TRIANGLE_FAN);
        for (int i = 0; i < 16; i++) {
            float angle = (float) (Math.PI * 2 * i / 16);
            glVertex2f(indicatorX + (float) Math.cos(angle) * 8,
                    indicatorY + (float) Math.sin(angle) * 8);
        }
        glEnd();

        // Close button (X)
        float closeX = x + panelWidth - 30;
        float closeY = y + 20;
        glColor4f(0.6f, 0.6f, 0.6f, panelProgress);
        glLineWidth(2.0f);
        glBegin(GL_LINES);
        glVertex2f(closeX - 6, closeY - 6);
        glVertex2f(closeX + 6, closeY + 6);
        glVertex2f(closeX + 6, closeY - 6);
        glVertex2f(closeX - 6, closeY + 6);
        glEnd();

        // Pause/Resume button
        float pauseX = x + panelWidth - 70;
        float pauseY = y + 15;
        glColor4f(0.4f, 0.4f, 0.5f, panelProgress);
        glBegin(GL_LINE_LOOP);
        glVertex2f(pauseX, pauseY);
        glVertex2f(pauseX + 30, pauseY);
        glVertex2f(pauseX + 30, pauseY + 20);
        glVertex2f(pauseX, pauseY + 20);
        glEnd();
    }

    private void renderTabBar(float x, float y) {
        float tabWidth = panelWidth / FocusTab.values().length;

        for (int i = 0; i < FocusTab.values().length; i++) {
            FocusTab tab = FocusTab.values()[i];
            float tabX = x + i * tabWidth;

            // Tab background
            if (tab == currentTab) {
                glColor4f(0.15f, 0.15f, 0.2f, panelProgress);
            } else {
                glColor4f(0.1f, 0.1f, 0.12f, panelProgress * 0.5f);
            }
            glBegin(GL_QUADS);
            glVertex2f(tabX, y);
            glVertex2f(tabX + tabWidth - 2, y);
            glVertex2f(tabX + tabWidth - 2, y + 35);
            glVertex2f(tabX, y + 35);
            glEnd();

            // Active tab indicator
            if (tab == currentTab) {
                Vector3f color = focusedAgent.getRole().getAccentColor();
                glColor4f(color.x, color.y, color.z, panelProgress);
                glBegin(GL_QUADS);
                glVertex2f(tabX, y + 33);
                glVertex2f(tabX + tabWidth - 2, y + 33);
                glVertex2f(tabX + tabWidth - 2, y + 35);
                glVertex2f(tabX, y + 35);
                glEnd();
            }
        }
    }

    private void renderTabContent(float x, float y, float width, float height) {
        // Content background
        glColor4f(0.06f, 0.06f, 0.08f, panelProgress * 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(x + 5, y);
        glVertex2f(x + width - 5, y);
        glVertex2f(x + width - 5, y + height);
        glVertex2f(x + 5, y + height);
        glEnd();

        switch (currentTab) {
            case OVERVIEW -> renderOverviewContent(x + 15, y + 15, width - 30, height - 30);
            case LOGS -> renderLogsContent(x + 15, y + 15, width - 30, height - 30);
            case TESTS -> renderTestsContent(x + 15, y + 15, width - 30, height - 30);
            case DIFF -> renderDiffContent(x + 15, y + 15, width - 30, height - 30);
            case TOKENS -> renderTokensContent(x + 15, y + 15, width - 30, height - 30);
        }
    }

    private void renderOverviewContent(float x, float y, float width, float height) {
        float lineHeight = 25;
        float currentY = y;

        // Status section
        renderInfoRow(x, currentY, "Status:", focusedAgent.getStatus().name());
        currentY += lineHeight;

        renderInfoRow(x, currentY, "Role:", focusedAgent.getRole().getDisplayName());
        currentY += lineHeight;

        renderInfoRow(x, currentY, "Type:", focusedAgent.getType().getDisplayName());
        currentY += lineHeight * 1.5f;

        // Task section
        if (focusedAgent.getCurrentTask() != null) {
            glColor4f(0.7f, 0.7f, 0.7f, panelProgress);
            renderLabel(x, currentY, "Current Task:");
            currentY += lineHeight;

            glColor4f(0.5f, 0.6f, 0.7f, panelProgress);
            renderLabel(x + 10, currentY, truncate(focusedAgent.getCurrentTask(), 45));
            currentY += lineHeight * 1.5f;
        }

        // Objective section
        if (focusedAgent.getCurrentObjective() != null) {
            glColor4f(0.7f, 0.7f, 0.7f, panelProgress);
            renderLabel(x, currentY, "Objective:");
            currentY += lineHeight;

            glColor4f(0.5f, 0.7f, 0.6f, panelProgress);
            renderLabel(x + 10, currentY, truncate(focusedAgent.getCurrentObjective(), 45));
            currentY += lineHeight * 1.5f;
        }

        // Stats section
        glColor4f(0.6f, 0.6f, 0.6f, panelProgress);
        renderLabel(x, currentY, "Session Stats:");
        currentY += lineHeight;

        renderInfoRow(x + 10, currentY, "Tokens Used:", "~12,450");
        currentY += lineHeight;
        renderInfoRow(x + 10, currentY, "Tasks Completed:", "7");
        currentY += lineHeight;
        renderInfoRow(x + 10, currentY, "Uptime:", "1h 23m");
    }

    private void renderLogsContent(float x, float y, float width, float height) {
        // Sample log entries
        String[] logs = {
            "[INFO] Starting task: Implement feature X",
            "[DEBUG] Reading file: src/main/App.java",
            "[INFO] Analyzing codebase structure...",
            "[DEBUG] Found 23 relevant files",
            "[INFO] Generating implementation plan",
            "[WARN] Large file detected, may take longer",
            "[INFO] Writing changes to 3 files",
            "[DEBUG] Running tests...",
            "[SUCCESS] All tests passed (12/12)",
            "[INFO] Task completed successfully"
        };

        float lineHeight = 20;
        float currentY = y - logScrollOffset;

        for (String log : logs) {
            if (currentY > y - 20 && currentY < y + height) {
                Vector3f logColor = getLogColor(log);
                glColor4f(logColor.x, logColor.y, logColor.z, panelProgress * 0.9f);
                renderLabel(x, currentY, truncate(log, 55));
            }
            currentY += lineHeight;
        }

        maxLogScroll = Math.max(0, logs.length * lineHeight - height);

        // Scroll indicator
        if (maxLogScroll > 0) {
            float scrollBarHeight = height * (height / (logs.length * lineHeight));
            float scrollBarY = y + (logScrollOffset / maxLogScroll) * (height - scrollBarHeight);

            glColor4f(0.3f, 0.3f, 0.4f, panelProgress * 0.5f);
            glBegin(GL_QUADS);
            glVertex2f(x + width - 8, scrollBarY);
            glVertex2f(x + width - 3, scrollBarY);
            glVertex2f(x + width - 3, scrollBarY + scrollBarHeight);
            glVertex2f(x + width - 8, scrollBarY + scrollBarHeight);
            glEnd();
        }
    }

    private void renderTestsContent(float x, float y, float width, float height) {
        float lineHeight = 22;
        float currentY = y;

        // Test summary
        glColor4f(0.3f, 0.8f, 0.4f, panelProgress);
        renderLabel(x, currentY, "12 passed");
        glColor4f(0.5f, 0.5f, 0.5f, panelProgress);
        renderLabel(x + 100, currentY, " | ");
        glColor4f(0.8f, 0.3f, 0.3f, panelProgress);
        renderLabel(x + 115, currentY, "0 failed");
        glColor4f(0.5f, 0.5f, 0.5f, panelProgress);
        renderLabel(x + 185, currentY, " | ");
        glColor4f(0.6f, 0.6f, 0.3f, panelProgress);
        renderLabel(x + 200, currentY, "2 skipped");
        currentY += lineHeight * 1.5f;

        // Test list
        String[] tests = {
            "[PASS] testUserAuthentication",
            "[PASS] testDatabaseConnection",
            "[PASS] testApiEndpoints",
            "[PASS] testInputValidation",
            "[SKIP] testIntegrationSuite",
            "[PASS] testErrorHandling"
        };

        for (String test : tests) {
            Vector3f color = test.contains("[PASS]") ?
                new Vector3f(0.3f, 0.7f, 0.4f) :
                test.contains("[SKIP]") ?
                    new Vector3f(0.6f, 0.6f, 0.3f) :
                    new Vector3f(0.8f, 0.3f, 0.3f);

            glColor4f(color.x, color.y, color.z, panelProgress * 0.9f);
            renderLabel(x, currentY, test);
            currentY += lineHeight;
        }
    }

    private void renderDiffContent(float x, float y, float width, float height) {
        float lineHeight = 18;
        float currentY = y;

        glColor4f(0.6f, 0.6f, 0.6f, panelProgress);
        renderLabel(x, currentY, "src/components/Button.tsx");
        currentY += lineHeight * 1.5f;

        String[] diffLines = {
            "  import React from 'react';",
            "- import { OldStyle } from './styles';",
            "+ import { NewStyle } from './styles';",
            "  ",
            "  export const Button = () => {",
            "-   return <button className={OldStyle}>;",
            "+   return <button className={NewStyle}>;",
            "  };"
        };

        for (String line : diffLines) {
            Vector3f color;
            if (line.startsWith("+")) {
                color = new Vector3f(0.3f, 0.7f, 0.4f);
            } else if (line.startsWith("-")) {
                color = new Vector3f(0.8f, 0.3f, 0.3f);
            } else {
                color = new Vector3f(0.5f, 0.5f, 0.5f);
            }

            glColor4f(color.x, color.y, color.z, panelProgress * 0.9f);
            renderLabel(x, currentY, line);
            currentY += lineHeight;
        }
    }

    private void renderTokensContent(float x, float y, float width, float height) {
        float lineHeight = 25;
        float currentY = y;

        // Token usage breakdown
        renderInfoRow(x, currentY, "Session Total:", "12,450 tokens");
        currentY += lineHeight;
        renderInfoRow(x, currentY, "Input Tokens:", "8,230");
        currentY += lineHeight;
        renderInfoRow(x, currentY, "Output Tokens:", "4,220");
        currentY += lineHeight * 1.5f;

        // Budget info
        glColor4f(0.6f, 0.6f, 0.6f, panelProgress);
        renderLabel(x, currentY, "Budget:");
        currentY += lineHeight;

        // Budget bar
        float budgetUsed = 0.45f; // 45% used
        float barWidth = width - 20;
        float barHeight = 20;

        // Background
        glColor4f(0.2f, 0.2f, 0.25f, panelProgress);
        glBegin(GL_QUADS);
        glVertex2f(x, currentY);
        glVertex2f(x + barWidth, currentY);
        glVertex2f(x + barWidth, currentY + barHeight);
        glVertex2f(x, currentY + barHeight);
        glEnd();

        // Used portion
        Vector3f budgetColor = budgetUsed < 0.7f ?
            new Vector3f(0.3f, 0.7f, 0.4f) :
            budgetUsed < 0.9f ?
                new Vector3f(0.7f, 0.7f, 0.3f) :
                new Vector3f(0.8f, 0.3f, 0.3f);

        glColor4f(budgetColor.x, budgetColor.y, budgetColor.z, panelProgress);
        glBegin(GL_QUADS);
        glVertex2f(x, currentY);
        glVertex2f(x + barWidth * budgetUsed, currentY);
        glVertex2f(x + barWidth * budgetUsed, currentY + barHeight);
        glVertex2f(x, currentY + barHeight);
        glEnd();

        currentY += barHeight + 10;
        renderInfoRow(x, currentY, "Used:", "$4.50 / $10.00 (45%)");
    }

    private void renderInfoRow(float x, float y, String label, String value) {
        glColor4f(0.6f, 0.6f, 0.6f, panelProgress);
        renderLabel(x, y, label);
        glColor4f(0.9f, 0.9f, 0.9f, panelProgress);
        renderLabel(x + 120, y, value);
    }

    private void renderLabel(float x, float y, String text) {
        // Placeholder - actual text rendering would use font system
        // For now, just mark the text position
        glPointSize(1.0f);
        glBegin(GL_POINTS);
        glVertex2f(x, y);
        glEnd();
    }

    private Vector3f getStatusColor(AgentStatus status) {
        return switch (status) {
            case RUNNING -> new Vector3f(0.3f, 0.8f, 0.4f);
            case PAUSED -> new Vector3f(0.6f, 0.6f, 0.3f);
            case BLOCKED -> new Vector3f(0.9f, 0.6f, 0.2f);
            case ERROR -> new Vector3f(0.9f, 0.3f, 0.3f);
            case IDLE -> new Vector3f(0.4f, 0.4f, 0.5f);
            case COMPLETE -> new Vector3f(0.3f, 0.6f, 0.9f);
        };
    }

    private Vector3f getLogColor(String log) {
        if (log.contains("[ERROR]") || log.contains("[FAIL]")) {
            return new Vector3f(0.9f, 0.3f, 0.3f);
        } else if (log.contains("[WARN]")) {
            return new Vector3f(0.9f, 0.7f, 0.3f);
        } else if (log.contains("[SUCCESS]")) {
            return new Vector3f(0.3f, 0.8f, 0.4f);
        } else if (log.contains("[DEBUG]")) {
            return new Vector3f(0.5f, 0.5f, 0.6f);
        } else {
            return new Vector3f(0.7f, 0.7f, 0.7f);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Handle click in focus mode.
     */
    public boolean handleClick(float mouseX, float mouseY, int windowWidth, int windowHeight, Camera camera) {
        if (!active) return false;

        float slideOffset = (1.0f - panelProgress) * (panelWidth + panelMargin);
        float panelX = windowWidth - panelWidth - panelMargin + slideOffset;
        float panelY = panelMargin + 60;

        // Check close button
        float closeX = panelX + panelWidth - 30;
        float closeY = panelY + 20;
        if (mouseX >= closeX - 10 && mouseX <= closeX + 10 &&
            mouseY >= closeY - 10 && mouseY <= closeY + 10) {
            exit(camera);
            return true;
        }

        // Check tabs
        float tabY = panelY + 70;
        if (mouseY >= tabY && mouseY <= tabY + 35) {
            float tabWidth = panelWidth / FocusTab.values().length;
            int tabIndex = (int) ((mouseX - panelX) / tabWidth);
            if (tabIndex >= 0 && tabIndex < FocusTab.values().length) {
                currentTab = FocusTab.values()[tabIndex];
                logScrollOffset = 0;
                return true;
            }
        }

        // Check if click is within panel bounds
        if (mouseX >= panelX && mouseX <= panelX + panelWidth &&
            mouseY >= panelY && mouseY <= panelY + panelHeight) {
            return true; // Consume click
        }

        return false;
    }

    /**
     * Handle scroll in focus mode (for logs).
     */
    public void handleScroll(float yOffset) {
        if (!active || currentTab != FocusTab.LOGS) return;

        logScrollOffset = Math.max(0, Math.min(maxLogScroll,
            logScrollOffset - yOffset * 30));
    }

    /**
     * Handle keyboard input in focus mode.
     */
    public void handleKeyInput(int key, int action) {
        if (!active) return;

        // Tab switching with number keys
        if (action == 1) { // GLFW_PRESS
            if (key >= 49 && key <= 53) { // Keys 1-5
                int tabIndex = key - 49;
                if (tabIndex < FocusTab.values().length) {
                    currentTab = FocusTab.values()[tabIndex];
                    logScrollOffset = 0;
                }
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    public Agent getFocusedAgent() {
        return focusedAgent;
    }

    public int getFocusedCellIndex() {
        return focusedCellIndex;
    }

    public float getDimLevel() {
        return dimLevel;
    }

    public FocusTab getCurrentTab() {
        return currentTab;
    }

    public void setCurrentTab(FocusTab tab) {
        this.currentTab = tab;
        this.logScrollOffset = 0;
    }
}
