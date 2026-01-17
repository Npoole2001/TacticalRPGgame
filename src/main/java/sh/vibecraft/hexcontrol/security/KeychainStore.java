package sh.vibecraft.hexcontrol.security;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * OS-native secure storage integration per spec.
 * Attempts to use: macOS Keychain, Windows Credential Manager, Linux Secret Service.
 * Falls back to encrypted local store if OS keychain not available.
 */
public class KeychainStore {

    private static final String SERVICE_NAME = "HexControl";

    private final SecretStore fallbackStore;
    private final KeychainBackend backend;
    private boolean initialized = false;

    public KeychainStore(Path stateDir) {
        this.fallbackStore = new SecretStore(stateDir);
        this.backend = detectBackend();
    }

    /**
     * Initialize the keychain store.
     * @param passphrase Used only if falling back to encrypted local store
     */
    public boolean initialize(String passphrase) {
        if (backend != KeychainBackend.NONE) {
            // Test OS keychain access
            try {
                testKeychainAccess();
                initialized = true;
                System.out.println("Using OS keychain: " + backend);
                return true;
            } catch (Exception e) {
                System.out.println("OS keychain not available: " + e.getMessage());
            }
        }

        // Fall back to encrypted local store
        System.out.println("Falling back to encrypted local store");
        initialized = fallbackStore.unlock(passphrase);
        return initialized;
    }

    /**
     * Store a secret.
     */
    public void setSecret(String key, String value) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Keychain not initialized");
        }

        if (backend != KeychainBackend.NONE) {
            setOSSecret(key, value);
        } else {
            fallbackStore.setSecret(key, value);
            fallbackStore.save();
        }
    }

    /**
     * Retrieve a secret.
     */
    public String getSecret(String key) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Keychain not initialized");
        }

        if (backend != KeychainBackend.NONE) {
            return getOSSecret(key);
        } else {
            return fallbackStore.getSecret(key);
        }
    }

    /**
     * Remove a secret.
     */
    public void removeSecret(String key) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Keychain not initialized");
        }

        if (backend != KeychainBackend.NONE) {
            removeOSSecret(key);
        } else {
            fallbackStore.removeSecret(key);
            fallbackStore.save();
        }
    }

    /**
     * Check if a secret exists.
     */
    public boolean hasSecret(String key) {
        if (!initialized) return false;

        try {
            String value = getSecret(key);
            return value != null && !value.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all stored key names.
     */
    public Set<String> getSecretKeys() {
        if (!initialized) return Collections.emptySet();

        if (backend != KeychainBackend.NONE) {
            // OS keychains don't easily enumerate keys
            // Would need to maintain a separate list
            return Collections.emptySet();
        } else {
            return fallbackStore.getSecretKeys();
        }
    }

    /**
     * Lock the store and clear sensitive data.
     */
    public void lock() {
        if (backend == KeychainBackend.NONE) {
            fallbackStore.lock();
        }
        initialized = false;
    }

    private KeychainBackend detectBackend() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac")) {
            // Check if security command exists
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"which", "security"});
                if (p.waitFor() == 0) {
                    return KeychainBackend.MACOS_KEYCHAIN;
                }
            } catch (Exception e) {
                // Ignore
            }
        } else if (os.contains("win")) {
            // Windows Credential Manager available via cmdkey
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"where", "cmdkey"});
                if (p.waitFor() == 0) {
                    return KeychainBackend.WINDOWS_CREDENTIAL_MANAGER;
                }
            } catch (Exception e) {
                // Ignore
            }
        } else if (os.contains("linux")) {
            // Check for secret-tool (libsecret)
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"which", "secret-tool"});
                if (p.waitFor() == 0) {
                    return KeychainBackend.LINUX_SECRET_SERVICE;
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        return KeychainBackend.NONE;
    }

    private void testKeychainAccess() throws Exception {
        // Try a test operation
        String testKey = "hexcontrol-test";
        String testValue = "test-" + System.currentTimeMillis();

        setOSSecret(testKey, testValue);
        String retrieved = getOSSecret(testKey);
        removeOSSecret(testKey);

        if (!testValue.equals(retrieved)) {
            throw new Exception("Keychain test failed: value mismatch");
        }
    }

    private void setOSSecret(String key, String value) throws Exception {
        switch (backend) {
            case MACOS_KEYCHAIN -> setMacOSSecret(key, value);
            case WINDOWS_CREDENTIAL_MANAGER -> setWindowsSecret(key, value);
            case LINUX_SECRET_SERVICE -> setLinuxSecret(key, value);
            default -> throw new UnsupportedOperationException("No OS keychain available");
        }
    }

    private String getOSSecret(String key) throws Exception {
        return switch (backend) {
            case MACOS_KEYCHAIN -> getMacOSSecret(key);
            case WINDOWS_CREDENTIAL_MANAGER -> getWindowsSecret(key);
            case LINUX_SECRET_SERVICE -> getLinuxSecret(key);
            default -> throw new UnsupportedOperationException("No OS keychain available");
        };
    }

    private void removeOSSecret(String key) throws Exception {
        switch (backend) {
            case MACOS_KEYCHAIN -> removeMacOSSecret(key);
            case WINDOWS_CREDENTIAL_MANAGER -> removeWindowsSecret(key);
            case LINUX_SECRET_SERVICE -> removeLinuxSecret(key);
            default -> throw new UnsupportedOperationException("No OS keychain available");
        }
    }

    // macOS Keychain via 'security' command
    private void setMacOSSecret(String key, String value) throws Exception {
        // First try to delete existing
        try { removeMacOSSecret(key); } catch (Exception ignored) {}

        ProcessBuilder pb = new ProcessBuilder(
            "security", "add-generic-password",
            "-a", System.getProperty("user.name"),
            "-s", SERVICE_NAME + ":" + key,
            "-w", value,
            "-U"
        );
        Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new Exception("Failed to store secret in macOS Keychain");
        }
    }

    private String getMacOSSecret(String key) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "security", "find-generic-password",
            "-a", System.getProperty("user.name"),
            "-s", SERVICE_NAME + ":" + key,
            "-w"
        );
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        if (p.waitFor() != 0) {
            return null;
        }
        return output;
    }

    private void removeMacOSSecret(String key) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "security", "delete-generic-password",
            "-a", System.getProperty("user.name"),
            "-s", SERVICE_NAME + ":" + key
        );
        pb.start().waitFor();
    }

    // Windows Credential Manager via 'cmdkey'
    private void setWindowsSecret(String key, String value) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "cmdkey", "/generic:" + SERVICE_NAME + ":" + key,
            "/user:" + System.getProperty("user.name"),
            "/pass:" + value
        );
        Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new Exception("Failed to store secret in Windows Credential Manager");
        }
    }

    private String getWindowsSecret(String key) throws Exception {
        // cmdkey doesn't easily retrieve passwords, would need PowerShell
        // For now, fall back to encrypted store on Windows
        throw new UnsupportedOperationException("Windows credential retrieval not implemented");
    }

    private void removeWindowsSecret(String key) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "cmdkey", "/delete:" + SERVICE_NAME + ":" + key
        );
        pb.start().waitFor();
    }

    // Linux Secret Service via 'secret-tool'
    private void setLinuxSecret(String key, String value) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "secret-tool", "store",
            "--label=" + SERVICE_NAME + " " + key,
            "service", SERVICE_NAME,
            "key", key
        );
        Process p = pb.start();
        p.getOutputStream().write(value.getBytes());
        p.getOutputStream().close();
        if (p.waitFor() != 0) {
            throw new Exception("Failed to store secret in Linux Secret Service");
        }
    }

    private String getLinuxSecret(String key) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "secret-tool", "lookup",
            "service", SERVICE_NAME,
            "key", key
        );
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        if (p.waitFor() != 0) {
            return null;
        }
        return output;
    }

    private void removeLinuxSecret(String key) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "secret-tool", "clear",
            "service", SERVICE_NAME,
            "key", key
        );
        pb.start().waitFor();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public KeychainBackend getBackend() {
        return backend;
    }

    public boolean isUsingOSKeychain() {
        return backend != KeychainBackend.NONE && initialized;
    }

    /**
     * Available keychain backends.
     */
    public enum KeychainBackend {
        MACOS_KEYCHAIN("macOS Keychain"),
        WINDOWS_CREDENTIAL_MANAGER("Windows Credential Manager"),
        LINUX_SECRET_SERVICE("Linux Secret Service"),
        NONE("Encrypted Local Store");

        private final String displayName;

        KeychainBackend(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
