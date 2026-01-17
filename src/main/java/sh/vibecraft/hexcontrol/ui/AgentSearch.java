package sh.vibecraft.hexcontrol.ui;

import org.joml.Vector3f;
import sh.vibecraft.hexcontrol.agent.*;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Agent search panel for quick navigation.
 * Per spec: search by name, role, status, tag, task keyword.
 */
public class AgentSearch {

    private boolean visible = false;
    private float openProgress = 0.0f;
    private float animationSpeed = 12.0f;

    // Search state
    private StringBuilder searchQuery = new StringBuilder();
    private List<SearchResult> results = new ArrayList<>();
    private int selectedIndex = 0;
    private int hoveredIndex = -1;

    // Panel dimensions
    private float panelWidth = 400;
    private float panelHeight = 350;
    private float panelX, panelY;

    // Reference to grid for searching
    private HexGrid hexGrid;
    private Camera camera;

    // Callback for selection
    private SearchCallback callback;

    public AgentSearch() {
    }

    public void open(HexGrid hexGrid, Camera camera) {
        this.hexGrid = hexGrid;
        this.camera = camera;
        this.visible = true;
        this.searchQuery.setLength(0);
        this.selectedIndex = 0;
        updateResults();
    }

    public void close() {
        this.visible = false;
        this.searchQuery.setLength(0);
        this.results.clear();
    }

    public void toggle(HexGrid hexGrid, Camera camera) {
        if (visible) close();
        else open(hexGrid, camera);
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
        if (!isVisible()) return;

        // Center top of screen
        panelX = (windowWidth - panelWidth) / 2.0f;
        panelY = 50;

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
        float slideY = (1.0f - openProgress) * -50;

        float drawX = panelX;
        float drawY = panelY + slideY;
        float drawW = panelWidth;
        float drawH = panelHeight * Math.min(1.0f, 0.3f + results.size() * 0.1f);
        drawH = Math.max(100, Math.min(drawH, panelHeight));

        // Panel background
        glColor4f(0.08f, 0.08f, 0.1f, 0.95f * alpha);
        glBegin(GL_QUADS);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + drawH);
        glVertex2f(drawX, drawY + drawH);
        glEnd();

        // Border
        glColor4f(0.3f, 0.4f, 0.6f, alpha);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + drawH);
        glVertex2f(drawX, drawY + drawH);
        glEnd();

        // Search input field
        float inputX = drawX + 10;
        float inputY = drawY + 10;
        float inputW = drawW - 20;
        float inputH = 35;

        // Input background
        glColor4f(0.05f, 0.05f, 0.07f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(inputX, inputY);
        glVertex2f(inputX + inputW, inputY);
        glVertex2f(inputX + inputW, inputY + inputH);
        glVertex2f(inputX, inputY + inputH);
        glEnd();

        // Input border
        glColor4f(0.4f, 0.5f, 0.7f, alpha);
        glBegin(GL_LINE_LOOP);
        glVertex2f(inputX, inputY);
        glVertex2f(inputX + inputW, inputY);
        glVertex2f(inputX + inputW, inputY + inputH);
        glVertex2f(inputX, inputY + inputH);
        glEnd();

        // Search icon (magnifying glass)
        float iconX = inputX + 10;
        float iconY = inputY + inputH / 2;
        glColor4f(0.5f, 0.5f, 0.6f, alpha);
        glLineWidth(2.0f);

        // Circle
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 16; i++) {
            float angle = (float)(Math.PI * 2 * i / 16);
            glVertex2f(iconX + (float)Math.cos(angle) * 6,
                       iconY + (float)Math.sin(angle) * 6);
        }
        glEnd();

        // Handle
        glBegin(GL_LINES);
        glVertex2f(iconX + 4, iconY + 4);
        glVertex2f(iconX + 9, iconY + 9);
        glEnd();

        // Cursor blink (simple)
        float cursorX = inputX + 30 + searchQuery.length() * 8;  // Approximate character width
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            glColor4f(0.8f, 0.8f, 0.8f, alpha);
            glBegin(GL_LINES);
            glVertex2f(cursorX, inputY + 8);
            glVertex2f(cursorX, inputY + inputH - 8);
            glEnd();
        }

        // Results list
        float resultY = inputY + inputH + 10;
        float resultHeight = 50;

        if (results.isEmpty() && searchQuery.length() > 0) {
            // No results message
            glColor4f(0.5f, 0.5f, 0.5f, alpha);
            // Note: actual text rendering would need NanoVG
        } else {
            for (int i = 0; i < results.size() && resultY < drawY + drawH - 10; i++) {
                SearchResult result = results.get(i);

                // Highlight selected/hovered
                boolean selected = (i == selectedIndex);
                boolean hovered = (i == hoveredIndex);

                if (selected) {
                    glColor4f(0.2f, 0.25f, 0.35f, alpha);
                } else if (hovered) {
                    glColor4f(0.15f, 0.15f, 0.2f, alpha);
                } else {
                    glColor4f(0.1f, 0.1f, 0.12f, alpha);
                }

                glBegin(GL_QUADS);
                glVertex2f(inputX, resultY);
                glVertex2f(inputX + inputW, resultY);
                glVertex2f(inputX + inputW, resultY + resultHeight - 5);
                glVertex2f(inputX, resultY + resultHeight - 5);
                glEnd();

                // Agent color indicator
                Vector3f color = result.agent.getColor();
                glColor4f(color.x, color.y, color.z, alpha);
                glBegin(GL_QUADS);
                glVertex2f(inputX, resultY);
                glVertex2f(inputX + 4, resultY);
                glVertex2f(inputX + 4, resultY + resultHeight - 5);
                glVertex2f(inputX, resultY + resultHeight - 5);
                glEnd();

                // Status indicator
                Vector3f statusColor = result.agent.getStatusColor();
                glColor4f(statusColor.x, statusColor.y, statusColor.z, alpha);
                float statusX = inputX + inputW - 20;
                float statusY = resultY + 10;
                glBegin(GL_QUADS);
                glVertex2f(statusX, statusY);
                glVertex2f(statusX + 10, statusY);
                glVertex2f(statusX + 10, statusY + 10);
                glVertex2f(statusX, statusY + 10);
                glEnd();

                // Role badge background
                Vector3f roleColor = result.agent.getRole().getAccentColor();
                glColor4f(roleColor.x * 0.5f, roleColor.y * 0.5f, roleColor.z * 0.5f, alpha * 0.5f);
                float badgeX = inputX + inputW - 80;
                float badgeY = resultY + 25;
                float badgeW = 55;
                float badgeH = 16;
                glBegin(GL_QUADS);
                glVertex2f(badgeX, badgeY);
                glVertex2f(badgeX + badgeW, badgeY);
                glVertex2f(badgeX + badgeW, badgeY + badgeH);
                glVertex2f(badgeX, badgeY + badgeH);
                glEnd();

                resultY += resultHeight;
            }
        }

        // Keyboard hints at bottom
        float hintY = drawY + drawH - 25;
        glColor4f(0.4f, 0.4f, 0.45f, alpha);
        // Hints: "Enter: Select | Esc: Close | Up/Down: Navigate"

        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    public void handleKeyInput(int key, int action) {
        if (!visible) return;

        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS || action == org.lwjgl.glfw.GLFW.GLFW_REPEAT) {
            switch (key) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> close();
                case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER -> selectCurrent();
                case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                    selectedIndex = Math.max(0, selectedIndex - 1);
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                    selectedIndex = Math.min(results.size() - 1, selectedIndex + 1);
                }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchQuery.length() > 0) {
                        searchQuery.deleteCharAt(searchQuery.length() - 1);
                        updateResults();
                    }
                }
            }
        }
    }

    public void handleCharInput(char c) {
        if (!visible) return;

        if (!Character.isISOControl(c)) {
            searchQuery.append(c);
            updateResults();
        }
    }

    public boolean handleClick(float mouseX, float mouseY) {
        if (!isVisible()) return false;

        float slideY = (1.0f - openProgress) * -50;
        float drawX = panelX;
        float drawY = panelY + slideY;
        float drawW = panelWidth;
        float drawH = panelHeight * Math.min(1.0f, 0.3f + results.size() * 0.1f);
        drawH = Math.max(100, Math.min(drawH, panelHeight));

        // Check if click is outside panel
        if (mouseX < drawX || mouseX > drawX + drawW ||
            mouseY < drawY || mouseY > drawY + drawH) {
            close();
            return true;
        }

        // Check result clicks
        float inputH = 35;
        float resultStartY = drawY + 10 + inputH + 10;
        float resultHeight = 50;

        if (mouseY >= resultStartY) {
            int clickedIndex = (int)((mouseY - resultStartY) / resultHeight);
            if (clickedIndex >= 0 && clickedIndex < results.size()) {
                selectedIndex = clickedIndex;
                selectCurrent();
                return true;
            }
        }

        return true;
    }

    public void updateHover(float mouseX, float mouseY) {
        if (!isVisible()) {
            hoveredIndex = -1;
            return;
        }

        float slideY = (1.0f - openProgress) * -50;
        float drawX = panelX;
        float drawY = panelY + slideY;

        float inputH = 35;
        float resultStartY = drawY + 10 + inputH + 10;
        float resultHeight = 50;

        if (mouseY >= resultStartY && mouseX >= drawX && mouseX <= drawX + panelWidth) {
            hoveredIndex = (int)((mouseY - resultStartY) / resultHeight);
            if (hoveredIndex >= results.size()) {
                hoveredIndex = -1;
            }
        } else {
            hoveredIndex = -1;
        }
    }

    private void updateResults() {
        results.clear();
        selectedIndex = 0;

        if (hexGrid == null) return;

        String query = searchQuery.toString().toLowerCase().trim();

        for (int i = 0; i < hexGrid.getCells().size(); i++) {
            var cell = hexGrid.getCells().get(i);
            AIAgent agent = cell.getAgent();

            if (agent == null || agent.getType() == AgentType.EMPTY) continue;

            // Calculate match score
            int score = calculateMatchScore(agent, query);
            if (score > 0 || query.isEmpty()) {
                results.add(new SearchResult(agent, i, score));
            }
        }

        // Sort by score (highest first), then by name
        results.sort((a, b) -> {
            if (a.score != b.score) return Integer.compare(b.score, a.score);
            return a.agent.getName().compareToIgnoreCase(b.agent.getName());
        });

        // Limit results
        if (results.size() > 8) {
            results = new ArrayList<>(results.subList(0, 8));
        }
    }

    private int calculateMatchScore(AIAgent agent, String query) {
        if (query.isEmpty()) return 1;  // Show all agents when no query

        int score = 0;

        // Name match (highest priority)
        String name = agent.getName().toLowerCase();
        if (name.equals(query)) {
            score += 100;
        } else if (name.startsWith(query)) {
            score += 50;
        } else if (name.contains(query)) {
            score += 25;
        }

        // Type match
        String type = agent.getType().getDisplayName().toLowerCase();
        if (type.contains(query)) {
            score += 20;
        }

        // Role match
        String role = agent.getRole().getDisplayName().toLowerCase();
        String roleShort = agent.getRole().getShortCode().toLowerCase();
        if (role.contains(query) || roleShort.equals(query)) {
            score += 20;
        }

        // Status match
        String status = agent.getStatus().getDisplayName().toLowerCase();
        if (status.equals(query) || status.startsWith(query)) {
            score += 15;
        }

        // Task match
        String task = agent.getCurrentTask();
        if (task != null && task.toLowerCase().contains(query)) {
            score += 10;
        }

        // Objective match
        String objective = agent.getCurrentObjective();
        if (objective != null && objective.toLowerCase().contains(query)) {
            score += 10;
        }

        // Tag match
        for (String tag : agent.getTags()) {
            if (tag.toLowerCase().contains(query)) {
                score += 15;
                break;
            }
        }

        return score;
    }

    private void selectCurrent() {
        if (results.isEmpty() || selectedIndex < 0 || selectedIndex >= results.size()) {
            return;
        }

        SearchResult result = results.get(selectedIndex);

        // Focus camera on the agent
        var cell = hexGrid.getCells().get(result.cellIndex);
        camera.focusOn(cell.getWorldX(), cell.getWorldZ());

        // Select the hex
        hexGrid.setSelected(result.cellIndex);

        System.out.println("AgentSearch: Selected " + result.agent.getName());

        // Notify callback
        if (callback != null) {
            callback.onAgentSelected(result.agent, result.cellIndex);
        }

        close();
    }

    public void setCallback(SearchCallback callback) {
        this.callback = callback;
    }

    public String getQuery() {
        return searchQuery.toString();
    }

    /**
     * Search result entry.
     */
    private static class SearchResult {
        final AIAgent agent;
        final int cellIndex;
        final int score;

        SearchResult(AIAgent agent, int cellIndex, int score) {
            this.agent = agent;
            this.cellIndex = cellIndex;
            this.score = score;
        }
    }

    /**
     * Callback for agent selection.
     */
    public interface SearchCallback {
        void onAgentSelected(AIAgent agent, int cellIndex);
    }
}
