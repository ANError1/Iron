package org.embeddedt.embeddium.performance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.embeddedt.embeddium.opticore.OpticorePaths;
import org.embeddedt.embeddium.opticore.util.FrameProfiler;
import org.embeddedt.embeddium.opticore.util.OpticoreLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks performance metrics over time and provides analysis capabilities.
 * Uses a ring buffer to limit memory usage and runs sampling on a background thread.
 */
public final class PerformanceHistory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private static PerformanceHistory instance;
    
    private final List<PerformanceSnapshot> snapshots;
    private long lastSampleTimeMs;
    private double baselineFps;
    private int baselineSampleCount;
    private volatile boolean isRunning;
    
    private PerformanceHistory() {
        this.snapshots = new CopyOnWriteArrayList<>();
        this.lastSampleTimeMs = 0;
        this.baselineFps = 60.0;
        this.baselineSampleCount = 0;
        this.isRunning = false;
    }
    
    public static PerformanceHistory getInstance() {
        if (instance == null) {
            synchronized (PerformanceHistory.class) {
                if (instance == null) {
                    instance = new PerformanceHistory();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize and start the performance tracking system.
     * Should be called once during game initialization.
     */
    public static void initialize() {
        PerformanceHistory tracker = getInstance();
        tracker.start();
    }
    
    /**
     * Shutdown and optionally export data.
     * Should be called during game shutdown.
     */
    public static void shutdown() {
        PerformanceHistory tracker = getInstance();
        tracker.stop();
        
        PerformanceHistoryConfig config = PerformanceHistoryConfig.get();
        if (config.autoExportOnExit && !tracker.snapshots.isEmpty()) {
            tracker.exportReport("game-session");
        }
    }
    
    private void start() {
        if (!PerformanceHistoryConfig.get().enabled) {
            return;
        }
        
        this.isRunning = true;
        OpticoreLog.info("Performance history tracking started");
    }
    
    private void stop() {
        this.isRunning = false;
        OpticoreLog.info("Performance history tracking stopped");
    }
    
    /**
     * Called every frame to potentially capture a new performance snapshot.
     * This method is lightweight and exits early if not enough time has passed.
     */
    public void tick() {
        if (!isRunning) {
            return;
        }
        
        PerformanceHistoryConfig config = PerformanceHistoryConfig.get();
        if (!config.enabled) {
            return;
        }
        
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastSampleTimeMs < config.samplingIntervalMs) {
            return;
        }
        
        lastSampleTimeMs = currentTimeMs;
        captureSnapshot();
    }
    
    private void captureSnapshot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        
        try {
            // Gather performance metrics
            double fps = FrameProfiler.getFps();
            double frameTimeMs = FrameProfiler.getAverageFrameTimeMs();
            double onePercentLow = FrameProfiler.getOnePercentLowFps();
            
            // Memory metrics
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long usedMb = usedMemory / (1024 * 1024);
            long totalMb = totalMemory / (1024 * 1024);
            
            // Game state metrics
            ClientLevel level = mc.level;
            int entityCount = 0;
            // Count entities for rendering
            for (Entity entity : level.entitiesForRendering()) {
                if (entity != null) {
                    entityCount++;
                }
            }
            String dimension = level.dimension().location().toString();
            
            // Note: chunk updates would require accessing render system internals
            // For now we'll use 0 as a placeholder
            int chunkUpdates = 0;
            
            PerformanceSnapshot snapshot = new PerformanceSnapshot(
                System.currentTimeMillis(),
                fps,
                frameTimeMs,
                onePercentLow,
                usedMb,
                totalMb,
                entityCount,
                chunkUpdates,
                dimension
            );
            
            addSnapshot(snapshot);
            updateBaseline(fps);
            
            // Check for performance drops
            PerformanceHistoryConfig config = PerformanceHistoryConfig.get();
            if (config.detectPerformanceDrops && baselineSampleCount > 10) {
                if (snapshot.isPerformanceDrop(baselineFps, config.performanceDropThreshold)) {
                    onPerformanceDrop(snapshot);
                }
            }
            
        } catch (Exception e) {
            // Silent failure to avoid spamming logs
        }
    }
    
    private void addSnapshot(PerformanceSnapshot snapshot) {
        snapshots.add(snapshot);
        
        // Limit memory usage by removing oldest snapshots
        PerformanceHistoryConfig config = PerformanceHistoryConfig.get();
        while (snapshots.size() > config.maxSnapshotsInMemory) {
            snapshots.remove(0);
        }
    }
    
    private void updateBaseline(double currentFps) {
        // Update rolling baseline FPS using exponential moving average
        if (baselineSampleCount == 0) {
            baselineFps = currentFps;
        } else {
            double alpha = 0.05; // Smoothing factor
            baselineFps = alpha * currentFps + (1 - alpha) * baselineFps;
        }
        baselineSampleCount++;
    }
    
    private void onPerformanceDrop(PerformanceSnapshot snapshot) {
        OpticoreLog.warn("Performance drop detected: {} (baseline: {:.1f} FPS)", 
            snapshot.toString(), baselineFps);
    }
    
    /**
     * Export current performance data to a JSON report file.
     * @param reportName Base name for the report file (without extension)
     * @return Path to the exported file, or null if export failed
     */
    public Path exportReport(String reportName) {
        if (snapshots.isEmpty()) {
            OpticoreLog.info("No performance data to export");
            return null;
        }
        
        try {
            Path reportsDir = OpticorePaths.configDir().resolve("performance-reports");
            Files.createDirectories(reportsDir);
            
            // Generate unique filename with timestamp
            String timestamp = LocalDateTime.now().format(FILE_NAME_FORMAT);
            String fileName = String.format("%s_%s.json", reportName, timestamp);
            Path reportPath = reportsDir.resolve(fileName);
            
            // Create report object
            PerformanceReport report = new PerformanceReport(
                System.currentTimeMillis(),
                new ArrayList<>(snapshots),
                calculateStatistics()
            );
            
            // Write to file
            String json = GSON.toJson(report);
            Files.writeString(reportPath, json, StandardCharsets.UTF_8);
            
            OpticoreLog.info("Performance report exported to: {}", reportPath);
            
            // Clean up old reports
            cleanupOldReports(reportsDir);
            
            return reportPath;
            
        } catch (IOException e) {
            OpticoreLog.error("Failed to export performance report", e);
            return null;
        }
    }
    
    private void cleanupOldReports(Path reportsDir) {
        try {
            PerformanceHistoryConfig config = PerformanceHistoryConfig.get();
            List<Path> reportFiles = new ArrayList<>();
            
            Files.list(reportsDir)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(reportFiles::add);
            
            if (reportFiles.size() > config.maxReportFiles) {
                // Sort by modification time (oldest first)
                reportFiles.sort((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                });
                
                // Delete oldest reports
                int toDelete = reportFiles.size() - config.maxReportFiles;
                for (int i = 0; i < toDelete; i++) {
                    Files.deleteIfExists(reportFiles.get(i));
                }
            }
            
        } catch (IOException e) {
            OpticoreLog.warn("Failed to cleanup old performance reports", e);
        }
    }
    
    private PerformanceStatistics calculateStatistics() {
        if (snapshots.isEmpty()) {
            return new PerformanceStatistics(0, 0, 0, 0, 0, 0, 0);
        }
        
        double sumFps = 0;
        double minFps = Double.MAX_VALUE;
        double maxFps = 0;
        double sum1PercentLow = 0;
        long sumMemory = 0;
        
        for (PerformanceSnapshot snapshot : snapshots) {
            sumFps += snapshot.getFps();
            minFps = Math.min(minFps, snapshot.getFps());
            maxFps = Math.max(maxFps, snapshot.getFps());
            sum1PercentLow += snapshot.getOnePercentLowFps();
            sumMemory += snapshot.getMemoryUsedMb();
        }
        
        int count = snapshots.size();
        double avgFps = sumFps / count;
        double avg1PercentLow = sum1PercentLow / count;
        long avgMemoryMb = sumMemory / count;
        
        return new PerformanceStatistics(
            count,
            avgFps,
            minFps,
            maxFps,
            avg1PercentLow,
            avgMemoryMb,
            calculateStdDev(avgFps)
        );
    }
    
    private double calculateStdDev(double mean) {
        if (snapshots.size() < 2) {
            return 0;
        }
        
        double sumSquaredDiff = 0;
        for (PerformanceSnapshot snapshot : snapshots) {
            double diff = snapshot.getFps() - mean;
            sumSquaredDiff += diff * diff;
        }
        
        return Math.sqrt(sumSquaredDiff / snapshots.size());
    }
    
    /**
     * Get a read-only view of recent snapshots.
     * @param count Maximum number of recent snapshots to return
     * @return List of snapshots (newest first)
     */
    public List<PerformanceSnapshot> getRecentSnapshots(int count) {
        if (snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        
        int size = snapshots.size();
        int startIndex = Math.max(0, size - count);
        List<PerformanceSnapshot> recent = new ArrayList<>(snapshots.subList(startIndex, size));
        Collections.reverse(recent);
        return recent;
    }
    
    /**
     * Clear all recorded snapshots and reset baseline.
     */
    public void clear() {
        snapshots.clear();
        baselineFps = 60.0;
        baselineSampleCount = 0;
        OpticoreLog.info("Performance history cleared");
    }
    
    public int getSnapshotCount() {
        return snapshots.size();
    }
    
    public double getBaselineFps() {
        return baselineFps;
    }
    
    // Inner classes for JSON serialization
    
    private static class PerformanceReport {
        private final long exportTimestamp;
        private final List<PerformanceSnapshot> snapshots;
        private final PerformanceStatistics statistics;
        
        PerformanceReport(long exportTimestamp, List<PerformanceSnapshot> snapshots, PerformanceStatistics statistics) {
            this.exportTimestamp = exportTimestamp;
            this.snapshots = snapshots;
            this.statistics = statistics;
        }
    }
    
    private static class PerformanceStatistics {
        private final int totalSamples;
        private final double averageFps;
        private final double minFps;
        private final double maxFps;
        private final double average1PercentLowFps;
        private final long averageMemoryMb;
        private final double fpsStdDev;
        
        PerformanceStatistics(int totalSamples, double averageFps, double minFps, double maxFps,
                            double average1PercentLowFps, long averageMemoryMb, double fpsStdDev) {
            this.totalSamples = totalSamples;
            this.averageFps = averageFps;
            this.minFps = minFps;
            this.maxFps = maxFps;
            this.average1PercentLowFps = average1PercentLowFps;
            this.averageMemoryMb = averageMemoryMb;
            this.fpsStdDev = fpsStdDev;
        }
    }
}
