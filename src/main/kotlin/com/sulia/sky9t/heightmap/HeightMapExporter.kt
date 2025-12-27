package com.sulia.sky9t.heightmap

import com.google.gson.annotations.SerializedName
import de.darkatra.bfme2.map.MapFile
import de.darkatra.bfme2.map.heightmap.HeightMap
import com.sulia.sky9t.GSON
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

// ============================================================================
// Domain Models
// ============================================================================

data class HeightMapMetadata(
    @SerializedName("MapName") val mapName: String,
    @SerializedName("Width") val width: Int,
    @SerializedName("Height") val height: Int,
    @SerializedName("Border") val border: Int,
    @SerializedName("Format") val format: String = "RAW 16-bit, Little Endian"
)

data class HeightMapData(
    val buffer: ByteBuffer,
    val image: BufferedImage
)

// ============================================================================
// HeightMap Exporter
// ============================================================================

class HeightMapExporter(private val mapFile: MapFile) {
    private val heightMap: HeightMap = mapFile.heightMap
    private val width = heightMap.width.toInt()
    private val height = heightMap.height.toInt()

    fun export(outputDir: Path) {
        printDimensions()

        val heightMapData = buildHeightMapData()

        saveRawFile(outputDir, heightMapData.buffer)
        savePngFile(outputDir, heightMapData.image)
        saveMetadata(outputDir)

        printExportSummary()
    }

    private fun buildHeightMapData(): HeightMapData {
        val buffer = createBuffer()
        val image = createImage()

        heightMap.elevations.rowMap().forEach { (x, columnMap) ->
            columnMap.forEach { (y, elevation) ->
                writeElevation(buffer, image, x.toInt(), y.toInt(), elevation)
            }
        }

        return HeightMapData(buffer, image)
    }

    private fun createBuffer(): ByteBuffer {
        return ByteBuffer
            .allocate(width * height * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun createImage(): BufferedImage {
        return BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY)
    }

    private fun writeElevation(
        buffer: ByteBuffer,
        image: BufferedImage,
        x: Int,
        y: Int,
        elevation: UShort
    ) {
        val yFlipped = flipY(y)

        buffer.putChar(elevation.toInt().toChar())
        image.raster.setDataElements(x, yFlipped, shortArrayOf(elevation.toShort()))
    }

    private fun flipY(y: Int): Int {
        return (heightMap.height - 1u - y.toUInt()).toInt()
    }

    private fun saveRawFile(outputDir: Path, buffer: ByteBuffer) {
        val rawOutputPath = outputDir.resolve("heightmap.raw")
        rawOutputPath.writeBytes(buffer.array())
    }

    private fun savePngFile(outputDir: Path, image: BufferedImage) {
        val pngOutputPath = outputDir.resolve("heightmap.png")
        pngOutputPath.outputStream().use { output ->
            ImageIO.write(image, "PNG", output)
        }
    }

    private fun saveMetadata(outputDir: Path) {
        val metadata = HeightMapMetadata(
            mapName = mapFile.worldInfo["mapName"]?.value.toString(),
            width = width,
            height = height,
            border = heightMap.borderWidth.toInt()
        )

        val metadataPath = outputDir.resolve("heightmap.json")
        metadataPath.writeText(GSON.toJson(metadata))
    }

    private fun printDimensions() {
        println("  Dimensions: ${width}x${height}")
    }

    private fun printExportSummary() {
        println("  Saved RAW: heightmap.raw")
        println("  Saved PNG: heightmap.png")
    }
}