package sh.vibecraft.hexcontrol.ui;

import org.lwjgl.nanovg.NVGColor;
import sh.vibecraft.hexcontrol.agent.AIAgent;
import sh.vibecraft.hexcontrol.agent.AgentType;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu system for interacting with AI agents.
 * Displays when a hex is clicked.
 */
public class MenuSystem {

    private boolean menuOpen = false;
    private AIAgent currentAgent = null;

    // Menu positioning
    private float menuX, menuY;
    private float menuWidth = 300;
    private float menuHeight = 400;

    // Menu items
    private List<MenuItem> menuItems;
    private int hoveredItem = -1;

    // Animation
    private float openProgress = 0.0f;
    private float targetProgress = 0.0f;
    private float animationSpeed = 8.0f;

    public MenuSystem() {
        menuItems = new ArrayList<>();
    }

    public void openMenu(AIAgent agent, int windowWidth, int windowHeight) {
        this.currentAgent = agent;
        this.menuOpen = true;
        this.targetProgress = 1.0f;

        // Center the menu
        menuX = (windowWidth - menuWidth) / 2.0f;
        menuY = (windowHeight - menuHeight) / 2.0f;

        // Build menu items
        buildMenuItems();

        System.out.println("Opening menu for: " + agent.getName());
    }

    private void buildMenuItems() {
        menuItems.clear();

        if (currentAgent == null) return;

        // Header shows agent info
        menuItems.add(new MenuItem("Open Chat", MenuItemType.BUTTON, () -> {
            System.out.println("Opening chat with: " + currentAgent.getName());
            // TODO: Open chat window
        }));

        menuItems.add(new MenuItem("Configure", MenuItemType.BUTTON, () -> {
            System.out.println("Configure: " + currentAgent.getName());
            // TODO: Open config dialog
        }));

        menuItems.add(new MenuItem("Change Type", MenuItemType.SUBMENU, null));

        menuItems.add(new MenuItem("Rename", MenuItemType.BUTTON, () -> {
            System.out.println("Rename: " + currentAgent.getName());
            // TODO: Open rename dialog
        }));

        menuItems.add(new MenuItem("View History", MenuItemType.BUTTON, () -> {
            System.out.println("View history: " + currentAgent.getName());
            // TODO: Show history
        }));

        menuItems.add(new MenuItem("Clear Session", MenuItemType.BUTTON, () -> {
            System.out.println("Clear session: " + currentAgent.getName());
            currentAgent.setLastMessage(null);
        }));

        menuItems.add(new MenuItem("Remove Agent", MenuItemType.DANGER, () -> {
            System.out.println("Remove agent: " + currentAgent.getName());
            currentAgent.setType(AgentType.EMPTY);
            closeMenu();
        }));
    }

    public void closeMenu() {
        targetProgress = 0.0f;
        hoveredItem = -1;
    }

    public boolean isMenuOpen() {
        return menuOpen || openProgress > 0.01f;
    }

    public void update(float deltaTime) {
        // Animate menu open/close
        openProgress += (targetProgress - openProgress) * animationSpeed * deltaTime;

        if (openProgress < 0.01f && targetProgress == 0.0f) {
            menuOpen = false;
            currentAgent = null;
        }
    }

    public void render(int windowWidth, int windowHeight) {
        if (!isMenuOpen()) return;

        // Use immediate mode for simple UI (will upgrade to NanoVG later)
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);

        float alpha = openProgress;
        float scale = 0.8f + 0.2f * openProgress;

        float centerX = menuX + menuWidth / 2;
        float centerY = menuY + menuHeight / 2;

        float drawX = centerX - (menuWidth * scale) / 2;
        float drawY = centerY - (menuHeight * scale) / 2;
        float drawW = menuWidth * scale;
        float drawH = menuHeight * scale;

        // Draw semi-transparent overlay
        glColor4f(0.0f, 0.0f, 0.0f, 0.5f * alpha);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(windowWidth, 0);
        glVertex2f(windowWidth, windowHeight);
        glVertex2f(0, windowHeight);
        glEnd();

        // Draw menu background
        glColor4f(0.15f, 0.15f, 0.2f, 0.95f * alpha);
        glBegin(GL_QUADS);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + drawH);
        glVertex2f(drawX, drawY + drawH);
        glEnd();

        // Draw menu border
        glColor4f(0.4f, 0.4f, 0.5f, alpha);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + drawH);
        glVertex2f(drawX, drawY + drawH);
        glEnd();

        // Draw agent color indicator at top
        if (currentAgent != null) {
            var color = currentAgent.getColor();
            glColor4f(color.x, color.y, color.z, alpha);
            glBegin(GL_QUADS);
            glVertex2f(drawX, drawY);
            glVertex2f(drawX + drawW, drawY);
            glVertex2f(drawX + drawW, drawY + 8);
            glVertex2f(drawX, drawY + 8);
            glEnd();
        }

        // Draw menu items
        float itemHeight = 40;
        float itemY = drawY + 60; // Leave space for header
        float itemPadding = 10;

        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem item = menuItems.get(i);
            float iy = itemY + i * itemHeight;

            // Item background (highlight on hover)
            if (i == hoveredItem) {
                glColor4f(0.3f, 0.3f, 0.4f, 0.8f * alpha);
            } else {
                glColor4f(0.2f, 0.2f, 0.25f, 0.6f * alpha);
            }
            glBegin(GL_QUADS);
            glVertex2f(drawX + itemPadding, iy);
            glVertex2f(drawX + drawW - itemPadding, iy);
            glVertex2f(drawX + drawW - itemPadding, iy + itemHeight - 4);
            glVertex2f(drawX + itemPadding, iy + itemHeight - 4);
            glEnd();

            // Danger items get red tint
            if (item.type == MenuItemType.DANGER) {
                glColor4f(0.5f, 0.2f, 0.2f, 0.3f * alpha);
                glBegin(GL_QUADS);
                glVertex2f(drawX + itemPadding, iy);
                glVertex2f(drawX + drawW - itemPadding, iy);
                glVertex2f(drawX + drawW - itemPadding, iy + itemHeight - 4);
                glVertex2f(drawX + itemPadding, iy + itemHeight - 4);
                glEnd();
            }
        }

        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    public boolean handleClick(float x, float y) {
        if (!menuOpen) return false;

        // Check if click is within menu bounds
        if (x < menuX || x > menuX + menuWidth ||
            y < menuY || y > menuY + menuHeight) {
            return false;
        }

        // Find clicked item
        float itemHeight = 40;
        float itemY = menuY + 60;

        for (int i = 0; i < menuItems.size(); i++) {
            float iy = itemY + i * itemHeight;
            if (y >= iy && y < iy + itemHeight - 4) {
                MenuItem item = menuItems.get(i);
                if (item.action != null) {
                    item.action.run();
                }
                return true;
            }
        }

        return true; // Click was in menu area
    }

    public void updateHover(float x, float y) {
        if (!menuOpen) {
            hoveredItem = -1;
            return;
        }

        if (x < menuX || x > menuX + menuWidth ||
            y < menuY || y > menuY + menuHeight) {
            hoveredItem = -1;
            return;
        }

        float itemHeight = 40;
        float itemY = menuY + 60;

        hoveredItem = -1;
        for (int i = 0; i < menuItems.size(); i++) {
            float iy = itemY + i * itemHeight;
            if (y >= iy && y < iy + itemHeight - 4) {
                hoveredItem = i;
                break;
            }
        }
    }

    public void cleanup() {
        // Nothing to cleanup for now
    }

    /**
     * Menu item definition.
     */
    private static class MenuItem {
        String label;
        MenuItemType type;
        Runnable action;

        MenuItem(String label, MenuItemType type, Runnable action) {
            this.label = label;
            this.type = type;
            this.action = action;
        }
    }

    private enum MenuItemType {
        BUTTON,
        SUBMENU,
        DANGER
    }
}
