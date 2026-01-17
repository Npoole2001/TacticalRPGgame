package sh.vibecraft.hexcontrol.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 3D camera with support for panning and zooming.
 * Positioned above the hex grid looking down.
 */
public class Camera {

    // Camera position and target
    private Vector3f position;
    private Vector3f target;
    private Vector3f up;

    // Camera properties
    private float zoom = 10.0f;
    private float minZoom = 3.0f;
    private float maxZoom = 50.0f;
    private float panSpeed = 10.0f;

    // Panning state
    private float targetX = 0.0f;
    private float targetZ = 0.0f;
    private float currentX = 0.0f;
    private float currentZ = 0.0f;
    private float smoothing = 8.0f;

    // Matrices
    private Matrix4f viewMatrix;
    private Matrix4f projectionMatrix;
    private Matrix4f viewProjectionMatrix;

    // Projection parameters
    private float fov = 45.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 100.0f;
    private float aspectRatio;

    public Camera(int windowWidth, int windowHeight) {
        position = new Vector3f(0.0f, zoom, zoom * 0.5f);
        target = new Vector3f(0.0f, 0.0f, 0.0f);
        up = new Vector3f(0.0f, 1.0f, 0.0f);

        viewMatrix = new Matrix4f();
        projectionMatrix = new Matrix4f();
        viewProjectionMatrix = new Matrix4f();

        aspectRatio = (float) windowWidth / windowHeight;
        updateProjection(windowWidth, windowHeight);
        updateView();
    }

    public void update(float deltaTime) {
        // Smooth camera movement
        currentX += (targetX - currentX) * smoothing * deltaTime;
        currentZ += (targetZ - currentZ) * smoothing * deltaTime;

        // Update camera position (looking down at an angle)
        position.set(currentX, zoom, currentZ + zoom * 0.5f);
        target.set(currentX, 0.0f, currentZ);

        updateView();
    }

    public void pan(float dx, float dz) {
        float scale = zoom / 10.0f; // Pan faster when zoomed out
        targetX += dx * panSpeed * scale;
        targetZ += dz * panSpeed * scale;
    }

    public void zoom(float amount) {
        zoom -= amount * 2.0f;
        zoom = Math.max(minZoom, Math.min(maxZoom, zoom));
    }

    public void updateProjection(int width, int height) {
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

    public Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public Matrix4f getViewProjectionMatrix() {
        return viewProjectionMatrix;
    }

    public Vector3f getPosition() {
        return position;
    }

    public float getZoom() {
        return zoom;
    }

    /**
     * Convert screen coordinates to world ray for picking.
     */
    public Vector3f screenToWorldRay(float screenX, float screenY, int windowWidth, int windowHeight) {
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
    public Vector3f screenToWorld(float screenX, float screenY, int windowWidth, int windowHeight) {
        Vector3f rayDir = screenToWorldRay(screenX, screenY, windowWidth, windowHeight);

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
