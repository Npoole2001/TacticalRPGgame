package sh.vibecraft.hexcontrol.render.effects;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Glitch shimmer effect for active tiles.
 * Per spec: "subtle glitch shimmer on active tiles (low amplitude; not distracting)"
 */
public class GlitchShimmer {

    private int shaderProgram;
    private int vao;
    private int vbo;

    private int uViewProjection;
    private int uModel;
    private int uTime;
    private int uColor;
    private int uIntensity;
    private int uGlitchSeed;

    private Random random = new Random();

    public GlitchShimmer() {
        createShaders();
        createGeometry();
    }

    private void createShaders() {
        String vertexSource = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUV;

            uniform mat4 uViewProjection;
            uniform mat4 uModel;
            uniform float uTime;
            uniform float uGlitchSeed;

            out vec2 fragUV;
            out float glitchOffset;

            // Pseudo-random function
            float rand(vec2 co) {
                return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
            }

            void main() {
                vec3 pos = aPos;

                // Subtle vertex displacement for glitch effect
                float glitchTime = floor(uTime * 8.0 + uGlitchSeed);
                float glitchRand = rand(vec2(glitchTime, aPos.x + aPos.z));

                if (glitchRand > 0.95) {
                    // Occasional horizontal shift
                    pos.x += (rand(vec2(glitchTime * 2.0, aPos.z)) - 0.5) * 0.1;
                }

                glitchOffset = glitchRand;
                fragUV = aUV;

                vec4 worldPos = uModel * vec4(pos, 1.0);
                gl_Position = uViewProjection * worldPos;
            }
            """;

        String fragmentSource = """
            #version 330 core
            in vec2 fragUV;
            in float glitchOffset;

            uniform vec3 uColor;
            uniform float uTime;
            uniform float uIntensity;
            uniform float uGlitchSeed;

            out vec4 FragColor;

            float rand(vec2 co) {
                return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
            }

            void main() {
                vec2 uv = fragUV;

                // Time-based glitch
                float glitchTime = floor(uTime * 12.0 + uGlitchSeed);

                // Scanline effect
                float scanline = sin(uv.y * 50.0 + uTime * 10.0) * 0.5 + 0.5;
                scanline = pow(scanline, 8.0) * 0.15;

                // Random horizontal bars
                float barY = floor(uv.y * 20.0);
                float barRand = rand(vec2(barY, glitchTime));
                float bar = 0.0;
                if (barRand > 0.97) {
                    bar = 0.3;
                }

                // Color separation (chromatic aberration)
                float separation = 0.0;
                if (rand(vec2(glitchTime, 0.5)) > 0.9) {
                    separation = 0.02 * uIntensity;
                }

                // Base color with effects
                vec3 color = uColor;

                // Add slight color shift
                float colorShift = rand(vec2(glitchTime * 0.5, uv.x)) * 0.1;
                color.r += colorShift * uIntensity;
                color.b -= colorShift * uIntensity * 0.5;

                // Combine effects
                float alpha = (scanline + bar) * uIntensity;

                // Edge glow
                float edgeDist = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
                float edgeGlow = smoothstep(0.0, 0.15, edgeDist);
                alpha *= edgeGlow;

                // Occasional flash
                if (rand(vec2(glitchTime * 3.0, uGlitchSeed)) > 0.98) {
                    alpha += 0.2;
                }

                FragColor = vec4(color, alpha * 0.6);
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
        uTime = glGetUniformLocation(shaderProgram, "uTime");
        uColor = glGetUniformLocation(shaderProgram, "uColor");
        uIntensity = glGetUniformLocation(shaderProgram, "uIntensity");
        uGlitchSeed = glGetUniformLocation(shaderProgram, "uGlitchSeed");
    }

    private void createGeometry() {
        // Hex shape with UVs
        int segments = 6;
        float[] vertices = new float[segments * 3 * 5];  // 3 verts per tri, 5 floats each

        int idx = 0;
        for (int i = 0; i < segments; i++) {
            float angle1 = (float)(Math.PI / 3.0 * i);
            float angle2 = (float)(Math.PI / 3.0 * (i + 1));

            // Center
            vertices[idx++] = 0.0f;
            vertices[idx++] = 0.2f;  // Slightly above tile
            vertices[idx++] = 0.0f;
            vertices[idx++] = 0.5f;
            vertices[idx++] = 0.5f;

            // First edge
            float x1 = (float)Math.cos(angle1) * 0.95f;
            float z1 = (float)Math.sin(angle1) * 0.95f;
            vertices[idx++] = x1;
            vertices[idx++] = 0.2f;
            vertices[idx++] = z1;
            vertices[idx++] = 0.5f + x1 * 0.5f;
            vertices[idx++] = 0.5f + z1 * 0.5f;

            // Second edge
            float x2 = (float)Math.cos(angle2) * 0.95f;
            float z2 = (float)Math.sin(angle2) * 0.95f;
            vertices[idx++] = x2;
            vertices[idx++] = 0.2f;
            vertices[idx++] = z2;
            vertices[idx++] = 0.5f + x2 * 0.5f;
            vertices[idx++] = 0.5f + z2 * 0.5f;
        }

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(buffer);

        int stride = 5 * Float.BYTES;

        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    /**
     * Render glitch shimmer on a hex tile.
     *
     * @param intensity 0.0 to 1.0 - how strong the effect should be
     */
    public void render(Matrix4f viewProjection, float x, float z, float scale,
                       Vector3f color, float time, float intensity, int tileIndex) {
        if (intensity <= 0.01f) return;

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

        glUniform1f(uTime, time);
        glUniform3f(uColor, color.x, color.y, color.z);
        glUniform1f(uIntensity, intensity);
        glUniform1f(uGlitchSeed, tileIndex * 0.1f);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 18);
        glBindVertexArray(0);

        glUseProgram(0);
    }

    /**
     * Calculate intensity based on agent activity level.
     * Higher activity = more glitch effect.
     */
    public static float calculateIntensity(sh.vibecraft.hexcontrol.agent.AgentStatus status,
                                            float activityLevel) {
        float baseIntensity = switch (status) {
            case RUNNING -> 0.4f + activityLevel * 0.4f;
            case BLOCKED -> 0.3f + (float)Math.sin(System.currentTimeMillis() * 0.005) * 0.2f;
            case ERROR -> 0.6f;
            default -> 0.0f;
        };

        return Math.min(1.0f, baseIntensity);
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }
}
