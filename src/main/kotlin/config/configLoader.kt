package config

import CELL_SIZE
import HEIGHTMAP_SCALE
import PREVIEW_CELL_SIZE
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

// ============================================================================
// Configuration Data Classes
// ============================================================================

data class MapExporterConfig(
    val cellSize: Int,
    val previewCellSize: Int,
    val generatePreviews: Boolean,
    val generateTileMap: Boolean,
    val generateHeightMap: Boolean,
    val heightMapScale: Double,
    val pathToMapsFolder: Path,
    val pathToTexturesFolder: Path,
    val pathToOutput: Path,
    val pathToTerrainIni: Path
)

// ============================================================================
// Configuration Loading
// ============================================================================

fun loadConfig(configPath: Path): MapExporterConfig {
    if (!configPath.exists()) {
        throw IllegalStateException("Config file not found: $configPath")
    }

    val properties = mutableMapOf<String, String>()

    configPath.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
            return@forEach
        }

        val parts = trimmed.split("=", limit = 2)
        if (parts.size == 2) {
            val key = parts[0].trim()
            val value = parts[1].trim()
            properties[key] = value
        }
    }

    val cellSize = properties["cell_size"]?.toInt() ?: CELL_SIZE
    val previewCellSize = properties["preview_cell_size"]?.toInt() ?: PREVIEW_CELL_SIZE
    val heightMapScale = properties["heightmap_scale_value"]?.toDouble() ?: HEIGHTMAP_SCALE

    val generatePreviews = properties["generate_previews"]?.toInt() ?: 0
    val generateTileMap = properties["generate_tilemap"]?.toInt() ?: 0
    val generateHeightMap = properties["generate_heightmap"]?.toInt() ?: 0

    val pathToMapsFolder = properties["path_to_maps_folder"]
        ?.let { Path.of(it) }
        ?: throw IllegalStateException("Missing required config: path_to_maps_folder")

    val pathToTexturesFolder = properties["path_to_textures_folder"]
        ?.let { Path.of(it) }
        ?: throw IllegalStateException("Missing required config: path_to_textures_folder")

    val pathToOutput = properties["path_to_output"]
        ?.let { Path.of(it) }
        ?: throw IllegalStateException("Missing required config: path_to_output")

    val pathToTerrainIni = properties["path_to_terrain_ini"]
        ?.let { Path.of(it) }
        ?: throw IllegalStateException("Missing required config: path_to_terrain_ini")

    return MapExporterConfig(
        cellSize = cellSize,
        previewCellSize = previewCellSize,
        generatePreviews = generatePreviews == 1,
        generateTileMap = generateTileMap == 1,
        generateHeightMap = generateHeightMap == 1,
        heightMapScale = heightMapScale,
        pathToMapsFolder = pathToMapsFolder,
        pathToTexturesFolder = pathToTexturesFolder,
        pathToOutput = pathToOutput,
        pathToTerrainIni = pathToTerrainIni
    )
}