import de.darkatra.bfme2.map.blendtile.BlendTileTexture
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

// ============================================================================
// Helper Functions
// ============================================================================

fun normalMapName(textureFileName: String?): String {
    if (textureFileName != null) {
        val dotIndex = textureFileName.lastIndexOf('.')
        return if (dotIndex != -1) {
            textureFileName.substring(0, dotIndex) + "_nrm" + textureFileName.substring(dotIndex)
        } else {
            textureFileName + "_nrm"
        }
    }
    return "NO FILENAME"
}

fun loadTextureNames(textures: List<BlendTileTexture>, terrainMappings: Map<String, String>): Map<String, String> {
    val textureNames = mutableMapOf<String, String>()

    textures.forEach { texture ->
        val fileName = mapTextureNameToFile(texture.name, terrainMappings)
        textureNames[texture.name] = fileName
    }

    return textureNames
}

fun loadTextureImages(
    textures: List<BlendTileTexture>,
    texturesDir: Path,
    terrainMappings: Map<String, String>
): Map<String, BufferedImage> {
    val textureImages = mutableMapOf<String, BufferedImage>()

    textures.forEach { texture ->
        val fileName = mapTextureNameToFile(texture.name, terrainMappings)
        val textureFile = texturesDir.resolve(fileName).toFile()

        if (textureFile.exists()) {
            val textureImage = ImageIO.read(textureFile)
            if (textureImage != null) {
                textureImages[texture.name] = textureImage
            }
        } else {
            println("  Warning: Missing texture: ${texture.name}")
        }
    }

    return textureImages
}

fun getTextureForTileValue(
    tileValue: UInt,
    textures: List<BlendTileTexture>,
    textureImages: Map<String, BufferedImage>
): Pair<BufferedImage, Int>? {
    var cellStart = 0u

    textures.forEachIndexed { index, texture ->
        val textureImage = textureImages[texture.name]
        if (textureImage != null) {
            val gridCols = textureImage.width / 32
            val gridRows = textureImage.height / 32
            val totalCells = (gridCols * gridRows).toUInt()

            if (tileValue >= cellStart && tileValue < cellStart + totalCells) {
                return Pair(textureImage, index)
            }

            cellStart += totalCells
        }
    }

    return null
}

private fun mapTextureNameToFile(textureName: String, terrainMappings: Map<String, String>): String {
    terrainMappings[textureName]?.let { return it }
    throw IllegalArgumentException("Texture name $textureName not found in terrain.ini")
}