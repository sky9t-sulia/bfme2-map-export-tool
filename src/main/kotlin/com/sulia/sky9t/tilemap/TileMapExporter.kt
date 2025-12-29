package com.sulia.sky9t.tilemap

import com.google.gson.annotations.SerializedName
import com.sulia.sky9t.GSON
import com.sulia.sky9t.config.MapExporterConfig
import com.sulia.sky9t.extension.height
import com.sulia.sky9t.extension.width
import com.sulia.sky9t.texture.TextureManager
import de.darkatra.bfme2.map.MapFile
import de.darkatra.bfme2.map.blendtile.BlendDirection
import de.darkatra.bfme2.map.blendtile.BlendFlags
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.writeText

data class MapDimensions(
    @SerializedName("Width") val width: UInt,
    @SerializedName("Height") val height: UInt,
    @SerializedName("TileSize") val tileSize: UInt
)

data class TileDataExport(
    @SerializedName("GridX") val gridX: UInt,
    @SerializedName("GridY") val gridY: UInt,
    @SerializedName("X1") val x1: Int,
    @SerializedName("Y1") val y1: Int,
    @SerializedName("X2") val x2: UInt,
    @SerializedName("Y2") val y2: UInt,
    @SerializedName("TextureIndex") val textureIndex: Int,
    @SerializedName("TextureName") val textureName: String,
    @SerializedName("TextureSize") val textureSize: UInt,
    @SerializedName("TileValue") val tileValue: UShort
)

data class BlendDescriptionExport(
    @SerializedName("Direction") val direction: BlendDirection,
    @SerializedName("Flags") val flags: BlendFlags,
    @SerializedName("SecondaryTextureTile") val secondaryTextureTile: UInt
)

data class TextureDataExport(
    @SerializedName("Index") val index: Int,
    @SerializedName("Name") val name: String,
    @SerializedName("FileName") val fileName: String,
    @SerializedName("Width") val width: UInt,
    @SerializedName("CellStart") val cellStart: UInt,
    @SerializedName("CellCount") val cellCount: UInt,
    @SerializedName("CellSize") val cellSize: UInt,
)

data class UnityMapExport(
    @SerializedName("MapName") val mapName: String,
    @SerializedName("Dimensions") val dimensions: MapDimensions,
    @SerializedName("Tiles") val tiles: List<TileDataExport>,
    @SerializedName("Textures") val textures: List<TextureDataExport>,
    @SerializedName("TextureCellCount") val textureCellCount: UInt,
    @SerializedName("BlendDescriptions") val blendDescriptions: List<BlendDescriptionExport>
)

class TileMapExporter(val mapFile: MapFile, val textureManager: TextureManager, val config: MapExporterConfig) {
    val tilesExportList: MutableList<TileDataExport> = mutableListOf()
    val blendDescriptionsExportList: MutableList<BlendDescriptionExport> = mutableListOf()
    val textureExportList: MutableList<TextureDataExport> = mutableListOf()
    val dimensions = MapDimensions(mapFile.width(), mapFile.height(), config.tileSize)

    fun export(outputDir: Path) {
        if (config.generatePreviews) {
            generatePreviewImage(outputDir)
        }

        mapFile.blendTileData.blendDescriptions.forEach {
            blendDescriptionsExportList.add(
                BlendDescriptionExport(
                    direction = it.blendDirection,
                    flags = it.flags,
                    secondaryTextureTile = it.secondaryTextureTile
                )
            )
        }



        exportJson(
            outputDir,
            UnityMapExport(
                mapName = mapFile.worldInfo["mapName"]?.value.toString(),
                dimensions = dimensions,
                tiles = tilesExportList,
                textures = textureExportList.distinctBy { it.index }.sortedBy { it.index },
                textureCellCount = mapFile.blendTileData.textureCellCount,
                blendDescriptions = blendDescriptionsExportList
            )
        )

        printExportSummary(mapFile.blendTileData.tiles.rowMap().size, mapFile.blendTileData.textures.size)
    }

    fun generatePreviewImage(outputDir: Path) {
        val imageWidth = (mapFile.width() * config.previewTileSize).toInt()
        val imageHeight = (mapFile.height() * config.previewTileSize).toInt()
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()

        try {
            drawBaseTiles(graphics)

            if (config.blendTiles) {
                applyTileBlends(
                    mapFile,
                    textureManager,
                    config,
                    graphics,
                    image
                )
            }

            savePreviewImage(outputDir, image)
        } finally {
            graphics.dispose()
        }
    }

    fun drawBaseTiles(graphics: Graphics2D) {
        mapFile.blendTileData.tiles.rowMap().forEach { (x, row) ->
            row.forEach { (y, tileValue) ->
                val yf = mapFile.height() - 1u - y
                drawTile(graphics, x, yf, tileValue)
            }
        }
    }

    fun drawTile(graphics: Graphics2D, x: UInt, y: UInt, tileValue: UShort) {
        val tileTextureInfo = textureManager.getTileTextureFromTileValue(tileValue, x, y)

        if (tileTextureInfo != null) {
            val (texture, point) = tileTextureInfo
            val textureImage = textureManager.getTextureImage(texture)
            val previewTileSize = config.previewTileSize.toInt()

            val cellImage = textureImage.getSubimage(
                point.x,
                point.y,
                config.tileSize.toInt(),
                config.tileSize.toInt()
            )

            tilesExportList.add(TileDataExport(
                gridX = x,
                gridY = y,
                x1 = point.x,
                y1 = point.y,
                x2 = config.tileSize,
                y2 = config.tileSize,
                textureIndex = mapFile.blendTileData.textures.indexOf(texture),
                textureName = texture.name,
                textureSize = textureImage.width.toUInt(),
                tileValue = tileValue
            ))

            textureExportList.add(TextureDataExport(
                index = mapFile.blendTileData.textures.indexOf(texture),
                name = texture.name,
                fileName = textureManager.getTextureFileName(texture),
                width = textureImage.width.toUInt(),
                cellStart = texture.cellStart,
                cellCount = texture.cellCount,
                cellSize = texture.cellSize
            ))

            graphics.drawImage(
                cellImage,
                x.toInt() * previewTileSize,
                y.toInt() * previewTileSize,
                previewTileSize,
                previewTileSize,
                null
            )
        }
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