package org.embeddedt.embeddium.opticore.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.embeddedt.embeddium.opticore.culling.CullingEngine;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Render-thread view of the worker's decisions.
 *
 * <p>Holds the culled-id set for the current frame only. The mixin site performs a single
 * {@code contains} call against it, so it is deliberately just a hash set behind an atomic
 * reference rather than a richer structure.
 */
public final class CullingState {
    private static final AtomicReference<IntOpenHashSet> CULLED = new AtomicReference<>(new IntOpenHashSet());
    private static volatile CullingEngine.CullResult lastResult;
    private static volatile double lastWorkerMillis;
    private static volatile int lastScannedEntities;
    private static volatile long appliedFrame;

    private CullingState() {}

    /** Applies a worker result for the given frame. Called once per frame from the render hook. */
    public static void apply(CullingEngine.CullResult result, long currentFrame, int maxAgeFrames) {
        if (result == null) {
            return;
        }

        if (result.isStale(currentFrame, maxAgeFrames)) {
            // Results from a prior world have a larger frame id after the counter is reset, so they
            // must be rejected as well as ordinarily old results. Showing a few extra entities for
            // one frame is far less jarring than applying another world's entity ids.
            CULLED.set(EMPTY);
            return;
        }

        // The worker already publishes a defensive copy, so this reference is safe to share. It is
        // never mutated after publication.
        CULLED.set(result.culledEntityIds());
        lastResult = result;
        lastWorkerMillis = result.evaluationMillis();
        lastScannedEntities = result.scannedEntities();
        appliedFrame = currentFrame;
    }

    /** Drops all culling decisions, used on world unload or when the mod is disabled. */
    public static void clear() {
        CULLED.set(EMPTY);
        lastResult = null;
        lastWorkerMillis = 0.0;
        lastScannedEntities = 0;
        appliedFrame = 0L;
    }

    /**
     * Shared empty set. Only ever read, so a single instance is safe and avoids allocating on every
     * world unload or disabled frame.
     */
    private static final IntOpenHashSet EMPTY = new IntOpenHashSet();

    public static boolean isCulled(int entityId) {
        return CULLED.get().contains(entityId);
    }

    public static int getCulledCount() {
        return CULLED.get().size();
    }

    public static CullingEngine.CullResult getLastResult() {
        return lastResult;
    }

    public static double getLastWorkerMillis() {
        return lastWorkerMillis;
    }

    public static int getLastScannedEntities() {
        return lastScannedEntities;
    }

    public static long getAppliedFrame() {
        return appliedFrame;
    }
}
