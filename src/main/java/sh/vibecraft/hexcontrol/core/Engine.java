package sh.vibecraft.hexcontrol.core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import sh.vibecraft.hexcontrol.render.Renderer;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;
import sh.vibecraft.hexcontrol.render.BiomePatterns;
import sh.vibecraft.hexcontrol.input.InputHandler;
import sh.vibecraft.hexcontrol.ui.*;
import sh.vibecraft.hexcontrol.demo.DemoMode;
import sh.vibecraft.hexcontrol.persistence.StateManager;
import sh.vibecraft.hexcontrol.security.AuditLog;
import sh.vibecraft.hexcontrol.security.PrivacyMode;
import sh.vibecraft.hexcontrol.security.KeychainStore;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Core engine managing the application lifecycle, window, and main loop.
 * Integrates all subsystems per the spec.
 */
public class Engine {

    private long window;
    private int windowWidth = 1280;
    private int windowHeight = 720;

    // Core systems
    private Renderer renderer;
    private Camera camera;
    private HexGrid hexGrid;
    private InputHandler inputHandler;
    private MenuSystem menuSystem;
    private StatsHUD statsHUD;
    private BiomePatterns biomePatterns;

    // V1 UI Components
    private SettingsPanel settingsPanel;
    private MiniMap miniMap;
    private AgentSearch agentSearch;
    private EventTimeline eventTimeline;

    // Demo mode
    private DemoMode demoMode;
    private boolean demoModeActive = false;

    // Persistence and security
    private StateManager stateManager;
    private AuditLog auditLog;
    private PrivacyMode privacyMode;
    private KeychainStore keychainStore;

    // Timing
    private float gameTime = 0.0f;

    private boolean running = true;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4); // Anti-aliasing

        // Create the window
        window = glfwCreateWindow(windowWidth, windowHeight, "HexControl - Agent Orchestration", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Center the window
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // V-Sync
        glfwShowWindow(window);

        // Initialize OpenGL
        GL.createCapabilities();

        // Enable depth testing and multisampling
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Set clear color (pure black per spec)
        glClearColor(0.02f, 0.02f, 0.03f, 1.0f);

        // Initialize core components
        camera = new Camera(windowWidth, windowHeight);
        hexGrid = new HexGrid(19); // 19 hexes = 2 rings around center
        renderer = new Renderer();
        menuSystem = new MenuSystem();
        statsHUD = new StatsHUD();
        biomePatterns = new BiomePatterns();
        inputHandler = new InputHandler(window, camera, hexGrid, menuSystem);

        // Initialize V1 UI components
        settingsPanel = new SettingsPanel();
        miniMap = new MiniMap();
        agentSearch = new AgentSearch();
        eventTimeline = new EventTimeline();
        demoMode = new DemoMode();
        privacyMode = new PrivacyMode();

        // Initialize persistence
        stateManager = new StateManager();
        stateManager.initialize(camera, hexGrid);

        // Initialize security
        keychainStore = new KeychainStore(stateManager.getStateDir());
        keychainStore.initialize("hexcontrol-default");  // Default passphrase for initial setup
        settingsPanel.setKeychainStore(keychainStore);

        // Try to load previous state
        if (stateManager.load()) {
            System.out.println("Restored previous session");
        }

        // Initialize audit log
        auditLog = new AuditLog(stateManager.getStateDir());
        auditLog.initialize();
        auditLog.logSecurityEvent("APP_START", "HexControl started");

        // Setup additional input callbacks for new UI components
        setupExtendedInputCallbacks();

        // Setup window resize callback
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            windowWidth = width;
            windowHeight = height;
            glViewport(0, 0, width, height);
            camera.updateProjection(width, height);
            stateManager.markDirty();
        });

        System.out.println("=========================================");
        System.out.println("  HexControl - Agent Orchestration");
        System.out.println("=========================================");
        System.out.println("Controls:");
        System.out.println("  MMB + drag    : Rotate camera");
        System.out.println("  RMB + drag    : Pan camera");
        System.out.println("  WASD          : Pan camera");
        System.out.println("  Scroll        : Zoom in/out");
        System.out.println("  Left click    : Select hex / Open menu");
        System.out.println("  1-9           : Quick-select agent");
        System.out.println("  ESC           : Close menu / Exit");
        System.out.println("-----------------------------------------");
        System.out.println("  ,             : Open Settings");
        System.out.println("  Ctrl+F        : Agent Search");
        System.out.println("  M             : Toggle Mini-map");
        System.out.println("  T             : Toggle Event Timeline");
        System.out.println("  F5            : Toggle Demo Mode");
        System.out.println("  F6            : Toggle Demo Simulation");
        System.out.println("=========================================");
    }

    private void loop() {
        long lastTime = System.nanoTime();

        while (!glfwWindowShouldClose(window) && running) {
            long currentTime = System.nanoTime();
            float deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
            lastTime = currentTime;
            gameTime += deltaTime;

            // Poll events
            glfwPollEvents();

            // Update all systems
            inputHandler.update(deltaTime);
            camera.update(deltaTime);
            hexGrid.update(deltaTime);
            menuSystem.update(deltaTime);
            statsHUD.update(deltaTime, hexGrid);

            // Update V1 UI components
            settingsPanel.update(deltaTime);
            miniMap.update(deltaTime, hexGrid, camera);
            agentSearch.update(deltaTime);
            eventTimeline.update(deltaTime);

            // Update demo mode if active
            if (demoModeActive) {
                demoMode.update(deltaTime, hexGrid);
            }

            // Render
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Render hex grid
            renderer.begin(camera);
            hexGrid.render(renderer);
            renderer.end();

            // Render biome patterns on tiles
            renderBiomePatterns();

            // Render effects (rings, particles)
            hexGrid.renderEffects(camera);

            // Render UI overlay (order matters for layering)
            statsHUD.render(windowWidth, windowHeight);
            miniMap.render(windowWidth, windowHeight, hexGrid, camera);
            menuSystem.render(windowWidth, windowHeight);
            eventTimeline.render(windowWidth, windowHeight);
            agentSearch.render(windowWidth, windowHeight);
            settingsPanel.render(windowWidth, windowHeight);

            glfwSwapBuffers(window);
        }
    }

    private void renderBiomePatterns() {
        var viewProjection = camera.getViewProjectionMatrix();

        for (var cell : hexGrid.getCells()) {
            var agent = cell.getAgent();
            if (agent == null || agent.getType() == sh.vibecraft.hexcontrol.agent.AgentType.EMPTY) {
                continue;
            }

            biomePatterns.render(
                viewProjection,
                cell.getWorldX(),
                cell.getWorldZ(),
                1.0f,
                agent.getRole(),
                agent.getRole().getAccentColor(),
                gameTime
            );
        }
    }

    private void setupExtendedInputCallbacks() {
        // Key callback for new UI components
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            // Forward to input handler first
            inputHandler.keyCallback(key, scancode, action, mods);

            // Handle settings panel toggle (comma key)
            if (key == GLFW_KEY_COMMA && action == GLFW_PRESS) {
                settingsPanel.toggle();
            }

            // Handle agent search toggle (Ctrl+F)
            if (key == GLFW_KEY_F && action == GLFW_PRESS && (mods & GLFW_MOD_CONTROL) != 0) {
                agentSearch.toggle(hexGrid, camera);
            }

            // Handle demo mode toggle (F5)
            if (key == GLFW_KEY_F5 && action == GLFW_PRESS) {
                toggleDemoMode();
            }

            // Handle demo simulation toggle (F6)
            if (key == GLFW_KEY_F6 && action == GLFW_PRESS && demoModeActive) {
                demoMode.toggleSimulation();
            }

            // Handle minimap toggle (M)
            if (key == GLFW_KEY_M && action == GLFW_PRESS && !settingsPanel.isVisible() && !agentSearch.isVisible()) {
                miniMap.toggle();
            }

            // Handle timeline toggle (T)
            if (key == GLFW_KEY_T && action == GLFW_PRESS && !settingsPanel.isVisible() && !agentSearch.isVisible()) {
                int selectedIdx = hexGrid.getSelectedIndex();
                if (selectedIdx >= 0) {
                    var cell = hexGrid.getCells().get(selectedIdx);
                    if (cell.getAgent() != null) {
                        eventTimeline.toggle(cell.getAgent());
                    }
                }
            }

            // Forward key input to active panels
            if (settingsPanel.isVisible()) {
                settingsPanel.handleKeyInput(key, action);
            }
            if (agentSearch.isVisible()) {
                agentSearch.handleKeyInput(key, action);
            }

            // Escape closes panels
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                if (settingsPanel.isVisible()) {
                    settingsPanel.close();
                } else if (agentSearch.isVisible()) {
                    agentSearch.close();
                } else if (eventTimeline.isVisible()) {
                    eventTimeline.hide();
                }
            }
        });

        // Character callback for text input
        glfwSetCharCallback(window, (win, codepoint) -> {
            char c = (char) codepoint;
            if (settingsPanel.isVisible()) {
                settingsPanel.handleCharInput(c);
            }
            if (agentSearch.isVisible()) {
                agentSearch.handleCharInput(c);
            }
        });

        // Mouse button callback for UI components
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                double[] xpos = new double[1];
                double[] ypos = new double[1];
                glfwGetCursorPos(window, xpos, ypos);
                float mouseX = (float) xpos[0];
                float mouseY = (float) ypos[0];

                // Check UI panels first (in reverse render order)
                if (settingsPanel.isVisible() && settingsPanel.handleClick(mouseX, mouseY)) {
                    return;
                }
                if (agentSearch.isVisible() && agentSearch.handleClick(mouseX, mouseY)) {
                    return;
                }
                if (eventTimeline.isVisible() && eventTimeline.handleClick(mouseX, mouseY, windowWidth, windowHeight)) {
                    return;
                }
                if (miniMap.handleClick(mouseX, mouseY, windowWidth, windowHeight, camera)) {
                    return;
                }

                // Otherwise forward to input handler
                inputHandler.mouseButtonCallback(button, action, mods);
            } else {
                inputHandler.mouseButtonCallback(button, action, mods);
            }
        });

        // Scroll callback for UI components
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (eventTimeline.isVisible()) {
                eventTimeline.handleScroll((float) yoffset);
            } else {
                inputHandler.scrollCallback(xoffset, yoffset);
            }
        });

        // Cursor position callback for hover effects
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            float mouseX = (float) xpos;
            float mouseY = (float) ypos;

            settingsPanel.updateHover(mouseX, mouseY);
            agentSearch.updateHover(mouseX, mouseY);
            miniMap.updateHover(mouseX, mouseY, windowWidth, windowHeight);

            inputHandler.cursorPosCallback(xpos, ypos);
        });
    }

    private void toggleDemoMode() {
        demoModeActive = !demoModeActive;
        if (demoModeActive) {
            demoMode.populateGrid(hexGrid);
            System.out.println("Demo mode activated - Press F6 to start/stop simulation");
        } else {
            System.out.println("Demo mode deactivated");
        }
    }

    private void cleanup() {
        // Log shutdown
        auditLog.logSecurityEvent("APP_STOP", "HexControl shutting down");

        // Lock keychain
        if (keychainStore != null) {
            keychainStore.lock();
        }

        // Save state
        stateManager.shutdown();
        auditLog.shutdown();

        // Cleanup OpenGL resources
        renderer.cleanup();
        hexGrid.cleanup();
        menuSystem.cleanup();
        biomePatterns.cleanup();

        // Cleanup GLFW
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();

        System.out.println("HexControl shutdown complete.");
    }

    public void stop() {
        running = false;
    }

    public StateManager getStateManager() {
        return stateManager;
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }
}
