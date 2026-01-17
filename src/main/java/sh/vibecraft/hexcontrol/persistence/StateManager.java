package sh.vibecraft.hexcontrol.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import sh.vibecraft.hexcontrol.render.Camera;
import sh.vibecraft.hexcontrol.render.HexGrid;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * Manages application state persistence.
 * Per spec: autosave every 30-60 seconds, save on important transitions.
 */
public class StateManager {

    private static final String STATE_DIR = ".hexcontrol";
    private static final String STATE_FILE = "appstate.json";
    private static final String BACKUP_FILE = "appstate.backup.json";
    private static final long AUTOSAVE_INTERVAL_MS = 45000; // 45 seconds

    private final Path stateDir;
    private final Path stateFile;
    private final Path backupFile;

    private final Gson gson;
    private final ScheduledExecutorService autosaveExecutor;

    private Camera camera;
    private HexGrid hexGrid;

    private long lastSaveTime = 0;
    private boolean dirty = false;

    public StateManager() {
        // Use user home directory
        String userHome = System.getProperty("user.home");
        stateDir = Paths.get(userHome, STATE_DIR);
        stateFile = stateDir.resolve(STATE_FILE);
        backupFile = stateDir.resolve(BACKUP_FILE);

        gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

        autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HexControl-Autosave");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialize state manager with references to live objects.
     */
    public void initialize(Camera camera, HexGrid hexGrid) {
        this.camera = camera;
        this.hexGrid = hexGrid;

        // Ensure state directory exists
        try {
            Files.createDirectories(stateDir);
        } catch (IOException e) {
            System.err.println("Warning: Could not create state directory: " + e.getMessage());
        }

        // Start autosave
        autosaveExecutor.scheduleAtFixedRate(
            this::autosave,
            AUTOSAVE_INTERVAL_MS,
            AUTOSAVE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        System.out.println("StateManager initialized. State directory: " + stateDir);
    }

    /**
     * Load saved state, if it exists.
     * @return true if state was loaded successfully
     */
    public boolean load() {
        if (!Files.exists(stateFile)) {
            System.out.println("No saved state found, starting fresh.");
            return false;
        }

        if (loadFromFile(stateFile, false)) {
            return true;
        }

        if (Files.exists(backupFile)) {
            return loadFromFile(backupFile, true);
        }

        return false;
    }

    private boolean loadFromFile(Path file, boolean isBackup) {
        try {
            String json = Files.readString(file);
            AppState state = gson.fromJson(json, AppState.class);

            if (state == null) {
                System.err.println("Error loading state: state file was empty.");
                return false;
            }

            state.ensureDefaults();

            if (!state.migrate()) {
                System.err.println("Error loading state: migration failed.");
                return false;
            }

            var issues = state.validate();
            if (!issues.isEmpty()) {
                System.err.println("State validation warnings: " + String.join("; ", issues));
            }

            state.apply(camera, hexGrid);
            if (isBackup) {
                System.out.println("Loaded backup state");
            } else {
                System.out.println("Loaded state: " + state.agents.size() + " agents");
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error loading state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Save current state to disk.
     */
    public synchronized void save() {
        if (camera == null || hexGrid == null) {
            return;
        }

        try {
            AppState state = AppState.capture(camera, hexGrid);
            String json = gson.toJson(state);

            // Backup existing file first
            if (Files.exists(stateFile)) {
                Files.copy(stateFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Write new state
            Files.writeString(stateFile, json);

            lastSaveTime = System.currentTimeMillis();
            dirty = false;

            System.out.println("State saved: " + state.agents.size() + " agents");
        } catch (Exception e) {
            System.err.println("Error saving state: " + e.getMessage());
        }
    }

    /**
     * Mark state as dirty (needs save).
     */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Autosave if dirty.
     */
    private void autosave() {
        if (dirty) {
            save();
        }
    }

    /**
     * Shutdown and save final state.
     */
    public void shutdown() {
        autosaveExecutor.shutdown();
        save();
    }

    /**
     * Get the state directory path.
     */
    public Path getStateDir() {
        return stateDir;
    }

    /**
     * Check if state is dirty.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Get time since last save in milliseconds.
     */
    public long getTimeSinceLastSave() {
        return System.currentTimeMillis() - lastSaveTime;
    }
}
