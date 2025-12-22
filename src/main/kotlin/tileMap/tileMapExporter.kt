package tileMap

import gson
import config.MapExporterConfig
import de.darkatra.bfme2.map.MapFile
import getTextureForTileValue
import loadTextureImages
import loadTextureNames
import normalMapName
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.writeText
import kotlin.text.toInt

// ============================================================================
// Tilemap Export
// ============================================================================

data class TileData(
    val tileValue: Int,
    val textureIndex: Int,
    val cell: List<Int>,
    val textureOffset: List<Int>
)

data class TextureData(
    val index: Int,
    val size: Int,
    val name: String,
    val fileName: String?,
    val normalMapFileName: String,
)

data class UnityMapExport(
    val mapName: String,
    val dimensions: MapDimensions,
    val tiles: List<TileData>,
    val textures: List<TextureData>,
)

data class MapDimensions(
    val width: Int,
    val length: Int,
    val tileSize: Int
)

fun exportTileMapJson(
    mapFile: MapFile,
    terrainMappings: Map<String, String>,
    outputDir: Path,
    mapName: String,
    config: MapExporterConfig,
) {
    val blendTileData = mapFile.blendTileData
    val tiles = blendTileData.tiles.rowMap()
    val textures = blendTileData.textures

    val textureNames = loadTextureNames(textures, terrainMappings)
    val textureImages = loadTextureImages(textures, config.pathToTexturesFolder, terrainMappings)

    val width = mapFile.heightMap.width.toInt()
    val length = mapFile.heightMap.height.toInt()

    println("  Tile grid: ${width}x${length}")

    val dimensions = MapDimensions(width, length, config.cellSize)
    val tileDataList = mutableListOf<TileData>()
    val textureDataList = mutableListOf<TextureData>()

    var graphics: Graphics2D? = null
    var image : BufferedImage? = null

    if (config.generatePreviews) {
        val imageWidth = (width * config.previewCellSize)
        val imageHeight = (length * config.previewCellSize)
        image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB)
        graphics = image.createGraphics()
    }

    textures.forEachIndexed { index, texture ->
        textureDataList.add(
            TextureData(
                index = index,
                size = textureImages[texture.name]?.width ?: 0,
                name = texture.name,
                fileName = textureNames[texture.name]?.lowercase(),
                normalMapFileName = normalMapName(textureNames[texture.name]).lowercase()
            )
        )
    }

    tiles.forEach { (x, row) ->
        row.forEach { (y, tileValue) ->
            val result = getTextureForTileValue(tileValue.toUInt(), textures, textureImages)
            val flippedY = (length - 1).toUInt() - y

            if (result != null) {
                val (textureImage, index) = result

                val cellCount = textureImage.width / config.cellSize

                val texX = (x.toInt() % cellCount) * config.cellSize
                val texY = (flippedY.toInt() % cellCount) * config.cellSize

                val tileData = TileData(
                    tileValue = tileValue.toInt(),
                    textureIndex = index,
                    cell = listOf(x.toInt(), flippedY.toInt()),
                    textureOffset = listOf(texX, texY)
                )

                tileDataList.add(tileData)

                if (config.generatePreviews) {
                    val pixelX = x.toInt() * config.previewCellSize
                    val pixelY = flippedY.toInt() * config.previewCellSize

                    try {
                        val cellImage = textureImage.getSubimage(texX, texY, config.cellSize, config.cellSize)
                        graphics?.drawImage(
                            cellImage, pixelX, pixelY,
                            config.previewCellSize, config.previewCellSize, null
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    if (config.generatePreviews) {
        graphics?.dispose()
        val outputFile = outputDir.resolve("tilemap.png")
        ImageIO.write(image, "PNG", outputFile.toFile())
    }

    val export = UnityMapExport(
        mapName = mapName,
        dimensions = dimensions,
        tiles = tileDataList,
        textures = textureDataList,
    )

    val json = gson.toJson(export)
    val jsonFile = outputDir.resolve("${mapName}_unity.json")
    jsonFile.writeText(json)

    println("  Tiles: ${tileDataList.size}")
    println("  Textures: ${textureDataList.size}")
    println("  Saved: ${mapName}_unity.json")

    if (config.generatePreviews) {
        println("  Saved: tilemap.png")
    }
}