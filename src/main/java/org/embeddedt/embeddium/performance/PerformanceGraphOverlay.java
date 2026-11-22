package org.embeddedt.embeddium.performance;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.embeddedt.embeddium.api.EmbeddiumConstants;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders a performance graph overlay on the HUD showing FPS history.
 */
@Mod.EventBusSubscriber(modid = EmbeddiumConstants.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PerformanceGraphOverlay {
    private static final int GRAPH_WIDTH = 200;
    private static final int GRAPH_HEIGHT = 60;
    private static final int GRAPH_PADDING = 5;
    private static final int BACKGROUND_COLOR = 0xC0000000;
    private static final int GRID_COLOR = 0x40FFFFFF;
    private static final int FPS_LINE_COLOR = 0xFF00FF00;
    private static final int LOW_FPS_LINE_COLOR = 0xFFFF8800;
    
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("iron_performance_graph", PerformanceGraphOverlay::render);
    }
    
    private static void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        PerformanceHistoryConfig config = PerformanceHistoryConfig.get();
        if (!config.enabled || !config.showGraphOverlay) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.renderDebug) {
            return; // Don't show when menu is open or debug screen is active
        }
        
        PerformanceHistory history = PerformanceHistory.getInstance();
        List<PerformanceSnapshot> snapshots = history.getRecentSnapshots(GRAPH_WIDTH);
        
        if (snapshots.isEmpty()) {
            return;
        }
        
        // Position in bottom-right corner
        int x = screenWidth - GRAPH_WIDTH - GRAPH_PADDING;
        int y = screenHeight - GRAPH_HEIGHT - GRAPH_PADDING - 20; // Leave room for chat
        
        renderGraph(guiGraphics, x, y, snapshots);
        renderLabels(guiGraphics, mc, x, y, snapshots, history);
    }
    
    private static void renderGraph(GuiGraphics guiGraphics, int x, int y, List<PerformanceSnapshot> snapshots) {
        PoseStack poseStack = guiGraphics.pose();
        
        // Draw background
        guiGraphics.fill(x, y, x + GRAPH_WIDTH, y + GRAPH_HEIGHT, BACKGROUND_COLOR);
        
        // Draw grid lines
        drawHorizontalGridLines(guiGraphics, x, y);
        
        // Find max FPS for scaling
        double maxFps = 120.0; // Default ceiling
        for (PerformanceSnapshot snapshot : snapshots) {
            if (snapshot.getFps() > maxFps) {
                maxFps = Math.ceil(snapshot.getFps() / 30.0) * 30.0; // Round up to nearest 30
            }
        }
        
        // Draw FPS line
        drawPerformanceLine(poseStack, x, y, snapshots, maxFps, FPS_LINE_COLOR, true);
        
        // Draw 1% low FPS line
        drawPerformanceLine(poseStack, x, y, snapshots, maxFps, LOW_FPS_LINE_COLOR, false);
    }
    
    private static void drawHorizontalGridLines(GuiGraphics guiGraphics, int x, int y) {
        // Draw lines at 30, 60, 90, 120 FPS equivalents
        int[] fpsMarks = {30, 60, 90, 120};
        double maxFps = 120.0;
        
        for (int fpsMark : fpsMarks) {
            int lineY = y + GRAPH_HEIGHT - (int)((fpsMark / maxFps) * GRAPH_HEIGHT);
            guiGraphics.fill(x, lineY, x + GRAPH_WIDTH, lineY + 1, GRID_COLOR);
        }
    }
    
    private static void drawPerformanceLine(PoseStack poseStack, int x, int y, 
                                           List<PerformanceSnapshot> snapshots, 
                                           double maxFps, int color, boolean useFps) {
        if (snapshots.size() < 2) {
            return;
        }
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        
        Matrix4f matrix = poseStack.last().pose();
        
        int dataPoints = Math.min(snapshots.size(), GRAPH_WIDTH);
        float xStep = (float) GRAPH_WIDTH / dataPoints;
        
        for (int i = 0; i < dataPoints; i++) {
            PerformanceSnapshot snapshot = snapshots.get(snapshots.size() - dataPoints + i);
            double value = useFps ? snapshot.getFps() : snapshot.getOnePercentLowFps();
            
            float graphX = x + (i * xStep);
            float graphY = y + GRAPH_HEIGHT - (float)((value / maxFps) * GRAPH_HEIGHT);
            
            // Clamp to graph bounds
            graphY = Math.max(y, Math.min(y + GRAPH_HEIGHT, graphY));
            
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;
            
            bufferBuilder.vertex(matrix, graphX, graphY, 0).color(r, g, b, a).endVertex();
        }
        
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
    }
    
    private static void renderLabels(GuiGraphics guiGraphics, Minecraft mc, int x, int y, 
                                     List<PerformanceSnapshot> snapshots, PerformanceHistory history) {
        if (snapshots.isEmpty()) {
            return;
        }
        
        PerformanceSnapshot latest = snapshots.get(0);
        
        // Current FPS
        String fpsText = String.format("FPS: %.0f", latest.getFps());
        guiGraphics.drawString(mc.font, fpsText, x + 2, y + 2, FPS_LINE_COLOR, true);
        
        // 1% Low
        String lowText = String.format("1%% Low: %.0f", latest.getOnePercentLowFps());
        guiGraphics.drawString(mc.font, lowText, x + 2, y + 12, LOW_FPS_LINE_COLOR, true);
        
        // Memory
        String memText = String.format("Mem: %dMB/%dMB", 
            latest.getMemoryUsedMb(), latest.getMemoryTotalMb());
        guiGraphics.drawString(mc.font, memText, x + 2, y + 22, 0xFFAAAAFF, true);
        
        // Sample count
        String samplesText = String.format("%d samples", history.getSnapshotCount());
        int textWidth = mc.font.width(samplesText);
        guiGraphics.drawString(mc.font, samplesText, 
            x + GRAPH_WIDTH - textWidth - 2, y + GRAPH_HEIGHT - 10, 0xFF888888, true);
    }
}
