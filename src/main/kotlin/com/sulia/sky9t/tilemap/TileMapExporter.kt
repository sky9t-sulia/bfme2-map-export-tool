package com.sulia.sky9t.tilemap

import com.google.gson.annotations.SerializedName
import com.sulia.sky9t.GSON
import com.sulia.sky9t.config.MapExporterConfig
import com.sulia.sky9t.extension.height
import com.sulia.sky9t.extension.width
import com.sulia.sky9t.texture.TextureManager
import de.darkatra.bfme2.map.MapFile
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.writeText
import kotlin.math.ceil

data class TileMapBlock(
    @SerializedName("Index")
    val index: Int,
    @SerializedName("X")
    val blockX: Int,
    @SerializedName("Y")
    val blockY: Int,
    @SerializedName("RelativeFileNamePath")
    val fileName: String,
)

data class BlockInfo(
    @SerializedName("WorkDirectory")
    val workDirectory: String,
    @SerializedName("TotalBlocks")
    var totalBlocks: Int,
    @SerializedName("SizeInPixels")
    val blockSizeInPixels: Int,
    @SerializedName("SizeInTiles")
    val blockSizeInTiles: UInt,
    @SerializedName("Blocks")
    val blocks: List<TileMapBlock> = emptyList(),
)

class TileMapExporter(val mapFile: MapFile, val textureManager: TextureManager, val config: MapExporterConfig) {
    var blockInfo: BlockInfo ? = null

    fun export(outputDir: Path) {
        if (!config.generatePreview && !config.splitImageByBlocks) {
            println("  Skipping tile map export (no output options selected).")
            return
        }

        blockInfo = if (config.splitImageByBlocks) {
            BlockInfo(
                workDirectory = outputDir.toAbsolutePath().toString(),
                totalBlocks = 0,
                blockSizeInPixels = (config.blockSize * config.previewTileSize).toInt(),
                blockSizeInTiles = config.blockSize,
                blocks = mutableListOf(),
            )
        } else {
            null
        }

        generateImage(outputDir)

        printExportSummary(
            tileCount = mapFile.blendTileData.tiles.rowMap().size * mapFile.blendTileData.tiles.rowMap().values.first().size,
            textureCount = mapFile.blendTileData.textures.size
        )
    }

    fun generateImage(outputDir: Path) {
        val imageWidth = (mapFile.width() * config.previewTileSize).toInt()
        val imageHeight = (mapFile.height() * config.previewTileSize).toInt()
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()

        try {
            println("  Drawing base tiles...")
            drawBaseTiles(graphics)

            if (config.blendTiles) {
                println("  Applying tile blends...")
                applyTileBlends(
                    mapFile,
                    textureManager,
                    config,
                    graphics,
                    image
                )
            }

            if (config.splitImageByBlocks) {
                println("  Splitting image into blocks...")
                applyBlockSplits(
                    config,
                    outputDir,
                    image
                )
            }

            if (config.generatePreview) {
                println("  Generating preview image...")
                savePreviewImage(outputDir, image)
            }
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

    private fun applyBlockSplits(
        config: MapExporterConfig,
        outputDir: Path,
        image: BufferedImage
    ) {
        val blockSizeInPixels = (config.blockSize * config.previewTileSize).toInt()

        val blocksX = ceil(image.width.toDouble() / blockSizeInPixels).toInt()
        val blocksY = ceil(image.height.toDouble() / blockSizeInPixels).toInt()
        val totalBlocks = blocksX * blocksY

        val block = BufferedImage(blockSizeInPixels, blockSizeInPixels, BufferedImage.TYPE_INT_RGB)
        val blockFileName = "blocks/dir"
        val blockFilePath = outputDir.resolve(blockFileName)
        blockFilePath.parent.toFile().mkdirs()

        var blockCounter = 0
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                blockCounter++

                val g = block.createGraphics()
                try {
                    val srcX = bx * blockSizeInPixels
                    // Flip Y coordinate
                    val srcY = image.height - (by + 1) * blockSizeInPixels
                    val srcWidth = minOf(blockSizeInPixels, image.width - srcX)
                    val srcHeight = minOf(blockSizeInPixels, image.height - srcY)

                    g.drawImage(
                        image,
                        0,
                        0,
                        srcWidth,
                        srcHeight,
                        srcX,
                        srcY,
                        srcX + srcWidth,
                        srcY + srcHeight,
                        null
                    )
                } finally {
                    g.dispose()
                }

                val blockFileName = "blocks/tilemap_block_${bx}_$by.png"
                val blockFilePath = outputDir.resolve(blockFileName)

                ImageIO.write(block, "PNG", blockFilePath.toFile())
                println("  Saved: $blockFileName [$blockCounter/$totalBlocks]")

                (blockInfo?.blocks as MutableList).add(
                    TileMapBlock(
                        index = blockCounter - 1,
                        blockX = bx,
                        blockY = by,
                        fileName = blockFileName,
                    )
                )
            }
        }

        blockInfo?.totalBlocks = totalBlocks

        exportBlockInfo(outputDir)
    }

    private fun exportBlockInfo(outputDir: Path) {
        val blockInfoPath = outputDir.resolve("blocks.json")
        val json = GSON.toJson(blockInfo)
        blockInfoPath.writeText(json)
        println("  Saved: blocks.json")
    }

    private fun savePreviewImage(outputDir: Path, image: BufferedImage) {
        val outputFile = outputDir.resolve("tilemap.png")
        ImageIO.write(image, "PNG", outputFile.toFile())
    }

    private fun printExportSummary(tileCount: Int, textureCount: Int) {
        println("  Tiles: $tileCount")
        println("  Textures: $textureCount")

        if (config.generatePreview) {
            println("  Saved: tilemap.png")
        }
    }
}