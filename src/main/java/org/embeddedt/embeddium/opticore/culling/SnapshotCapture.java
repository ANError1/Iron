package org.embeddedt.embeddium.opticore.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.embeddedt.embeddium.opticore.OpticoreConfig;
import org.embeddedt.embeddium.opticore.util.ModCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs on the render thread and copies the minimum amount of state needed by the worker.
 *
 * <p>This is the only place where live entities are read. It performs no occlusion raycasts itself;
 * those are issued through {@link RaycastBridge}, which hops to the main thread, so this method
 * stays cheap and non-blocking.
 */
public final class SnapshotCapture {
    private SnapshotCapture() {}

    private static long lastCaptureNanos;
    private static long generation;
    private static long frameCounter;

    private static final List<Entity> SCRATCH = new ArrayList<>(256);

    /**
     * Maps an entity id to the probe request issued for it, so answers are matched to the entity they
     * were requested for. Render thread only.
     */
    private static final it.unimi.dsi.fastutil.ints.Int2LongMap PENDING_PROBES =
            new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

    /**
     * Builds a snapshot if enough time has elapsed since the previous one.
     *
     * @return a snapshot, or null when the interval has not elapsed or no world is loaded
     */
    public static VisibilitySnapshot captureIfDue(Minecraft client, FrustumCuller frustum) {
        OpticoreConfig config = OpticoreConfig.get();

        if (!config.enabled || client.level == null || client.player == null) {
            return null;
        }

        long now = System.nanoTime();
        long interval = config.captureIntervalMs * 1_000_000L;
        if (now - lastCaptureNanos < interval) {
            return null;
        }
        lastCaptureNanos = now;
        frameCounter++;

        return capture(client, frustum, now);
    }

    private static VisibilitySnapshot capture(Minecraft client, FrustumCuller frustum, long captureNanos) {
        OpticoreConfig config = OpticoreConfig.get();
        var camera = client.gameRenderer.getMainCamera();

        // Prefer the actual camera position; it differs from the player when a third-person view is
        // active. getLookVector() returns a Vector3f, for which Vec3 has a dedicated constructor.
        boolean cameraReady = camera != null && camera.isInitialized();

        Vec3 cameraPos = cameraReady
                ? camera.getPosition()
                : client.player.getEyePosition();

        Vec3 look = cameraReady
                ? new Vec3(camera.getLookVector())
                : client.player.getViewVector(1.0F);

        // Normalise defensively: a zero-length look vector would make every forward test arbitrary.
        double lookLength = look.length();
        if (lookLength < 1.0e-6) {
            look = Vec3.ZERO;
        } else {
            look = look.scale(1.0 / lookLength);
        }

        double camX = cameraPos.x;
        double camY = cameraPos.y;
        double camZ = cameraPos.z;

        boolean occlusionUnavailable = !config.enableOcclusionCulling || RaycastBridge.wasWorldUnavailable();

        var level = client.level;
        assert level != null;

        SCRATCH.clear();

        // entitiesForRendering() is the public ClientLevel accessor that vanilla itself uses while
        // rendering, and it returns a properly typed Iterable<Entity>. The obvious-looking
        // level.getEntities().getAll() does not compile here: the no-argument Level#getEntities() is
        // protected, and the overloads that are public require an EntityTypeTest plus an AABB.
        for (Entity entity : level.entitiesForRendering()) {
            if (entity != null) {
                SCRATCH.add(entity);
            }
        }

        List<VisibilitySnapshot.EntitySample> samples = new ArrayList<>(SCRATCH.size());

        double maxEntityDistanceSq = maxConfiguredDistanceSq(config);

        // Occlusion probes run on the client tick, so at most a few answers can be usefully consumed
        // per second. Capping how many results are applied per capture keeps the cost bounded on a
        // large entity count.
        int probesRemaining = Math.max(0, config.maxOcclusionTestsPerCapture);

        for (int i = 0; i < SCRATCH.size(); i++) {
            Entity entity = SCRATCH.get(i);
            if (entity.isRemoved()) {
                continue;
            }

            boolean isProtected = EntityClassifier.isProtected(entity);

            double dx = entity.getX() - camX;
            double dy = entity.getY() - camY;
            double dz = entity.getZ() - camZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;

            double thresholdSq = isProtected || !config.enableDistanceCulling
                    ? Double.MAX_VALUE
                    : EntityClassifier.distanceThresholdSq(entity);

            double forwardDot = dx * look.x + dy * look.y + dz * look.z;

            // Only spend raycasts on entities the frustum can actually see. Without this test the
            // occlusion budget would be consumed by entities behind the player.
            boolean onScreen = isProtected
                    || (frustum != null && frustum.isValid() && frustum.isVisible(entity.getBoundingBox()));

            boolean occludedCandidate = !isProtected
                    && onScreen
                    && forwardDot > 0.0
                    && distanceSq > OCCLUSION_MIN_DISTANCE_SQ
                    && distanceSq <= Math.min(thresholdSq, maxEntityDistanceSq);

            boolean lineOfSightUnavailable = true;
            boolean hasLineOfSight = true;

            if (occludedCandidate && probesRemaining > 0) {
                // A probe is requested at most once per capture, because Minecraft.execute drains on
                // the client tick and an answer can be several frames away.
                long requestId = RaycastBridge.request(cameraPos, entity.getBoundingBox().getCenter());

                if (requestId == 0L) {
                    // No new probe could be issued. Fall back to an earlier one for this same entity,
                    // whose answer may have arrived since.
                    //
                    // Int2LongMap.get returns the default (0) for a missing key; request ids start at
                    // 1, so 0 reliably means "no outstanding probe".
                    requestId = PENDING_PROBES.get(entity.getId());
                } else {
                    // Remember which entity this probe belongs to, so the answer is matched to the
                    // right entity instead of being applied to whichever entity happens to come next
                    // in iteration order.
                    PENDING_PROBES.put(entity.getId(), requestId);
                    probesRemaining--;
                }

                Boolean answer = RaycastBridge.resultFor(requestId);
                if (answer != null) {
                    lineOfSightUnavailable = false;
                    hasLineOfSight = answer;
                    PENDING_PROBES.remove(entity.getId());
                }
            }

            samples.add(new VisibilitySnapshot.EntitySample(
                    entity.getId(),
                    distanceSq,
                    thresholdSq,
                    forwardDot,
                    occludedCandidate,
                    lineOfSightUnavailable,
                    hasLineOfSight,
                    isProtected,
                    entity.getBoundingBox()
            ));
        }

        generation++;
        FRAME_STAMP_NANOS = captureNanos;

        return new VisibilitySnapshot(
                frameCounter,
                generation,
                camX, camY, camZ,
                look.x, look.y, look.z,
                captureNanos,
                samples,
                occlusionUnavailable
        );
    }

    /** Entities closer than this are never tested for occlusion; they are always visible. */
    private static final double OCCLUSION_MIN_DISTANCE_SQ = 16.0 * 16.0;

    private static volatile long FRAME_STAMP_NANOS;

    private static double maxConfiguredDistanceSq(OpticoreConfig config) {
        int max = Math.max(
                Math.max(config.passiveMobDistance, config.hostileMobDistance),
                Math.max(Math.max(config.armorStandDistance, config.droppedItemDistance), config.miscEntityDistance)
        );
        return (double) max * (double) max;
    }

    public static long getFrameStampNanos() {
        return FRAME_STAMP_NANOS;
    }

    /** Oculus and Iris render a shadow pass from the light's point of view; entity culling is
     *  suspended there because our frustum describes the player's view, not the light's. */
    public static boolean shouldSuspendForShaderPass() {
        return ModCompat.isShaderStackPresent() && ShaderPassDetector.isShadowPass();
    }

    public static void reset() {
        lastCaptureNanos = 0L;
        generation = 0L;
        frameCounter = 0L;
        SCRATCH.clear();
        PENDING_PROBES.clear();
    }
}
