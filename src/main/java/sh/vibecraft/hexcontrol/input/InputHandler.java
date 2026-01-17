package sh.vibecraft.hexcontrol.input;

import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;
import sh.vibecraft.hexcontrol.ui.MenuSystem;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles all input events per spec:
 * - MMB drag: Rotate camera
 * - RMB drag or WASD: Pan camera
 * - Scroll: Zoom
 * - Left click: Select/interact with tiles
 */
public class InputHandler {

    private long window;
    private Camera camera;
    private HexGrid hexGrid;
    private MenuSystem menuSystem;

    // Window dimensions
    private int windowWidth = 1280;
    private int windowHeight = 720;

    // Mouse state
    private double mouseX, mouseY;
    private double lastMouseX, lastMouseY;
    private boolean middleButtonDown = false;
    private boolean rightButtonDown = false;
    private boolean leftButtonDown = false;
    private boolean isDragging = false;

    // Drag threshold
    private static final float DRAG_THRESHOLD = 5.0f;
    private double dragStartX, dragStartY;

    // Keyboard state for WASD
    private boolean keyW = false, keyA = false, keyS = false, keyD = false;

    // Rotation sensitivity
    private float rotationSensitivity = 0.3f;
    private float panSensitivity = 0.05f;
    private float keyboardPanSpeed = 15.0f;

    public InputHandler(long window, Camera camera, HexGrid hexGrid, MenuSystem menuSystem) {
        this.window = window;
        this.camera = camera;
        this.hexGrid = hexGrid;
        this.menuSystem = menuSystem;

        setupCallbacks();
    }

    private void setupCallbacks() {
        // Note: Most callbacks are now set up by Engine.setupExtendedInputCallbacks()
        // to properly integrate with UI components. This method only sets up
        // callbacks that are specific to InputHandler.

        // Window size callback
        glfwSetWindowSizeCallback(window, (win, width, height) -> {
            windowWidth = width;
            windowHeight = height;
        });
    }

    public void update(float deltaTime) {
        // Handle middle mouse rotation
        if (middleButtonDown && isDragging) {
            float dx = (float) (mouseX - lastMouseX);
            float dy = (float) (mouseY - lastMouseY);

            // Rotate camera - horizontal mouse movement = yaw, vertical = pitch
            camera.rotate(-dx * rotationSensitivity, dy * rotationSensitivity);
        }

        // Handle right mouse panning
        if (rightButtonDown && isDragging) {
            float dx = (float) (mouseX - lastMouseX);
            float dy = (float) (mouseY - lastMouseY);

            // Pan camera - screen movement translates to world pan
            camera.pan(-dx * panSensitivity, -dy * panSensitivity);
        }

        // Handle WASD panning
        float panX = 0, panZ = 0;
        if (keyW) panZ -= keyboardPanSpeed * deltaTime;
        if (keyS) panZ += keyboardPanSpeed * deltaTime;
        if (keyA) panX -= keyboardPanSpeed * deltaTime;
        if (keyD) panX += keyboardPanSpeed * deltaTime;

        if (panX != 0 || panZ != 0) {
            camera.pan(panX, panZ);
        }
    }

    private void updateHover() {
        if (menuSystem.isMenuOpen()) {
            hexGrid.setHovered(-1);
            return;
        }

        Vector3f worldPos = camera.screenToWorld((float) mouseX, (float) mouseY);
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
        Vector3f worldPos = camera.screenToWorld((float) mouseX, (float) mouseY);
        if (worldPos != null) {
            int hexIndex = hexGrid.getHexAt(worldPos.x, worldPos.z);
            if (hexIndex >= 0) {
                hexGrid.setSelected(hexIndex);
                var cell = hexGrid.getCell(hexIndex);
                if (cell != null) {
                    System.out.println("Selected: " + cell.getAgent());
                    var agent = cell.getAgent();
                    if (agent != null && agent.getType() != sh.vibecraft.hexcontrol.agent.AgentType.EMPTY) {
                        menuSystem.openMenu(agent, windowWidth, windowHeight);
                    } else {
                        menuSystem.closeMenu();
                    }
                }
            } else {
                hexGrid.setSelected(-1);
            }
        }
    }

    public double getMouseX() { return mouseX; }
    public double getMouseY() { return mouseY; }
    public int getWindowWidth() { return windowWidth; }
    public int getWindowHeight() { return windowHeight; }

    /**
     * Handle key callback from external source.
     */
    public void keyCallback(int key, int scancode, int action, int mods) {
        // ESC handling
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
            if (menuSystem.isMenuOpen()) {
                menuSystem.closeMenu();
                hexGrid.setSelected(-1);
            }
        }

        // WASD for panning
        if (key == GLFW_KEY_W) keyW = (action != GLFW_RELEASE);
        if (key == GLFW_KEY_A) keyA = (action != GLFW_RELEASE);
        if (key == GLFW_KEY_S) keyS = (action != GLFW_RELEASE);
        if (key == GLFW_KEY_D) keyD = (action != GLFW_RELEASE);

        // Number keys 1-9 to select hexes directly
        if (action == GLFW_PRESS && key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
            int index = key - GLFW_KEY_1;
            if (index < hexGrid.getCells().size()) {
                hexGrid.setSelected(index);
                var cell = hexGrid.getCell(index);
                if (cell != null) {
                    // Focus camera on selected hex
                    camera.focusOn(cell.getWorldX(), cell.getWorldZ());
                    var agent = cell.getAgent();
                    if (agent != null && agent.getType() != sh.vibecraft.hexcontrol.agent.AgentType.EMPTY) {
                        menuSystem.openMenu(agent, windowWidth, windowHeight);
                    } else {
                        menuSystem.closeMenu();
                    }
                }
            }
        }
    }

    /**
     * Handle mouse button callback from external source.
     */
    public void mouseButtonCallback(int button, int action, int mods) {
        // Middle mouse - rotation
        if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
            middleButtonDown = (action == GLFW_PRESS);
            if (action == GLFW_PRESS) {
                dragStartX = mouseX;
                dragStartY = mouseY;
                isDragging = false;
            }
        }

        // Right mouse - panning
        if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW_PRESS) {
                rightButtonDown = true;
                dragStartX = mouseX;
                dragStartY = mouseY;
                isDragging = false;
            } else if (action == GLFW_RELEASE) {
                if (!isDragging && menuSystem.isMenuOpen()) {
                    menuSystem.closeMenu();
                    hexGrid.setSelected(-1);
                }
                rightButtonDown = false;
                isDragging = false;
            }
        }

        // Left mouse - selection
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
    }

    /**
     * Handle scroll callback from external source.
     */
    public void scrollCallback(double xOffset, double yOffset) {
        camera.zoom((float) yOffset);
    }

    /**
     * Handle cursor position callback from external source.
     */
    public void cursorPosCallback(double x, double y) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        mouseX = x;
        mouseY = y;

        // Check for drag
        if (leftButtonDown || middleButtonDown || rightButtonDown) {
            double dx = x - dragStartX;
            double dy = y - dragStartY;
            if (Math.sqrt(dx * dx + dy * dy) > DRAG_THRESHOLD) {
                isDragging = true;
            }
        }

        // Update hover when not dragging
        if (!isDragging) {
            updateHover();
        }

        // Update menu hover state
        if (menuSystem.isMenuOpen()) {
            menuSystem.updateHover((float) mouseX, (float) mouseY);
        }
    }
}
