package tilemap

import com.google.gson.annotations.SerializedName
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

// ============================================================================
// Tilemap Export
// ============================================================================

data class Point(
    val x: Int,
    val y: Int
)

data class TileData(
    @SerializedName("TileValue")
    val tileValue: UShort,
    @SerializedName("TextureIndex")
    val textureIndex: Int,
    @SerializedName("Cell")
    val cell: Point,
    @SerializedName("TextureOffset")
    val textureOffset: Point
)

data class TextureData(
    @SerializedName("Index")
    val index: Int,
    @SerializedName("Size")
    val size: Int,
    @SerializedName("Name")
    val name: String,
    @SerializedName("FileName")
    val fileName: String?,
    @SerializedName("NormalMapFileName")
    val normalMapFileName: String,
)

data class UnityMapExport(
    @SerializedName("MapName")
    val mapName: String,
    @SerializedName("Dimensions")
    val dimensions: MapDimensions,
    @SerializedName("Tiles")
    val tiles: List<TileData>,
    @SerializedName("Textures")
    val textures: List<TextureData>,
)

data class MapDimensions(
    @SerializedName("Width")
    val width: Int,
    @SerializedName("Height")
    val height: Int,
    @SerializedName("TileSize")
    val tileSize: Int
)

fun exportTileMapJson(
    mapFile: MapFile,
    terrainMappings: Map<String, String>,
    outputDir: Path,
    config: MapExporterConfig,
) {
    val blendTileData = mapFile.blendTileData
    val tiles = blendTileData.tiles.rowMap()
    val textures = blendTileData.textures

    val textureNames = loadTextureNames(textures, terrainMappings)
    val textureImages = loadTextureImages(textures, config.pathToTexturesFolder, terrainMappings)

    val width = mapFile.heightMap.width.toInt()
    val height = mapFile.heightMap.height.toInt()

    println("  Tile grid: ${width}x${height}")

    val dimensions = MapDimensions(width, height, config.cellSize)
    val tileDataList = mutableListOf<TileData>()
    val textureDataList = mutableListOf<TextureData>()

    var graphics: Graphics2D? = null
    var image : BufferedImage? = null

    if (config.generatePreviews) {
        val imageWidth = (width * config.previewCellSize)
        val imageHeight = (height * config.previewCellSize)
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

            if (result != null) {
                val (textureImage, index) = result

                val cellCount = textureImage.width / config.cellSize

                val texX = (x.toInt() % cellCount) * config.cellSize
                val texY = (y.toInt() % cellCount) * config.cellSize

                val tileData = TileData(
                    tileValue = tileValue,
                    textureIndex = index,
                    cell = Point(x.toInt(), y.toInt()),
                    textureOffset = Point(texX, texY)
                )

                tileDataList.add(tileData)

                if (config.generatePreviews) {
                    val pixelX = x.toInt() * config.previewCellSize
                    val pixelY = y.toInt() * config.previewCellSize

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
        mapName = mapFile.worldInfo["mapName"]?.value.toString(),
        dimensions = dimensions,
        tiles = tileDataList,
        textures = textureDataList,
    )

    val json = gson.toJson(export)
    val jsonFile = outputDir.resolve("tilemap.json")
    jsonFile.writeText(json)

    println("  Tiles: ${tileDataList.size}")
    println("  Textures: ${textureDataList.size}")
    println("  Saved: tilemap.json")

    if (config.generatePreviews) {
        println("  Saved: tilemap.png")
    }
}