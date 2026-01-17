package sh.vibecraft.hexcontrol.render.effects;

import org.joml.Vector3f;

/**
 * A single square particle for smoke/activity effects.
 */
public class Particle {
    public Vector3f position;
    public Vector3f velocity;
    public Vector3f color;
    public float size;
    public float alpha;
    public float life;
    public float maxLife;
    public float rotation;
    public float rotationSpeed;

    public Particle() {
        position = new Vector3f();
        velocity = new Vector3f();
        color = new Vector3f(1.0f);
        size = 0.1f;
        alpha = 1.0f;
        life = 0.0f;
        maxLife = 1.0f;
        rotation = 0.0f;
        rotationSpeed = 0.0f;
    }

    public void reset(float x, float y, float z, Vector3f color, float size, float life) {
        position.set(x, y, z);
        velocity.set(0, 0, 0);
        this.color.set(color);
        this.size = size;
        this.alpha = 1.0f;
        this.life = life;
        this.maxLife = life;
        this.rotation = (float) (Math.random() * Math.PI * 2);
        this.rotationSpeed = (float) ((Math.random() - 0.5) * 2.0);
    }

    public boolean isAlive() {
        return life > 0;
    }

    public void update(float deltaTime) {
        if (life <= 0) return;

        life -= deltaTime;
        position.add(
            velocity.x * deltaTime,
            velocity.y * deltaTime,
            velocity.z * deltaTime
        );
        rotation += rotationSpeed * deltaTime;

        // Fade out
        alpha = Math.max(0, life / maxLife);
    }
}
