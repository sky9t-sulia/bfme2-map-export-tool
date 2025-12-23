package heightmap

import gson
import de.darkatra.bfme2.map.MapFile
import de.darkatra.bfme2.map.heightmap.HeightMap
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

fun exportHeightMap(mapFile: MapFile, outputDir: Path) {
    val heightMap: HeightMap = mapFile.heightMap

    println("  Dimensions: ${heightMap.width}x${heightMap.width}")

    exportHeightmapRaw16(heightMap, outputDir)

    // Export metadata JSON
    val metadata = mapOf(
        "MapName" to mapFile.worldInfo["mapName"]?.value.toString(),
        "Width" to heightMap.width.toInt(),
        "Height" to heightMap.height.toInt(),
        "Border" to heightMap.borderWidth.toInt(),
        "Format" to "RAW 16-bit, Little Endian",
    )

    outputDir.resolve("heightmap.json").writeText(gson.toJson(metadata))
}

private fun exportHeightmapRaw16(
    heightMap: HeightMap,
    outputDir: Path
) {
    val buffer = ByteBuffer
        .allocate(heightMap.width.toInt() * heightMap.height.toInt() * 2)
        .order(ByteOrder.LITTLE_ENDIAN)

    val image = BufferedImage(
        heightMap.width.toInt(),
        heightMap.height.toInt(),
        BufferedImage.TYPE_USHORT_GRAY
    )

    heightMap.elevations.rowMap().forEach { (index, map) ->
        map.entries.forEach { entry ->
            val x = index.toInt()
            val y = entry.key.toInt()
            val height = entry.value

            buffer.putChar(height.toInt().toChar())
            image.raster.setDataElements(
                x, y,
                shortArrayOf(height.toShort())
            )
        }
    }

    val rawOutputPath = outputDir.resolve("heightmap.raw")
    rawOutputPath.writeBytes(buffer.array())

    val pngOutputPath = outputDir.resolve("heightmap.png")
    pngOutputPath.outputStream().use { output ->
        ImageIO.write(image, "PNG", output)
    }

    println("  Saved RAW: heightmap.raw")
    println("  Saved PNG: heightmap.png")
}