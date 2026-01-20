package sh.vibecraft.hexcontrol.render.effects;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Scanline sweep effect for global actions.
 * Per spec: "scanline sweep across grid when global actions occur"
 */
public class ScanlineEffect {

    private int shaderProgram;
    private int vao;
    private int vbo;

    private int uViewProjection;
    private int uProgress;
    private int uColor;
    private int uDirection;
    private int uWidth;
    private int uGridBounds;

    // Animation state
    private boolean active = false;
    private float progress = 0.0f;
    private float speed = 2.0f;
    private float lineWidth = 0.5f;
    private Vector3f color = new Vector3f(0.4f, 0.7f, 1.0f);
    private ScanDirection direction = ScanDirection.DOWN;

    // Grid bounds
    private float gridMinX = -10f, gridMaxX = 10f;
    private float gridMinZ = -10f, gridMaxZ = 10f;

    // Callback when scan completes
    private Runnable onComplete;

    public ScanlineEffect() {
        createShaders();
        createGeometry();
    }

    private void createShaders() {
        String vertexSource = """
            #version 330 core
            layout(location = 0) in vec3 aPos;

            uniform mat4 uViewProjection;
            uniform float uProgress;
            uniform int uDirection;
            uniform vec4 uGridBounds;  // minX, maxX, minZ, maxZ

            out float fragAlpha;
            out vec3 fragPos;

            void main() {
                vec3 pos = aPos;

                float minX = uGridBounds.x;
                float maxX = uGridBounds.y;
                float minZ = uGridBounds.z;
                float maxZ = uGridBounds.w;

                // Position the scanline based on direction and progress
                if (uDirection == 0) {
                    // DOWN (Z increasing)
                    pos.z = mix(minZ - 2.0, maxZ + 2.0, uProgress);
                    pos.x = pos.x * (maxX - minX) + (minX + maxX) * 0.5;
                } else if (uDirection == 1) {
                    // UP (Z decreasing)
                    pos.z = mix(maxZ + 2.0, minZ - 2.0, uProgress);
                    pos.x = pos.x * (maxX - minX) + (minX + maxX) * 0.5;
                } else if (uDirection == 2) {
                    // RIGHT (X increasing)
                    pos.x = mix(minX - 2.0, maxX + 2.0, uProgress);
                    pos.z = pos.z * (maxZ - minZ) + (minZ + maxZ) * 0.5;
                } else {
                    // LEFT (X decreasing)
                    pos.x = mix(maxX + 2.0, minX - 2.0, uProgress);
                    pos.z = pos.z * (maxZ - minZ) + (minZ + maxZ) * 0.5;
                }

                fragPos = pos;
                fragAlpha = 1.0 - abs(aPos.y);  // Fade at edges

                gl_Position = uViewProjection * vec4(pos, 1.0);
            }
            """;

        String fragmentSource = """
            #version 330 core
            in float fragAlpha;
            in vec3 fragPos;

            uniform vec3 uColor;
            uniform float uWidth;
            uniform float uProgress;

            out vec4 FragColor;

            void main() {
                // Core line
                float intensity = fragAlpha;

                // Add some glow falloff
                float glow = exp(-abs(fragPos.y) * 2.0) * 0.5;

                // Pulse effect
                float pulse = 0.8 + 0.2 * sin(uProgress * 20.0);

                FragColor = vec4(uColor * pulse, (intensity + glow) * 0.8);
            }
            """;

        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexSource);
        glCompileShader(vertexShader);

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentSource);
        glCompileShader(fragmentShader);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        uViewProjection = glGetUniformLocation(shaderProgram, "uViewProjection");
        uProgress = glGetUniformLocation(shaderProgram, "uProgress");
        uColor = glGetUniformLocation(shaderProgram, "uColor");
        uDirection = glGetUniformLocation(shaderProgram, "uDirection");
        uWidth = glGetUniformLocation(shaderProgram, "uWidth");
        uGridBounds = glGetUniformLocation(shaderProgram, "uGridBounds");
    }

    private void createGeometry() {
        // Create a line strip for the scanline
        // Positions are -1 to 1 in X, varying Y for gradient
        float[] vertices = new float[]{
            -0.5f, 0.3f, 0.0f,
            -0.5f, 0.0f, 0.0f,
             0.5f, 0.0f, 0.0f,
             0.5f, 0.3f, 0.0f,
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(buffer);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }

    /**
     * Trigger a scanline sweep.
     */
    public void trigger(ScanDirection direction, Vector3f color, Runnable onComplete) {
        this.direction = direction;
        this.color = color;
        this.onComplete = onComplete;
        this.progress = 0.0f;
        this.active = true;
    }

    /**
     * Trigger a default scanline sweep.
     */
    public void trigger() {
        trigger(ScanDirection.DOWN, new Vector3f(0.4f, 0.7f, 1.0f), null);
    }

    /**
     * Trigger for "start all agents" action.
     */
    public void triggerStartAll() {
        trigger(ScanDirection.DOWN, new Vector3f(0.2f, 0.8f, 0.4f), null);
    }

    /**
     * Trigger for "pause all agents" action.
     */
    public void triggerPauseAll() {
        trigger(ScanDirection.UP, new Vector3f(0.8f, 0.6f, 0.2f), null);
    }

    /**
     * Alias for triggerPauseAll - used by Engine.
     */
    public void startPauseAll() {
        triggerPauseAll();
    }

    /**
     * Trigger for error/alert action.
     */
    public void triggerAlert() {
        trigger(ScanDirection.RIGHT, new Vector3f(0.9f, 0.3f, 0.3f), null);
    }

    public void setGridBounds(float minX, float maxX, float minZ, float maxZ) {
        this.gridMinX = minX;
        this.gridMaxX = maxX;
        this.gridMinZ = minZ;
        this.gridMaxZ = maxZ;
    }

    public void update(float deltaTime) {
        if (!active) return;

        progress += deltaTime * speed;

        if (progress >= 1.0f) {
            active = false;
            progress = 1.0f;

            if (onComplete != null) {
                onComplete.run();
                onComplete = null;
            }
        }
    }

    /**
     * Render as 2D screen-space overlay.
     */
    public void render(int windowWidth, int windowHeight) {
        if (!active) return;

        // Setup 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);

        // Draw scanline as simple 2D bar
        float lineY = progress * windowHeight;
        float lineHeight = 4.0f;
        float glowHeight = 20.0f;

        // Glow
        glBegin(GL_QUADS);
        glColor4f(color.x, color.y, color.z, 0.0f);
        glVertex2f(0, lineY - glowHeight);
        glVertex2f(windowWidth, lineY - glowHeight);
        glColor4f(color.x, color.y, color.z, 0.4f);
        glVertex2f(windowWidth, lineY);
        glVertex2f(0, lineY);
        glEnd();

        // Main line
        glColor4f(color.x, color.y, color.z, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(0, lineY - lineHeight / 2);
        glVertex2f(windowWidth, lineY - lineHeight / 2);
        glVertex2f(windowWidth, lineY + lineHeight / 2);
        glVertex2f(0, lineY + lineHeight / 2);
        glEnd();

        // Glow below
        glBegin(GL_QUADS);
        glColor4f(color.x, color.y, color.z, 0.4f);
        glVertex2f(0, lineY);
        glVertex2f(windowWidth, lineY);
        glColor4f(color.x, color.y, color.z, 0.0f);
        glVertex2f(windowWidth, lineY + glowHeight);
        glVertex2f(0, lineY + glowHeight);
        glEnd();

        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    public void render(Matrix4f viewProjection) {
        if (!active) return;

        glUseProgram(shaderProgram);

        float[] vpMatrix = new float[16];
        viewProjection.get(vpMatrix);
        glUniformMatrix4fv(uViewProjection, false, vpMatrix);

        glUniform1f(uProgress, progress);
        glUniform3f(uColor, color.x, color.y, color.z);
        glUniform1i(uDirection, direction.ordinal());
        glUniform1f(uWidth, lineWidth);
        glUniform4f(uGridBounds, gridMinX, gridMaxX, gridMinZ, gridMaxZ);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
        glBindVertexArray(0);

        glUseProgram(0);
    }

    public boolean isActive() {
        return active;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void setLineWidth(float width) {
        this.lineWidth = width;
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }

    /**
     * Direction of the scanline sweep.
     */
    public enum ScanDirection {
        DOWN,   // Z increasing
        UP,     // Z decreasing
        RIGHT,  // X increasing
        LEFT    // X decreasing
    }
}
