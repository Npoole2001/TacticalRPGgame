package sh.vibecraft.hexcontrol.ui;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.opengl.GL11.*;

/**
 * Alert/toast notification system.
 * Per spec: alerts for needs-input, test failures, PR ready.
 */
public class AlertSystem {

    private final List<Alert> alerts;
    private final List<Alert> pendingAlerts;

    // Layout settings
    private float margin = 20;
    private float alertWidth = 350;
    private float alertHeight = 70;
    private float alertSpacing = 10;
    private float maxVisibleAlerts = 5;

    // Animation settings
    private float slideSpeed = 8.0f;
    private float defaultDuration = 5.0f;

    public AlertSystem() {
        this.alerts = new CopyOnWriteArrayList<>();
        this.pendingAlerts = new ArrayList<>();
    }

    /**
     * Show an info alert.
     */
    public void info(String title, String message) {
        addAlert(new Alert(AlertType.INFO, title, message, defaultDuration));
    }

    /**
     * Show a success alert.
     */
    public void success(String title, String message) {
        addAlert(new Alert(AlertType.SUCCESS, title, message, defaultDuration));
    }

    /**
     * Show a warning alert.
     */
    public void warning(String title, String message) {
        addAlert(new Alert(AlertType.WARNING, title, message, defaultDuration + 2));
    }

    /**
     * Show an error alert.
     */
    public void error(String title, String message) {
        addAlert(new Alert(AlertType.ERROR, title, message, defaultDuration + 5));
    }

    /**
     * Show a needs-input alert (persistent until dismissed).
     */
    public Alert needsInput(String agentName, String question) {
        Alert alert = new Alert(AlertType.NEEDS_INPUT, agentName + " needs input", question, -1);
        alert.persistent = true;
        addAlert(alert);
        return alert;
    }

    /**
     * Show a PR ready alert.
     */
    public void prReady(String agentName, String prTitle) {
        addAlert(new Alert(AlertType.PR_READY, "PR Ready: " + agentName, prTitle, defaultDuration + 3));
    }

    /**
     * Show a test failure alert.
     */
    public void testFailure(String agentName, String testInfo) {
        addAlert(new Alert(AlertType.TEST_FAILURE, "Tests Failed: " + agentName, testInfo, defaultDuration + 5));
    }

    /**
     * Show a build failure alert.
     */
    public void buildFailure(String agentName, String errorInfo) {
        addAlert(new Alert(AlertType.BUILD_FAILURE, "Build Failed: " + agentName, errorInfo, defaultDuration + 5));
    }

    /**
     * Show an agent status change alert.
     */
    public void statusChange(String agentName, String oldStatus, String newStatus) {
        AlertType type = switch (newStatus.toLowerCase()) {
            case "error" -> AlertType.ERROR;
            case "blocked" -> AlertType.WARNING;
            case "running" -> AlertType.INFO;
            default -> AlertType.INFO;
        };
        addAlert(new Alert(type, agentName, oldStatus + " → " + newStatus, 3.0f));
    }

    private void addAlert(Alert alert) {
        // Limit total alerts
        while (alerts.size() >= maxVisibleAlerts) {
            Alert oldest = alerts.get(0);
            oldest.dismissing = true;
        }
        alerts.add(alert);
    }

    /**
     * Dismiss an alert by reference.
     */
    public void dismiss(Alert alert) {
        alert.dismissing = true;
    }

    /**
     * Dismiss all alerts.
     */
    public void dismissAll() {
        for (Alert alert : alerts) {
            alert.dismissing = true;
        }
    }

    public void update(float deltaTime) {
        List<Alert> toRemove = new ArrayList<>();

        float targetY = margin;
        for (int i = alerts.size() - 1; i >= 0; i--) {
            Alert alert = alerts.get(i);

            // Update lifetime
            if (!alert.persistent && alert.duration > 0) {
                alert.lifetime += deltaTime;
                if (alert.lifetime >= alert.duration) {
                    alert.dismissing = true;
                }
            }

            // Update animation
            if (alert.dismissing) {
                alert.slideProgress -= slideSpeed * deltaTime;
                if (alert.slideProgress <= 0) {
                    toRemove.add(alert);
                    continue;
                }
            } else {
                alert.slideProgress = Math.min(1.0f, alert.slideProgress + slideSpeed * deltaTime);
            }

            // Update Y position (stack from top)
            alert.targetY = targetY;
            alert.currentY += (alert.targetY - alert.currentY) * slideSpeed * deltaTime;
            targetY += alertHeight + alertSpacing;
        }

        alerts.removeAll(toRemove);
    }

    public void render(int windowWidth, int windowHeight) {
        if (alerts.isEmpty()) return;

        // Setup 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);

        for (Alert alert : alerts) {
            renderAlert(alert, windowWidth, windowHeight);
        }

        glEnable(GL_DEPTH_TEST);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void renderAlert(Alert alert, int windowWidth, int windowHeight) {
        float alpha = alert.slideProgress;

        // Slide in from right
        float slideOffset = (1.0f - alert.slideProgress) * (alertWidth + margin);
        float x = windowWidth - alertWidth - margin + slideOffset;
        float y = alert.currentY;

        // Get colors based on type
        Vector3f bgColor = getBackgroundColor(alert.type);
        Vector3f accentColor = getAccentColor(alert.type);

        // Background
        glColor4f(bgColor.x, bgColor.y, bgColor.z, 0.95f * alpha);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + alertWidth, y);
        glVertex2f(x + alertWidth, y + alertHeight);
        glVertex2f(x, y + alertHeight);
        glEnd();

        // Left accent bar
        glColor4f(accentColor.x, accentColor.y, accentColor.z, alpha);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + 4, y);
        glVertex2f(x + 4, y + alertHeight);
        glVertex2f(x, y + alertHeight);
        glEnd();

        // Border
        glColor4f(accentColor.x * 0.8f, accentColor.y * 0.8f, accentColor.z * 0.8f, alpha * 0.5f);
        glLineWidth(1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + alertWidth, y);
        glVertex2f(x + alertWidth, y + alertHeight);
        glVertex2f(x, y + alertHeight);
        glEnd();

        // Icon based on type
        renderIcon(alert.type, x + 15, y + alertHeight / 2, alpha);

        // Progress bar for timed alerts
        if (!alert.persistent && alert.duration > 0) {
            float progress = 1.0f - (alert.lifetime / alert.duration);
            glColor4f(accentColor.x, accentColor.y, accentColor.z, alpha * 0.3f);
            glBegin(GL_QUADS);
            glVertex2f(x + 4, y + alertHeight - 3);
            glVertex2f(x + 4 + (alertWidth - 4) * progress, y + alertHeight - 3);
            glVertex2f(x + 4 + (alertWidth - 4) * progress, y + alertHeight);
            glVertex2f(x + 4, y + alertHeight);
            glEnd();
        }

        // Close button
        float closeX = x + alertWidth - 25;
        float closeY = y + 10;
        glColor4f(0.6f, 0.6f, 0.6f, alpha);
        glLineWidth(2.0f);
        glBegin(GL_LINES);
        glVertex2f(closeX, closeY);
        glVertex2f(closeX + 10, closeY + 10);
        glVertex2f(closeX + 10, closeY);
        glVertex2f(closeX, closeY + 10);
        glEnd();

        // Action button for needs-input alerts
        if (alert.type == AlertType.NEEDS_INPUT) {
            float btnX = x + alertWidth - 80;
            float btnY = y + alertHeight - 28;
            float btnW = 60;
            float btnH = 20;

            glColor4f(accentColor.x, accentColor.y, accentColor.z, alpha * 0.8f);
            glBegin(GL_QUADS);
            glVertex2f(btnX, btnY);
            glVertex2f(btnX + btnW, btnY);
            glVertex2f(btnX + btnW, btnY + btnH);
            glVertex2f(btnX, btnY + btnH);
            glEnd();
        }
    }

    private void renderIcon(AlertType type, float x, float y, float alpha) {
        Vector3f color = getAccentColor(type);
        glColor4f(color.x, color.y, color.z, alpha);

        switch (type) {
            case INFO -> {
                // Circle with 'i'
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 16; i++) {
                    float angle = (float)(Math.PI * 2 * i / 16);
                    glVertex2f(x + (float)Math.cos(angle) * 10, y + (float)Math.sin(angle) * 10);
                }
                glEnd();
                glBegin(GL_LINES);
                glVertex2f(x, y - 3);
                glVertex2f(x, y + 5);
                glEnd();
                glBegin(GL_POINTS);
                glVertex2f(x, y - 6);
                glEnd();
            }
            case SUCCESS -> {
                // Checkmark
                glLineWidth(3.0f);
                glBegin(GL_LINE_STRIP);
                glVertex2f(x - 8, y);
                glVertex2f(x - 2, y + 6);
                glVertex2f(x + 8, y - 6);
                glEnd();
            }
            case WARNING, NEEDS_INPUT -> {
                // Triangle with !
                glBegin(GL_LINE_LOOP);
                glVertex2f(x, y - 10);
                glVertex2f(x - 10, y + 8);
                glVertex2f(x + 10, y + 8);
                glEnd();
                glBegin(GL_LINES);
                glVertex2f(x, y - 4);
                glVertex2f(x, y + 2);
                glEnd();
                glBegin(GL_POINTS);
                glVertex2f(x, y + 5);
                glEnd();
            }
            case ERROR, TEST_FAILURE, BUILD_FAILURE -> {
                // X in circle
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 16; i++) {
                    float angle = (float)(Math.PI * 2 * i / 16);
                    glVertex2f(x + (float)Math.cos(angle) * 10, y + (float)Math.sin(angle) * 10);
                }
                glEnd();
                glLineWidth(2.0f);
                glBegin(GL_LINES);
                glVertex2f(x - 5, y - 5);
                glVertex2f(x + 5, y + 5);
                glVertex2f(x + 5, y - 5);
                glVertex2f(x - 5, y + 5);
                glEnd();
            }
            case PR_READY -> {
                // Git branch icon
                glBegin(GL_LINE_STRIP);
                glVertex2f(x - 5, y + 8);
                glVertex2f(x - 5, y - 2);
                glVertex2f(x, y - 5);
                glVertex2f(x + 5, y - 2);
                glEnd();
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 8; i++) {
                    float angle = (float)(Math.PI * 2 * i / 8);
                    glVertex2f(x + 5 + (float)Math.cos(angle) * 4, y - 2 + (float)Math.sin(angle) * 4);
                }
                glEnd();
            }
        }
    }

    private Vector3f getBackgroundColor(AlertType type) {
        return switch (type) {
            case INFO -> new Vector3f(0.1f, 0.12f, 0.18f);
            case SUCCESS -> new Vector3f(0.08f, 0.15f, 0.1f);
            case WARNING, NEEDS_INPUT -> new Vector3f(0.18f, 0.15f, 0.08f);
            case ERROR, TEST_FAILURE, BUILD_FAILURE -> new Vector3f(0.18f, 0.08f, 0.08f);
            case PR_READY -> new Vector3f(0.1f, 0.15f, 0.18f);
        };
    }

    private Vector3f getAccentColor(AlertType type) {
        return switch (type) {
            case INFO -> new Vector3f(0.3f, 0.5f, 0.9f);
            case SUCCESS -> new Vector3f(0.2f, 0.8f, 0.3f);
            case WARNING, NEEDS_INPUT -> new Vector3f(0.9f, 0.7f, 0.2f);
            case ERROR, TEST_FAILURE, BUILD_FAILURE -> new Vector3f(0.9f, 0.3f, 0.3f);
            case PR_READY -> new Vector3f(0.4f, 0.7f, 0.9f);
        };
    }

    /**
     * Handle click on alerts. Returns true if click was handled.
     */
    public boolean handleClick(float mouseX, float mouseY, int windowWidth, int windowHeight) {
        for (int i = alerts.size() - 1; i >= 0; i--) {
            Alert alert = alerts.get(i);

            float slideOffset = (1.0f - alert.slideProgress) * (alertWidth + margin);
            float x = windowWidth - alertWidth - margin + slideOffset;
            float y = alert.currentY;

            if (mouseX >= x && mouseX <= x + alertWidth &&
                mouseY >= y && mouseY <= y + alertHeight) {

                // Close button
                float closeX = x + alertWidth - 25;
                float closeY = y + 10;
                if (mouseX >= closeX && mouseX <= closeX + 10 &&
                    mouseY >= closeY && mouseY <= closeY + 10) {
                    alert.dismissing = true;
                    return true;
                }

                // Action button for needs-input
                if (alert.type == AlertType.NEEDS_INPUT) {
                    float btnX = x + alertWidth - 80;
                    float btnY = y + alertHeight - 28;
                    if (mouseX >= btnX && mouseX <= btnX + 60 &&
                        mouseY >= btnY && mouseY <= btnY + 20) {
                        if (alert.actionCallback != null) {
                            alert.actionCallback.run();
                        }
                        alert.dismissing = true;
                        return true;
                    }
                }

                // Clicking anywhere else on alert focuses it
                if (alert.focusCallback != null) {
                    alert.focusCallback.run();
                }
                return true;
            }
        }
        return false;
    }

    public List<Alert> getAlerts() {
        return alerts;
    }

    public boolean hasAlerts() {
        return !alerts.isEmpty();
    }

    public int getAlertCount() {
        return alerts.size();
    }

    /**
     * Alert types.
     */
    public enum AlertType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR,
        NEEDS_INPUT,
        PR_READY,
        TEST_FAILURE,
        BUILD_FAILURE
    }

    /**
     * Individual alert instance.
     */
    public static class Alert {
        public final AlertType type;
        public final String title;
        public final String message;
        public final float duration;
        public final long createdAt;

        public float lifetime = 0;
        public float slideProgress = 0;
        public float currentY = -100;
        public float targetY = 0;
        public boolean dismissing = false;
        public boolean persistent = false;

        public Runnable actionCallback;
        public Runnable focusCallback;

        public Alert(AlertType type, String title, String message, float duration) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.duration = duration;
            this.createdAt = System.currentTimeMillis();
        }

        public void setActionCallback(Runnable callback) {
            this.actionCallback = callback;
        }

        public void setFocusCallback(Runnable callback) {
            this.focusCallback = callback;
        }
    }
}
