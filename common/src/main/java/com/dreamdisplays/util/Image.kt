package com.dreamdisplays.util

import com.mojang.blaze3d.platform.NativeImage
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import org.jspecify.annotations.NullMarked
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.URI
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import javax.imageio.ImageIO

/**
 * Utility object for image handling.
 */
@NullMarked
object Image {
    @JvmStatic
    fun fetchImageTextureFromUrl(url: String): CompletableFuture<DynamicTexture> {
        val imageFuture = CompletableFuture.supplyAsync {
            try {
                val bi =
                    ImageIO.read(URL.of(URI.create(url), null)) ?: throw IOException("Failed to decode image: $url")
                convertToNativeImage(bi)
            } catch (e: Exception) {
                LoggingManager.error("Failed to load image from $url", e)
                throw RuntimeException("Failed to load image from URL", e)
            }
        }

        return imageFuture.thenCompose(Function { nativeImage: NativeImage? ->
            val texFuture = CompletableFuture<DynamicTexture>()
            Minecraft.getInstance().execute {
                try {
                    val tex = DynamicTexture({ url }, nativeImage!!)
                    texFuture.complete(tex)
                    nativeImage.close()
                } catch (t: Throwable) {
                    texFuture.completeExceptionally(t)
                    nativeImage!!.close()
                }
            }
            texFuture
        })
    }

    // Converts BufferedImage to NativeImage
    private fun convertToNativeImage(img: BufferedImage): NativeImage {
        val width = img.width
        val height = img.height
        val nativeImage = NativeImage(NativeImage.Format.RGBA, width, height, false)

        for (y in 0..<height) {
            for (x in 0..<width) {
                val argb = img.getRGB(x, y)
                val a = (argb shr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val rgba = (a shl 24) or (b shl 16) or (g shl 8) or r
                nativeImage.setPixelABGR(x, y, rgba)
            }
        }

        return nativeImage
    }
}
