package sh.vibecraft.hexcontrol.core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import sh.vibecraft.hexcontrol.render.Renderer;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;
import sh.vibecraft.hexcontrol.input.InputHandler;
import sh.vibecraft.hexcontrol.ui.MenuSystem;
import sh.vibecraft.hexcontrol.ui.StatsHUD;
import sh.vibecraft.hexcontrol.persistence.StateManager;
import sh.vibecraft.hexcontrol.security.AuditLog;

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

    // Persistence and security
    private StateManager stateManager;
    private AuditLog auditLog;

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

        // Initialize components
        camera = new Camera(windowWidth, windowHeight);
        hexGrid = new HexGrid(19); // 19 hexes = 2 rings around center
        renderer = new Renderer();
        menuSystem = new MenuSystem();
        statsHUD = new StatsHUD();
        inputHandler = new InputHandler(window, camera, hexGrid, menuSystem);

        // Initialize persistence
        stateManager = new StateManager();
        stateManager.initialize(camera, hexGrid);

        // Try to load previous state
        if (stateManager.load()) {
            System.out.println("Restored previous session");
        }

        // Initialize audit log
        auditLog = new AuditLog(stateManager.getStateDir());
        auditLog.initialize();
        auditLog.logSecurityEvent("APP_START", "HexControl started");

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
        System.out.println("=========================================");
    }

    private void loop() {
        long lastTime = System.nanoTime();

        while (!glfwWindowShouldClose(window) && running) {
            long currentTime = System.nanoTime();
            float deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
            lastTime = currentTime;

            // Poll events
            glfwPollEvents();

            // Update all systems
            inputHandler.update(deltaTime);
            camera.update(deltaTime);
            hexGrid.update(deltaTime);
            menuSystem.update(deltaTime);
            statsHUD.update(deltaTime, hexGrid);

            // Render
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Render hex grid
            renderer.begin(camera);
            hexGrid.render(renderer);
            renderer.end();

            // Render effects (rings, particles)
            hexGrid.renderEffects(camera);

            // Render UI overlay
            statsHUD.render(windowWidth, windowHeight);
            menuSystem.render(windowWidth, windowHeight);

            glfwSwapBuffers(window);
        }
    }

    private void cleanup() {
        // Log shutdown
        auditLog.logSecurityEvent("APP_STOP", "HexControl shutting down");

        // Save state
        stateManager.shutdown();
        auditLog.shutdown();

        // Cleanup OpenGL resources
        renderer.cleanup();
        hexGrid.cleanup();
        menuSystem.cleanup();

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
