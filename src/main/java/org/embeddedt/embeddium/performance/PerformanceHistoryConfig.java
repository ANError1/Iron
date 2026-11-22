package org.embeddedt.embeddium.performance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.embeddedt.embeddium.opticore.OpticorePaths;
import org.embeddedt.embeddium.opticore.util.OpticoreLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for the performance history tracking system.
 */
public final class PerformanceHistoryConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "performance-history.json";
    
    /** Whether performance history tracking is enabled */
    public volatile boolean enabled = true;
    
    /** Whether to show the performance graph overlay in-game */
    public volatile boolean showGraphOverlay = false;
    
    /** Whether to auto-detect and log performance drops */
    public volatile boolean detectPerformanceDrops = true;
    
    /** FPS drop percentage to trigger a performance event (0.3 = 30% drop) */
    public volatile double performanceDropThreshold = 0.3;
    
    /** Sampling interval in milliseconds (how often to capture snapshots) */
    public volatile int samplingIntervalMs = 1000;
    
    /** Maximum number of snapshots to keep in memory (affects memory usage) */
    public volatile int maxSnapshotsInMemory = 600; // 10 minutes at 1 sample/sec
    
    /** Whether to export performance reports automatically when game closes */
    public volatile boolean autoExportOnExit = false;
    
    /** Maximum number of performance reports to keep (oldest ones are deleted) */
    public volatile int maxReportFiles = 10;

    private static volatile PerformanceHistoryConfig instance;

    public static PerformanceHistoryConfig get() {
        PerformanceHistoryConfig local = instance;
        if (local == null) {
            synchronized (PerformanceHistoryConfig.class) {
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

    private static PerformanceHistoryConfig load() {
        Path path = configPath();
        PerformanceHistoryConfig config = new PerformanceHistoryConfig();

        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                config.readFrom(root);
            } catch (Throwable t) {
                OpticoreLog.warn("Could not read {}; using defaults", FILE_NAME, t);
                config = new PerformanceHistoryConfig();
            }
        }

        config.save();
        return config;
    }

    private void readFrom(JsonObject root) {
        this.enabled = readBoolean(root, "enabled", this.enabled);
        this.showGraphOverlay = readBoolean(root, "showGraphOverlay", this.showGraphOverlay);
        this.detectPerformanceDrops = readBoolean(root, "detectPerformanceDrops", this.detectPerformanceDrops);
        this.performanceDropThreshold = readDouble(root, "performanceDropThreshold", this.performanceDropThreshold);
        this.samplingIntervalMs = readInt(root, "samplingIntervalMs", this.samplingIntervalMs);
        this.maxSnapshotsInMemory = readInt(root, "maxSnapshotsInMemory", this.maxSnapshotsInMemory);
        this.autoExportOnExit = readBoolean(root, "autoExportOnExit", this.autoExportOnExit);
        this.maxReportFiles = readInt(root, "maxReportFiles", this.maxReportFiles);

        this.sanitize();
    }

    public void applyDefaults() {
        PerformanceHistoryConfig defaults = new PerformanceHistoryConfig();
        this.enabled = defaults.enabled;
        this.showGraphOverlay = defaults.showGraphOverlay;
        this.detectPerformanceDrops = defaults.detectPerformanceDrops;
        this.performanceDropThreshold = defaults.performanceDropThreshold;
        this.samplingIntervalMs = defaults.samplingIntervalMs;
        this.maxSnapshotsInMemory = defaults.maxSnapshotsInMemory;
        this.autoExportOnExit = defaults.autoExportOnExit;
        this.maxReportFiles = defaults.maxReportFiles;
    }

    public void sanitize() {
        this.performanceDropThreshold = clamp(this.performanceDropThreshold, 0.1, 0.9);
        this.samplingIntervalMs = clamp(this.samplingIntervalMs, 100, 10000);
        this.maxSnapshotsInMemory = clamp(this.maxSnapshotsInMemory, 60, 3600);
        this.maxReportFiles = clamp(this.maxReportFiles, 1, 100);
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

    private static double readDouble(JsonObject root, String key, double fallback) {
        try {
            return root.has(key) ? root.get(key).getAsDouble() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
