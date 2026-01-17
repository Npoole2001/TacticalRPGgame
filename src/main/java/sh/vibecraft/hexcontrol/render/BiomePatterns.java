package sh.vibecraft.hexcontrol.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import sh.vibecraft.hexcontrol.agent.AgentRole;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Procedural biome patterns for role-based tile styling.
 * Per spec:
 * - Builder: diagonal micro-lines
 * - Tester: dot-grid pattern
 * - Reviewer: thin crosshatch
 * - Researcher: subtle waveform line
 * - Ops/Integrator: circuit-like pattern
 */
public class BiomePatterns {

    private int shaderProgram;
    private int vao;
    private int vbo;

    private int uViewProjection;
    private int uModel;
    private int uColor;
    private int uPatternType;
    private int uTime;
    private int uAlpha;

    // Hex geometry for pattern rendering
    private static final int HEX_SEGMENTS = 6;

    public BiomePatterns() {
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

            out vec2 fragUV;
            out vec3 fragPos;

            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                fragPos = worldPos.xyz;
                fragUV = aUV;
                gl_Position = uViewProjection * worldPos;
            }
            """;

        String fragmentSource = """
            #version 330 core
            in vec2 fragUV;
            in vec3 fragPos;

            uniform vec3 uColor;
            uniform int uPatternType;
            uniform float uTime;
            uniform float uAlpha;

            out vec4 FragColor;

            // Pattern functions
            float diagonal(vec2 uv, float scale) {
                float line = sin((uv.x + uv.y) * scale * 3.14159 * 10.0);
                return smoothstep(0.7, 0.8, abs(line));
            }

            float dots(vec2 uv, float scale) {
                vec2 grid = fract(uv * scale * 8.0) - 0.5;
                float d = length(grid);
                return 1.0 - smoothstep(0.1, 0.15, d);
            }

            float crosshatch(vec2 uv, float scale) {
                float line1 = sin((uv.x + uv.y) * scale * 3.14159 * 8.0);
                float line2 = sin((uv.x - uv.y) * scale * 3.14159 * 8.0);
                float cross = max(abs(line1), abs(line2));
                return smoothstep(0.6, 0.7, cross);
            }

            float waveform(vec2 uv, float time, float scale) {
                float wave = sin(uv.x * scale * 15.0 + time * 2.0) * 0.1;
                float dist = abs(uv.y - 0.5 - wave);
                return 1.0 - smoothstep(0.02, 0.04, dist);
            }

            float circuit(vec2 uv, float scale) {
                vec2 grid = fract(uv * scale * 6.0);
                float h = step(0.45, grid.x) * step(grid.x, 0.55);
                float v = step(0.45, grid.y) * step(grid.y, 0.55);

                // Add some L-shaped connectors
                vec2 cell = floor(uv * scale * 6.0);
                float seed = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);

                if (seed > 0.6) {
                    h += step(0.8, grid.x) * step(0.4, grid.y) * step(grid.y, 0.6);
                }
                if (seed > 0.3 && seed <= 0.6) {
                    v += step(0.8, grid.y) * step(0.4, grid.x) * step(grid.x, 0.6);
                }

                return min(1.0, h + v);
            }

            float grid_pattern(vec2 uv, float scale) {
                vec2 grid = fract(uv * scale * 5.0);
                float h = smoothstep(0.48, 0.5, grid.y) * smoothstep(0.52, 0.5, grid.y);
                float v = smoothstep(0.48, 0.5, grid.x) * smoothstep(0.52, 0.5, grid.x);
                return max(h, v);
            }

            float lines_pattern(vec2 uv, float scale) {
                float line = sin(uv.y * scale * 3.14159 * 12.0);
                return smoothstep(0.8, 0.85, abs(line));
            }

            void main() {
                float pattern = 0.0;
                float scale = 1.0;

                // Map UV to hex-local coordinates
                vec2 localUV = fragUV;

                // Pattern selection based on type
                switch (uPatternType) {
                    case 0: // None
                        discard;
                    case 1: // Diagonal (Builder/Coder)
                        pattern = diagonal(localUV, scale);
                        break;
                    case 2: // Dots (Tester)
                        pattern = dots(localUV, scale);
                        break;
                    case 3: // Crosshatch (Reviewer/Tech Lead)
                        pattern = crosshatch(localUV, scale);
                        break;
                    case 4: // Waveform (Researcher)
                        pattern = waveform(localUV, uTime, scale);
                        break;
                    case 5: // Circuit (Ops/PM)
                        pattern = circuit(localUV, scale);
                        break;
                    case 6: // Grid (QA)
                        pattern = grid_pattern(localUV, scale);
                        break;
                    case 7: // Lines (Docs)
                        pattern = lines_pattern(localUV, scale);
                        break;
                    default:
                        discard;
                }

                if (pattern < 0.1) discard;

                FragColor = vec4(uColor, pattern * uAlpha * 0.4);
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
        uPatternType = glGetUniformLocation(shaderProgram, "uPatternType");
        uTime = glGetUniformLocation(shaderProgram, "uTime");
        uAlpha = glGetUniformLocation(shaderProgram, "uAlpha");
    }

    private void createGeometry() {
        // Create hex with UVs for pattern mapping
        // 6 triangles from center, each vertex has position + UV
        float[] vertices = new float[HEX_SEGMENTS * 3 * 5];  // 3 vertices per triangle, 5 floats each (xyz + uv)

        float radius = 1.0f;
        float height = 0.15f;  // Slightly above tile surface

        int idx = 0;
        for (int i = 0; i < HEX_SEGMENTS; i++) {
            float angle1 = (float)(Math.PI / 3.0 * i);
            float angle2 = (float)(Math.PI / 3.0 * (i + 1));

            // Center vertex
            vertices[idx++] = 0.0f;
            vertices[idx++] = height;
            vertices[idx++] = 0.0f;
            vertices[idx++] = 0.5f;  // UV center
            vertices[idx++] = 0.5f;

            // First edge vertex
            float x1 = (float)Math.cos(angle1) * radius;
            float z1 = (float)Math.sin(angle1) * radius;
            vertices[idx++] = x1;
            vertices[idx++] = height;
            vertices[idx++] = z1;
            vertices[idx++] = 0.5f + x1 * 0.5f;
            vertices[idx++] = 0.5f + z1 * 0.5f;

            // Second edge vertex
            float x2 = (float)Math.cos(angle2) * radius;
            float z2 = (float)Math.sin(angle2) * radius;
            vertices[idx++] = x2;
            vertices[idx++] = height;
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

        // Position attribute
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // UV attribute
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    /**
     * Render a biome pattern on a hex tile.
     */
    public void render(Matrix4f viewProjection, float x, float z, float scale,
                       AgentRole role, Vector3f color, float time) {
        int patternType = getPatternType(role);
        if (patternType == 0) return;  // No pattern for this role

        glUseProgram(shaderProgram);

        float[] vpMatrix = new float[16];
        viewProjection.get(vpMatrix);
        glUniformMatrix4fv(uViewProjection, false, vpMatrix);

        Matrix4f model = new Matrix4f();
        model.translate(x, 0.0f, z);
        model.scale(scale * 0.9f);  // Slightly smaller than hex

        float[] modelMatrix = new float[16];
        model.get(modelMatrix);
        glUniformMatrix4fv(uModel, false, modelMatrix);

        glUniform3f(uColor, color.x, color.y, color.z);
        glUniform1i(uPatternType, patternType);
        glUniform1f(uTime, time);
        glUniform1f(uAlpha, 1.0f);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, HEX_SEGMENTS * 3);
        glBindVertexArray(0);

        glUseProgram(0);
    }

    /**
     * Get pattern type ID for a role.
     */
    private int getPatternType(AgentRole role) {
        String patternId = role.getPatternId();

        return switch (patternId) {
            case "diagonal" -> 1;
            case "dots" -> 2;
            case "crosshatch" -> 3;
            case "waveform" -> 4;
            case "circuit" -> 5;
            case "grid" -> 6;
            case "lines" -> 7;
            case "none" -> 0;
            default -> 0;
        };
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }
}
