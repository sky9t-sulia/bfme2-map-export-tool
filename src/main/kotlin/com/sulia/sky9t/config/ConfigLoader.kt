package com.sulia.sky9t.config

import com.sulia.sky9t.BLOCK_SIZE
import com.sulia.sky9t.TILE_SIZE
import com.sulia.sky9t.PREVIEW_TILE_SIZE
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

// ============================================================================
// Domain Models
// ============================================================================

data class MapExporterConfig(
    val tileSize: UInt,
    val blockSize: UInt,
    val previewTileSize: UInt,
    val generatePreview: Boolean,
    val splitImageByBlocks: Boolean,
    val blendTiles: Boolean,
    val generateTileMap: Boolean,
    val generateHeightMap: Boolean,
    val pathToMapsFolder: Path,
    val pathToTexturesFolder: Path,
    val pathToOutput: Path,
    val pathToTerrainIni: Path
)

// ============================================================================
// Config Loader
// ============================================================================

class ConfigLoader(private val configPath: Path) {
    private val properties = mutableMapOf<String, String>()

    fun load(): MapExporterConfig {
        validateConfigExists()
        parseConfigFile()
        return buildConfig()
    }

    private fun validateConfigExists() {
        if (!configPath.exists()) {
            throw IllegalStateException("Config file not found: $configPath")
        }
    }

    private fun parseConfigFile() {
        configPath.readLines().forEach { line ->
            parseLine(line.trim())
        }
    }

    private fun parseLine(line: String) {
        if (shouldSkipLine(line)) return

        val (key, value) = parseKeyValue(line) ?: return
        properties[key] = value
    }

    private fun shouldSkipLine(line: String): Boolean {
        return line.isEmpty() || line.startsWith("#") || line.startsWith(";")
    }

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val parts = line.split("=", limit = 2)
        if (parts.size != 2) return null

        val key = parts[0].trim()
        val value = parts[1].trim()
        return key to value
    }

    private fun buildConfig(): MapExporterConfig {
        return MapExporterConfig(
            tileSize = getUInt("tile_size", TILE_SIZE),
            previewTileSize = getUInt("preview_tile_size", PREVIEW_TILE_SIZE),
            blockSize = getUInt("block_size", BLOCK_SIZE),
            splitImageByBlocks = getBoolean("split_image_by_blocks", false),
            generatePreview = getBoolean("generate_preview", false),
            blendTiles = getBoolean("blend_tiles", true),
            generateTileMap = getBoolean("generate_tilemap", false),
            generateHeightMap = getBoolean("generate_heightmap", false),
            pathToMapsFolder = getRequiredPath("path_to_maps_folder"),
            pathToTexturesFolder = getRequiredPath("path_to_textures_folder"),
            pathToOutput = getRequiredPath("path_to_output"),
            pathToTerrainIni = getRequiredPath("path_to_terrain_ini")
        )
    }

    private fun getUInt(key: String, default: UInt): UInt {
        return properties[key]?.toUIntOrNull() ?: default
    }

    private fun getBoolean(key: String, default: Boolean): Boolean {
        return when (properties[key]?.toIntOrNull()) {
            1 -> true
            0 -> false
            else -> default
        }
    }

    private fun getRequiredPath(key: String): Path {
        val value = properties[key]
            ?: throw IllegalStateException("Missing required config: $key")
        return Path.of(value)
    }
}

// ============================================================================
// Public API Function
// ============================================================================

fun loadConfig(configPath: Path): MapExporterConfig {
    val loader = ConfigLoader(configPath)
    return loader.load()
}