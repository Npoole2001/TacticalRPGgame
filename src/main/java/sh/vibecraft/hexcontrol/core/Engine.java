package sh.vibecraft.hexcontrol.core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import sh.vibecraft.hexcontrol.render.Renderer;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;
import sh.vibecraft.hexcontrol.input.InputHandler;
import sh.vibecraft.hexcontrol.ui.MenuSystem;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Core engine managing the application lifecycle, window, and main loop.
 */
public class Engine {

    private long window;
    private int windowWidth = 1280;
    private int windowHeight = 720;

    private Renderer renderer;
    private Camera camera;
    private HexGrid hexGrid;
    private InputHandler inputHandler;
    private MenuSystem menuSystem;

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
        window = glfwCreateWindow(windowWidth, windowHeight, "HexControl - AI Agent Manager", NULL, NULL);
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

        // Set clear color (dark background)
        glClearColor(0.1f, 0.1f, 0.15f, 1.0f);

        // Initialize components
        camera = new Camera(windowWidth, windowHeight);
        hexGrid = new HexGrid(7); // Create a grid of 7 hexes (center + 6 surrounding)
        renderer = new Renderer();
        menuSystem = new MenuSystem();
        inputHandler = new InputHandler(window, camera, hexGrid, menuSystem);

        // Setup window resize callback
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            windowWidth = width;
            windowHeight = height;
            glViewport(0, 0, width, height);
            camera.updateProjection(width, height);
        });

        System.out.println("HexControl initialized successfully!");
        System.out.println("Controls:");
        System.out.println("  - Middle mouse button + drag: Pan camera");
        System.out.println("  - Scroll wheel: Zoom in/out");
        System.out.println("  - Left click on hex: Open agent menu");
        System.out.println("  - ESC: Exit");
    }

    private void loop() {
        long lastTime = System.nanoTime();

        while (!glfwWindowShouldClose(window) && running) {
            long currentTime = System.nanoTime();
            float deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
            lastTime = currentTime;

            // Poll events
            glfwPollEvents();

            // Update
            inputHandler.update(deltaTime);
            camera.update(deltaTime);
            hexGrid.update(deltaTime);
            menuSystem.update(deltaTime);

            // Render
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            renderer.begin(camera);
            hexGrid.render(renderer);
            renderer.end();

            // Render UI on top
            menuSystem.render(windowWidth, windowHeight);

            glfwSwapBuffers(window);
        }
    }

    private void cleanup() {
        renderer.cleanup();
        hexGrid.cleanup();
        menuSystem.cleanup();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();

        System.out.println("HexControl shutdown complete.");
    }

    public void stop() {
        running = false;
    }
}
