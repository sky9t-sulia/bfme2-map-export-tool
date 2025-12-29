package com.sulia.sky9t.texture

import com.sulia.sky9t.config.MapExporterConfig
import com.sulia.sky9t.config.TextureNameFile
import de.darkatra.bfme2.map.MapFile
import de.darkatra.bfme2.map.blendtile.BlendTileTexture
import java.awt.Point
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class TextureManager(
    val mapFile: MapFile, val texturesMap: Map<String, TextureNameFile>, val config: MapExporterConfig
) {
    private val tileValueToTextureIndex: Map<UShort, Int> by lazy {
        buildTileValueIndex()
    }

    // Cache for reverse lookup: color -> texture name
    private val colorToTextureName = mutableMapOf<Int, String>()
    private val loadedTexturesCache: MutableMap<String, BufferedImage?> = mutableMapOf()

    fun getTextureImage(texture: BlendTileTexture): BufferedImage {
        if (loadedTexturesCache.containsKey(texture.name)) {
            return loadedTexturesCache[texture.name]!!
        }

        val textureData = texturesMap[texture.name] ?: throw Exception("Texture mapping not found for ${texture.name}")
        val textureFilePath = config.pathToTexturesFolder.resolve(textureData.textureFile)
        loadedTexturesCache[texture.name] = ImageIO.read(textureFilePath.toFile())

        return loadedTexturesCache[texture.name]!!
    }

    private fun buildTileValueIndex(): Map<UShort, Int> {
        val index = mutableMapOf<UShort, Int>()
        var cellStart = 0u

        mapFile.blendTileData.textures.forEachIndexed { textureIndex, texture ->
            val totalCells = texture.cellCount

            // Map all tile values in this texture's range to the texture index
            for (tileValue in cellStart until cellStart + totalCells) {
                index[tileValue.toUShort()] = textureIndex
            }

            cellStart += totalCells
        }

        return index
    }

    fun getTileTextureFromTileValue(tileValue: UShort, x: UInt = 0u, y: UInt = 0u): Pair<BlendTileTexture, Point>? {
        val textureIndex = tileValueToTextureIndex[(tileValue / 4u).toUShort()] ?: return null
        val texture = mapFile.blendTileData.textures[textureIndex]

        val tilesPerRow = texture.cellSize * 2u
        val textureOffset = Point(
            ((x % tilesPerRow) * config.tileSize).toInt(),
            ((y % tilesPerRow) * config.tileSize).toInt()
        )

        return Pair(texture, textureOffset)
    }

    fun getTextureNameFromColor(rgb: Int): String? {
        // Check cache first
        colorToTextureName[rgb]?.let { return it }

        // Sample all loaded textures and find the closest match
        // This is expensive, so cache the result!
        var closestMatch: String? = null
        var minDistance = Int.MAX_VALUE

        // Iterate through all textures in the map file
        mapFile.blendTileData.textures.forEach { texture ->
            // Make sure texture is loaded
            val image = try {
                getTextureImage(texture)
            } catch (_: Exception) {
                return@forEach // Skip if texture can't be loaded
            }

            // Sample center pixel of texture
            val centerX = image.width / 2
            val centerY = image.height / 2
            val textureRgb = image.getRGB(centerX, centerY)

            val distance = colorDistance(rgb, textureRgb)
            if (distance < minDistance) {
                minDistance = distance
                closestMatch = texture.name
            }
        }

        // Cache for next time
        closestMatch?.let { colorToTextureName[rgb] = it }

        return closestMatch
    }

    private fun colorDistance(rgb1: Int, rgb2: Int): Int {
        val r1 = (rgb1 shr 16) and 0xFF
        val g1 = (rgb1 shr 8) and 0xFF
        val b1 = rgb1 and 0xFF

        val r2 = (rgb2 shr 16) and 0xFF
        val g2 = (rgb2 shr 8) and 0xFF
        val b2 = rgb2 and 0xFF

        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2

        return dr * dr + dg * dg + db * db
    }
}