package sh.vibecraft.hexcontrol.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 3D camera with orbital rotation (MMB), pan (RMB/WASD), and zoom (scroll).
 * Maintains minimum pitch of 20° above horizon per spec.
 */
public class Camera {

    // Orbital camera parameters
    private float yaw = 0.0f;           // Rotation around Y axis (degrees)
    private float pitch = 60.0f;        // Angle from horizon (degrees) - starts looking down
    private float distance = 15.0f;     // Distance from target

    // Constraints
    private static final float MIN_PITCH = 20.0f;   // Never go below 20° (spec requirement)
    private static final float MAX_PITCH = 89.0f;   // Don't go fully vertical
    private static final float MIN_DISTANCE = 5.0f;
    private static final float MAX_DISTANCE = 60.0f;

    // Target position (what we're looking at)
    private Vector3f target;
    private Vector3f targetSmooth;  // Smoothed target for interpolation

    // Calculated camera position
    private Vector3f position;
    private Vector3f up;

    // Input targets for smooth interpolation
    private float targetYaw = 0.0f;
    private float targetPitch = 60.0f;
    private float targetDistance = 15.0f;
    private Vector3f panTarget;

    // Smoothing
    private float rotationSmoothing = 10.0f;
    private float panSmoothing = 8.0f;
    private float zoomSmoothing = 10.0f;

    // Matrices
    private Matrix4f viewMatrix;
    private Matrix4f projectionMatrix;
    private Matrix4f viewProjectionMatrix;

    // Projection parameters
    private float fov = 45.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 200.0f;
    private float aspectRatio;

    // Window dimensions for unprojection
    private int windowWidth;
    private int windowHeight;

    public Camera(int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;

        target = new Vector3f(0.0f, 0.0f, 0.0f);
        targetSmooth = new Vector3f(0.0f, 0.0f, 0.0f);
        panTarget = new Vector3f(0.0f, 0.0f, 0.0f);
        position = new Vector3f();
        up = new Vector3f(0.0f, 1.0f, 0.0f);

        viewMatrix = new Matrix4f();
        projectionMatrix = new Matrix4f();
        viewProjectionMatrix = new Matrix4f();

        aspectRatio = (float) windowWidth / windowHeight;
        updateProjection(windowWidth, windowHeight);
        calculatePosition();
        updateView();
    }

    public void update(float deltaTime) {
        // Smooth interpolation of rotation
        yaw += (targetYaw - yaw) * rotationSmoothing * deltaTime;
        pitch += (targetPitch - pitch) * rotationSmoothing * deltaTime;
        distance += (targetDistance - distance) * zoomSmoothing * deltaTime;

        // Smooth pan
        targetSmooth.x += (panTarget.x - targetSmooth.x) * panSmoothing * deltaTime;
        targetSmooth.z += (panTarget.z - targetSmooth.z) * panSmoothing * deltaTime;
        target.set(targetSmooth);

        calculatePosition();
        updateView();
    }

    /**
     * Rotate camera (middle mouse drag).
     * @param deltaYaw horizontal rotation in degrees
     * @param deltaPitch vertical rotation in degrees
     */
    public void rotate(float deltaYaw, float deltaPitch) {
        targetYaw += deltaYaw;
        targetPitch += deltaPitch;

        // Clamp pitch to valid range (20° to 89°)
        targetPitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, targetPitch));

        // Normalize yaw to 0-360
        while (targetYaw < 0) targetYaw += 360;
        while (targetYaw >= 360) targetYaw -= 360;
    }

    /**
     * Pan camera (right mouse drag or WASD).
     * Pan is relative to current camera orientation.
     */
    public void pan(float dx, float dz) {
        // Convert camera yaw to radians for direction calculation
        float yawRad = (float) Math.toRadians(yaw);

        // Calculate forward and right vectors in world space (on XZ plane)
        float forwardX = (float) Math.sin(yawRad);
        float forwardZ = (float) Math.cos(yawRad);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) -Math.sin(yawRad);

        // Scale pan speed by distance (pan faster when zoomed out)
        float scale = distance / 15.0f;

        panTarget.x += (rightX * dx + forwardX * dz) * scale;
        panTarget.z += (rightZ * dx + forwardZ * dz) * scale;
    }

    /**
     * Zoom camera (scroll wheel).
     */
    public void zoom(float amount) {
        targetDistance -= amount * 2.0f;
        targetDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, targetDistance));
    }

    /**
     * Focus camera on a specific world position.
     */
    public void focusOn(float x, float z) {
        panTarget.x = x;
        panTarget.z = z;
    }

    private void calculatePosition() {
        // Convert angles to radians
        float pitchRad = (float) Math.toRadians(pitch);
        float yawRad = (float) Math.toRadians(yaw);

        // Calculate camera position on sphere around target
        float horizontalDist = distance * (float) Math.cos(pitchRad);
        float verticalDist = distance * (float) Math.sin(pitchRad);

        position.x = target.x + horizontalDist * (float) Math.sin(yawRad);
        position.y = target.y + verticalDist;
        position.z = target.z + horizontalDist * (float) Math.cos(yawRad);
    }

    public void updateProjection(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
        aspectRatio = (float) width / height;
        projectionMatrix.identity();
        projectionMatrix.perspective((float) Math.toRadians(fov), aspectRatio, nearPlane, farPlane);
        updateViewProjection();
    }

    private void updateView() {
        viewMatrix.identity();
        viewMatrix.lookAt(position, target, up);
        updateViewProjection();
    }

    private void updateViewProjection() {
        projectionMatrix.mul(viewMatrix, viewProjectionMatrix);
    }

    // Getters
    public Matrix4f getViewMatrix() { return viewMatrix; }
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public Matrix4f getViewProjectionMatrix() { return viewProjectionMatrix; }
    public Vector3f getPosition() { return position; }
    public Vector3f getTarget() { return target; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getDistance() { return distance; }

    // For persistence
    public void setYaw(float yaw) { this.yaw = this.targetYaw = yaw; }
    public void setPitch(float pitch) {
        this.pitch = this.targetPitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }
    public void setDistance(float distance) {
        this.distance = this.targetDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }
    public void setTarget(float x, float z) {
        target.x = targetSmooth.x = panTarget.x = x;
        target.z = targetSmooth.z = panTarget.z = z;
    }

    /**
     * Set target zoom distance with smooth interpolation.
     */
    public void setTargetZoom(float zoomDistance) {
        this.targetDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, zoomDistance));
    }

    /**
     * Convert screen coordinates to world ray for picking.
     */
    public Vector3f screenToWorldRay(float screenX, float screenY) {
        // Normalize screen coordinates to [-1, 1]
        float x = (2.0f * screenX) / windowWidth - 1.0f;
        float y = 1.0f - (2.0f * screenY) / windowHeight;

        // Create ray in clip space
        org.joml.Vector4f rayClip = new org.joml.Vector4f(x, y, -1.0f, 1.0f);

        // Transform to eye space
        Matrix4f invProjection = new Matrix4f();
        projectionMatrix.invert(invProjection);
        org.joml.Vector4f rayEye = invProjection.transform(rayClip);
        rayEye.z = -1.0f;
        rayEye.w = 0.0f;

        // Transform to world space
        Matrix4f invView = new Matrix4f();
        viewMatrix.invert(invView);
        org.joml.Vector4f rayWorld = invView.transform(rayEye);

        Vector3f rayDir = new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z);
        rayDir.normalize();

        return rayDir;
    }

    /**
     * Get the world position where a screen point intersects the Y=0 plane.
     */
    public Vector3f screenToWorld(float screenX, float screenY) {
        Vector3f rayDir = screenToWorldRay(screenX, screenY);

        // Intersect with Y=0 plane
        if (Math.abs(rayDir.y) < 0.0001f) {
            return null; // Ray parallel to plane
        }

        float t = -position.y / rayDir.y;
        if (t < 0) {
            return null; // Intersection behind camera
        }

        return new Vector3f(
            position.x + rayDir.x * t,
            0.0f,
            position.z + rayDir.z * t
        );
    }
}
