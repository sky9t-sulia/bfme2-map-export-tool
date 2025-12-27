package com.sulia.sky9t.tilemap

import com.google.gson.annotations.SerializedName
import com.sulia.sky9t.config.MapExporterConfig
import com.sulia.sky9t.config.TextureMapping
import de.darkatra.bfme2.map.MapFile
import de.darkatra.bfme2.map.blendtile.BlendDirection
import de.darkatra.bfme2.map.blendtile.BlendFlags
import com.sulia.sky9t.GSON
import com.sulia.sky9t.texture.TextureManager
import com.sulia.sky9t.texture.TextureUtils
import java.awt.Graphics2D
import java.awt.Point
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.writeText

// ============================================================================
// Export Data Models
// ============================================================================

data class TileData(
    @SerializedName("Index") val index: UInt = 0u,
    @SerializedName("TileValue") val tileValue: UShort,
    @SerializedName("TextureIndex") val textureIndex: Int,
    @SerializedName("Cell") val cell: Point,
    @SerializedName("TextureOffset") val textureOffset: Point,
    @SerializedName("BlendValue") val blendValue: UInt
)

data class TextureData(
    @SerializedName("Index") val index: Int,
    @SerializedName("Size") val size: Int,
    @SerializedName("Name") val name: String,
    @SerializedName("FileName") val fileName: String?,
    @SerializedName("NormalMapFileName") val normalMapFileName: String
)

data class BlendData(
    @SerializedName("Index") val index: Int = 0,
    @SerializedName("SecondaryTextureTile") val secondaryTextureTile: UInt,
    @SerializedName("Flags") val flags: BlendFlags,
    @SerializedName("BlendingDirection") val blendDirection: BlendDirection
)

data class MapDimensions(
    @SerializedName("Width") val width: UInt,
    @SerializedName("Height") val height: UInt,
    @SerializedName("TileSize") val tileSize: UInt
)

data class UnityMapExport(
    @SerializedName("MapName") val mapName: String,
    @SerializedName("Dimensions") val dimensions: MapDimensions,
    @SerializedName("Tiles") val tiles: List<TileData>,
    @SerializedName("Textures") val textures: List<TextureData>,
    @SerializedName("BlendDescriptions") val blendDescriptions: List<BlendData>
)

// ============================================================================
// TileMap Exporter
// ============================================================================

class TileMapExporter(
    private val mapFile: MapFile,
    private val texturesList: List<TextureMapping>,
    private val config: MapExporterConfig
) {
    private val blendTileData = mapFile.blendTileData
    private val tiles = blendTileData.tiles.rowMap()
    private val textures = blendTileData.textures
    private val width = mapFile.heightMap.width
    private val height = mapFile.heightMap.height

    private val textureManager by lazy {
        TextureManager(textures, config.pathToTexturesFolder, texturesList)
    }

    private val textureNames by lazy {
        textureManager.getTextureNames()
    }

    private val textureImages by lazy {
        textureManager.getTextureImages()
    }

    fun export(outputDir: Path) {
        val dimensions = MapDimensions(width, height, config.cellSize)
        val tileDataList = buildTileDataList()
        val textureDataList = buildTextureDataList()
        val blendDescriptions = buildBlendDescriptions()

        if (config.generatePreviews) {
            generatePreviewImage(outputDir, tileDataList)
        }

        exportJson(
            outputDir,
            UnityMapExport(
                mapName = mapFile.worldInfo["mapName"]?.value.toString(),
                dimensions = dimensions,
                tiles = tileDataList,
                textures = textureDataList,
                blendDescriptions = blendDescriptions
            )
        )

        printExportSummary(tileDataList.size, textureDataList.size)
    }

    private fun buildTileDataList(): List<TileData> {
        return tiles.flatMap { (x, row) ->
            row.mapNotNull { (y, tileValue) ->
                createTileData(x, y, tileValue)
            }
        }
    }

    private fun createTileData(x: UInt, y: UInt, tileValue: UShort): TileData? {
        val yFlipped = (height - 1u) - y
        val textureInfo = textureManager.getTextureForTileValue(tileValue) ?: return null

        val cellCount = textureInfo.image.width.toUInt() / config.cellSize
        val textureOffset = Point(
            ((x % cellCount) * config.cellSize).toInt(),
            ((yFlipped % cellCount) * config.cellSize).toInt()
        )

        val blendValue = blendTileData.blends.rowMap()[x]?.get(yFlipped) ?: 0u
        val tileIndex = x * height + yFlipped

        return TileData(
            index = tileIndex,
            tileValue = tileValue,
            textureIndex = textureInfo.index,
            cell = Point(x.toInt(), yFlipped.toInt()),
            textureOffset = textureOffset,
            blendValue = blendValue
        )
    }

    private fun buildTextureDataList(): List<TextureData> {
        return textures.mapIndexed { index, texture ->
            TextureData(
                index = index,
                size = textureImages[texture.name]?.width ?: 0,
                name = texture.name,
                fileName = textureNames[texture.name]?.lowercase(),
                normalMapFileName = TextureUtils.normalMapName(textureNames[texture.name]).lowercase()
            )
        }
    }

    private fun buildBlendDescriptions(): List<BlendData> {
        return blendTileData.blendDescriptions.mapIndexed { index, description ->
            BlendData(
                index = index,
                secondaryTextureTile = description.secondaryTextureTile,
                flags = description.flags,
                blendDirection = description.blendDirection
            )
        }
    }

    private fun generatePreviewImage(outputDir: Path, tileDataList: List<TileData>) {
        val imageWidth = (width * config.previewCellSize).toInt()
        val imageHeight = (height * config.previewCellSize).toInt()
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()

        try {
            drawBaseTiles(graphics, tileDataList)

            if (config.blendTiles) {
                applyBlending(graphics)
            }

            savePreviewImage(outputDir, image)
        } finally {
            graphics.dispose()
        }
    }

    private fun drawBaseTiles(graphics: Graphics2D, tileDataList: List<TileData>) {
        tileDataList.forEach { tile ->
            drawTile(graphics, tile)
        }
    }

    private fun drawTile(graphics: Graphics2D, tile: TileData) {
        val texture = textureImages[textures[tile.textureIndex].name] ?: return

        try {
            val cellImage = texture.getSubimage(
                tile.textureOffset.x,
                tile.textureOffset.y,
                config.cellSize.toInt(),
                config.cellSize.toInt()
            )

            val pixelX = (tile.cell.x.toUInt() * config.previewCellSize).toInt()
            val pixelY = (tile.cell.y.toUInt() * config.previewCellSize).toInt()

            graphics.drawImage(
                cellImage,
                pixelX,
                pixelY,
                config.previewCellSize.toInt(),
                config.previewCellSize.toInt(),
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyBlending(graphics: Graphics2D) {
        applyTileBlends(
            blendTileData,
            height,
            tiles,
            textureManager,
            config.previewCellSize,
            graphics
        )
    }

    private fun savePreviewImage(outputDir: Path, image: BufferedImage) {
        val outputFile = outputDir.resolve("tilemap.png")
        ImageIO.write(image, "PNG", outputFile.toFile())
    }

    private fun exportJson(outputDir: Path, data: UnityMapExport) {
        val json = GSON.toJson(data)
        val jsonFile = outputDir.resolve("tilemap.json")
        jsonFile.writeText(json)
    }

    private fun printExportSummary(tileCount: Int, textureCount: Int) {
        println("  Tiles: $tileCount")
        println("  Textures: $textureCount")
        println("  Saved: tilemap.json")

        if (config.generatePreviews) {
            println("  Saved: tilemap.png")
        }
    }
}