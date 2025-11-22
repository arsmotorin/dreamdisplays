package com.dreamdisplays.util;

import me.inotsleep.utils.logging.LoggingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class ImageUtil {

    public static CompletableFuture<DynamicTexture> fetchImageTextureFromUrl(String url) {
        CompletableFuture<NativeImage> imageFuture = CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage bi = ImageIO.read(URL.of(URI.create(url), null));
                if (bi == null) {
                    throw new IOException("Failed to decode image: " + url);
                }
                return convertToNativeImage(bi);
            } catch (Exception e) {
                LoggingManager.error("Failed to load image from " + url, e);
                return null;
            }
        });

        return imageFuture.thenCompose(nativeImage -> {
            CompletableFuture<DynamicTexture> texFuture = new CompletableFuture<>();

            Minecraft.getInstance().execute(() -> {
                    try {
                        DynamicTexture tex = new DynamicTexture(() -> url, nativeImage);
                        texFuture.complete(tex);
                    } catch (Throwable t) {
                        texFuture.completeExceptionally(t);
                    }
                });

            return texFuture;
        });
    }

    // Converts BufferedImage to NativeImage
    private static NativeImage convertToNativeImage(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int rgba = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setPixelABGR(x, y, rgba);

            }
        }

        return nativeImage;
    }
}