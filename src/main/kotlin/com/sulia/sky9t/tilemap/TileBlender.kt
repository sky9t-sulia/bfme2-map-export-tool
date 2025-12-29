package com.sulia.sky9t.tilemap

import com.sulia.sky9t.config.MapExporterConfig
import com.sulia.sky9t.extension.applyAlphaMask
import com.sulia.sky9t.extension.height
import com.sulia.sky9t.texture.TextureManager
import de.darkatra.bfme2.map.MapFile
import de.darkatra.bfme2.map.blendtile.BlendDescription
import de.darkatra.bfme2.map.blendtile.BlendDirection
import de.darkatra.bfme2.map.blendtile.BlendFlags
import java.awt.Graphics2D
import java.awt.Point
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

// ============================================================================
// Domain Models
// ============================================================================

enum class ExplicitBlendDirection {
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
    val blendDirection: ExplicitBlendDirection,
    val textureName: String,
)

data class BlendContext(
    val shouldBlend: Boolean,
    val direction: ExplicitBlendDirection,
    val maskType: BlendMaskType,
) {
    companion object {
        val NONE = BlendContext(
            shouldBlend = false,
            direction = ExplicitBlendDirection.NONE,
            maskType = BlendMaskType.NONE,
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
    private val previewTileSize: UInt,
    private val tileSize: UInt,
    private val imageBuffer: BufferedImage
) {
    private val maskCache: Map<String, BufferedImage> by lazy {
        BlendMaskLoader.loadMasks()
    }

    private var secondaryTileValue: UInt = 0u

    fun processBlend(
        blendDescription: BlendDescription,
        coordinate: TileCoordinate,
        graphics: Graphics2D,
        isThreeWayBlend: Boolean = false
    ) {
        secondaryTileValue = blendDescription.secondaryTextureTile

        val direction = blendDescription.toSimpleDirection()
        val tile = getTileInfo(coordinate, direction) ?: return

        // For 3-way blends, check what's already rendered
        val baseTextureName = if (isThreeWayBlend) {
            getRenderedTextureName(coordinate) ?: tile.textureName
        } else {
            tile.textureName
        }

        val primaryContext = analyzeBlendContext(
            tile,
            coordinate,
            baseTextureName,
            isThreeWayBlend
        )

        if (primaryContext.shouldBlend) {
            applyBlend(primaryContext, coordinate, graphics)
        }
    }

    private fun getRenderedTextureName(coordinate: TileCoordinate): String? {
        val screenPosition = coordinate.toScreenPosition(height, previewTileSize)

        // Sample from center of the tile
        val centerX = screenPosition.x + previewTileSize.toInt() / 2
        val centerY = screenPosition.y + previewTileSize.toInt() / 2

        // Bounds check
        if (centerX < 0 || centerX >= imageBuffer.width ||
            centerY < 0 || centerY >= imageBuffer.height) {
            return null
        }

        // Get the RGB color at this position
        val rgb = imageBuffer.getRGB(centerX, centerY)

        // Match color to texture name
        return textureManager.getTextureNameFromColor(rgb)
    }

    private fun applyBlend(
        context: BlendContext,
        coordinate: TileCoordinate,
        graphics: Graphics2D
    ) {
        val (texture, _) = textureManager.getTileTextureFromTileValue(secondaryTileValue.toUShort()) ?: return
        val targetImage = textureManager.getTextureImage(texture)
        val screenPosition = coordinate.toScreenPosition(height, previewTileSize)

        val mask = getMask(context) ?: return
        val subtexture = extractSubtexture(targetImage, coordinate)
        val blendedImage = subtexture.applyAlphaMask(mask)

        graphics.drawImage(
            blendedImage,
            screenPosition.x,
            screenPosition.y,
            previewTileSize.toInt(),
            previewTileSize.toInt(),
            null
        )
    }

    private fun extractSubtexture(image: BufferedImage, coordinate: TileCoordinate): BufferedImage {
        val yFlipped = (height - 1u) - coordinate.y.toUInt()
        val cellCount = image.width.toUInt() / tileSize
        val texX = (coordinate.x.toUInt() % cellCount) * tileSize
        val texY = (yFlipped % cellCount) * tileSize

        return image.getSubimage(texX.toInt(), texY.toInt(), tileSize.toInt(), tileSize.toInt())
    }

    private fun analyzeBlendContext(
        tile: TileInfo,
        coordinate: TileCoordinate,
        baseTextureName: String,
        isThreeWayBlend: Boolean
    ): BlendContext {
        return when {
            tile.blendDirection.isDiagonal -> analyzeDiagonalBlend(
                tile,
                coordinate,
                baseTextureName,
                isThreeWayBlend
            )
            tile.blendDirection.isEdge -> analyzeEdgeBlend(
                tile,
                coordinate,
                baseTextureName,
                isThreeWayBlend
            )
            else -> BlendContext.NONE
        }
    }

    private fun analyzeDiagonalBlend(
        tile: TileInfo,
        coord: TileCoordinate,
        baseTextureName: String,
        isThreeWayBlend: Boolean,
    ): BlendContext {
        val neighbors = coord.getDiagonalNeighbors(tile.blendDirection)
        val neighborTextures = if (isThreeWayBlend) {
            listOfNotNull(
                getRenderedTextureName(neighbors.diagonal),
                getRenderedTextureName(neighbors.adjacentSide1),
                getRenderedTextureName(neighbors.adjacentSide2),
            ).filter { it != baseTextureName }
        } else {
            listOfNotNull(
                getTileInfo(neighbors.diagonal, ExplicitBlendDirection.NONE),
                getTileInfo(neighbors.adjacentSide1, ExplicitBlendDirection.NONE),
                getTileInfo(neighbors.adjacentSide2, ExplicitBlendDirection.NONE),
            ).filter { it.textureName != baseTextureName }
                .map { it.textureName }
        }

        if (neighborTextures.isEmpty()) return BlendContext.NONE

        val (targetTexture, _) = textureManager.getTileTextureFromTileValue(
            secondaryTileValue.toUShort(),
            coord.x.toUInt(),
            coord.y.toUInt()
        ) ?: return BlendContext.NONE

        val matchCount = neighborTextures.count { it == targetTexture.name }

        val maskType = when {
            matchCount >= 2 -> BlendMaskType.CORNER
            else -> BlendMaskType.DIAGONAL_ONLY
        }

        return BlendContext(
            shouldBlend = true,
            direction = tile.blendDirection,
            maskType = maskType,
        )
    }

    private fun analyzeEdgeBlend(
        tile: TileInfo,
        coord: TileCoordinate,
        baseTextureName: String,
        isThreeWayBlend: Boolean
    ): BlendContext {
        val neighbor = coord.getEdgeNeighbor(tile.blendDirection)

        val neighborTextureName = if (isThreeWayBlend) {
            getRenderedTextureName(neighbor)
        } else {
            getTileInfo(neighbor, ExplicitBlendDirection.NONE)?.textureName
        }

        return if (neighborTextureName != null && neighborTextureName != baseTextureName) {
            BlendContext(
                shouldBlend = true,
                direction = tile.blendDirection,
                maskType = BlendMaskType.EDGE
            )
        } else {
            BlendContext.NONE
        }
    }

    private fun getTileInfo(coordinate: TileCoordinate, direction: ExplicitBlendDirection): TileInfo? {
        val tileValue = getTileValue(coordinate)
        if (tileValue == UInt.MAX_VALUE) return null

        val (textureInfo, _) = textureManager.getTileTextureFromTileValue(tileValue.toUShort()) ?: return null

        return TileInfo(
            tileValue = tileValue,
            blendDirection = direction,
            textureName = textureInfo.name,
        )
    }

    private fun getTileValue(coordinate: TileCoordinate): UInt {
        return tiles[coordinate.x.toUInt()]
            ?.get(coordinate.y.toUInt())
            ?.toUInt()
            ?: UInt.MAX_VALUE
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

private fun TileCoordinate.getDiagonalNeighbors(direction: ExplicitBlendDirection): NeighborAnalysis {
    return when (direction) {
        ExplicitBlendDirection.TOP_LEFT -> NeighborAnalysis(
            diagonal = offset(-1, 1),
            adjacentSide1 = offset(0, 1),
            adjacentSide2 = offset(-1, 0),
        )
        ExplicitBlendDirection.TOP_RIGHT -> NeighborAnalysis(
            diagonal = offset(1, 1),
            adjacentSide1 = offset(0, 1),
            adjacentSide2 = offset(1, 0),
        )
        ExplicitBlendDirection.BOTTOM_LEFT -> NeighborAnalysis(
            diagonal = offset(-1, -1),
            adjacentSide1 = offset(0, -1),
            adjacentSide2 = offset(-1, 0),
        )
        ExplicitBlendDirection.BOTTOM_RIGHT -> NeighborAnalysis(
            diagonal = offset(1, -1),
            adjacentSide1 = offset(0, -1),
            adjacentSide2 = offset(1, 0),
        )
        else -> error("Invalid diagonal direction: $direction")
    }
}

private fun TileCoordinate.getEdgeNeighbor(direction: ExplicitBlendDirection): TileCoordinate {
    return when (direction) {
        ExplicitBlendDirection.LEFT -> offset(-1, 0)
        ExplicitBlendDirection.RIGHT -> offset(1, 0)
        ExplicitBlendDirection.TOP -> offset(0, 1)
        ExplicitBlendDirection.BOTTOM -> offset(0, -1)
        else -> error("Invalid edge direction: $direction")
    }
}

private fun TileCoordinate.toScreenPosition(mapHeight: UInt, cellSize: UInt): Point {
    val yFlipped = (mapHeight - 1u) - y.toUInt()
    return Point((x.toUInt() * cellSize).toInt(), (yFlipped * cellSize).toInt())
}

private val ExplicitBlendDirection.edgeMaskKey: String?
    get() = when (this) {
        ExplicitBlendDirection.LEFT -> "left"
        ExplicitBlendDirection.RIGHT -> "right"
        ExplicitBlendDirection.TOP -> "top"
        ExplicitBlendDirection.BOTTOM -> "bottom"
        else -> null
    }

private val ExplicitBlendDirection.diagonalMaskKey: String?
    get() = when (this) {
        ExplicitBlendDirection.TOP_LEFT -> "top_left"
        ExplicitBlendDirection.TOP_RIGHT -> "top_right"
        ExplicitBlendDirection.BOTTOM_LEFT -> "bottom_left"
        ExplicitBlendDirection.BOTTOM_RIGHT -> "bottom_right"
        else -> null
    }

private val ExplicitBlendDirection.cornerMaskKey: String?
    get() = when (this) {
        ExplicitBlendDirection.TOP_LEFT -> "top_left_corner"
        ExplicitBlendDirection.TOP_RIGHT -> "top_right_corner"
        ExplicitBlendDirection.BOTTOM_LEFT -> "bottom_left_corner"
        ExplicitBlendDirection.BOTTOM_RIGHT -> "bottom_right_corner"
        else -> null
    }

private fun BlendDescription.toSimpleDirection(): ExplicitBlendDirection {
    val isFlipped = flags == BlendFlags.FLIPPED ||
            flags == BlendFlags.FLIPPED_ALSO_HAS_BOTTOM_LEFT_OR_TOP_RIGHT_BLEND

    return when (blendDirection) {
        BlendDirection.BLEND_TOWARDS_RIGHT ->
            if (isFlipped) ExplicitBlendDirection.LEFT
            else ExplicitBlendDirection.RIGHT

        BlendDirection.BLEND_TOWARDS_TOP ->
            if (isFlipped) ExplicitBlendDirection.BOTTOM
            else ExplicitBlendDirection.TOP

        BlendDirection.BLEND_TOWARDS_TOP_RIGHT ->
            if (isFlipped) ExplicitBlendDirection.BOTTOM_RIGHT
            else ExplicitBlendDirection.TOP_RIGHT

        BlendDirection.BLEND_TOWARDS_TOP_LEFT ->
            if (isFlipped) ExplicitBlendDirection.BOTTOM_LEFT
            else ExplicitBlendDirection.TOP_LEFT
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
    mapFile: MapFile,
    textureManager: TextureManager,
    config: MapExporterConfig,
    graphics: Graphics2D,
    imageBuffer: BufferedImage
) {
    val engine = TileBlendingEngine(
        mapFile.height(),
        mapFile.blendTileData.tiles.rowMap(),
        textureManager, config.previewTileSize,
        config.tileSize,
        imageBuffer
    )

    mapFile.blendTileData.blends.rowMap().forEach { (x, columnMap) ->
        columnMap.forEach { (y, blendValue) ->
            val coordinate = TileCoordinate(x.toInt(), y.toInt())

            // First blend layer (always process if there's any blending)
            if (blendValue != 0u) {
                val descriptionIndex = (blendValue - 1u).toInt()
                val blendDescription = mapFile.blendTileData.blendDescriptions[descriptionIndex]
                engine.processBlend(blendDescription, coordinate, graphics)
            }

            // Second blend layer (3-way blending)
            val threeWayDescriptionIndex = mapFile.blendTileData.threeWayBlends.rowMap()[x]?.get(y)
            if (threeWayDescriptionIndex != null && threeWayDescriptionIndex != 0u) {
                val threeWayBlendDescription = mapFile.blendTileData.blendDescriptions[(threeWayDescriptionIndex - 1u).toInt()]
                engine.processBlend(threeWayBlendDescription, coordinate, graphics, true)
            }
        }
    }
}