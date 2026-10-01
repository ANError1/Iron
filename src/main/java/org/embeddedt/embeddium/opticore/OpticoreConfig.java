package org.embeddedt.embeddium.opticore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.embeddedt.embeddium.opticore.util.OpticoreLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent JSON configuration. Malformed or unreadable files fall back to defaults rather than
 * propagating an exception into the render thread.
 */
public final class OpticoreConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Master switch. When false every hook short-circuits. */
    public volatile boolean enabled = true;

    /** Render the Opticore overlay on the HUD when the vanilla debug screen is hidden. */
    public volatile boolean showOverlay = false;

    /** Verbose logging of culling statistics once per second. */
    public volatile boolean logStatistics = false;

    /** Discard results older than this many frames, so a stalled worker cannot serve stale data. */
    public volatile int maxResultAgeFrames = 4;

    /**
     * Occlusion raycasts are the only work in this mod that touches live chunk data. The raycaster
     * snaps its view to the main thread, so this cap bounds the worst-case stall per capture.
     */
    public volatile int maxOcclusionTestsPerCapture = 4;

    /** Minimum interval between snapshots, in milliseconds. */
    public volatile int captureIntervalMs = 75;

    public volatile int passiveMobDistance = 128;
    public volatile int hostileMobDistance = 96;
    public volatile int armorStandDistance = 96;
    public volatile int droppedItemDistance = 48;
    public volatile int miscEntityDistance = 64;

    /** Whether distant entities may be hidden even when they are not occluded. */
    public volatile boolean enableDistanceCulling = true;

    /** Whether entities hidden behind solid geometry may be discarded. */
    public volatile boolean enableOcclusionCulling = true;

    private static final String FILE_NAME = "opticore.json";

    private static volatile OpticoreConfig instance;

    public static OpticoreConfig get() {
        OpticoreConfig local = instance;
        if (local == null) {
            synchronized (OpticoreConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    private static Path configPath() {
        return OpticorePaths.configDir().resolve(FILE_NAME);
    }

    private static OpticoreConfig load() {
        Path path = configPath();
        OpticoreConfig config = new OpticoreConfig();

        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                config.readFrom(root);
            } catch (Throwable t) {
                OpticoreLog.warn("Could not read {}; using defaults and rewriting the file", FILE_NAME, t);
                config = new OpticoreConfig();
            }
        }

        config.save();

        return config;
    }

    private void readFrom(JsonObject root) {
        this.enabled = readBoolean(root, "enabled", this.enabled);
        this.showOverlay = readBoolean(root, "showOverlay", this.showOverlay);
        this.logStatistics = readBoolean(root, "logStatistics", this.logStatistics);
        this.maxResultAgeFrames = readInt(root, "maxResultAgeFrames", this.maxResultAgeFrames);
        this.maxOcclusionTestsPerCapture = readInt(root, "maxOcclusionTestsPerCapture", this.maxOcclusionTestsPerCapture);
        this.captureIntervalMs = readInt(root, "captureIntervalMs", this.captureIntervalMs);
        this.passiveMobDistance = readInt(root, "passiveMobDistance", this.passiveMobDistance);
        this.hostileMobDistance = readInt(root, "hostileMobDistance", this.hostileMobDistance);
        this.armorStandDistance = readInt(root, "armorStandDistance", this.armorStandDistance);
        this.droppedItemDistance = readInt(root, "droppedItemDistance", this.droppedItemDistance);
        this.miscEntityDistance = readInt(root, "miscEntityDistance", this.miscEntityDistance);
        this.enableDistanceCulling = readBoolean(root, "enableDistanceCulling", this.enableDistanceCulling);
        this.enableOcclusionCulling = readBoolean(root, "enableOcclusionCulling", this.enableOcclusionCulling);

        this.sanitize();
    }

    /** Restores every field to its compiled-in default. */
    public void applyDefaults() {
        OpticoreConfig defaults = new OpticoreConfig();

        this.enabled = defaults.enabled;
        this.showOverlay = defaults.showOverlay;
        this.logStatistics = defaults.logStatistics;
        this.maxResultAgeFrames = defaults.maxResultAgeFrames;
        this.maxOcclusionTestsPerCapture = defaults.maxOcclusionTestsPerCapture;
        this.captureIntervalMs = defaults.captureIntervalMs;
        this.passiveMobDistance = defaults.passiveMobDistance;
        this.hostileMobDistance = defaults.hostileMobDistance;
        this.armorStandDistance = defaults.armorStandDistance;
        this.droppedItemDistance = defaults.droppedItemDistance;
        this.miscEntityDistance = defaults.miscEntityDistance;
        this.enableDistanceCulling = defaults.enableDistanceCulling;
        this.enableOcclusionCulling = defaults.enableOcclusionCulling;
    }

    /** Clamps values that would otherwise cause pathological behaviour (for example a zero interval). */
    public void sanitize() {
        this.maxResultAgeFrames = clamp(this.maxResultAgeFrames, 1, 60);
        this.maxOcclusionTestsPerCapture = clamp(this.maxOcclusionTestsPerCapture, 0, 32);
        this.captureIntervalMs = clamp(this.captureIntervalMs, 10, 2000);
        this.passiveMobDistance = clamp(this.passiveMobDistance, 16, 512);
        this.hostileMobDistance = clamp(this.hostileMobDistance, 16, 512);
        this.armorStandDistance = clamp(this.armorStandDistance, 16, 512);
        this.droppedItemDistance = clamp(this.droppedItemDistance, 8, 512);
        this.miscEntityDistance = clamp(this.miscEntityDistance, 8, 512);
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            OpticoreLog.warn("Could not write {}", FILE_NAME, e);
        }
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        try {
            return root.has(key) ? root.get(key).getAsBoolean() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        try {
            return root.has(key) ? root.get(key).getAsInt() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
