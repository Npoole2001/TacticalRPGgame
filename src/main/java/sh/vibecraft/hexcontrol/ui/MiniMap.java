package sh.vibecraft.hexcontrol.ui;

import org.joml.Vector3f;
import sh.vibecraft.hexcontrol.agent.AgentType;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;

import static org.lwjgl.opengl.GL11.*;

/**
 * Mini-map showing a 2D overview of the hex grid.
 * Per spec: shows tile occupancy, camera viewport, click to recenter.
 */
public class MiniMap {

    private boolean visible = true;
    private boolean collapsed = false;
    private float collapseProgress = 1.0f;
    private float animationSpeed = 8.0f;

    // Position and size (bottom-left corner)
    private float margin = 10;
    private float mapSize = 150;
    private float collapsedSize = 30;

    // Interaction
    private boolean hovered = false;
    private float hoveredX, hoveredY;

    // Grid bounds for mapping
    private float gridMinX, gridMaxX, gridMinZ, gridMaxZ;

    public MiniMap() {
    }

    public void update(float deltaTime, HexGrid hexGrid, Camera camera) {
        // Animate collapse
        float target = collapsed ? 0.0f : 1.0f;
        collapseProgress += (target - collapseProgress) * animationSpeed * deltaTime;

        // Calculate grid bounds
        updateGridBounds(hexGrid);
    }

    private void updateGridBounds(HexGrid hexGrid) {
        gridMinX = Float.MAX_VALUE;
        gridMaxX = Float.MIN_VALUE;
        gridMinZ = Float.MAX_VALUE;
        gridMaxZ = Float.MIN_VALUE;

        for (var cell : hexGrid.getCells()) {
            float x = cell.getWorldX();
            float z = cell.getWorldZ();

            gridMinX = Math.min(gridMinX, x);
            gridMaxX = Math.max(gridMaxX, x);
            gridMinZ = Math.min(gridMinZ, z);
            gridMaxZ = Math.max(gridMaxZ, z);
        }

        // Add padding
        float padding = 2.0f;
        gridMinX -= padding;
        gridMaxX += padding;
        gridMinZ -= padding;
        gridMaxZ += padding;
    }

    public void render(int windowWidth, int windowHeight, HexGrid hexGrid, Camera camera) {
        if (!visible) return;

        // Setup 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);

        float currentSize = collapsedSize + (mapSize - collapsedSize) * collapseProgress;
        float x = margin;
        float y = windowHeight - margin - currentSize;

        // Background
        glColor4f(0.05f, 0.05f, 0.08f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + currentSize, y);
        glVertex2f(x + currentSize, y + currentSize);
        glVertex2f(x, y + currentSize);
        glEnd();

        // Border
        glColor4f(0.3f, 0.3f, 0.35f, 1.0f);
        glLineWidth(1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + currentSize, y);
        glVertex2f(x + currentSize, y + currentSize);
        glVertex2f(x, y + currentSize);
        glEnd();

        // Only render content if not collapsed
        if (collapseProgress > 0.1f) {
            float alpha = collapseProgress;

            // Render hex cells
            renderHexCells(x, y, currentSize, hexGrid, alpha);

            // Render camera viewport indicator
            renderCameraViewport(x, y, currentSize, camera, alpha);
        }

        // Collapse/expand indicator
        float indicatorX = x + currentSize - 15;
        float indicatorY = y + 5;
        glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
        glBegin(GL_TRIANGLES);
        if (collapsed) {
            // Arrow pointing down-right (expand)
            glVertex2f(indicatorX, indicatorY);
            glVertex2f(indicatorX + 10, indicatorY + 5);
            glVertex2f(indicatorX, indicatorY + 10);
        } else {
            // Arrow pointing up-left (collapse)
            glVertex2f(indicatorX + 10, indicatorY);
            glVertex2f(indicatorX, indicatorY + 5);
            glVertex2f(indicatorX + 10, indicatorY + 10);
        }
        glEnd();

        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void renderHexCells(float mapX, float mapY, float mapSize, HexGrid hexGrid, float alpha) {
        float gridWidth = gridMaxX - gridMinX;
        float gridHeight = gridMaxZ - gridMinZ;
        float scale = Math.min(mapSize / gridWidth, mapSize / gridHeight) * 0.9f;

        float offsetX = mapX + mapSize / 2;
        float offsetY = mapY + mapSize / 2;

        float hexRadius = scale * 0.8f;

        for (int i = 0; i < hexGrid.getCells().size(); i++) {
            var cell = hexGrid.getCells().get(i);
            var agent = cell.getAgent();

            // Map world position to minimap position
            float worldX = cell.getWorldX();
            float worldZ = cell.getWorldZ();

            float screenX = offsetX + (worldX - (gridMinX + gridMaxX) / 2) * scale;
            float screenY = offsetY + (worldZ - (gridMinZ + gridMaxZ) / 2) * scale;

            // Get color based on agent status
            Vector3f color;
            if (agent == null || agent.getType() == AgentType.EMPTY) {
                color = new Vector3f(0.2f, 0.2f, 0.25f);  // Empty
            } else {
                color = agent.getColor();
            }

            // Selection highlight
            if (i == hexGrid.getSelectedIndex()) {
                glColor4f(1.0f, 1.0f, 1.0f, alpha * 0.8f);
                drawMiniHex(screenX, screenY, hexRadius * 1.3f);
            }

            // Draw hex
            glColor4f(color.x, color.y, color.z, alpha * 0.9f);
            drawMiniHex(screenX, screenY, hexRadius);

            // Status indicator (small dot for running agents)
            if (agent != null && agent.getType() != AgentType.EMPTY) {
                Vector3f statusColor = agent.getStatusColor();
                glColor4f(statusColor.x, statusColor.y, statusColor.z, alpha);

                float dotSize = 2.0f;
                glBegin(GL_QUADS);
                glVertex2f(screenX - dotSize, screenY - dotSize);
                glVertex2f(screenX + dotSize, screenY - dotSize);
                glVertex2f(screenX + dotSize, screenY + dotSize);
                glVertex2f(screenX - dotSize, screenY + dotSize);
                glEnd();
            }
        }
    }

    private void drawMiniHex(float cx, float cy, float radius) {
        glBegin(GL_POLYGON);
        for (int i = 0; i < 6; i++) {
            float angle = (float)(Math.PI / 3.0 * i);
            glVertex2f(cx + (float)Math.cos(angle) * radius,
                       cy + (float)Math.sin(angle) * radius);
        }
        glEnd();
    }

    private void renderCameraViewport(float mapX, float mapY, float mapSize, Camera camera, float alpha) {
        float gridWidth = gridMaxX - gridMinX;
        float gridHeight = gridMaxZ - gridMinZ;
        float scale = Math.min(mapSize / gridWidth, mapSize / gridHeight) * 0.9f;

        float offsetX = mapX + mapSize / 2;
        float offsetY = mapY + mapSize / 2;

        // Camera target position
        Vector3f target = camera.getTarget();
        float camX = offsetX + (target.x - (gridMinX + gridMaxX) / 2) * scale;
        float camY = offsetY + (target.z - (gridMinZ + gridMaxZ) / 2) * scale;

        // Viewport indicator (based on zoom level)
        float viewSize = 30.0f / camera.getDistance() * mapSize;
        viewSize = Math.max(15, Math.min(viewSize, mapSize * 0.8f));

        // Draw viewport rectangle
        glColor4f(1.0f, 1.0f, 1.0f, alpha * 0.3f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(camX - viewSize / 2, camY - viewSize / 2);
        glVertex2f(camX + viewSize / 2, camY - viewSize / 2);
        glVertex2f(camX + viewSize / 2, camY + viewSize / 2);
        glVertex2f(camX - viewSize / 2, camY + viewSize / 2);
        glEnd();

        // Camera direction indicator (arrow)
        float yaw = camera.getYaw();
        float arrowLength = 8.0f;
        float arrowDirX = (float)Math.sin(Math.toRadians(yaw));
        float arrowDirY = (float)Math.cos(Math.toRadians(yaw));

        glColor4f(1.0f, 1.0f, 1.0f, alpha * 0.7f);
        glLineWidth(2.0f);
        glBegin(GL_LINES);
        glVertex2f(camX, camY);
        glVertex2f(camX + arrowDirX * arrowLength, camY + arrowDirY * arrowLength);
        glEnd();

        // Center dot
        glColor4f(1.0f, 1.0f, 1.0f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(camX - 2, camY - 2);
        glVertex2f(camX + 2, camY - 2);
        glVertex2f(camX + 2, camY + 2);
        glVertex2f(camX - 2, camY + 2);
        glEnd();
    }

    /**
     * Handle click on minimap. Returns true if click was handled.
     */
    public boolean handleClick(float mouseX, float mouseY, int windowWidth, int windowHeight,
                                Camera camera) {
        if (!visible) return false;

        float currentSize = collapsedSize + (mapSize - collapsedSize) * collapseProgress;
        float x = margin;
        float y = windowHeight - margin - currentSize;

        // Check if click is in minimap area
        if (mouseX < x || mouseX > x + currentSize ||
            mouseY < y || mouseY > y + currentSize) {
            return false;
        }

        // Check collapse toggle area
        float toggleX = x + currentSize - 20;
        float toggleY = y;
        if (mouseX >= toggleX && mouseY <= y + 20) {
            collapsed = !collapsed;
            return true;
        }

        // If collapsed, don't handle content clicks
        if (collapsed || collapseProgress < 0.5f) {
            return true;
        }

        // Map click to world position and focus camera
        float gridWidth = gridMaxX - gridMinX;
        float gridHeight = gridMaxZ - gridMinZ;
        float scale = Math.min(currentSize / gridWidth, currentSize / gridHeight) * 0.9f;

        float offsetX = x + currentSize / 2;
        float offsetY = y + currentSize / 2;

        float worldX = (mouseX - offsetX) / scale + (gridMinX + gridMaxX) / 2;
        float worldZ = (mouseY - offsetY) / scale + (gridMinZ + gridMaxZ) / 2;

        camera.focusOn(worldX, worldZ);
        System.out.println("MiniMap: Focusing camera on (" + worldX + ", " + worldZ + ")");

        return true;
    }

    public void updateHover(float mouseX, float mouseY, int windowWidth, int windowHeight) {
        float currentSize = collapsedSize + (mapSize - collapsedSize) * collapseProgress;
        float x = margin;
        float y = windowHeight - margin - currentSize;

        hovered = mouseX >= x && mouseX <= x + currentSize &&
                  mouseY >= y && mouseY <= y + currentSize;

        if (hovered) {
            hoveredX = mouseX;
            hoveredY = mouseY;
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggle() {
        this.visible = !this.visible;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public float getMapSize() {
        return mapSize;
    }

    public void setMapSize(float size) {
        this.mapSize = size;
    }
}
