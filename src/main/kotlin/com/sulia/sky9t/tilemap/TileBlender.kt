package com.sulia.sky9t.tilemap

import com.sulia.sky9t.texture.TextureManager
import de.darkatra.bfme2.map.blendtile.BlendDescription
import de.darkatra.bfme2.map.blendtile.BlendDirection
import de.darkatra.bfme2.map.blendtile.BlendFlags
import de.darkatra.bfme2.map.blendtile.BlendTileData
import java.awt.Graphics2D
import java.awt.Point
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

// ============================================================================
// Domain Models
// ============================================================================

enum class SimpleBlendDirection {
    LEFT, RIGHT, TOP, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    NONE;

    val isDiagonal: Boolean
        get() = this in setOf(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)

    val isEdge: Boolean
        get() = this in setOf(LEFT, RIGHT, TOP, BOTTOM)
}

enum class BlendMaskType {
    EDGE,
    CORNER,
    DIAGONAL_ONLY,
    NONE
}

data class TileCoordinate(val x: Int, val y: Int) {
    fun offset(dx: Int, dy: Int) = TileCoordinate(x + dx, y + dy)
}

data class TileInfo(
    val tileValue: UInt,
    val blendDirection: SimpleBlendDirection,
    val textureName: String,
    val textureImage: BufferedImage
)

data class BlendContext(
    val shouldBlend: Boolean,
    val direction: SimpleBlendDirection,
    val maskType: BlendMaskType,
    val targetTexture: String
) {
    companion object {
        val NONE = BlendContext(
            shouldBlend = false,
            direction = SimpleBlendDirection.NONE,
            maskType = BlendMaskType.NONE,
            targetTexture = ""
        )
    }
}

data class NeighborAnalysis(
    val diagonal: TileCoordinate,
    val adjacentSide1: TileCoordinate,
    val adjacentSide2: TileCoordinate
)

// ============================================================================
// Tile Blending Engine
// ============================================================================

class TileBlendingEngine(
    private val height: UInt,
    private val tiles: Map<UInt, Map<UInt, UShort>>,
    private val textureManager: TextureManager,
    private val cellSize: UInt
) {
    private val maskCache: Map<String, BufferedImage> by lazy {
        BlendMaskLoader.loadMasks()
    }

    private val textureImages: Map<String, BufferedImage> by lazy {
        textureManager.getTextureImages()
    }

    fun processBlend(
        blendDescription: BlendDescription,
        coordinate: TileCoordinate,
        graphics: Graphics2D
    ) {
        val direction = blendDescription.toSimpleDirection()
        val tile = getTileInfo(coordinate, direction) ?: return
        val blendContext = analyzeBlendContext(tile, coordinate)

        if (blendContext.shouldBlend) {
            applyBlend(blendContext, coordinate, graphics)
        }
    }

    private fun applyBlend(
        context: BlendContext,
        coordinate: TileCoordinate,
        graphics: Graphics2D
    ) {
        val targetImage = textureImages[context.targetTexture] ?: return
        val mask = getMask(context) ?: return
        val subtexture = extractSubtexture(targetImage, coordinate)
        val blendedImage = subtexture.applyAlphaMask(mask)

        val screenPosition = coordinate.toScreenPosition(height, cellSize)
        graphics.drawImage(blendedImage, screenPosition.x, screenPosition.y, null)
    }

    private fun analyzeBlendContext(tile: TileInfo, coordinate: TileCoordinate): BlendContext {
        return when {
            tile.blendDirection.isDiagonal -> analyzeDiagonalBlend(tile, coordinate)
            tile.blendDirection.isEdge -> analyzeEdgeBlend(tile, coordinate)
            else -> BlendContext.NONE
        }
    }

    private fun analyzeDiagonalBlend(tile: TileInfo, coord: TileCoordinate): BlendContext {
        val neighbors = coord.getDiagonalNeighbors(tile.blendDirection)
        val neighborTextures = listOfNotNull(
            getTileInfo(neighbors.diagonal, SimpleBlendDirection.NONE),
            getTileInfo(neighbors.adjacentSide1, SimpleBlendDirection.NONE),
            getTileInfo(neighbors.adjacentSide2, SimpleBlendDirection.NONE)
        ).filter { it.textureName != tile.textureName }

        if (neighborTextures.isEmpty()) return BlendContext.NONE

        val targetTexture = neighborTextures
            .groupingBy { it.textureName }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: return BlendContext.NONE

        val matchCount = neighborTextures.count { it.textureName == targetTexture }
        val maskType = when {
            matchCount >= 2 -> BlendMaskType.CORNER
            matchCount == 1 -> BlendMaskType.DIAGONAL_ONLY
            else -> return BlendContext.NONE
        }

        return BlendContext(
            shouldBlend = true,
            direction = tile.blendDirection,
            maskType = maskType,
            targetTexture = targetTexture
        )
    }

    private fun analyzeEdgeBlend(tile: TileInfo, coord: TileCoordinate): BlendContext {
        val neighbor = coord.getEdgeNeighbor(tile.blendDirection)
        val neighborTile = getTileInfo(neighbor, SimpleBlendDirection.NONE)

        return if (neighborTile != null && neighborTile.textureName != tile.textureName) {
            BlendContext(
                shouldBlend = true,
                direction = tile.blendDirection,
                maskType = BlendMaskType.EDGE,
                targetTexture = neighborTile.textureName
            )
        } else {
            BlendContext.NONE
        }
    }

    private fun getTileInfo(coordinate: TileCoordinate, direction: SimpleBlendDirection): TileInfo? {
        val tileValue = getTileValue(coordinate)
        if (tileValue == UInt.MAX_VALUE) return null

        val textureInfo = textureManager.getTextureForTileValue(tileValue.toUShort()) ?: return null

        return TileInfo(
            tileValue = tileValue,
            blendDirection = direction,
            textureName = textureInfo.name,
            textureImage = textureInfo.image
        )
    }

    private fun getTileValue(coordinate: TileCoordinate): UInt {
        return tiles[coordinate.x.toUInt()]
            ?.get(coordinate.y.toUInt())
            ?.toUInt()
            ?: UInt.MAX_VALUE
    }

    private fun extractSubtexture(image: BufferedImage, coordinate: TileCoordinate): BufferedImage {
        val yFlipped = (height - 1u) - coordinate.y.toUInt()
        val cellCount = image.width.toUInt() / cellSize
        val texX = (coordinate.x.toUInt() % cellCount) * cellSize
        val texY = (yFlipped % cellCount) * cellSize

        return image.getSubimage(texX.toInt(), texY.toInt(), cellSize.toInt(), cellSize.toInt())
    }

    private fun getMask(context: BlendContext): BufferedImage? {
        val maskKey = when (context.maskType) {
            BlendMaskType.EDGE -> context.direction.edgeMaskKey
            BlendMaskType.DIAGONAL_ONLY -> context.direction.diagonalMaskKey
            BlendMaskType.CORNER -> context.direction.cornerMaskKey
            BlendMaskType.NONE -> null
        }
        return maskKey?.let { maskCache[it] }
    }
}

// ============================================================================
// Extension Functions
// ============================================================================

private fun TileCoordinate.getDiagonalNeighbors(direction: SimpleBlendDirection): NeighborAnalysis {
    return when (direction) {
        SimpleBlendDirection.TOP_LEFT -> NeighborAnalysis(
            diagonal = offset(-1, 1),
            adjacentSide1 = offset(0, 1),
            adjacentSide2 = offset(-1, 0)
        )
        SimpleBlendDirection.TOP_RIGHT -> NeighborAnalysis(
            diagonal = offset(1, 1),
            adjacentSide1 = offset(0, 1),
            adjacentSide2 = offset(1, 0)
        )
        SimpleBlendDirection.BOTTOM_LEFT -> NeighborAnalysis(
            diagonal = offset(-1, -1),
            adjacentSide1 = offset(0, -1),
            adjacentSide2 = offset(-1, 0)
        )
        SimpleBlendDirection.BOTTOM_RIGHT -> NeighborAnalysis(
            diagonal = offset(1, -1),
            adjacentSide1 = offset(0, -1),
            adjacentSide2 = offset(1, 0)
        )
        else -> error("Invalid diagonal direction: $direction")
    }
}

private fun TileCoordinate.getEdgeNeighbor(direction: SimpleBlendDirection): TileCoordinate {
    return when (direction) {
        SimpleBlendDirection.LEFT -> offset(-1, 0)
        SimpleBlendDirection.RIGHT -> offset(1, 0)
        SimpleBlendDirection.TOP -> offset(0, 1)
        SimpleBlendDirection.BOTTOM -> offset(0, -1)
        else -> error("Invalid edge direction: $direction")
    }
}

private fun TileCoordinate.toScreenPosition(mapHeight: UInt, cellSize: UInt): Point {
    val yFlipped = (mapHeight - 1u) - y.toUInt()
    return Point((x.toUInt() * cellSize).toInt(), (yFlipped * cellSize).toInt())
}

private val SimpleBlendDirection.edgeMaskKey: String?
    get() = when (this) {
        SimpleBlendDirection.LEFT -> "left"
        SimpleBlendDirection.RIGHT -> "right"
        SimpleBlendDirection.TOP -> "top"
        SimpleBlendDirection.BOTTOM -> "bottom"
        else -> null
    }

private val SimpleBlendDirection.diagonalMaskKey: String?
    get() = when (this) {
        SimpleBlendDirection.TOP_LEFT -> "top_left"
        SimpleBlendDirection.TOP_RIGHT -> "top_right"
        SimpleBlendDirection.BOTTOM_LEFT -> "bottom_left"
        SimpleBlendDirection.BOTTOM_RIGHT -> "bottom_right"
        else -> null
    }

private val SimpleBlendDirection.cornerMaskKey: String?
    get() = when (this) {
        SimpleBlendDirection.TOP_LEFT -> "top_left_corner"
        SimpleBlendDirection.TOP_RIGHT -> "top_right_corner"
        SimpleBlendDirection.BOTTOM_LEFT -> "bottom_left_corner"
        SimpleBlendDirection.BOTTOM_RIGHT -> "bottom_right_corner"
        else -> null
    }

private fun BlendDescription.toSimpleDirection(): SimpleBlendDirection {
    return when (blendDirection) {
        BlendDirection.BLEND_TOWARDS_RIGHT ->
            if (flags == BlendFlags.FLIPPED) SimpleBlendDirection.LEFT
            else SimpleBlendDirection.RIGHT

        BlendDirection.BLEND_TOWARDS_TOP ->
            if (flags == BlendFlags.FLIPPED) SimpleBlendDirection.BOTTOM
            else SimpleBlendDirection.TOP

        BlendDirection.BLEND_TOWARDS_TOP_RIGHT ->
            if (flags == BlendFlags.FLIPPED) SimpleBlendDirection.BOTTOM_RIGHT
            else SimpleBlendDirection.TOP_RIGHT

        BlendDirection.BLEND_TOWARDS_TOP_LEFT ->
            if (flags == BlendFlags.FLIPPED) SimpleBlendDirection.BOTTOM_LEFT
            else SimpleBlendDirection.TOP_LEFT
    }
}

private fun BufferedImage.applyAlphaMask(mask: BufferedImage): BufferedImage {
    val result = toARGB()

    for (y in 0 until result.height) {
        for (x in 0 until result.width) {
            val baseColor = result.getRGB(x, y)
            val maskAlpha = (mask.getRGB(x, y) ushr 24) and 0xFF

            val red = (baseColor ushr 16) and 0xFF
            val green = (baseColor ushr 8) and 0xFF
            val blue = baseColor and 0xFF

            val blendedColor = (maskAlpha shl 24) or (red shl 16) or (green shl 8) or blue
            result.setRGB(x, y, blendedColor)
        }
    }

    return result
}

private fun BufferedImage.toARGB(): BufferedImage {
    if (type == BufferedImage.TYPE_INT_ARGB) return this

    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
        val g = createGraphics()
        try {
            g.drawImage(this@toARGB, 0, 0, null)
        } finally {
            g.dispose()
        }
    }
}

// ============================================================================
// Mask Loading
// ============================================================================

object BlendMaskLoader {
    private val MASK_DIR = Path.of("blend-masks")

    fun loadMasks(): Map<String, BufferedImage> {
        val horizontal = loadMask("horizontal.png")
        val vertical = loadMask("vertical.png")
        val diagonal = loadMask("diagonal.png")
        val diagonalWithNeighbors = loadMask("diagonal_with_neighbors.png")

        return buildMap {
            // Edge masks
            put("right", horizontal.flipHorizontal())
            put("left", horizontal)
            put("top", vertical)
            put("bottom", vertical.flipVertical())

            // Diagonal masks
            put("top_right", diagonal)
            put("top_left", diagonal.flipHorizontal())
            put("bottom_right", diagonal.flipVertical())
            put("bottom_left", diagonal.flipBoth())

            // Corner masks
            put("top_right_corner", diagonalWithNeighbors)
            put("top_left_corner", diagonalWithNeighbors.flipHorizontal())
            put("bottom_right_corner", diagonalWithNeighbors.flipVertical())
            put("bottom_left_corner", diagonalWithNeighbors.flipBoth())
        }
    }

    private fun loadMask(filename: String): BufferedImage {
        return ImageIO.read(MASK_DIR.resolve(filename).toFile())
    }

    private fun BufferedImage.flipHorizontal(): BufferedImage =
        flip(horizontally = true, vertically = false)

    private fun BufferedImage.flipVertical(): BufferedImage =
        flip(horizontally = false, vertically = true)

    private fun BufferedImage.flipBoth(): BufferedImage =
        flip(horizontally = true, vertically = true)

    private fun BufferedImage.flip(horizontally: Boolean, vertically: Boolean): BufferedImage {
        val flipped = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val newX = if (horizontally) width - 1 - x else x
                val newY = if (vertically) height - 1 - y else y
                flipped.setRGB(newX, newY, getRGB(x, y))
            }
        }

        return flipped
    }
}

// ============================================================================
// Public API
// ============================================================================

fun applyTileBlends(
    blendTileData: BlendTileData,
    height: UInt,
    tiles: Map<UInt, Map<UInt, UShort>>,
    textureManager: TextureManager,
    cellSize: UInt,
    graphics: Graphics2D
) {
    val engine = TileBlendingEngine(height, tiles, textureManager, cellSize)

    blendTileData.blends.rowMap().forEach { (x, columnMap) ->
        columnMap.forEach { (y, blendValue) ->
            if (blendValue != 0u) {
                val descriptionIndex = (blendValue - 1u).toInt()
                val blendDescription = blendTileData.blendDescriptions[descriptionIndex]
                val coordinate = TileCoordinate(x.toInt(), y.toInt())

                engine.processBlend(blendDescription, coordinate, graphics)
            }
        }
    }
}