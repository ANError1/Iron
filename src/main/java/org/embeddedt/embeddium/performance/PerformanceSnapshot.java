package org.embeddedt.embeddium.performance;

import com.google.gson.annotations.SerializedName;

/**
 * Immutable snapshot of performance metrics at a specific point in time.
 * Designed to be lightweight and serializable to JSON.
 */
public final class PerformanceSnapshot {
    @SerializedName("timestamp")
    private final long timestampMs;
    
    @SerializedName("fps")
    private final double fps;
    
    @SerializedName("frame_time_ms")
    private final double frameTimeMs;
    
    @SerializedName("one_percent_low_fps")
    private final double onePercentLowFps;
    
    @SerializedName("memory_used_mb")
    private final long memoryUsedMb;
    
    @SerializedName("memory_total_mb")
    private final long memoryTotalMb;
    
    @SerializedName("entity_count")
    private final int entityCount;
    
    @SerializedName("chunk_updates")
    private final int chunkUpdates;
    
    @SerializedName("dimension")
    private final String dimension;

    public PerformanceSnapshot(
            long timestampMs,
            double fps,
            double frameTimeMs,
            double onePercentLowFps,
            long memoryUsedMb,
            long memoryTotalMb,
            int entityCount,
            int chunkUpdates,
            String dimension
    ) {
        this.timestampMs = timestampMs;
        this.fps = fps;
        this.frameTimeMs = frameTimeMs;
        this.onePercentLowFps = onePercentLowFps;
        this.memoryUsedMb = memoryUsedMb;
        this.memoryTotalMb = memoryTotalMb;
        this.entityCount = entityCount;
        this.chunkUpdates = chunkUpdates;
        this.dimension = dimension;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public double getFps() {
        return fps;
    }

    public double getFrameTimeMs() {
        return frameTimeMs;
    }

    public double getOnePercentLowFps() {
        return onePercentLowFps;
    }

    public long getMemoryUsedMb() {
        return memoryUsedMb;
    }

    public long getMemoryTotalMb() {
        return memoryTotalMb;
    }

    public int getEntityCount() {
        return entityCount;
    }

    public int getChunkUpdates() {
        return chunkUpdates;
    }

    public String getDimension() {
        return dimension;
    }

    /**
     * Detects if this snapshot represents a performance drop compared to a baseline.
     * @param baselineFps The baseline FPS to compare against
     * @param dropThreshold The percentage drop to consider significant (e.g., 0.3 for 30%)
     * @return true if FPS dropped by more than the threshold
     */
    public boolean isPerformanceDrop(double baselineFps, double dropThreshold) {
        if (baselineFps <= 0) {
            return false;
        }
        double drop = (baselineFps - this.fps) / baselineFps;
        return drop >= dropThreshold;
    }

    @Override
    public String toString() {
        return String.format("PerformanceSnapshot{fps=%.1f, frameTime=%.2fms, 1%%Low=%.1f, mem=%dMB/%dMB, entities=%d}",
                fps, frameTimeMs, onePercentLowFps, memoryUsedMb, memoryTotalMb, entityCount);
    }
}
