package org.embeddedt.embeddium.opticore.culling;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.embeddedt.embeddium.opticore.OpticoreConfig;
import org.embeddedt.embeddium.opticore.util.FrameProfiler;
import org.embeddedt.embeddium.opticore.util.OpticoreLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Background worker that turns an immutable {@link VisibilitySnapshot} into a set of entity ids to
 * skip this frame.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Only one snapshot is ever in flight. A newer capture replaces an unprocessed one, because a
 *       stale frame of visibility data is worse than no data.</li>
 *   <li>The main thread never blocks. It polls the result and, if none is ready, renders everything.
 *       This is why a slow worker degrades gracefully into vanilla behaviour instead of stuttering.</li>
 *   <li>Results carry the frame they were produced for. The consumer rejects anything older than
 *       {@code maxResultAgeFrames}, which prevents a hiccup from leaving entities invisible.</li>
 *   <li>Entity ids are the only thing that crosses the thread boundary in the result direction.
 *       No world, entity, or chunk object is retained by the worker.</li>
 * </ul>
 */
public final class CullingEngine {
    /** Bounded so a stuck worker cannot grow the queue without limit. */
    private static final int REQUEST_QUEUE_CAPACITY = 2;

    private final BlockingQueue<VisibilitySnapshot> requests = new ArrayBlockingQueue<>(REQUEST_QUEUE_CAPACITY);
    private final AtomicReference<CullResult> result = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong acceptedGeneration = new AtomicLong();

    private volatile Thread worker;
    private volatile long resyncTarget;

    /** Owned by the worker thread only; never read from the render thread. */
    private final IntOpenHashSet scratchCulled = new IntOpenHashSet();

    private static final CullingEngine INSTANCE = new CullingEngine();

    public static CullingEngine getInstance() {
        return INSTANCE;
    }

    private CullingEngine() {}

    public void start() {
        if (this.running.compareAndSet(false, true)) {
            this.result.set(null);
            this.resyncTarget = 0L;

            Thread thread = new Thread(this::runLoop, "Opticore-Cull-Worker");
            thread.setDaemon(true);
            // Below normal priority: this work is purely advisory and must never starve the client.
            thread.setPriority(Math.max(Thread.MIN_PRIORITY + 1, Thread.NORM_PRIORITY - 2));
            this.worker = thread;
            thread.start();

            OpticoreLog.info("Culling worker started");
        }
    }

    public void stop() {
        if (this.running.compareAndSet(true, false)) {
            Thread thread = this.worker;
            if (thread != null) {
                thread.interrupt();
            }
            this.requests.clear();
            this.result.set(null);
            this.worker = null;
        }
    }

    /**
     * Hands a freshly captured snapshot to the worker, discarding any unprocessed predecessor.
     *
     * @return false when the worker is not running or the snapshot was already superseded
     */
    public boolean submit(VisibilitySnapshot snapshot) {
        if (!this.running.get() || snapshot == null) {
            return false;
        }

        // Offer first so the common case (empty queue) never allocates or locks twice.
        if (!this.requests.offer(snapshot)) {
            // Queue full: drop the oldest unprocessed snapshot and retry once.
            this.requests.poll();
            if (!this.requests.offer(snapshot)) {
                return false;
            }
        }

        return true;
    }

    /** Non-blocking read of the latest result. */
    public CullResult poll() {
        return this.result.getAndSet(null);
    }

    public CullResult peek() {
        return this.result.get();
    }

    public boolean isRunning() {
        return this.running.get();
    }

    /** Frame number the caller last consumed, used to let the worker skip redundant captures. */
    public long getAcceptedGeneration() {
        return this.acceptedGeneration.get();
    }

    /**
     * Requests a full re-evaluation on the next submission, used after a world change or a config
     * edit. The worker clears its scratch state when the generation drops to zero.
     */
    public void invalidate() {
        this.resyncTarget = 1L;
        this.result.set(null);
    }

    private void runLoop() {
        CullResult previous = null;

        while (this.running.get()) {
            try {
                VisibilitySnapshot snapshot = this.requests.poll(250L, TimeUnit.MILLISECONDS);

                if (snapshot == null) {
                    // Idle: let the JVM reclaim the previous frame's id set.
                    previous = null;
                    this.scratchCulled.clear();
                    continue;
                }

                if (this.resyncTarget != 0L) {
                    this.scratchCulled.clear();
                    previous = null;
                    this.resyncTarget = 0L;
                }

                previous = evaluate(snapshot, previous);
                this.result.set(previous);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                // A crash on this thread would silently disable culling; log it and keep going.
                OpticoreLog.warn("Culling worker encountered an error and will continue", t);
            }
        }
    }

    private CullResult evaluate(VisibilitySnapshot snapshot, CullResult previous) {
        OpticoreConfig config = OpticoreConfig.get();
        IntOpenHashSet culled = this.scratchCulled;
        culled.clear();

        List<VisibilitySnapshot.EntitySample> samples = snapshot.entities();

        long predictStart = System.nanoTime();
        for (int i = 0; i < samples.size(); i++) {
            VisibilitySnapshot.EntitySample sample = samples.get(i);

            if (sample.protectedEntity()) {
                continue;
            }

            // Stage 1: hard distance cutoff, per entity category.
            if (config.enableDistanceCulling && sample.distanceSq() > sample.maxDistanceSq()) {
                culled.add(sample.entityId());
                continue;
            }

            // Stage 2: behind the camera and far enough that the player cannot see it.
            if (sample.distanceSq() > BACKFACE_MIN_DISTANCE_SQ && sample.forwardDot() < -BACKFACE_MARGIN) {
                culled.add(sample.entityId());
                continue;
            }

            // Stage 3: occlusion. Only entities that passed the frustum and distance tests are ever
            // treated as candidates, and probes are issued by SnapshotCapture on the render thread,
            // so this stage is pure bookkeeping here.
            if (!config.enableOcclusionCulling || snapshot.occlusionUnavailable()) {
                continue;
            }

            if (!sample.occludedCandidate()) {
                continue;
            }

            if (sample.lineOfSightUnavailable()) {
                // No answer yet. Keep the previous decision for this entity so it does not flip
                // visibility every time a probe is in flight.
                if (previous != null && previous.culledEntityIds().contains(sample.entityId())) {
                    culled.add(sample.entityId());
                }
                continue;
            }

            if (!sample.hasLineOfSight()) {
                culled.add(sample.entityId());
            }
        }
        long predictEnd = System.nanoTime();

        this.acceptedGeneration.set(snapshot.generation());

        // Publish an immutable snapshot of the scratch set, never the scratch set itself.
        //
        // The scratch set is reused every iteration, so handing it to the render thread would let
        // the worker clear and refill it while the main thread is calling contains() on it. That is
        // a real data race: IntOpenHashSet offers no thread safety, and a concurrent clear during a
        // lookup can return a wrong answer or leave the table inconsistent. Copying costs one small
        // allocation per pass and removes the hazard entirely.
        IntOpenHashSet published = new IntOpenHashSet(culled);

        return new CullResult(
                snapshot.frameId(),
                snapshot.generation(),
                published,
                predictEnd - predictStart,
                snapshot.entities().size()
        );
    }

    /**
     * An entity has to be at least this far away before "behind the camera" is allowed to cull it,
     * otherwise turning around would visibly pop nearby mobs into existence.
     */
    private static final double BACKFACE_MIN_DISTANCE_SQ = 24.0 * 24.0;

    /** Cosine-ish margin so entities at the very edge of the view are not dropped. */
    private static final double BACKFACE_MARGIN = 0.15;

    /** Result of a single worker pass. */
    public record CullResult(long frameId,
                             long generation,
                             IntOpenHashSet culledEntityIds,
                             long evaluationNanos,
                             int scannedEntities) {

        /** @return true when this result is too old to be worth applying */
        public boolean isStale(long currentFrame, int maxAgeFrames) {
            long age = currentFrame - this.frameId;
            // A negative age occurs after the frame counter is reset for a new world while an old
            // worker result is still being published. Treat it as stale instead of accepting it.
            return age < 0L || age > maxAgeFrames;
        }

        public double evaluationMillis() {
            return this.evaluationNanos / 1_000_000.0;
        }

        @SuppressWarnings("unused")
        public static CullResult empty() {
            return new CullResult(0L, 0L, new IntOpenHashSet(), 0L, 0);
        }
    }

    /** Convenience accessor used by the frame hook. */
    public static double currentFrameTimeMs() {
        return FrameProfiler.getAverageFrameTimeMs();
    }
}
