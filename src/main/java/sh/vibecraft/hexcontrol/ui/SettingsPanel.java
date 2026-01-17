package sh.vibecraft.hexcontrol.ui;

import org.joml.Vector3f;
import sh.vibecraft.hexcontrol.agent.AgentType;
import sh.vibecraft.hexcontrol.security.KeychainStore;

import java.util.*;

import static org.lwjgl.opengl.GL11.*;

/**
 * Settings panel for configuring API keys, providers, and application settings.
 * Per spec: add/remove keys, test connection, show last-used timestamp.
 */
public class SettingsPanel {

    private boolean visible = false;
    private float openProgress = 0.0f;
    private float animationSpeed = 8.0f;

    // Panel dimensions
    private float panelWidth = 600;
    private float panelHeight = 500;
    private float panelX, panelY;

    // Current tab
    private SettingsTab currentTab = SettingsTab.PROVIDERS;

    // Provider settings
    private final List<ProviderConfig> providers;
    private int selectedProviderIndex = -1;
    private int hoveredProviderIndex = -1;

    // Input state for API key entry
    private boolean enteringApiKey = false;
    private StringBuilder apiKeyInput = new StringBuilder();
    private AgentType keyEntryProvider = null;

    // General settings
    private boolean privacyModeEnabled = false;
    private boolean dryRunModeDefault = true;
    private boolean autoSaveEnabled = true;
    private int autoSaveIntervalSeconds = 45;
    private int maxFilesPerChange = 10;
    private int maxLinesPerChange = 400;
    private int maxRuntimeMinutes = 10;

    // Keybinds
    private final Map<String, String> keybinds;

    // Listeners
    private final List<SettingsListener> listeners;

    // Keychain reference
    private KeychainStore keychainStore;

    public SettingsPanel() {
        this.providers = new ArrayList<>();
        this.keybinds = new LinkedHashMap<>();
        this.listeners = new ArrayList<>();

        initializeDefaultProviders();
        initializeDefaultKeybinds();
    }

    private void initializeDefaultProviders() {
        providers.add(new ProviderConfig(AgentType.CLAUDE, "Anthropic (Claude)",
            "https://api.anthropic.com", false, 0, "Not configured"));
        providers.add(new ProviderConfig(AgentType.CHATGPT, "OpenAI (ChatGPT)",
            "https://api.openai.com", false, 0, "Not configured"));
        providers.add(new ProviderConfig(AgentType.GEMINI, "Google (Gemini)",
            "https://generativelanguage.googleapis.com", false, 0, "Not configured"));
        providers.add(new ProviderConfig(AgentType.COPILOT, "GitHub Copilot",
            "https://api.github.com", false, 0, "Not configured"));
        providers.add(new ProviderConfig(AgentType.LLAMA, "Local (Llama)",
            "http://localhost:11434", false, 0, "Not configured"));
        providers.add(new ProviderConfig(AgentType.MISTRAL, "Mistral AI",
            "https://api.mistral.ai", false, 0, "Not configured"));
    }

    private void initializeDefaultKeybinds() {
        keybinds.put("Pan Up", "W");
        keybinds.put("Pan Down", "S");
        keybinds.put("Pan Left", "A");
        keybinds.put("Pan Right", "D");
        keybinds.put("Zoom In", "Scroll Up");
        keybinds.put("Zoom Out", "Scroll Down");
        keybinds.put("Rotate Camera", "MMB Drag");
        keybinds.put("Pan Camera", "RMB Drag");
        keybinds.put("Select Tile", "LMB");
        keybinds.put("Open Settings", "Comma (,)");
        keybinds.put("Toggle Demo", "F5");
        keybinds.put("Search Agents", "Ctrl+F");
        keybinds.put("Close Panel", "Escape");
    }

    public void setKeychainStore(KeychainStore keychainStore) {
        this.keychainStore = keychainStore;
        loadSavedKeys();
    }

    private void loadSavedKeys() {
        if (keychainStore == null || !keychainStore.isInitialized()) return;

        for (ProviderConfig provider : providers) {
            String keyName = "api_key_" + provider.type.name().toLowerCase();
            if (keychainStore.hasSecret(keyName)) {
                provider.configured = true;
                provider.statusMessage = "Key configured (secured)";
            }
        }
    }

    public void open() {
        visible = true;
    }

    public void close() {
        visible = false;
        enteringApiKey = false;
        apiKeyInput.setLength(0);
    }

    public void toggle() {
        if (visible) close();
        else open();
    }

    public boolean isVisible() {
        return visible || openProgress > 0.01f;
    }

    public void update(float deltaTime) {
        float target = visible ? 1.0f : 0.0f;
        openProgress += (target - openProgress) * animationSpeed * deltaTime;

        if (openProgress < 0.01f && !visible) {
            openProgress = 0.0f;
        }
    }

    public void render(int windowWidth, int windowHeight) {
        if (!isVisible()) return;

        // Center the panel
        panelX = (windowWidth - panelWidth) / 2.0f;
        panelY = (windowHeight - panelHeight) / 2.0f;

        // Setup 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);

        float alpha = openProgress;
        float scale = 0.9f + 0.1f * openProgress;

        float centerX = panelX + panelWidth / 2;
        float centerY = panelY + panelHeight / 2;
        float drawX = centerX - (panelWidth * scale) / 2;
        float drawY = centerY - (panelHeight * scale) / 2;
        float drawW = panelWidth * scale;
        float drawH = panelHeight * scale;

        // Overlay background
        glColor4f(0.0f, 0.0f, 0.0f, 0.7f * alpha);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(windowWidth, 0);
        glVertex2f(windowWidth, windowHeight);
        glVertex2f(0, windowHeight);
        glEnd();

        // Panel background
        glColor4f(0.1f, 0.1f, 0.12f, 0.98f * alpha);
        glBegin(GL_QUADS);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + drawH);
        glVertex2f(drawX, drawY + drawH);
        glEnd();

        // Panel border
        glColor4f(0.4f, 0.4f, 0.45f, alpha);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + drawH);
        glVertex2f(drawX, drawY + drawH);
        glEnd();

        // Header bar
        glColor4f(0.15f, 0.15f, 0.18f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(drawX, drawY);
        glVertex2f(drawX + drawW, drawY);
        glVertex2f(drawX + drawW, drawY + 40);
        glVertex2f(drawX, drawY + 40);
        glEnd();

        // Close button (X)
        float closeX = drawX + drawW - 30;
        float closeY = drawY + 12;
        glColor4f(0.6f, 0.3f, 0.3f, alpha);
        glLineWidth(2.0f);
        glBegin(GL_LINES);
        glVertex2f(closeX, closeY);
        glVertex2f(closeX + 16, closeY + 16);
        glVertex2f(closeX + 16, closeY);
        glVertex2f(closeX, closeY + 16);
        glEnd();

        // Tab bar
        float tabY = drawY + 50;
        float tabWidth = drawW / SettingsTab.values().length;
        for (int i = 0; i < SettingsTab.values().length; i++) {
            SettingsTab tab = SettingsTab.values()[i];
            float tabX = drawX + i * tabWidth;

            if (tab == currentTab) {
                glColor4f(0.25f, 0.25f, 0.3f, alpha);
            } else {
                glColor4f(0.15f, 0.15f, 0.18f, alpha);
            }
            glBegin(GL_QUADS);
            glVertex2f(tabX, tabY);
            glVertex2f(tabX + tabWidth - 2, tabY);
            glVertex2f(tabX + tabWidth - 2, tabY + 30);
            glVertex2f(tabX, tabY + 30);
            glEnd();

            // Tab indicator
            if (tab == currentTab) {
                glColor4f(0.4f, 0.6f, 0.9f, alpha);
                glBegin(GL_QUADS);
                glVertex2f(tabX, tabY + 28);
                glVertex2f(tabX + tabWidth - 2, tabY + 28);
                glVertex2f(tabX + tabWidth - 2, tabY + 30);
                glVertex2f(tabX, tabY + 30);
                glEnd();
            }
        }

        // Content area
        float contentY = tabY + 40;
        float contentH = drawH - (contentY - drawY) - 20;

        switch (currentTab) {
            case PROVIDERS -> renderProvidersTab(drawX + 15, contentY, drawW - 30, contentH, alpha);
            case GENERAL -> renderGeneralTab(drawX + 15, contentY, drawW - 30, contentH, alpha);
            case KEYBINDS -> renderKeybindsTab(drawX + 15, contentY, drawW - 30, contentH, alpha);
            case ABOUT -> renderAboutTab(drawX + 15, contentY, drawW - 30, contentH, alpha);
        }

        glEnable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void renderProvidersTab(float x, float y, float w, float h, float alpha) {
        float itemHeight = 55;
        float itemY = y;

        for (int i = 0; i < providers.size(); i++) {
            ProviderConfig provider = providers.get(i);

            // Item background
            boolean hovered = (i == hoveredProviderIndex);
            boolean selected = (i == selectedProviderIndex);

            if (selected) {
                glColor4f(0.2f, 0.25f, 0.35f, alpha);
            } else if (hovered) {
                glColor4f(0.18f, 0.18f, 0.22f, alpha);
            } else {
                glColor4f(0.12f, 0.12f, 0.15f, alpha);
            }

            glBegin(GL_QUADS);
            glVertex2f(x, itemY);
            glVertex2f(x + w, itemY);
            glVertex2f(x + w, itemY + itemHeight - 5);
            glVertex2f(x, itemY + itemHeight - 5);
            glEnd();

            // Provider color indicator
            Vector3f color = provider.type.getDefaultColor();
            glColor4f(color.x, color.y, color.z, alpha);
            glBegin(GL_QUADS);
            glVertex2f(x, itemY);
            glVertex2f(x + 5, itemY);
            glVertex2f(x + 5, itemY + itemHeight - 5);
            glVertex2f(x, itemY + itemHeight - 5);
            glEnd();

            // Status indicator
            if (provider.configured) {
                glColor4f(0.2f, 0.8f, 0.3f, alpha);  // Green = configured
            } else {
                glColor4f(0.5f, 0.5f, 0.5f, alpha);  // Gray = not configured
            }
            float dotX = x + w - 20;
            float dotY = itemY + itemHeight / 2 - 5;
            glBegin(GL_QUADS);
            glVertex2f(dotX, dotY);
            glVertex2f(dotX + 10, dotY);
            glVertex2f(dotX + 10, dotY + 10);
            glVertex2f(dotX, dotY + 10);
            glEnd();

            // Action buttons (Configure / Test / Remove)
            if (selected || hovered) {
                float btnY = itemY + 30;
                float btnW = 60;
                float btnH = 18;

                // Configure button
                float configX = x + w - 200;
                glColor4f(0.3f, 0.4f, 0.6f, alpha);
                glBegin(GL_QUADS);
                glVertex2f(configX, btnY);
                glVertex2f(configX + btnW, btnY);
                glVertex2f(configX + btnW, btnY + btnH);
                glVertex2f(configX, btnY + btnH);
                glEnd();

                // Test button
                float testX = configX + btnW + 5;
                glColor4f(0.3f, 0.5f, 0.4f, alpha);
                glBegin(GL_QUADS);
                glVertex2f(testX, btnY);
                glVertex2f(testX + btnW, btnY);
                glVertex2f(testX + btnW, btnY + btnH);
                glVertex2f(testX, btnY + btnH);
                glEnd();

                // Remove button
                float removeX = testX + btnW + 5;
                glColor4f(0.5f, 0.3f, 0.3f, alpha);
                glBegin(GL_QUADS);
                glVertex2f(removeX, btnY);
                glVertex2f(removeX + btnW, btnY);
                glVertex2f(removeX + btnW, btnY + btnH);
                glVertex2f(removeX, btnY + btnH);
                glEnd();
            }

            itemY += itemHeight;
        }

        // API Key entry overlay
        if (enteringApiKey && keyEntryProvider != null) {
            renderApiKeyEntry(x, y, w, h, alpha);
        }
    }

    private void renderApiKeyEntry(float x, float y, float w, float h, float alpha) {
        // Darken background
        glColor4f(0.0f, 0.0f, 0.0f, 0.8f * alpha);
        glBegin(GL_QUADS);
        glVertex2f(x - 15, y - 40);
        glVertex2f(x + w + 15, y - 40);
        glVertex2f(x + w + 15, y + h);
        glVertex2f(x - 15, y + h);
        glEnd();

        // Entry dialog
        float dialogW = 400;
        float dialogH = 150;
        float dialogX = x + (w - dialogW) / 2;
        float dialogY = y + (h - dialogH) / 2;

        glColor4f(0.15f, 0.15f, 0.2f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(dialogX, dialogY);
        glVertex2f(dialogX + dialogW, dialogY);
        glVertex2f(dialogX + dialogW, dialogY + dialogH);
        glVertex2f(dialogX, dialogY + dialogH);
        glEnd();

        // Border
        glColor4f(0.4f, 0.5f, 0.7f, alpha);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(dialogX, dialogY);
        glVertex2f(dialogX + dialogW, dialogY);
        glVertex2f(dialogX + dialogW, dialogY + dialogH);
        glVertex2f(dialogX, dialogY + dialogH);
        glEnd();

        // Input field
        float inputX = dialogX + 20;
        float inputY = dialogY + 50;
        float inputW = dialogW - 40;
        float inputH = 30;

        glColor4f(0.08f, 0.08f, 0.1f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(inputX, inputY);
        glVertex2f(inputX + inputW, inputY);
        glVertex2f(inputX + inputW, inputY + inputH);
        glVertex2f(inputX, inputY + inputH);
        glEnd();

        // Input border
        glColor4f(0.4f, 0.4f, 0.5f, alpha);
        glBegin(GL_LINE_LOOP);
        glVertex2f(inputX, inputY);
        glVertex2f(inputX + inputW, inputY);
        glVertex2f(inputX + inputW, inputY + inputH);
        glVertex2f(inputX, inputY + inputH);
        glEnd();

        // Mask display (show dots for security)
        String masked = "*".repeat(Math.min(apiKeyInput.length(), 40));
        // Note: actual text rendering would need NanoVG

        // Save / Cancel buttons
        float btnW = 80;
        float btnH = 25;
        float btnY = dialogY + dialogH - 40;

        // Save button
        glColor4f(0.3f, 0.5f, 0.4f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(dialogX + dialogW / 2 - btnW - 10, btnY);
        glVertex2f(dialogX + dialogW / 2 - 10, btnY);
        glVertex2f(dialogX + dialogW / 2 - 10, btnY + btnH);
        glVertex2f(dialogX + dialogW / 2 - btnW - 10, btnY + btnH);
        glEnd();

        // Cancel button
        glColor4f(0.4f, 0.3f, 0.3f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(dialogX + dialogW / 2 + 10, btnY);
        glVertex2f(dialogX + dialogW / 2 + btnW + 10, btnY);
        glVertex2f(dialogX + dialogW / 2 + btnW + 10, btnY + btnH);
        glVertex2f(dialogX + dialogW / 2 + 10, btnY + btnH);
        glEnd();
    }

    private void renderGeneralTab(float x, float y, float w, float h, float alpha) {
        float itemHeight = 40;
        float itemY = y;

        // Privacy Mode toggle
        renderToggleSetting(x, itemY, w, "Privacy Mode", "Hide sensitive paths and usernames",
            privacyModeEnabled, alpha);
        itemY += itemHeight;

        // Dry-Run Mode toggle
        renderToggleSetting(x, itemY, w, "Dry-Run Default", "New agents start in dry-run mode",
            dryRunModeDefault, alpha);
        itemY += itemHeight;

        // Auto-save toggle
        renderToggleSetting(x, itemY, w, "Auto-Save", "Automatically save state periodically",
            autoSaveEnabled, alpha);
        itemY += itemHeight;

        // Separator
        itemY += 10;
        glColor4f(0.25f, 0.25f, 0.3f, alpha);
        glBegin(GL_LINES);
        glVertex2f(x, itemY);
        glVertex2f(x + w, itemY);
        glEnd();
        itemY += 20;

        // Budget settings
        renderSliderSetting(x, itemY, w, "Max Files/Change", maxFilesPerChange, 1, 50, alpha);
        itemY += itemHeight;

        renderSliderSetting(x, itemY, w, "Max Lines/Change", maxLinesPerChange, 50, 2000, alpha);
        itemY += itemHeight;

        renderSliderSetting(x, itemY, w, "Max Runtime (min)", maxRuntimeMinutes, 1, 60, alpha);
        itemY += itemHeight;

        renderSliderSetting(x, itemY, w, "Auto-Save Interval (s)", autoSaveIntervalSeconds, 15, 120, alpha);
    }

    private void renderToggleSetting(float x, float y, float w, String label, String description,
                                      boolean enabled, float alpha) {
        // Background
        glColor4f(0.12f, 0.12f, 0.15f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + 35);
        glVertex2f(x, y + 35);
        glEnd();

        // Toggle switch
        float toggleX = x + w - 50;
        float toggleY = y + 8;
        float toggleW = 40;
        float toggleH = 20;

        // Track
        if (enabled) {
            glColor4f(0.2f, 0.6f, 0.4f, alpha);
        } else {
            glColor4f(0.3f, 0.3f, 0.35f, alpha);
        }
        glBegin(GL_QUADS);
        glVertex2f(toggleX, toggleY);
        glVertex2f(toggleX + toggleW, toggleY);
        glVertex2f(toggleX + toggleW, toggleY + toggleH);
        glVertex2f(toggleX, toggleY + toggleH);
        glEnd();

        // Thumb
        float thumbX = enabled ? toggleX + toggleW - 18 : toggleX + 2;
        glColor4f(0.9f, 0.9f, 0.9f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(thumbX, toggleY + 2);
        glVertex2f(thumbX + 16, toggleY + 2);
        glVertex2f(thumbX + 16, toggleY + toggleH - 2);
        glVertex2f(thumbX, toggleY + toggleH - 2);
        glEnd();
    }

    private void renderSliderSetting(float x, float y, float w, String label, int value,
                                      int min, int max, float alpha) {
        // Background
        glColor4f(0.12f, 0.12f, 0.15f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + 35);
        glVertex2f(x, y + 35);
        glEnd();

        // Slider track
        float sliderX = x + w - 200;
        float sliderY = y + 14;
        float sliderW = 150;
        float sliderH = 8;

        glColor4f(0.25f, 0.25f, 0.3f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(sliderX, sliderY);
        glVertex2f(sliderX + sliderW, sliderY);
        glVertex2f(sliderX + sliderW, sliderY + sliderH);
        glVertex2f(sliderX, sliderY + sliderH);
        glEnd();

        // Slider fill
        float fillRatio = (float)(value - min) / (max - min);
        glColor4f(0.3f, 0.5f, 0.7f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(sliderX, sliderY);
        glVertex2f(sliderX + sliderW * fillRatio, sliderY);
        glVertex2f(sliderX + sliderW * fillRatio, sliderY + sliderH);
        glVertex2f(sliderX, sliderY + sliderH);
        glEnd();

        // Slider thumb
        float thumbX = sliderX + sliderW * fillRatio - 5;
        glColor4f(0.9f, 0.9f, 0.9f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(thumbX, sliderY - 3);
        glVertex2f(thumbX + 10, sliderY - 3);
        glVertex2f(thumbX + 10, sliderY + sliderH + 3);
        glVertex2f(thumbX, sliderY + sliderH + 3);
        glEnd();

        // Value display
        float valueX = x + w - 40;
        glColor4f(0.7f, 0.7f, 0.7f, alpha);
        // Note: actual text would need NanoVG
    }

    private void renderKeybindsTab(float x, float y, float w, float h, float alpha) {
        float itemHeight = 32;
        float itemY = y;

        int index = 0;
        for (Map.Entry<String, String> entry : keybinds.entrySet()) {
            // Alternating background
            if (index % 2 == 0) {
                glColor4f(0.12f, 0.12f, 0.15f, alpha);
            } else {
                glColor4f(0.1f, 0.1f, 0.12f, alpha);
            }
            glBegin(GL_QUADS);
            glVertex2f(x, itemY);
            glVertex2f(x + w, itemY);
            glVertex2f(x + w, itemY + itemHeight - 2);
            glVertex2f(x, itemY + itemHeight - 2);
            glEnd();

            // Key binding box
            float bindX = x + w - 120;
            float bindY = itemY + 5;
            float bindW = 100;
            float bindH = itemHeight - 12;

            glColor4f(0.2f, 0.2f, 0.25f, alpha);
            glBegin(GL_QUADS);
            glVertex2f(bindX, bindY);
            glVertex2f(bindX + bindW, bindY);
            glVertex2f(bindX + bindW, bindY + bindH);
            glVertex2f(bindX, bindY + bindH);
            glEnd();

            itemY += itemHeight;
            index++;

            if (itemY > y + h - itemHeight) break;  // Don't overflow
        }
    }

    private void renderAboutTab(float x, float y, float w, float h, float alpha) {
        // Version info box
        glColor4f(0.12f, 0.12f, 0.15f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + 100);
        glVertex2f(x, y + 100);
        glEnd();

        // Logo placeholder
        glColor4f(0.3f, 0.5f, 0.7f, alpha);
        float logoSize = 60;
        float logoX = x + 20;
        float logoY = y + 20;

        // Simple hex logo
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 6; i++) {
            float angle = (float)(Math.PI / 3.0 * i - Math.PI / 6.0);
            glVertex2f(logoX + logoSize / 2 + (float)Math.cos(angle) * logoSize / 2,
                       logoY + logoSize / 2 + (float)Math.sin(angle) * logoSize / 2);
        }
        glEnd();

        // Links section
        float linksY = y + 120;
        String[] links = {
            "GitHub: github.com/example/hexcontrol",
            "Documentation: docs.hexcontrol.dev",
            "Report Issues: github.com/example/hexcontrol/issues"
        };

        for (String link : links) {
            glColor4f(0.15f, 0.15f, 0.18f, alpha);
            glBegin(GL_QUADS);
            glVertex2f(x, linksY);
            glVertex2f(x + w, linksY);
            glVertex2f(x + w, linksY + 28);
            glVertex2f(x, linksY + 28);
            glEnd();
            linksY += 35;
        }
    }

    public boolean handleClick(float mouseX, float mouseY) {
        if (!isVisible()) return false;

        // Check if click is outside panel (close it)
        if (mouseX < panelX || mouseX > panelX + panelWidth ||
            mouseY < panelY || mouseY > panelY + panelHeight) {
            close();
            return true;
        }

        // Close button
        float closeX = panelX + panelWidth - 30;
        float closeY = panelY + 12;
        if (mouseX >= closeX && mouseX <= closeX + 16 &&
            mouseY >= closeY && mouseY <= closeY + 16) {
            close();
            return true;
        }

        // Tab clicks
        float tabY = panelY + 50;
        float tabWidth = panelWidth / SettingsTab.values().length;
        if (mouseY >= tabY && mouseY <= tabY + 30) {
            int tabIndex = (int)((mouseX - panelX) / tabWidth);
            if (tabIndex >= 0 && tabIndex < SettingsTab.values().length) {
                currentTab = SettingsTab.values()[tabIndex];
                return true;
            }
        }

        // Provider list clicks
        if (currentTab == SettingsTab.PROVIDERS) {
            float contentY = tabY + 40;
            float itemHeight = 55;
            int clickedIndex = (int)((mouseY - contentY) / itemHeight);
            if (clickedIndex >= 0 && clickedIndex < providers.size()) {
                selectedProviderIndex = clickedIndex;

                // Check for button clicks within selected item
                ProviderConfig provider = providers.get(clickedIndex);
                float itemY = contentY + clickedIndex * itemHeight;
                float btnY = itemY + 30;

                if (mouseY >= btnY && mouseY <= btnY + 18) {
                    float btnW = 60;
                    float configX = panelX + 15 + panelWidth - 30 - 200;

                    if (mouseX >= configX && mouseX <= configX + btnW) {
                        // Configure button
                        startApiKeyEntry(provider.type);
                        return true;
                    }

                    float testX = configX + btnW + 5;
                    if (mouseX >= testX && mouseX <= testX + btnW) {
                        // Test button
                        testProviderConnection(provider);
                        return true;
                    }

                    float removeX = testX + btnW + 5;
                    if (mouseX >= removeX && mouseX <= removeX + btnW) {
                        // Remove button
                        removeProviderKey(provider);
                        return true;
                    }
                }
                return true;
            }
        }

        // General tab toggle clicks
        if (currentTab == SettingsTab.GENERAL) {
            float contentY = tabY + 40;
            float itemHeight = 40;

            // Privacy mode toggle
            if (mouseY >= contentY && mouseY <= contentY + 35) {
                float toggleX = panelX + panelWidth - 50;
                if (mouseX >= toggleX - 20 && mouseX <= toggleX + 50) {
                    privacyModeEnabled = !privacyModeEnabled;
                    notifySettingsChanged();
                    return true;
                }
            }
            contentY += itemHeight;

            // Dry-run toggle
            if (mouseY >= contentY && mouseY <= contentY + 35) {
                float toggleX = panelX + panelWidth - 50;
                if (mouseX >= toggleX - 20 && mouseX <= toggleX + 50) {
                    dryRunModeDefault = !dryRunModeDefault;
                    notifySettingsChanged();
                    return true;
                }
            }
            contentY += itemHeight;

            // Auto-save toggle
            if (mouseY >= contentY && mouseY <= contentY + 35) {
                float toggleX = panelX + panelWidth - 50;
                if (mouseX >= toggleX - 20 && mouseX <= toggleX + 50) {
                    autoSaveEnabled = !autoSaveEnabled;
                    notifySettingsChanged();
                    return true;
                }
            }
        }

        return true;  // Click was in panel area
    }

    public void updateHover(float mouseX, float mouseY) {
        if (!isVisible()) return;

        hoveredProviderIndex = -1;

        if (currentTab == SettingsTab.PROVIDERS) {
            float tabY = panelY + 50;
            float contentY = tabY + 40;
            float itemHeight = 55;

            if (mouseX >= panelX && mouseX <= panelX + panelWidth) {
                int hoverIndex = (int)((mouseY - contentY) / itemHeight);
                if (hoverIndex >= 0 && hoverIndex < providers.size()) {
                    hoveredProviderIndex = hoverIndex;
                }
            }
        }
    }

    public void handleKeyInput(int key, int action) {
        if (!isVisible()) return;

        if (enteringApiKey) {
            // Handle key input for API key entry
            // Note: Full text input would need GLFW character callback
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                cancelApiKeyEntry();
            } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                saveApiKey();
            } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && apiKeyInput.length() > 0) {
                apiKeyInput.deleteCharAt(apiKeyInput.length() - 1);
            }
        }
    }

    public void handleCharInput(char c) {
        if (enteringApiKey && !Character.isISOControl(c)) {
            apiKeyInput.append(c);
        }
    }

    private void startApiKeyEntry(AgentType type) {
        enteringApiKey = true;
        keyEntryProvider = type;
        apiKeyInput.setLength(0);
        System.out.println("Enter API key for: " + type.getDisplayName());
    }

    private void cancelApiKeyEntry() {
        enteringApiKey = false;
        keyEntryProvider = null;
        apiKeyInput.setLength(0);
    }

    private void saveApiKey() {
        if (keyEntryProvider == null || apiKeyInput.length() == 0) {
            cancelApiKeyEntry();
            return;
        }

        String key = apiKeyInput.toString();
        String keyName = "api_key_" + keyEntryProvider.name().toLowerCase();

        try {
            if (keychainStore != null && keychainStore.isInitialized()) {
                keychainStore.setSecret(keyName, key);
            }

            // Update provider config
            for (ProviderConfig provider : providers) {
                if (provider.type == keyEntryProvider) {
                    provider.configured = true;
                    provider.lastUsed = System.currentTimeMillis();
                    provider.statusMessage = "Key configured successfully";
                    break;
                }
            }

            System.out.println("API key saved for: " + keyEntryProvider.getDisplayName());
            notifySettingsChanged();
        } catch (Exception e) {
            System.err.println("Failed to save API key: " + e.getMessage());
            for (ProviderConfig provider : providers) {
                if (provider.type == keyEntryProvider) {
                    provider.statusMessage = "Failed to save: " + e.getMessage();
                    break;
                }
            }
        }

        cancelApiKeyEntry();
    }

    private void testProviderConnection(ProviderConfig provider) {
        System.out.println("Testing connection to: " + provider.name);
        // TODO: Implement actual connection test
        provider.statusMessage = "Connection test: OK (simulated)";
    }

    private void removeProviderKey(ProviderConfig provider) {
        String keyName = "api_key_" + provider.type.name().toLowerCase();

        try {
            if (keychainStore != null && keychainStore.isInitialized()) {
                keychainStore.removeSecret(keyName);
            }

            provider.configured = false;
            provider.statusMessage = "Key removed";
            System.out.println("API key removed for: " + provider.name);
            notifySettingsChanged();
        } catch (Exception e) {
            System.err.println("Failed to remove API key: " + e.getMessage());
        }
    }

    private void notifySettingsChanged() {
        for (SettingsListener listener : listeners) {
            listener.onSettingsChanged(this);
        }
    }

    public void addListener(SettingsListener listener) {
        listeners.add(listener);
    }

    // Getters for settings
    public boolean isPrivacyModeEnabled() { return privacyModeEnabled; }
    public void setPrivacyModeEnabled(boolean enabled) { this.privacyModeEnabled = enabled; }

    public boolean isDryRunModeDefault() { return dryRunModeDefault; }
    public void setDryRunModeDefault(boolean enabled) { this.dryRunModeDefault = enabled; }

    public boolean isAutoSaveEnabled() { return autoSaveEnabled; }
    public int getAutoSaveIntervalSeconds() { return autoSaveIntervalSeconds; }

    public int getMaxFilesPerChange() { return maxFilesPerChange; }
    public int getMaxLinesPerChange() { return maxLinesPerChange; }
    public int getMaxRuntimeMinutes() { return maxRuntimeMinutes; }

    public List<ProviderConfig> getProviders() { return providers; }

    /**
     * Settings tabs.
     */
    public enum SettingsTab {
        PROVIDERS("Providers"),
        GENERAL("General"),
        KEYBINDS("Keybinds"),
        ABOUT("About");

        private final String label;

        SettingsTab(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }
    }

    /**
     * Provider configuration.
     */
    public static class ProviderConfig {
        public final AgentType type;
        public final String name;
        public final String endpoint;
        public boolean configured;
        public long lastUsed;
        public String statusMessage;

        public ProviderConfig(AgentType type, String name, String endpoint,
                              boolean configured, long lastUsed, String statusMessage) {
            this.type = type;
            this.name = name;
            this.endpoint = endpoint;
            this.configured = configured;
            this.lastUsed = lastUsed;
            this.statusMessage = statusMessage;
        }
    }

    /**
     * Listener for settings changes.
     */
    public interface SettingsListener {
        void onSettingsChanged(SettingsPanel settings);
    }
}
