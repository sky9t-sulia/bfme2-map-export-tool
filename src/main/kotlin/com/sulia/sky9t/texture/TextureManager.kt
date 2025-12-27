package com.sulia.sky9t.texture

import com.sulia.sky9t.config.TextureMapping
import de.darkatra.bfme2.map.blendtile.BlendTileTexture
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists

// ============================================================================
// Domain Models
// ============================================================================

data class TextureInfo(
    val image: BufferedImage,
    val index: Int,
    val name: String
)

// ============================================================================
// Texture Manager
// ============================================================================

class TextureManager(
    private val textures: List<BlendTileTexture>,
    private val texturesDir: Path,
    private val textureMappings: List<TextureMapping>
) {
    private val textureNameCache = mutableMapOf<String, String>()
    private val textureImageCache = mutableMapOf<String, BufferedImage>()

    fun getTextureNames(): Map<String, String> {
        if (textureNameCache.isEmpty()) {
            loadTextureNames()
        }
        return textureNameCache
    }

    fun getTextureImages(): Map<String, BufferedImage> {
        if (textureImageCache.isEmpty()) {
            loadTextureImages()
        }
        return textureImageCache
    }

    fun getTextureForTileValue(tileValue: UShort): TextureInfo? {
        // Ensure textures are loaded
        if (textureImageCache.isEmpty()) {
            loadTextureImages()
        }

        var cellStart = 0u

        textures.forEachIndexed { index, texture ->
            val textureImage = textureImageCache[texture.name] ?: return@forEachIndexed

            val totalCells = calculateTotalCells(textureImage)

            if (tileValue in cellStart until cellStart + totalCells) {
                return TextureInfo(
                    image = textureImage,
                    index = index,
                    name = texture.name
                )
            }

            cellStart += totalCells
        }

        return null
    }

    private fun loadTextureNames() {
        textures.forEach { texture ->
            val fileName = mapTextureNameToFile(texture.name)
            textureNameCache[texture.name] = fileName
        }
    }

    private fun loadTextureImages() {
        textures.forEach { texture ->
            val fileName = mapTextureNameToFile(texture.name)
            val textureFile = texturesDir.resolve(fileName)

            if (textureFile.exists()) {
                loadTextureImage(texture.name, textureFile)
            } else {
                printMissingTextureWarning(texture.name)
            }
        }
    }

    private fun loadTextureImage(textureName: String, textureFile: Path) {
        val textureImage = ImageIO.read(textureFile.toFile())
        if (textureImage != null) {
            textureImageCache[textureName] = textureImage
        }
    }

    private fun calculateTotalCells(image: BufferedImage): UInt {
        val gridCols = image.width / TEXTURE_CELL_SIZE
        val gridRows = image.height / TEXTURE_CELL_SIZE
        return (gridCols * gridRows).toUInt()
    }

    private fun mapTextureNameToFile(textureName: String): String {
        return textureMappings
            .firstOrNull { it.textureName == textureName }
            ?.textureFile
            ?: throw IllegalArgumentException("Texture name $textureName not found in terrain.ini")
    }

    private fun printMissingTextureWarning(textureName: String) {
        println("  Warning: Missing texture: $textureName")
    }

    companion object {
        private const val TEXTURE_CELL_SIZE = 32
    }
}

// ============================================================================
// Texture Utilities
// ============================================================================

object TextureUtils {
    fun normalMapName(textureFileName: String?): String {
        if (textureFileName == null) return "NO FILENAME"

        val dotIndex = textureFileName.lastIndexOf('.')
        return if (dotIndex != -1) {
            textureFileName.substring(0, dotIndex) + "_nrm" + textureFileName.substring(dotIndex)
        } else {
            "$textureFileName._nrm"
        }
    }
}