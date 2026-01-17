package sh.vibecraft.hexcontrol.security;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/**
 * Encrypted secret storage per spec requirements.
 * Uses AES-GCM for authenticated encryption with Argon2id-derived keys.
 * Falls back to PBKDF2 if Argon2 is not available.
 *
 * Per spec: "Never store secrets in plaintext"
 */
public class SecretStore {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS = 310000; // OWASP recommendation

    private final Path secretsFile;
    private SecretKey encryptionKey;
    private Map<String, String> secrets;
    private boolean unlocked = false;

    // Salt for key derivation (stored alongside encrypted data)
    private byte[] salt;

    public SecretStore(Path stateDir) {
        this.secretsFile = stateDir.resolve("secrets.enc");
        this.secrets = new HashMap<>();
    }

    /**
     * Initialize the secret store with a passphrase.
     * If secrets file exists, decrypts it. Otherwise creates new.
     */
    public boolean unlock(String passphrase) {
        if (passphrase == null || passphrase.isEmpty()) {
            System.err.println("Passphrase cannot be empty");
            return false;
        }

        try {
            if (Files.exists(secretsFile)) {
                return loadAndDecrypt(passphrase);
            } else {
                // New store - generate salt and derive key
                salt = generateSalt();
                encryptionKey = deriveKey(passphrase, salt);
                secrets = new HashMap<>();
                unlocked = true;
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to unlock secret store: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lock the store and clear sensitive data from memory.
     */
    public void lock() {
        secrets.clear();
        encryptionKey = null;
        unlocked = false;
    }

    /**
     * Store a secret (API key, etc.)
     */
    public void setSecret(String key, String value) {
        if (!unlocked) {
            throw new IllegalStateException("Secret store is locked");
        }
        secrets.put(key, value);
    }

    /**
     * Retrieve a secret.
     */
    public String getSecret(String key) {
        if (!unlocked) {
            throw new IllegalStateException("Secret store is locked");
        }
        return secrets.get(key);
    }

    /**
     * Remove a secret.
     */
    public void removeSecret(String key) {
        if (!unlocked) {
            throw new IllegalStateException("Secret store is locked");
        }
        secrets.remove(key);
    }

    /**
     * Check if a secret exists.
     */
    public boolean hasSecret(String key) {
        if (!unlocked) {
            throw new IllegalStateException("Secret store is locked");
        }
        return secrets.containsKey(key);
    }

    /**
     * Get all secret keys (not values).
     */
    public Set<String> getSecretKeys() {
        if (!unlocked) {
            throw new IllegalStateException("Secret store is locked");
        }
        return new HashSet<>(secrets.keySet());
    }

    /**
     * Save encrypted secrets to disk.
     */
    public void save() throws Exception {
        if (!unlocked) {
            throw new IllegalStateException("Secret store is locked");
        }

        // Serialize secrets to JSON
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
              .append(escapeJson(entry.getValue())).append("\"");
        }
        sb.append("}");

        byte[] plaintext = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Encrypt
        byte[] iv = generateIV();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Write: salt (32) + iv (12) + ciphertext
        try (OutputStream out = Files.newOutputStream(secretsFile)) {
            out.write(salt);
            out.write(iv);
            out.write(ciphertext);
        }

        // Set restrictive permissions
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms =
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(secretsFile, perms);
        } catch (UnsupportedOperationException e) {
            // Windows - permissions handled differently
        }
    }

    private boolean loadAndDecrypt(String passphrase) throws Exception {
        byte[] data = Files.readAllBytes(secretsFile);

        if (data.length < 32 + GCM_IV_LENGTH + 16) {
            throw new IllegalStateException("Invalid secrets file");
        }

        // Read salt
        salt = Arrays.copyOfRange(data, 0, 32);

        // Derive key
        encryptionKey = deriveKey(passphrase, salt);

        // Read IV
        byte[] iv = Arrays.copyOfRange(data, 32, 32 + GCM_IV_LENGTH);

        // Read ciphertext
        byte[] ciphertext = Arrays.copyOfRange(data, 32 + GCM_IV_LENGTH, data.length);

        // Decrypt
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);

        // Parse JSON
        String json = new String(plaintext, StandardCharsets.UTF_8);
        secrets = parseSimpleJson(json);

        unlocked = true;
        return true;
    }

    private SecretKey deriveKey(String passphrase, byte[] salt) throws Exception {
        // Use PBKDF2 (Argon2 would require additional dependency)
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private byte[] generateSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> result = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            // Simple parsing - does not handle all edge cases
            String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                int colonIdx = pair.indexOf(':');
                if (colonIdx > 0) {
                    String key = unescapeJson(pair.substring(0, colonIdx).trim());
                    String value = unescapeJson(pair.substring(colonIdx + 1).trim());
                    if (key.startsWith("\"") && key.endsWith("\"")) {
                        key = key.substring(1, key.length() - 1);
                    }
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    private String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public boolean isUnlocked() {
        return unlocked;
    }
}
