package org.embeddedt.embeddium.opticore.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.embeddedt.embeddium.opticore.util.OpticoreLog;

/**
 * Line-of-sight probes used to detect entities hidden behind terrain.
 *
 * <p>This is the only work in the mod that needs live world data, and therefore the only place where
 * a background thread could race the main thread. The hazard is removed by never raycasting off the
 * render thread: requests are queued onto the client's task queue and executed there.
 *
 * <h2>Why results are keyed</h2>
 * {@code Minecraft.execute} drains on the client tick, not per frame, so an answer can arrive
 * several frames after it was requested. That makes a single "last result" field unusable — a stale
 * answer belonging to entity A would be read back as the verdict for entity B.
 *
 * <p>Instead every request gets an id, and the answer is only reported when the id matches. A caller
 * that finds no matching answer treats the entity as visible, which is the safe direction: a missed
 * cull costs a little rendering, whereas a wrong cull makes entities flicker in and out.
 */
public final class RaycastBridge {
    /** Id of the request currently queued, or 0 when idle. */
    private static volatile long pendingRequestId;
    private static volatile Vec3 pendingFrom;
    private static volatile Vec3 pendingTo;

    /** Id of the most recently completed request, and its answer. */
    private static volatile long completedRequestId;
    private static volatile boolean completedResult = true;
    private static volatile boolean worldUnavailable;

    private static long nextRequestId = 1L;

    private RaycastBridge() {}

    /**
     * Requests a probe from {@code from} to {@code to}, if none is already queued.
     *
     * <p>Returns without blocking. The answer becomes available to {@link #resultFor} on a later
     * frame; until then the entity should be treated as visible.
     *
     * @return the request id, or 0 when a request is already in flight or no world is loaded
     */
    public static long request(Vec3 from, Vec3 to) {
        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null) {
            worldUnavailable = true;
            return 0L;
        }

        // Only one probe may be outstanding. The scheduler already caps how many are issued per
        // capture; allowing more would queue latency without improving accuracy, since the camera
        // will have moved by the time they run.
        if (pendingRequestId != 0L) {
            return 0L;
        }

        long requestId = nextRequestId++;
        pendingRequestId = requestId;
        pendingFrom = from;
        pendingTo = to;

        try {
            client.execute(() -> executeProbe(requestId, from, to));
        } catch (Throwable t) {
            // If the queue rejects the task, clear the slot so future requests are not blocked.
            if (pendingRequestId == requestId) {
                pendingRequestId = 0L;
            }
            OpticoreLog.warn("Could not queue a line-of-sight probe", t);
            return 0L;
        }

        return requestId;
    }

    private static void executeProbe(long requestId, Vec3 from, Vec3 to) {
        try {
            Level level = Minecraft.getInstance().level;

            if (level == null) {
                completedRequestId = requestId;
                completedResult = true;
                return;
            }

            BlockHitResult hit = level.clip(new ClipContext(
                    from,
                    to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    null
            ));

            completedResult = hit.getType() == HitResult.Type.MISS;
            completedRequestId = requestId;
        } catch (Throwable t) {
            // A failure must never surface as a hidden entity.
            completedResult = true;
            completedRequestId = requestId;
            OpticoreLog.warn("Line-of-sight probe failed; treating the target as visible", t);
        } finally {
            if (pendingRequestId == requestId) {
                pendingRequestId = 0L;
            }
        }
    }

    /**
     * Reads the answer for a specific request.
     *
     * @return {@code TRUE} when the path is clear, {@code FALSE} when it is blocked, or {@code null}
     *         when the answer has not arrived yet or the request has been superseded
     */
    public static Boolean resultFor(long requestId) {
        if (requestId == 0L || completedRequestId != requestId) {
            return null;
        }
        return completedResult;
    }

    public static boolean hasPendingWork() {
        return pendingRequestId != 0L;
    }

    public static boolean wasWorldUnavailable() {
        return worldUnavailable;
    }

    public static void reset() {
        pendingRequestId = 0L;
        pendingFrom = null;
        pendingTo = null;
        completedRequestId = 0L;
        completedResult = true;
        worldUnavailable = false;
    }

    @SuppressWarnings("unused")
    static Vec3 pendingFrom() {
        return pendingFrom;
    }

    @SuppressWarnings("unused")
    static Vec3 pendingTo() {
        return pendingTo;
    }
}
