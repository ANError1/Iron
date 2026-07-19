package org.embeddedt.embeddium.gui;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.embeddedt.embeddium.api.EmbeddiumConstants;

@Mod.EventBusSubscriber(modid = EmbeddiumConstants.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FpsDisplayOverlay {
    private static long lastFrameTime = 0;
    private static int currentFps = 0;
    private static int frameCount = 0;

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("iron_fps_display", FpsDisplayOverlay::renderFps);
    }

    private static void renderFps(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!SodiumClientMod.options().performance.showFps) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.screen != null) {
            return;
        }

        updateFps();

        String fpsText = currentFps + " FPS";
        int x = 2;
        int y = 2;

        int textWidth = client.font.width(fpsText);
        int textHeight = client.font.lineHeight;

        guiGraphics.fill(x - 1, y - 1, x + textWidth + 1, y + textHeight + 1, 0x90505050);
        guiGraphics.drawString(client.font, fpsText, x, y, getFpsColor(currentFps), true);
    }

    private static int getFpsColor(int fps) {
        if (fps >= 60) {
            return 0xFF55FF55;
        } else if (fps >= 30) {
            return 0xFFFFFF55;
        } else {
            return 0xFFFF5555;
        }
    }

    private static void updateFps() {
        long now = System.currentTimeMillis();
        if (lastFrameTime == 0) {
            lastFrameTime = now;
            return;
        }

        long elapsed = now - lastFrameTime;
        frameCount++;

        if (elapsed >= 500) {
            currentFps = (int) (frameCount * 1000L / elapsed);
            frameCount = 0;
            lastFrameTime = now;
        }
    }
}
