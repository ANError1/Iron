package org.embeddedt.embeddium.opticore.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.embeddedt.embeddium.opticore.OpticoreConfig;
import org.embeddedt.embeddium.opticore.culling.CullingEngine;
import org.embeddedt.embeddium.opticore.culling.RaycastBridge;
import org.embeddedt.embeddium.opticore.util.CullingState;
import org.embeddedt.embeddium.opticore.util.CullingTracker;
import org.embeddedt.embeddium.opticore.util.FrameProfiler;
import org.embeddedt.embeddium.opticore.util.LoadEstimator;
import org.embeddedt.embeddium.opticore.util.OpticoreLog;

/**
 * {@code /opticore} client command for inspecting and editing the configuration in game.
 *
 * <p>Client-side only: this never touches the server and works while connected to a vanilla one.
 */
public final class OpticoreCommand {
    private OpticoreCommand() {}

    private enum Setting {
        ENABLED, OVERLAY, DISTANCE_CULLING, OCCLUSION_CULLING, LOG_STATISTICS
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("opticore")
                .executes(OpticoreCommand::status);

        root.then(Commands.literal("status").executes(OpticoreCommand::status));

        root.then(Commands.literal("enabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setBoolean(ctx, Setting.ENABLED))));

        root.then(Commands.literal("overlay")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setBoolean(ctx, Setting.OVERLAY))));

        root.then(Commands.literal("distanceCulling")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setBoolean(ctx, Setting.DISTANCE_CULLING))));

        root.then(Commands.literal("occlusionCulling")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setBoolean(ctx, Setting.OCCLUSION_CULLING))));

        root.then(Commands.literal("logStatistics")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setBoolean(ctx, Setting.LOG_STATISTICS))));

        root.then(Commands.literal("captureInterval")
                .then(Commands.argument("millis", IntegerArgumentType.integer(10, 2000))
                        .executes(OpticoreCommand::setCaptureInterval)));

        root.then(Commands.literal("reset").executes(OpticoreCommand::reset));

        dispatcher.register(root);
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        OpticoreConfig config = OpticoreConfig.get();
        CullingEngine engine = CullingEngine.getInstance();

        reply(ctx, Component.literal("Opticore"
                + (config.enabled ? "" : " (disabled)")
                + " — worker " + (engine.isRunning() ? "running" : "stopped")));

        reply(ctx, Component.literal(String.format("  frame %.2f ms | 1%% low %.1f FPS",
                FrameProfiler.getAverageFrameTimeMs(), FrameProfiler.getOnePercentLowFps())));

        reply(ctx, Component.literal(String.format("  culled %d of %d entities | worker %.3f ms | load %.2f",
                CullingTracker.getEntitiesCulledThisFrame(),
                CullingTracker.getEntitiesConsideredThisFrame(),
                CullingState.getLastWorkerMillis(),
                LoadEstimator.getPressure())));

        reply(ctx, Component.literal("  distanceCulling=" + config.enableDistanceCulling
                + " occlusionCulling=" + config.enableOcclusionCulling
                + " raycastPending=" + RaycastBridge.hasPendingWork()));

        reply(ctx, Component.literal("  cutoffs: passive=" + config.passiveMobDistance
                + " hostile=" + config.hostileMobDistance
                + " stand=" + config.armorStandDistance
                + " item=" + config.droppedItemDistance
                + " misc=" + config.miscEntityDistance));

        return 1;
    }

    private static int setBoolean(CommandContext<CommandSourceStack> ctx, Setting setting) {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        OpticoreConfig config = OpticoreConfig.get();

        switch (setting) {
            case ENABLED -> config.enabled = value;
            case OVERLAY -> config.showOverlay = value;
            case DISTANCE_CULLING -> config.enableDistanceCulling = value;
            case OCCLUSION_CULLING -> {
                config.enableOcclusionCulling = value;
                RaycastBridge.reset();
            }
            case LOG_STATISTICS -> {
                config.logStatistics = value;
                CullingTracker.resetLogTimer();
            }
        }

        config.save();

        if (setting == Setting.ENABLED && !value) {
            CullingState.clear();
        }

        reply(ctx, Component.literal("Opticore: " + setting.name().toLowerCase() + " = " + value));
        return 1;
    }

    private static int setCaptureInterval(CommandContext<CommandSourceStack> ctx) {
        int millis = IntegerArgumentType.getInteger(ctx, "millis");
        OpticoreConfig config = OpticoreConfig.get();
        config.captureIntervalMs = millis;
        config.save();

        reply(ctx, Component.literal("Opticore: captureInterval = " + millis + " ms"));
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        OpticoreConfig config = OpticoreConfig.get();
        config.applyDefaults();
        config.save();

        CullingState.clear();
        CullingEngine.getInstance().invalidate();
        RaycastBridge.reset();

        reply(ctx, Component.literal("Opticore: configuration reset to defaults"));
        return 1;
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(message, false);
        }

        OpticoreLog.info("{}", message.getString());
    }
}
