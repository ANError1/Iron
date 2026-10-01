package org.embeddedt.embeddium.opticore.util;

import org.embeddedt.embeddium.opticore.OpticoreConfig;

import java.util.Arrays;

/**
 * Rolling frame-time statistics. This is the only input to the adaptive controllers, so it is kept
 * allocation-free on the hot path: samples are written into fixed ring buffers.
 */
public final class FrameProfiler {
    /** Roughly two seconds at 60 FPS. */
    private static final int HISTORY_SIZE = 120;

    private static final long[] frameTimes = new long[HISTORY_SIZE];
    private static int writeIndex;
    private static int sampleCount;

    private static long frameStartNanos;
    private static long lastFrameNanos;

    private static volatile double averageFrameTimeMs;
    private static volatile double onePercentLowFps;
    private static volatile double fps;

    private FrameProfiler() {}

    public static void beginFrame() {
        frameStartNanos = System.nanoTime();
    }

    public static void endFrame() {
        long start = frameStartNanos;
        if (start == 0L) {
            return;
        }
        frameStartNanos = 0L;

        long now = System.nanoTime();
        long duration = Math.max(0L, now - start);
        lastFrameNanos = duration;

        frameTimes[writeIndex] = duration;
        writeIndex = (writeIndex + 1) % HISTORY_SIZE;
        sampleCount = Math.min(HISTORY_SIZE, sampleCount + 1);

        if (sampleCount > 0) {
            long sum = 0L;
            for (int i = 0; i < sampleCount; i++) {
                sum += frameTimes[i];
            }
            double averageNs = (double) sum / sampleCount;
            averageFrameTimeMs = averageNs / 1_000_000.0;

            // The slowest 1% of frames, reported as FPS. This is what stutter actually feels like,
            // and it is the number the upstream project also reports.
            long[] sorted = Arrays.copyOf(frameTimes, sampleCount);
            Arrays.sort(sorted);
            int index = Math.min(sampleCount - 1, (int) Math.ceil(sampleCount * 0.99) - 1);
            long worst = sorted[Math.max(0, index)];
            onePercentLowFps = worst <= 0L ? 0.0 : 1_000_000_000.0 / worst;
            fps = averageNs <= 0.0 ? 0.0 : 1_000_000_000.0 / averageNs;
        }
    }

    public static double getAverageFrameTimeMs() {
        return averageFrameTimeMs;
    }

    public static double getOnePercentLowFps() {
        return onePercentLowFps;
    }

    public static double getFps() {
        return fps;
    }

    public static long getLastFrameNanos() {
        return lastFrameNanos;
    }

    public static int getSampleCount() {
        return sampleCount;
    }

    public static void reset() {
        Arrays.fill(frameTimes, 0L);
        writeIndex = 0;
        sampleCount = 0;
        frameStartNanos = 0L;
        lastFrameNanos = 0L;
        averageFrameTimeMs = 0.0;
        onePercentLowFps = 0.0;
        fps = 0.0;
    }

    /** Drives the optional periodic statistics line from the render thread. */
    public static void tickLogging() {
        if (OpticoreConfig.get().logStatistics) {
            CullingTracker.tickStatisticsLog();
        }
    }
}
