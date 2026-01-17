package sh.vibecraft.hexcontrol.input;

import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;
import sh.vibecraft.hexcontrol.ui.MenuSystem;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles all input events: mouse clicks, camera panning, and keyboard.
 */
public class InputHandler {

    private long window;
    private Camera camera;
    private HexGrid hexGrid;
    private MenuSystem menuSystem;

    // Window dimensions (updated via callback)
    private int windowWidth = 1280;
    private int windowHeight = 720;

    // Mouse state
    private double mouseX, mouseY;
    private double lastMouseX, lastMouseY;
    private boolean middleButtonDown = false;
    private boolean leftButtonDown = false;
    private boolean isDragging = false;

    // Drag threshold to distinguish click from drag
    private static final float DRAG_THRESHOLD = 5.0f;
    private double dragStartX, dragStartY;

    public InputHandler(long window, Camera camera, HexGrid hexGrid, MenuSystem menuSystem) {
        this.window = window;
        this.camera = camera;
        this.hexGrid = hexGrid;
        this.menuSystem = menuSystem;

        setupCallbacks();
    }

    private void setupCallbacks() {
        // Mouse position callback
        glfwSetCursorPosCallback(window, (win, x, y) -> {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            mouseX = x;
            mouseY = y;

            // Check for drag
            if (leftButtonDown || middleButtonDown) {
                double dx = x - dragStartX;
                double dy = y - dragStartY;
                if (Math.sqrt(dx * dx + dy * dy) > DRAG_THRESHOLD) {
                    isDragging = true;
                }
            }

            // Update hover
            updateHover();
        });

        // Mouse button callback
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
                middleButtonDown = (action == GLFW_PRESS);
                if (action == GLFW_PRESS) {
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    isDragging = false;
                }
            }

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    leftButtonDown = true;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    isDragging = false;
                } else if (action == GLFW_RELEASE) {
                    if (!isDragging) {
                        handleLeftClick();
                    }
                    leftButtonDown = false;
                    isDragging = false;
                }
            }

            if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                handleRightClick();
            }
        });

        // Scroll callback for zoom
        glfwSetScrollCallback(window, (win, xOffset, yOffset) -> {
            camera.zoom((float) yOffset);
        });

        // Keyboard callback
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                if (menuSystem.isMenuOpen()) {
                    menuSystem.closeMenu();
                } else {
                    glfwSetWindowShouldClose(window, true);
                }
            }

            // Number keys 1-9 to select hexes directly
            if (action == GLFW_PRESS && key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                int index = key - GLFW_KEY_1;
                if (index < hexGrid.getCells().size()) {
                    hexGrid.setSelected(index);
                    var cell = hexGrid.getCell(index);
                    if (cell != null) {
                        menuSystem.openMenu(cell.getAgent(), windowWidth, windowHeight);
                    }
                }
            }
        });

        // Window size callback
        glfwSetWindowSizeCallback(window, (win, width, height) -> {
            windowWidth = width;
            windowHeight = height;
        });
    }

    public void update(float deltaTime) {
        // Handle middle-mouse panning
        if (middleButtonDown && isDragging) {
            float dx = (float) (mouseX - lastMouseX);
            float dy = (float) (mouseY - lastMouseY);

            // Convert screen movement to world panning
            // Negative because dragging right should move camera left (view moves right)
            camera.pan(-dx * 0.01f, -dy * 0.01f);
        }
    }

    private void updateHover() {
        if (menuSystem.isMenuOpen()) {
            hexGrid.setHovered(-1);
            return;
        }

        Vector3f worldPos = camera.screenToWorld((float) mouseX, (float) mouseY, windowWidth, windowHeight);
        if (worldPos != null) {
            int hexIndex = hexGrid.getHexAt(worldPos.x, worldPos.z);
            hexGrid.setHovered(hexIndex);
        } else {
            hexGrid.setHovered(-1);
        }
    }

    private void handleLeftClick() {
        // Check if clicking on menu first
        if (menuSystem.isMenuOpen()) {
            if (menuSystem.handleClick((float) mouseX, (float) mouseY)) {
                return; // Click was handled by menu
            }
            // Click outside menu - close it
            menuSystem.closeMenu();
            hexGrid.setSelected(-1);
            return;
        }

        // Check for hex click
        Vector3f worldPos = camera.screenToWorld((float) mouseX, (float) mouseY, windowWidth, windowHeight);
        if (worldPos != null) {
            int hexIndex = hexGrid.getHexAt(worldPos.x, worldPos.z);
            if (hexIndex >= 0) {
                hexGrid.setSelected(hexIndex);
                var cell = hexGrid.getCell(hexIndex);
                if (cell != null) {
                    System.out.println("Selected: " + cell.getAgent());
                    menuSystem.openMenu(cell.getAgent(), windowWidth, windowHeight);
                }
            } else {
                hexGrid.setSelected(-1);
            }
        }
    }

    private void handleRightClick() {
        if (menuSystem.isMenuOpen()) {
            menuSystem.closeMenu();
            hexGrid.setSelected(-1);
        }
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }
}
