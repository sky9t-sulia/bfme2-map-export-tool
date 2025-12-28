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
}