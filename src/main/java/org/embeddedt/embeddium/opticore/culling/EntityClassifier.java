package org.embeddedt.embeddium.opticore.culling;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.embeddedt.embeddium.opticore.OpticoreConfig;

/**
 * Decides how far away a given entity type may still be rendered.
 *
 * <p>Only categories that are cheap to re-derive and safe to hide are considered. Players are never
 * culled, and neither are things the player is likely to be interacting with.
 */
public final class EntityClassifier {
    private EntityClassifier() {}

    /**
     * Entities that must never be hidden regardless of distance.
     *
     * <p>Players are excluded because hiding another player is the single most noticeable and most
     * complained-about artefact in this class of mod. Vehicles are excluded because the local player
     * may be riding them. End crystals and projectiles are excluded because they carry gameplay
     * information the player needs at range.
     */
    public static boolean isProtected(Entity entity) {
        return entity instanceof Player
                || entity.isPassenger()
                || entity.isVehicle()
                || entity instanceof EndCrystal
                || entity instanceof Projectile;
    }

    /**
     * @return the maximum squared distance at which this entity is still drawn, or
     *         {@link Double#MAX_VALUE} when the entity is never distance-culled
     */
    public static double distanceThresholdSq(Entity entity) {
        OpticoreConfig config = OpticoreConfig.get();

        int blocks = categoryDistance(entity, config);

        return (double) blocks * (double) blocks;
    }

    private static int categoryDistance(Entity entity, OpticoreConfig config) {
        if (entity instanceof Enemy) {
            return config.hostileMobDistance;
        }
        if (entity instanceof ArmorStand) {
            return config.armorStandDistance;
        }
        if (entity instanceof ItemEntity) {
            return config.droppedItemDistance;
        }
        if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature) {
            return config.passiveMobDistance;
        }
        return config.miscEntityDistance;
    }
}
