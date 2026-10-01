package org.embeddedt.embeddium.opticore.culling;

import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * An immutable description of what the camera could see at one instant.
 *
 * <p>This is the only object handed to the worker thread, and every field in it is either a
 * primitive or an immutable copy. By construction the worker cannot reach a live {@code Entity},
 * {@code Level}, or {@code ChunkAccess} through it, which is what makes the thread hop safe without
 * synchronisation.
 */
public record VisibilitySnapshot(long frameId,
                                 long generation,
                                 double cameraX,
                                 double cameraY,
                                 double cameraZ,
                                 double lookX,
                                 double lookY,
                                 double lookZ,
                                 long partialTickNanos,
                                 List<EntitySample> entities,
                                 boolean occlusionUnavailable) {

    public VisibilitySnapshot {
        entities = List.copyOf(entities);
    }

    /**
     * One entity, reduced to the values the worker needs.
     *
     * @param entityId             network/runtime entity id
     * @param distanceSq           squared distance from the camera to the entity position
     * @param maxDistanceSq        squared category cutoff, or {@link Double#MAX_VALUE} when protected
     * @param forwardDot           dot product of the camera's look vector with the direction to the
     *                             entity; negative means behind the camera
     * @param occludedCandidate    true when the entity is a sensible occlusion probe (on screen,
     *                             in front of the camera, and within the category distance)
     * @param lineOfSightUnavailable true when no raycast answer exists yet this frame
     * @param hasLineOfSight       the raycast answer, only meaningful when the above is false
     * @param protectedEntity      true when the entity may never be culled
     * @param bounds               the entity bounding box, retained for diagnostics only
     */
    public record EntitySample(int entityId,
                               double distanceSq,
                               double maxDistanceSq,
                               double forwardDot,
                               boolean occludedCandidate,
                               boolean lineOfSightUnavailable,
                               boolean hasLineOfSight,
                               boolean protectedEntity,
                               AABB bounds) {}

    @SuppressWarnings("unused")
    public int entityCount() {
        return this.entities.size();
    }
}
