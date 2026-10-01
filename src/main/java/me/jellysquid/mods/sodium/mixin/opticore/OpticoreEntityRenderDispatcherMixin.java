package me.jellysquid.mods.sodium.mixin.opticore;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.embeddedt.embeddium.opticore.OpticoreConfig;
import org.embeddedt.embeddium.opticore.culling.ShaderPassDetector;
import org.embeddedt.embeddium.opticore.util.CullingState;
import org.embeddedt.embeddium.opticore.util.CullingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips entity rendering for entities the worker has marked as not visible.
 *
 * <p>Guards, in order of importance:
 * <ul>
 *   <li>Nothing is culled while Oculus or Iris is drawing a shadow pass, because the visibility data
 *       describes the player's view rather than the light's.</li>
 *   <li>Nothing is culled when the mod is disabled or the config cannot be read, so a broken config
 *       file degrades to vanilla behaviour instead of an empty world.</li>
 *   <li>Any failure inside the check is swallowed. A culling optimisation must never be able to
 *       crash the game.</li>
 * </ul>
 *
 * <p>This class lives under {@code me.jellysquid.mods.sodium.mixin} rather than beside the rest of
 * the opticore implementation because {@code embeddium.mixins.json} declares
 * {@code "package": "me.jellysquid.mods.sodium.mixin"} and its mixin lists are resolved relative to
 * that root. It also makes the {@code opticore} rule in {@code MixinConfig} match, since that rule
 * is looked up by walking the mixin class's package path segment by segment.
 */
@Mixin(value = EntityRenderDispatcher.class, priority = 1500)
public abstract class OpticoreEntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void opticore$cullEntity(Entity entity,
                                     double x, double y, double z,
                                     float rotationYaw, float partialTicks,
                                     com.mojang.blaze3d.vertex.PoseStack poseStack,
                                     net.minecraft.client.renderer.MultiBufferSource bufferSource,
                                     int packedLight,
                                     CallbackInfo ci) {
        if (entity == null || ci.isCancelled()) {
            return;
        }

        try {
            if (!OpticoreConfig.get().enabled) {
                return;
            }

            if (ShaderPassDetector.isShadowPass()) {
                return;
            }

            if (CullingState.isCulled(entity.getId())) {
                CullingTracker.countCulled();
                ci.cancel();
                return;
            }

            CullingTracker.countDrawn();
        } catch (Throwable ignored) {
            // Never propagate: rendering must continue even if our state is inconsistent.
        }
    }
}
