package me.jellysquid.mods.sodium.mixin.opticore;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.embeddedt.embeddium.opticore.client.OpticoreClient;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame boundary and frustum capture.
 *
 * <p>Injecting here rather than into vanilla's terrain setup is deliberate. Embeddium replaces the
 * chunk-render scheduling and the frustum it uses, so hooking those would either fail to apply or
 * bind this mod to Embeddium internals. {@code renderLevel} itself is the frame entry point and is
 * stable across both.
 *
 * <p>{@code require = 0} is used throughout: a missing injection point degrades this mod to vanilla
 * behaviour instead of crashing the game at startup.
 *
 * <p>The matrices are read from the method's own parameters. In 1.20.1 {@code Camera} exposes no
 * projection or view matrix accessor — those are a Fabric/Yarn addition — so the {@link PoseStack}
 * and {@link GameRenderer#getProjectionMatrix(double)} are the only reliable sources. Using the
 * pose stack's top entry also means the frustum matches the frame exactly, including view bobbing.
 *
 * <p>This class lives under {@code me.jellysquid.mods.sodium.mixin} rather than beside the rest of
 * the opticore implementation because {@code embeddium.mixins.json} declares
 * {@code "package": "me.jellysquid.mods.sodium.mixin"} and its mixin lists are resolved relative to
 * that root. It also makes the {@code opticore} rule in {@code MixinConfig} match, since that rule
 * is looked up by walking the mixin class's package path segment by segment.
 */
@Mixin(value = LevelRenderer.class, priority = 1500)
public abstract class OpticoreLevelRendererMixin {

    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V", shift = At.Shift.AFTER),
            require = 0
    )
    private void opticore$beginFrame(PoseStack poseStack,
                                     float partialTick,
                                     long finishTimeNano,
                                     boolean renderBlockOutline,
                                     Camera camera,
                                     GameRenderer gameRenderer,
                                     LightTexture lightTexture,
                                     Matrix4f projectionMatrix,
                                     CallbackInfo ci) {
        OpticoreClient.onBeginFrame(poseStack, projectionMatrix);
    }

    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void opticore$endFrame(PoseStack poseStack,
                                   float partialTick,
                                   long finishTimeNano,
                                   boolean renderBlockOutline,
                                   Camera camera,
                                   GameRenderer gameRenderer,
                                   LightTexture lightTexture,
                                   Matrix4f projectionMatrix,
                                   CallbackInfo ci) {
        OpticoreClient.onEndFrame();
    }
}
