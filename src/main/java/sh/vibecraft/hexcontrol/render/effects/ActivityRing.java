package sh.vibecraft.hexcontrol.render.effects;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import sh.vibecraft.hexcontrol.agent.AgentStatus;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Activity ring effect around hex tiles.
 * Per spec:
 * - Idle: none or faint slow pulse
 * - Running: steady pulse + ring rotation
 * - High activity: tighter pulse + occasional glitch spikes
 * - Blocked: ring stutters / intermittent amber accent
 * - Error: ring becomes segmented + slow flashing (red accents)
 */
public class ActivityRing {

    private static final int RING_SEGMENTS = 64;

    private int shaderProgram;
    private int vao;
    private int vbo;

    private int uViewProjection;
    private int uModel;
    private int uColor;
    private int uTime;
    private int uStatus;
    private int uPulse;
    private int uSegmented;

    public ActivityRing() {
        createShaders();
        createGeometry();
    }

    private void createShaders() {
        String vertexSource = """
            #version 330 core
            layout(location = 0) in vec3 aPos;

            uniform mat4 uViewProjection;
            uniform mat4 uModel;

            out vec3 fragPos;
            out float angle;

            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                fragPos = worldPos.xyz;
                gl_Position = uViewProjection * worldPos;

                // Calculate angle for segment effects
                angle = atan(aPos.z, aPos.x);
            }
            """;

        String fragmentSource = """
            #version 330 core
            in vec3 fragPos;
            in float angle;

            uniform vec3 uColor;
            uniform float uTime;
            uniform int uStatus;  // 0=idle, 1=running, 2=sleeping, 3=blocked, 4=error
            uniform float uPulse;
            uniform int uSegmented;

            out vec4 FragColor;

            void main() {
                float alpha = 0.8;

                // Status-based effects
                if (uStatus == 0) {
                    // Idle: faint slow pulse
                    alpha = 0.2 + sin(uTime * 0.5) * 0.1;
                } else if (uStatus == 1) {
                    // Running: steady pulse + rotation effect
                    float rotatedAngle = angle + uTime * 2.0;
                    alpha = 0.6 + sin(rotatedAngle * 3.0) * 0.2;
                    alpha *= 0.8 + sin(uTime * 3.0) * 0.2;  // Pulse
                } else if (uStatus == 2) {
                    // Sleeping: very faint, slow
                    alpha = 0.1 + sin(uTime * 0.2) * 0.05;
                } else if (uStatus == 3) {
                    // Blocked: stuttering, amber flicker
                    float stutter = step(0.7, fract(uTime * 4.0));
                    alpha = 0.5 * (1.0 - stutter * 0.5);
                    alpha *= 0.7 + sin(uTime * 8.0) * 0.3;
                } else if (uStatus == 4) {
                    // Error: segmented + slow flash
                    if (uSegmented == 1) {
                        // Create segments by modulating based on angle
                        float segment = step(0.3, fract(angle * 1.5 + uTime * 0.5));
                        alpha = 0.7 * segment;
                    }
                    alpha *= 0.5 + sin(uTime * 2.0) * 0.5;  // Slow flash
                }

                // Apply pulse multiplier
                alpha *= uPulse;

                // Glitch shimmer (subtle noise-like effect)
                float glitch = fract(sin(dot(vec2(angle, uTime), vec2(12.9898, 78.233))) * 43758.5453);
                if (uStatus == 1 || uStatus == 3) {
                    alpha += glitch * 0.1 * step(0.95, glitch);
                }

                FragColor = vec4(uColor, alpha);
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
        uModel = glGetUniformLocation(shaderProgram, "uModel");
        uColor = glGetUniformLocation(shaderProgram, "uColor");
        uTime = glGetUniformLocation(shaderProgram, "uTime");
        uStatus = glGetUniformLocation(shaderProgram, "uStatus");
        uPulse = glGetUniformLocation(shaderProgram, "uPulse");
        uSegmented = glGetUniformLocation(shaderProgram, "uSegmented");
    }

    private void createGeometry() {
        // Create ring as a line loop
        float[] vertices = new float[RING_SEGMENTS * 3];
        float innerRadius = 0.9f;

        for (int i = 0; i < RING_SEGMENTS; i++) {
            float angle = (float) (2 * Math.PI * i / RING_SEGMENTS);
            vertices[i * 3] = (float) Math.cos(angle) * innerRadius;
            vertices[i * 3 + 1] = 0.25f;  // Slightly above ground
            vertices[i * 3 + 2] = (float) Math.sin(angle) * innerRadius;
        }

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
     * Render a ring at the specified position with given status.
     */
    public void render(Matrix4f viewProjection, float x, float z, float scale,
                       AgentStatus status, Vector3f color, float time, float pulseMultiplier) {
        glUseProgram(shaderProgram);

        float[] vpMatrix = new float[16];
        viewProjection.get(vpMatrix);
        glUniformMatrix4fv(uViewProjection, false, vpMatrix);

        Matrix4f model = new Matrix4f();
        model.translate(x, 0.0f, z);
        model.scale(scale);

        float[] modelMatrix = new float[16];
        model.get(modelMatrix);
        glUniformMatrix4fv(uModel, false, modelMatrix);

        glUniform3f(uColor, color.x, color.y, color.z);
        glUniform1f(uTime, time);
        glUniform1i(uStatus, status.ordinal());
        glUniform1f(uPulse, pulseMultiplier);
        glUniform1i(uSegmented, status == AgentStatus.ERROR ? 1 : 0);

        glLineWidth(2.0f);
        glBindVertexArray(vao);
        glDrawArrays(GL_LINE_LOOP, 0, RING_SEGMENTS);
        glBindVertexArray(0);

        glUseProgram(0);
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }
}
