package sh.vibecraft.hexcontrol.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Handles 3D rendering with shaders.
 */
public class Renderer {

    private int shaderProgram;
    private int vao;
    private int vbo;

    private int uViewProjection;
    private int uModel;
    private int uColor;
    private int uHighlight;

    private Camera camera;

    // Hex geometry (flat-topped hexagon)
    private static final int HEX_VERTICES = 18; // 6 triangles * 3 vertices
    private float[] hexVertices;

    public Renderer() {
        createShaders();
        createHexGeometry();
    }

    private void createShaders() {
        // Vertex shader
        String vertexSource = """
            #version 330 core
            layout(location = 0) in vec3 aPos;

            uniform mat4 uViewProjection;
            uniform mat4 uModel;

            out vec3 fragPos;

            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                fragPos = worldPos.xyz;
                gl_Position = uViewProjection * worldPos;
            }
            """;

        // Fragment shader
        String fragmentSource = """
            #version 330 core
            in vec3 fragPos;

            uniform vec3 uColor;
            uniform float uHighlight;

            out vec4 FragColor;

            void main() {
                // Simple lighting from above
                vec3 lightDir = normalize(vec3(0.3, 1.0, 0.2));
                float ambient = 0.4;
                float diffuse = max(dot(vec3(0.0, 1.0, 0.0), lightDir), 0.0) * 0.6;

                vec3 color = uColor * (ambient + diffuse);

                // Add highlight glow
                color = mix(color, vec3(1.0, 1.0, 1.0), uHighlight * 0.3);

                // Edge glow effect based on distance from center
                float dist = length(fragPos.xz);
                float edge = smoothstep(0.7, 0.9, dist);
                color = mix(color, uColor * 1.2, edge * 0.2);

                FragColor = vec4(color, 1.0);
            }
            """;

        // Compile vertex shader
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexSource);
        glCompileShader(vertexShader);
        checkShaderError(vertexShader, "VERTEX");

        // Compile fragment shader
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentSource);
        glCompileShader(fragmentShader);
        checkShaderError(fragmentShader, "FRAGMENT");

        // Link program
        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);
        checkProgramError(shaderProgram);

        // Cleanup shaders (they're linked now)
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        // Get uniform locations
        uViewProjection = glGetUniformLocation(shaderProgram, "uViewProjection");
        uModel = glGetUniformLocation(shaderProgram, "uModel");
        uColor = glGetUniformLocation(shaderProgram, "uColor");
        uHighlight = glGetUniformLocation(shaderProgram, "uHighlight");
    }

    private void checkShaderError(int shader, String type) {
        int success = glGetShaderi(shader, GL_COMPILE_STATUS);
        if (success == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            throw new RuntimeException(type + " shader compilation failed: " + log);
        }
    }

    private void checkProgramError(int program) {
        int success = glGetProgrami(program, GL_LINK_STATUS);
        if (success == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            throw new RuntimeException("Shader program linking failed: " + log);
        }
    }

    private void createHexGeometry() {
        // Create flat-topped hexagon vertices (6 triangles from center)
        float radius = 1.0f;
        float height = 0.2f; // Slight 3D extrusion

        // Generate hex top face (6 triangles)
        hexVertices = new float[HEX_VERTICES * 3];
        int idx = 0;

        for (int i = 0; i < 6; i++) {
            // Center vertex
            hexVertices[idx++] = 0.0f;
            hexVertices[idx++] = height;
            hexVertices[idx++] = 0.0f;

            // First corner
            float angle1 = (float) (Math.PI / 3.0 * i);
            hexVertices[idx++] = (float) Math.cos(angle1) * radius;
            hexVertices[idx++] = height;
            hexVertices[idx++] = (float) Math.sin(angle1) * radius;

            // Second corner
            float angle2 = (float) (Math.PI / 3.0 * (i + 1));
            hexVertices[idx++] = (float) Math.cos(angle2) * radius;
            hexVertices[idx++] = height;
            hexVertices[idx++] = (float) Math.sin(angle2) * radius;
        }

        // Create VAO and VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer vertexBuffer = MemoryUtil.memAllocFloat(hexVertices.length);
        vertexBuffer.put(hexVertices).flip();
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(vertexBuffer);

        // Position attribute
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }

    public void begin(Camera camera) {
        this.camera = camera;
        glUseProgram(shaderProgram);

        // Set view-projection matrix
        float[] vpMatrix = new float[16];
        camera.getViewProjectionMatrix().get(vpMatrix);
        glUniformMatrix4fv(uViewProjection, false, vpMatrix);
    }

    public void drawHex(float x, float z, float scale, Vector3f color, float highlight) {
        Matrix4f model = new Matrix4f();
        model.translate(x, 0.0f, z);
        model.scale(scale);

        float[] modelMatrix = new float[16];
        model.get(modelMatrix);

        glUniformMatrix4fv(uModel, false, modelMatrix);
        glUniform3f(uColor, color.x, color.y, color.z);
        glUniform1f(uHighlight, highlight);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, HEX_VERTICES);
        glBindVertexArray(0);
    }

    public void end() {
        glUseProgram(0);
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }
}
