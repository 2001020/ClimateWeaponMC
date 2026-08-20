package com.stormweapon.client;

import com.stormweapon.StormWeaponMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/**
 * Red semi-transparent border/vignette drawn around the screen edge while a launcher countdown is
 * in progress, alongside the title/subtitle alert. {@code GuiGraphics.fillGradient} only blends
 * top-to-bottom regardless of the rectangle's orientation, which cannot produce a horizontal fade
 * for the left/right edges, so instead every edge is drawn as many thin, progressively fainter
 * concentric strips stepping inward from the border -- simple flat fills, but the stacked result
 * still reads as a smooth glow.
 */
public final class LauncherAlertOverlay {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "launcher_alert");
    private static final int RED_RGB = 0x00E0201A;
    private static final int PEAK_ALPHA = 150;
    // Noticeably smaller than vanilla's fixed title/subtitle scale (4.0F/2.0F), which cannot be
    // configured from the packets that drive it -- this overlay draws the alert text itself instead.
    private static final float TITLE_SCALE = 2.75F;
    private static final float SUBTITLE_SCALE = 1.5F;

    private LauncherAlertOverlay() {}

    public static void register() {
        AddGuiOverlayLayersEvent.BUS.addListener(event -> {
            event.getLayeredDraw().addBelow(
                ForgeLayeredDraw.VANILLA_ROOT, LAYER_ID, ForgeLayeredDraw.PRE_SLEEP_STACK, LauncherAlertOverlay::draw
            );
        });
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!LauncherAlertClientState.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int thickness = Math.max(20, Math.min(width, height) / 9);

        for (int i = 0; i < thickness; i++) {
            float t = i / (float)thickness;
            // Squared falloff: dense right at the edge, fading out quickly rather than a long
            // linear smear across the whole border band.
            int alpha = Math.round(PEAK_ALPHA * (1.0F - t) * (1.0F - t));
            if (alpha <= 0) {
                continue;
            }
            int color = (alpha << 24) | RED_RGB;
            graphics.fill(i, i, width - i, i + 1, color);
            graphics.fill(i, height - 1 - i, width - i, height - i, color);
            graphics.fill(i, i, i + 1, height - i, color);
            graphics.fill(width - 1 - i, i, width - i, height - i, color);
        }

        drawTitleAndSubtitle(graphics, minecraft, width, height);
    }

    private static void drawTitleAndSubtitle(GuiGraphicsExtractor graphics, Minecraft minecraft, int width, int height) {
        Font font = minecraft.font;
        Component title = Component.translatable("message.stormweapon.launcher.countdown_title");
        Component subtitle = Component.translatable(
            "message.stormweapon.launcher.countdown_subtitle", LauncherAlertClientState.secondsRemaining());
        int textColor = ARGB.white(255);

        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0F, height / 2.0F);

        graphics.pose().pushMatrix();
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        int titleWidth = font.width(title);
        graphics.textWithBackdrop(font, title, -titleWidth / 2, -10, titleWidth, textColor);
        graphics.pose().popMatrix();

        graphics.pose().pushMatrix();
        graphics.pose().scale(SUBTITLE_SCALE, SUBTITLE_SCALE);
        int subtitleWidth = font.width(subtitle);
        graphics.textWithBackdrop(font, subtitle, -subtitleWidth / 2, 5, subtitleWidth, textColor);
        graphics.pose().popMatrix();

        graphics.pose().popMatrix();
    }
}
