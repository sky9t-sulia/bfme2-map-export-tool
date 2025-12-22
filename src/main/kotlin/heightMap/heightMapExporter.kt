package heightMap

import config.MapExporterConfig
import de.darkatra.bfme2.map.MapFile
import de.darkatra.bfme2.map.heightmap.HeightMap
import gson
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

// ============================================================================
// Heightmap Export
// ============================================================================

data class HeightMapConfiguration(
    val heightMap: HeightMap,
    val originalWidth: Int,
    val originalHeight: Int,
    val targetSize: Int,
    val minHeight: UShort,
    val maxHeight: UShort,
    val outputDir: Path,
)

fun exportHeightMap(mapFile: MapFile, outputDir: Path, config: MapExporterConfig) {
    val heightMap: HeightMap = mapFile.heightMap

    val width = heightMap.width.toInt()
    val height = heightMap.height.toInt()
    val elevations = heightMap.elevations

    // Find min/max for normalization
    var minHeight: UShort = UShort.MAX_VALUE
    var maxHeight: UShort = UShort.MIN_VALUE

    elevations.rowMap().forEach { row ->
        row.value.forEach { cell ->
            val h: UShort = cell.value
            if (h < minHeight) minHeight = h
            if (h > maxHeight) maxHeight = h
        }
    }

    println("  Dimensions: ${width}x${height}")
    println("  Height range: ${minHeight.toInt()} to ${maxHeight.toInt()}")

    // Unity requires power-of-2 + 1 resolutions
    val unityResolution = findNearestUnityResolution(width)
    val needsPadding = width != unityResolution

    if (needsPadding) {
        println("  Unity requires ${unityResolution}x${unityResolution} resolution")
        println("  Will pad from ${width}x${height} to ${unityResolution}x${unityResolution}")
    }

    val heightMapConfig = HeightMapConfiguration(
        heightMap,
        width,
        height,
        unityResolution,
        minHeight,
        maxHeight,
        outputDir
    )

    exportHeightmapRaw16Padded(heightMapConfig)

    // Export metadata JSON
    val metadata = mapOf(
        "width" to (unityResolution - 1),
        "length" to (unityResolution - 1),
        "format" to "RAW 16-bit, Little Endian",
        "scale" to config.heightMapScale
    )

    outputDir.resolve("heightmap_info.json").writeText(gson.toJson(metadata))
}

private fun findNearestUnityResolution(size: Int): Int {
    val validSizes = listOf(33, 65, 129, 257, 513, 1025, 2049, 4097)
    return validSizes.firstOrNull { it >= size } ?: validSizes.last()
}

private fun exportHeightmapRaw16Padded(conf: HeightMapConfiguration) {
    val buffer = ByteBuffer.allocate(conf.targetSize * conf.targetSize * 2)
    buffer.order(ByteOrder.LITTLE_ENDIAN)

    val image = BufferedImage(
        conf.targetSize, conf.targetSize, BufferedImage.TYPE_USHORT_GRAY)

    val heightRange = (conf.maxHeight - conf.minHeight).toInt()

    // Calculate padding to center the original heightmap
    val padX = (conf.targetSize - conf.originalWidth) / 2
    val padY = (conf.targetSize - conf.originalHeight) / 2

    // Create a map of existing data for quick lookup
    val heightData = mutableMapOf<Pair<UInt, UInt>, UShort>()
    conf.heightMap.elevations.rowMap().forEach { (x, row) ->
        row.forEach { (y, h) ->
            heightData[x to y] = h
        }
    }

    // Write padded data with centered content, flipped vertically for Unity
    for (x in 0 until conf.targetSize) {
        for (y in 0 until conf.targetSize) {
            val sourceX = x - padX
            val sourceY = (conf.originalHeight - 1) - (y - padY)

            val h = if (sourceX in 0..< conf.originalWidth && sourceY >= 0 && sourceY < conf.originalHeight) {
                heightData[sourceX.toUInt() to sourceY.toUInt()] ?: conf.minHeight
            } else {
                conf.minHeight
            }

            val normalized: UShort = if (heightRange > 0) {
                (((h.toInt() - conf.minHeight.toInt()) * 65535) / heightRange).toUShort()
            } else {
                0.toUShort()
            }

            image.raster.setDataElements(x, y, shortArrayOf(normalized.toShort()))
            buffer.putShort(normalized.toShort())
        }
    }

    val rawOutputPath = conf.outputDir.resolve("heightmap_normalized_${conf.targetSize}.raw")
    rawOutputPath.writeBytes(buffer.array())

    val pngOutputPath = conf.outputDir.resolve("heightmap_normalized_${conf.targetSize}.png")
    pngOutputPath.outputStream().use { output ->
        ImageIO.write(image, "PNG", output)
    }

    println("  Saved RAW: heightmap_normalized_${conf.targetSize}.raw")
    println("  Saved PNG: heightmap_normalized_${conf.targetSize}.png")
}