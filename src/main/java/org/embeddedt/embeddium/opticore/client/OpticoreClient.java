package org.embeddedt.embeddium.opticore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.embeddedt.embeddium.api.EmbeddiumConstants;
import org.embeddedt.embeddium.opticore.OpticoreConfig;
import org.embeddedt.embeddium.opticore.culling.CullingEngine;
import org.embeddedt.embeddium.opticore.culling.FrustumCuller;
import org.embeddedt.embeddium.opticore.culling.RaycastBridge;
import org.embeddedt.embeddium.opticore.culling.SnapshotCapture;
import org.embeddedt.embeddium.opticore.util.CullingState;
import org.embeddedt.embeddium.opticore.util.CullingTracker;
import org.embeddedt.embeddium.opticore.util.FrameProfiler;
import org.embeddedt.embeddium.opticore.util.LoadEstimator;
import org.embeddedt.embeddium.opticore.util.OpticoreLog;

/**
 * Client-side lifecycle and per-frame orchestration.
 *
 * <p>Everything except the entity render hook is driven from Forge events rather than mixins, which
 * keeps this mod's footprint on the render pipeline as small as possible. That matters on a
 * codebase where Embeddium has already replaced most of the interesting render methods.
 *
 * <p>All handlers are static. {@code @Mod.EventBusSubscriber} registers the class itself rather than
 * an instance, so this class is never constructed and does not need to be instantiable.
 *
 * <p>{@code bus = Bus.FORGE} is explicit: the game-lifecycle events used here live on the Forge bus,
 * not the mod bus that Embeddium's own subscribers use.
 */
@Mod.EventBusSubscriber(modid = EmbeddiumConstants.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class OpticoreClient {
    private static final FrustumCuller FRUSTUM = new FrustumCuller();

    /** Monotonic frame counter used to reject stale worker results. */
    private static long frameCounter;

    private static boolean initialised;

    private OpticoreClient() {}

    public static FrustumCuller frustum() {
        return FRUSTUM;
    }

    public static long currentFrame() {
        return frameCounter;
    }

    /**
     * Called once when the first world is joined. Deferred until then so that the config file and
     * the mod list are both definitely available.
     */
    public static void ensureStarted() {
        if (initialised) {
            return;
        }
        initialised = true;

        OpticoreConfig.get().sanitize();
        CullingEngine.getInstance().start();

        OpticoreLog.info("Opticore initialised (enabled: {})", OpticoreConfig.get().enabled);
    }

    /**
     * Per-frame entry point, invoked from the mixin on the world renderer.
     *
     * <p>The matrices are passed through rather than fetched here: in 1.20.1 the camera does not
     * expose its projection or view matrix, so the {@code renderLevel} parameters are the only
     * authoritative source.
     *
     * @param poseStack  the frame's pose stack, whose top entry is the model-view matrix
     * @param projection the frame's projection matrix
     */
    public static void onBeginFrame(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                    org.joml.Matrix4f projection) {
        if (!OpticoreConfig.get().enabled) {
            CullingState.clear();
            return;
        }

        frameCounter++;

        FrameProfiler.beginFrame();
        CullingTracker.reset();
        LoadEstimator.tick();

        boolean frustumReady = false;
        if (poseStack != null && projection != null) {
            frustumReady = FRUSTUM.update(poseStack.last().pose(), projection);
        }

        if (!frustumReady) {
            FRUSTUM.invalidate();
        }

        applyWorkerResult();

        if (!canCaptureThisFrame()) {
            return;
        }

        var snapshot = SnapshotCapture.captureIfDue(Minecraft.getInstance(), FRUSTUM);
        if (snapshot != null) {
            CullingTracker.countConsidered(snapshot.entityCount());
            CullingEngine.getInstance().submit(snapshot);
        }
    }

    /**
     * Entity culling must be suspended while Oculus or Iris draws the shadow pass, because our
     * frustum describes the player's view rather than the light's. Culling there would remove
     * shadows while leaving the entities themselves visible.
     */
    private static boolean canCaptureThisFrame() {
        return !SnapshotCapture.shouldSuspendForShaderPass();
    }

    private static void applyWorkerResult() {
        OpticoreConfig config = OpticoreConfig.get();
        CullingEngine.CullResult result = CullingEngine.getInstance().poll();
        if (result != null) {
            CullingState.apply(result, frameCounter, config.maxResultAgeFrames);
        }
    }

    public static void onEndFrame() {
        FrameProfiler.endFrame();
        FrameProfiler.tickLogging();
    }

    // ---------------------------------------------------------------------------------------------
    // Forge events
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        ensureStarted();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            resetForNewWorld();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!OpticoreConfig.get().showOverlay) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        // Respect the user's choice to show the vanilla F3 screen. In 1.20.1 there is no public
        // accessor for the debug overlay, so the renderDebug option flag is the supported check.
        if (client.options.renderDebug) {
            return;
        }
        drawOverlay(event.getGuiGraphics(), client);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        OpticoreCommand.register(event.getDispatcher());
    }

    private static void resetForNewWorld() {
        CullingState.clear();
        CullingEngine.getInstance().invalidate();
        RaycastBridge.reset();
        SnapshotCapture.reset();
        FrameProfiler.reset();
        LoadEstimator.reset();
        CullingTracker.resetLogTimer();
        FRUSTUM.invalidate();
        frameCounter = 0L;
    }

    private static void drawOverlay(GuiGraphics graphics, Minecraft client) {
        var font = client.font;
        int x = 4;
        int y = 4;
        int lineHeight = 10;

        double pressure = LoadEstimator.getPressure();
        String[] lines = {
                "Opticore",
                String.format("FPS %.0f  |  frame %.2f ms  |  1%% low %.1f",
                        FrameProfiler.getFps(),
                        FrameProfiler.getAverageFrameTimeMs(),
                        FrameProfiler.getOnePercentLowFps()),
                String.format("entities culled %d / %d",
                        CullingTracker.getEntitiesCulledThisFrame(),
                        CullingTracker.getEntitiesConsideredThisFrame()),
                String.format("worker %.3f ms  |  load %.2f", CullingState.getLastWorkerMillis(), pressure),
                CullingEngine.getInstance().isRunning() ? null : "worker stopped",
        };

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            graphics.drawString(font, line, x, y, 0xFFFFFFFF, true);
            y += lineHeight;
        }
    }
}
