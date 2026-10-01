package org.embeddedt.embeddium.opticore.util;

import org.embeddedt.embeddium.opticore.OpticoreConfig;
import org.embeddedt.embeddium.opticore.culling.CullingEngine;

/**
 * Lightweight counters for the HUD and for the optional statistics log.
 *
 * <p>Everything is reset at the top of each frame, so the values always describe the frame that is
 * currently being drawn.
 */
public final class CullingTracker {
    private static int entitiesCulledThisFrame;
    private static int entitiesConsideredThisFrame;
    private static int entitiesDrawnAndTested;

    private static long lastLogNanos;

    private CullingTracker() {}

    public static void reset() {
        entitiesCulledThisFrame = 0;
        entitiesConsideredThisFrame = 0;
        entitiesDrawnAndTested = 0;
    }

    /** Called from the entity render hook when an entity is skipped. */
    public static void countCulled() {
        entitiesCulledThisFrame++;
    }

    public static void countConsidered(int count) {
        entitiesConsideredThisFrame = count;
    }

    public static void countDrawn() {
        entitiesDrawnAndTested++;
    }

    public static int getEntitiesCulledThisFrame() {
        return entitiesCulledThisFrame;
    }

    public static int getEntitiesConsideredThisFrame() {
        return entitiesConsideredThisFrame;
    }

    public static int getEntitiesDrawnThisFrame() {
        return entitiesDrawnAndTested;
    }

    /** Emits one summary line per second when {@code logStatistics} is enabled. */
    public static void tickStatisticsLog() {
        long now = System.nanoTime();
        if (now - lastLogNanos < 1_000_000_000L) {
            return;
        }
        lastLogNanos = now;

        CullingEngine.CullResult result = CullingState.getLastResult();
        OpticoreConfig config = OpticoreConfig.get();

        OpticoreLog.info(
                "Opticore: {} culled / {} drawn this frame | worker {} ms | capture interval {} ms | avg frame {} ms | 1% low {} FPS",
                entitiesCulledThisFrame,
                entitiesDrawnAndTested,
                String.format("%.3f", CullingState.getLastWorkerMillis()),
                config.captureIntervalMs,
                String.format("%.2f", FrameProfiler.getAverageFrameTimeMs()),
                String.format("%.1f", FrameProfiler.getOnePercentLowFps())
        );

        if (result != null && OpticoreLog.isDebugEnabled()) {
            OpticoreLog.info("Opticore: worker scanned {} entities (generation {})",
                    result.scannedEntities(), result.generation());
        }
    }

    /** Forces the next statistics line to be emitted immediately. */
    public static void resetLogTimer() {
        lastLogNanos = 0L;
    }
}
