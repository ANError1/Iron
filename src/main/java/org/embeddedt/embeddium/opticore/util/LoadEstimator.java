package org.embeddedt.embeddium.opticore.util;

import org.embeddedt.embeddium.opticore.OpticoreConfig;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Converts frame-time feedback into a single advisory pressure value in the range {@code [0, 3]}.
 *
 * <p>This does not currently drive any rendering change. It exists so the HUD and the statistics log
 * can report how much headroom the client has, which is what the upstream project used to decide
 * whether aggressive culling was worth enabling. Keeping the calculation here means the policy and
 * the reporting cannot drift apart.
 */
public final class LoadEstimator {
    private static final double TARGET_FRAME_MS = 1000.0 / 60.0;

    private static volatile double smoothedFrameMs;
    private static double hysteresis = 1.0;
    private static final AtomicLong lastUpdateNanos = new AtomicLong();

    private LoadEstimator() {}

    public static void tick() {
        long now = System.nanoTime();
        long previous = lastUpdateNanos.get();
        if (previous != 0L && now - previous < 250_000_000L) {
            return;
        }
        lastUpdateNanos.set(now);

        double frameMs = FrameProfiler.getAverageFrameTimeMs();
        if (frameMs <= 0.0) {
            return;
        }

        // Exponential smoothing with a slow coefficient: quality decisions based on a single bad
        // frame are the main cause of visible oscillation in mods like this one.
        smoothedFrameMs = smoothedFrameMs == 0.0 ? frameMs : smoothedFrameMs * 0.9 + frameMs * 0.1;
    }

    /**
     * @return 1.0 when the frame budget is being met exactly, above 1.0 when the client is behind,
     *         clamped to 3.0 so one pathological frame cannot skew downstream reporting
     */
    public static double getPressure() {
        if (smoothedFrameMs <= 0.0) {
            return 1.0;
        }
        return clamp(smoothedFrameMs / TARGET_FRAME_MS);
    }

    public static double getSmoothedFrameMs() {
        return smoothedFrameMs;
    }

    private static double clamp(double value) {
        double applied = value * hysteresis;
        return Math.max(0.0, Math.min(3.0, applied));
    }

    public static void reset() {
        smoothedFrameMs = 0.0;
        hysteresis = 1.0;
        lastUpdateNanos.set(0L);
    }

    @SuppressWarnings("unused")
    static void setHysteresis(double value) {
        hysteresis = Math.max(0.25, Math.min(4.0, value));
    }

    /** @return the configured distance multiplier applied to entity cutoffs under load */
    public static double getDistanceScale() {
        OpticoreConfig config = OpticoreConfig.get();
        double pressure = getPressure();

        // Only tighten cutoffs once the client is meaningfully behind, and never past half of the
        // configured value. These thresholds are intentionally conservative.
        if (pressure > 1.4) {
            return 0.5;
        }
        if (pressure > 1.15) {
            return 0.75;
        }
        return 1.0;
    }

    @SuppressWarnings("unused")
    static boolean isConfigured() {
        return OpticoreConfig.get() != null;
    }
}
