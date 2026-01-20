package sh.vibecraft.hexcontrol.render.effects;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * Instanced particle system for efficient rendering of square smoke particles.
 * Per spec: "Square smoke particles rising from occupied tiles"
 */
public class ParticleSystem {

    private static final int MAX_PARTICLES = 1000;
    private static final int INSTANCE_DATA_SIZE = 10; // pos(3) + color(3) + size(1) + alpha(1) + rotation(1) + padding(1)

    private int shaderProgram;
    private int vao;
    private int vboQuad;
    private int vboInstances;

    private int uViewProjection;

    private List<Particle> particles;
    private Particle[] particlePool;
    private int poolIndex = 0;

    private FloatBuffer instanceBuffer;

    // Quad vertices for a square particle
    private static final float[] QUAD_VERTICES = {
        -0.5f, 0.0f, -0.5f,
         0.5f, 0.0f, -0.5f,
         0.5f, 0.0f,  0.5f,
        -0.5f, 0.0f, -0.5f,
         0.5f, 0.0f,  0.5f,
        -0.5f, 0.0f,  0.5f
    };

    public ParticleSystem() {
        particles = new ArrayList<>();
        particlePool = new Particle[MAX_PARTICLES];
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particlePool[i] = new Particle();
        }

        instanceBuffer = MemoryUtil.memAllocFloat(MAX_PARTICLES * INSTANCE_DATA_SIZE);

        createShaders();
        createBuffers();
    }

    private void createShaders() {
        String vertexSource = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aInstancePos;
            layout(location = 2) in vec3 aInstanceColor;
            layout(location = 3) in float aInstanceSize;
            layout(location = 4) in float aInstanceAlpha;
            layout(location = 5) in float aInstanceRotation;

            uniform mat4 uViewProjection;

            out vec3 fragColor;
            out float fragAlpha;

            void main() {
                // Rotate vertex around Y axis
                float c = cos(aInstanceRotation);
                float s = sin(aInstanceRotation);
                vec3 rotatedPos = vec3(
                    aPos.x * c - aPos.z * s,
                    aPos.y,
                    aPos.x * s + aPos.z * c
                );

                vec3 worldPos = aInstancePos + rotatedPos * aInstanceSize;
                gl_Position = uViewProjection * vec4(worldPos, 1.0);

                fragColor = aInstanceColor;
                fragAlpha = aInstanceAlpha;
            }
            """;

        String fragmentSource = """
            #version 330 core
            in vec3 fragColor;
            in float fragAlpha;
            out vec4 FragColor;

            void main() {
                FragColor = vec4(fragColor, fragAlpha * 0.6);
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
    }

    private void createBuffers() {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        // Quad geometry
        vboQuad = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboQuad);
        FloatBuffer quadBuffer = MemoryUtil.memAllocFloat(QUAD_VERTICES.length);
        quadBuffer.put(QUAD_VERTICES).flip();
        glBufferData(GL_ARRAY_BUFFER, quadBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(quadBuffer);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        // Instance data
        vboInstances = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboInstances);
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_PARTICLES * INSTANCE_DATA_SIZE * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = INSTANCE_DATA_SIZE * Float.BYTES;

        // Position (3 floats)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribDivisor(1, 1);

        // Color (3 floats)
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribDivisor(2, 1);

        // Size (1 float)
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6 * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribDivisor(3, 1);

        // Alpha (1 float)
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 7 * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribDivisor(4, 1);

        // Rotation (1 float)
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 8 * Float.BYTES);
        glEnableVertexAttribArray(5);
        glVertexAttribDivisor(5, 1);

        glBindVertexArray(0);
    }

    /**
     * Spawn smoke particles from a position (for running agents).
     */
    public void spawnSmoke(float x, float z, Vector3f color, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = getNextParticle();
            float offsetX = (float) (Math.random() - 0.5) * 0.5f;
            float offsetZ = (float) (Math.random() - 0.5) * 0.5f;

            p.reset(x + offsetX, 0.3f, z + offsetZ, color, 0.15f + (float) Math.random() * 0.1f, 1.5f + (float) Math.random());
            p.velocity.set(
                (float) (Math.random() - 0.5) * 0.3f,
                0.5f + (float) Math.random() * 0.5f,
                (float) (Math.random() - 0.5) * 0.3f
            );

            particles.add(p);
        }
    }

    /**
     * Spawn glitch particles (for high activity or errors).
     */
    public void spawnGlitch(float x, float z, Vector3f color, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = getNextParticle();
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = 0.5f + (float) Math.random() * 0.5f;

            p.reset(x + (float) Math.cos(angle) * dist, 0.2f, z + (float) Math.sin(angle) * dist, color, 0.05f, 0.3f);
            p.velocity.set(
                (float) Math.cos(angle) * 2.0f,
                (float) Math.random() * 2.0f,
                (float) Math.sin(angle) * 2.0f
            );

            particles.add(p);
        }
    }

    private Particle getNextParticle() {
        Particle p = particlePool[poolIndex];
        poolIndex = (poolIndex + 1) % MAX_PARTICLES;
        return p;
    }

    public void update(float deltaTime) {
        // Update all particles and remove dead ones
        particles.removeIf(p -> {
            p.update(deltaTime);
            return !p.isAlive();
        });
    }

    public void render(Matrix4f viewProjection) {
        if (particles.isEmpty()) return;

        glUseProgram(shaderProgram);

        float[] vpMatrix = new float[16];
        viewProjection.get(vpMatrix);
        glUniformMatrix4fv(uViewProjection, false, vpMatrix);

        // Update instance buffer
        instanceBuffer.clear();
        for (Particle p : particles) {
            instanceBuffer.put(p.position.x);
            instanceBuffer.put(p.position.y);
            instanceBuffer.put(p.position.z);
            instanceBuffer.put(p.color.x);
            instanceBuffer.put(p.color.y);
            instanceBuffer.put(p.color.z);
            instanceBuffer.put(p.size);
            instanceBuffer.put(p.alpha);
            instanceBuffer.put(p.rotation);
            instanceBuffer.put(0); // padding
        }
        instanceBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vboInstances);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceBuffer);

        // Draw instanced
        glBindVertexArray(vao);
        glDrawArraysInstanced(GL_TRIANGLES, 0, 6, particles.size());
        glBindVertexArray(0);

        glUseProgram(0);
    }

    public void cleanup() {
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vboQuad);
        glDeleteBuffers(vboInstances);
        MemoryUtil.memFree(instanceBuffer);
    }

    public int getParticleCount() {
        return particles.size();
    }
}
