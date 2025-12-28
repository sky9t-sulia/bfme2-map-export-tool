package com.sulia.sky9t.extension

import java.awt.image.BufferedImage

fun BufferedImage.applyAlphaMask(mask: BufferedImage): BufferedImage {
    val result = toARGB()

    for (y in 0 until result.height) {
        for (x in 0 until result.width) {
            val baseColor = result.getRGB(x, y)
            val maskAlpha = (mask.getRGB(x, y) ushr 24) and 0xFF

            val red = (baseColor ushr 16) and 0xFF
            val green = (baseColor ushr 8) and 0xFF
            val blue = baseColor and 0xFF

            val blendedColor = (maskAlpha shl 24) or (red shl 16) or (green shl 8) or blue
            result.setRGB(x, y, blendedColor)
        }
    }

    return result
}

private fun BufferedImage.toARGB(): BufferedImage {
    if (type == BufferedImage.TYPE_INT_ARGB) return this

    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
        val g = createGraphics()
        try {
            g.drawImage(this@toARGB, 0, 0, null)
        } finally {
            g.dispose()
        }
    }
}